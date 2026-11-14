package com.freeturn.lite;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class CoreConfigTest {
    private static final String KEY = "ab".repeat(32);
    private static final String KCP_JSON = "{\"nodelay\":1,\"interval\":40,\"resend\":2,\"nc\":1,"
            + "\"sndwnd\":256,\"rcvwnd\":128,\"mtu\":1200,\"acknodelay\":false}";

    private interface Check {
        void run() throws Exception;
    }

    private static int failures;

    public static void main(String[] args) {
        run("link with kcp object imports ARQ flags", CoreConfigTest::linkImportsKcpProfile);
        run("link imports obfuscation timing", CoreConfigTest::linkImportsObfTiming);
        run("link call URL replaces stale links", CoreConfigTest::linkCallUrlReplacesStaleLinks);
        run("link without call URL keeps links", CoreConfigTest::linkWithoutCallUrlKeepsLinks);
        run("direct link without n leaves stream count to core", CoreConfigTest::directLinkUsesCoreStreamDefault);
        run("link import drops subscription", CoreConfigTest::linkImportDropsSubscription);
        run("link with garbage after object is rejected", CoreConfigTest::linkWithTrailingGarbageIsRejected);
        run("vk command carries options", CoreConfigTest::vkCommandCarriesOptions);
        run("streams-per-cred 10 is passed", CoreConfigTest::streamsPerCredTenIsPassed);
        run("advanced captcha is passed", CoreConfigTest::advancedCaptchaIsPassed);
        run("manual captcha turns advanced off", CoreConfigTest::manualCaptchaTurnsAdvancedOff);
        run("direct command omits vk flags", CoreConfigTest::directCommandOmitsVkFlags);
        run("timing needs obfuscation", CoreConfigTest::timingNeedsObfuscation);
        run("kcp flags need tcp mode", CoreConfigTest::kcpFlagsNeedTcpMode);
        run("direct does not need links", CoreConfigTest::directDoesNotNeedLinks);
        run("vk needs links", CoreConfigTest::vkNeedsLinks);
        run("subscription replaces peer and links", CoreConfigTest::subscriptionReplacesPeerAndLinks);
        run("redacted command hides subscription", CoreConfigTest::redactedCommandHidesSubscription);
        run("saved config restores new fields", CoreConfigTest::savedConfigRestoresNewFields);
        if (args.length > 0) {
            run("core accepts every generated flag", () -> coreAcceptsEveryFlag(args[0]));
            run("core accepts advanced captcha", () -> coreAcceptsAdvancedCaptcha(args[0]));
            run("stop lets core finish cleanly", () -> stopLetsCoreFinishCleanly(args[0]));
        }
        if (failures > 0) {
            System.out.println(failures + " test(s) failed");
            System.exit(1);
        }
        System.out.println("All tests passed");
    }

    private static void linkImportsKcpProfile() {
        CoreConfig config = new CoreConfig();
        FreeturnUri.applyTo(config, link("\"mode\":\"tcp\",\"kcp\":" + KCP_JSON + ",\"name\":\"pc\""));
        assertTrue(config.tcpMode, "mode tcp");
        assertEquals("-kcp-nodelay 1 -kcp-interval 40 -kcp-resend 2 -kcp-nc 1 "
                + "-kcp-sndwnd 256 -kcp-rcvwnd 128 -kcp-mtu 1200 -kcp-acknodelay=false", config.kcpFlags);
        assertEquals("pc", config.wireGuardName);
    }

    private static void linkImportsObfTiming() {
        CoreConfig config = new CoreConfig();
        FreeturnUri.applyTo(config, link("\"obf\":\"rtpopus3\",\"key\":\"" + KEY + "\",\"timing\":20"));
        assertEquals(20, config.obfTimingMs);
    }

    private static void linkCallUrlReplacesStaleLinks() {
        CoreConfig config = new CoreConfig();
        config.links = "https://vk.ru/call/join/old";
        FreeturnUri.applyTo(config, link("\"vk\":\"https://vk.ru/call/join/new\""));
        assertEquals("https://vk.ru/call/join/new", config.links);
    }

    private static void linkWithoutCallUrlKeepsLinks() {
        CoreConfig config = new CoreConfig();
        config.links = "https://vk.ru/call/join/old";
        FreeturnUri.applyTo(config, link(""));
        assertEquals("https://vk.ru/call/join/old", config.links);
    }

    private static void directLinkUsesCoreStreamDefault() {
        CoreConfig config = new CoreConfig();
        FreeturnUri.applyTo(config, encode("{\"v\":1,\"provider\":\"direct\",\"peer\":\"1.2.3.4:56000\"}"));
        assertEquals("direct", config.provider);
        assertEquals(0, config.threads);
    }

    private static void linkImportDropsSubscription() {
        CoreConfig config = new CoreConfig();
        config.subUrl = "https://example.com/sub.md";
        FreeturnUri.applyTo(config, link(""));
        assertEquals("", config.subUrl);
    }

    private static void linkWithTrailingGarbageIsRejected() {
        String raw = encode("{\"v\":1,\"provider\":\"vk\",\"peer\":\"1.2.3.4:56000\",\"kcp\":{\"mtu\":1200}}}");
        try {
            FreeturnUri.applyTo(new CoreConfig(), raw);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void vkCommandCarriesOptions() {
        CoreConfig config = vkConfig();
        config.platform = "mobile";
        config.routes = true;
        config.subUrl = "https://example.com/sub.md";
        List<String> command = config.command();
        assertEquals("mobile", valueOf(command, "-platform"));
        assertEquals("https://example.com/sub.md", valueOf(command, "-sub"));
        assertEquals("https://vk.ru/call/join/a,https://vk.ru/call/join/b", valueOf(command, "-links"));
        assertTrue(command.contains("-routes"), "-routes");
    }

    private static void streamsPerCredTenIsPassed() {
        CoreConfig config = vkConfig();
        config.streamsPerCred = 10;
        assertEquals("10", valueOf(config.command(), "-streams-per-cred"));
    }

    private static void advancedCaptchaIsPassed() {
        CoreConfig config = vkConfig();
        assertTrue(!config.command().contains("-advanced-captcha"), "advanced captcha is off by default");
        config.advancedCaptcha = true;
        assertTrue(config.command().contains("-advanced-captcha"), "advanced captcha flag");
    }

    private static void manualCaptchaTurnsAdvancedOff() {
        CoreConfig config = vkConfig();
        config.advancedCaptcha = true;
        config.manualCaptcha = true;
        List<String> command = config.command();
        assertTrue(command.contains("-manual-captcha"), "manual captcha flag");
        assertTrue(!command.contains("-advanced-captcha"), "advanced captcha with manual");
    }

    private static void directCommandOmitsVkFlags() {
        CoreConfig config = vkConfig();
        config.provider = "direct";
        config.platform = "mobile";
        config.manualCaptcha = true;
        config.advancedCaptcha = true;
        config.udpTransport = true;
        List<String> command = config.command();
        assertEquals("direct", valueOf(command, "-provider"));
        for (String flag : List.of("-links", "-streams-per-cred", "-platform", "-manual-captcha", "-advanced-captcha", "-transport")) {
            assertTrue(!command.contains(flag), flag + " must be omitted");
        }
    }

    private static void timingNeedsObfuscation() {
        CoreConfig config = vkConfig();
        config.obfTimingMs = 20;
        assertTrue(!config.command().contains("-obf-timing"), "timing without profile");
        config.obfProfile = "rtpopus3";
        config.obfKey = KEY;
        assertEquals("20ms", valueOf(config.command(), "-obf-timing"));
    }

    private static void kcpFlagsNeedTcpMode() {
        CoreConfig config = vkConfig();
        config.kcpFlags = "-kcp-interval 40";
        assertTrue(!config.command().contains("-kcp-interval"), "kcp in udp mode");
        config.tcpMode = true;
        assertEquals("40", valueOf(config.command(), "-kcp-interval"));
    }

    private static void directDoesNotNeedLinks() {
        CoreConfig config = vkConfig();
        config.provider = "direct";
        config.links = "";
        assertEquals("", config.validate());
    }

    private static void vkNeedsLinks() {
        CoreConfig config = vkConfig();
        config.links = "";
        assertTrue(!config.validate().isEmpty(), "vk without links must be invalid");
    }

    private static void subscriptionReplacesPeerAndLinks() {
        CoreConfig config = vkConfig();
        config.peer = "";
        config.links = "";
        config.subUrl = "https://example.com/sub.md";
        assertEquals("", config.validate());
    }

    private static void redactedCommandHidesSubscription() {
        CoreConfig config = vkConfig();
        config.subUrl = "https://example.com/secret-sub.md";
        assertTrue(!config.redactedCommand().contains("secret-sub"), "subscription URL is sensitive");
    }

    private static void savedConfigRestoresNewFields() throws Exception {
        Path file = Files.createTempFile("freeturn-config", ".properties");
        try {
            CoreConfig config = new CoreConfig();
            config.platform = "mobile";
            config.subUrl = "https://example.com/sub.md";
            config.obfTimingMs = 25;
            config.routes = true;
            config.kcpFlags = "-kcp-mtu 1000";
            config.advancedCaptcha = true;
            config.save(file);

            CoreConfig restored = CoreConfig.load(file);
            assertEquals("mobile", restored.platform);
            assertEquals("https://example.com/sub.md", restored.subUrl);
            assertEquals(25, restored.obfTimingMs);
            assertTrue(restored.routes, "routes");
            assertEquals("-kcp-mtu 1000", restored.kcpFlags);
            assertTrue(restored.advancedCaptcha, "advanced captcha");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void coreAcceptsEveryFlag(String corePath) throws Exception {
        CoreConfig config = vkConfig();
        config.corePath = corePath;
        config.platform = "mobile";
        config.routes = true;
        config.tcpMode = true;
        config.bond = true;
        config.udpTransport = true;
        config.manualCaptcha = true;
        config.debug = true;
        config.dnsMode = "doh";
        config.dnsServers = "1.1.1.1";
        config.obfProfile = "rtpopus3";
        config.obfKey = KEY;
        config.obfTimingMs = 20;
        config.clientId = "test-client";
        config.kcpFlags = "-kcp-interval 40 -kcp-acknodelay=false";
        assertCoreParses(config);
    }

    // Ручная капча отключает продвинутую, поэтому её флаг проверяется отдельно.
    private static void coreAcceptsAdvancedCaptcha(String corePath) throws Exception {
        CoreConfig config = vkConfig();
        config.corePath = corePath;
        config.advancedCaptcha = true;
        assertTrue(config.command().contains("-advanced-captcha"), "advanced captcha flag");
        assertCoreParses(config);
    }

    private static void assertCoreParses(CoreConfig config) throws Exception {
        // -gen-obf-key завершает ядро сразу после разбора флагов, без сети.
        List<String> command = new ArrayList<>(config.command());
        command.add("-gen-obf-key");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        assertEquals(0, process.waitFor());
        assertTrue(output.toString().trim().matches("[0-9a-f]{64}"), "generated key, got: " + output);
    }

    private static void stopLetsCoreFinishCleanly(String corePath) throws Exception {
        CoreConfig config = new CoreConfig();
        config.corePath = corePath;
        config.provider = "direct";
        config.peer = "127.0.0.1:9";
        config.listen = "127.0.0.1:19000";
        config.threads = 0;
        config.clientId = "test-client";

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        List<String> lines = new ArrayList<>();
        CoreProcessRunner runner = new CoreProcessRunner();
        runner.start(config, new CoreProcessRunner.Listener() {
            @Override
            public void onLine(String line) {
                synchronized (lines) {
                    lines.add(line);
                }
                if (line.contains("version=")) started.countDown();
            }

            @Override
            public void onExit(int exitCode) {
                exited.countDown();
            }

            @Override
            public void onError(String message) {
                throw new AssertionError(message);
            }
        });
        assertTrue(started.await(10, TimeUnit.SECONDS), "core did not start");
        runner.stop();
        assertTrue(exited.await(20, TimeUnit.SECONDS), "core did not exit");
        synchronized (lines) {
            assertTrue(lines.stream().anyMatch(line -> line.contains("Terminating")),
                    "core must log clean shutdown, got: " + lines);
        }
        assertTrue(!runner.isRunning(), "runner must be stopped");
    }

    private static CoreConfig vkConfig() {
        CoreConfig config = new CoreConfig();
        config.corePath = "core.exe";
        config.peer = "1.2.3.4:56000";
        config.links = "https://vk.ru/call/join/a\nhttps://vk.ru/call/join/b";
        return config;
    }

    private static String link(String extraFields) {
        String extra = extraFields.isEmpty() ? "" : "," + extraFields;
        return encode("{\"v\":1,\"provider\":\"vk\",\"peer\":\"1.2.3.4:56000\"" + extra + "}");
    }

    private static String encode(String json) {
        return "freeturn://" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String valueOf(List<String> command, String flag) {
        int index = command.indexOf(flag);
        assertTrue(index >= 0 && index + 1 < command.size(), flag + " missing in " + command);
        return command.get(index + 1);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void run(String name, Check check) {
        try {
            check.run();
            System.out.println("ok   " + name);
        } catch (Throwable e) {
            failures++;
            System.out.println("FAIL " + name + ": " + e);
        }
    }
}
