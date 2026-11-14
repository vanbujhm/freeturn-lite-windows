package com.freeturn.lite;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class UpdateCheckerTest {
    // Форма ответа GitHub /releases/latest: вложенные объекты, массив assets, null,
    // скобки и кавычки внутри строк.
    private static final String RELEASE_JSON = """
            {
              "url": "https://api.github.com/repos/vanbujhm/freeturn-lite-windows/releases/1",
              "html_url": "https://github.com/vanbujhm/freeturn-lite-windows/releases/tag/v1.2.0",
              "id": 1,
              "author": {"login": "vanbujhm", "site_admin": false},
              "tag_name": "v1.2.0",
              "name": "FreeTurnLite 1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "FreeTurnLite-1.2.0-windows-x64.zip", "size": 62497022, "uploader": {"login": "vanbujhm"}},
                {"name": "FreeTurnLite-1.2.0-windows-x64.zip.sha256", "label": null}
              ],
              "body": "## What's new\\n- \\"quotes\\", [brackets] and {braces}"
            }
            """;

    private interface Check {
        void run() throws Exception;
    }

    private static int failures;

    public static void main(String[] args) {
        run("newer versions are detected", UpdateCheckerTest::newerVersionsAreDetected);
        run("same or older versions are not updates", UpdateCheckerTest::sameOrOlderAreNotUpdates);
        run("garbage versions are not updates", UpdateCheckerTest::garbageIsNotUpdate);
        run("release json is parsed", UpdateCheckerTest::releaseJsonIsParsed);
        run("release without version is rejected", UpdateCheckerTest::releaseWithoutVersionIsRejected);
        run("foreign release page is replaced", UpdateCheckerTest::foreignPageIsReplaced);
        run("json arrays are skipped", UpdateCheckerTest::jsonArraysAreSkipped);
        run("server with newer release offers update", UpdateCheckerTest::serverWithNewerRelease);
        run("server with same release offers nothing", UpdateCheckerTest::serverWithSameRelease);
        run("no releases is not an error", UpdateCheckerTest::noReleasesIsNotAnError);
        run("server error is reported", UpdateCheckerTest::serverErrorIsReported);
        run("update check setting is saved", UpdateCheckerTest::settingIsSaved);
        if (failures > 0) {
            System.out.println(failures + " test(s) failed");
            System.exit(1);
        }
        System.out.println("All update tests passed");
    }

    private static void newerVersionsAreDetected() {
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.0"), "minor");
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"), "numeric, not text, compare");
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"), "major with fewer parts");
        assertTrue(UpdateChecker.isNewer("1.1.0.1", "1.1.0"), "extra part");
        assertTrue(UpdateChecker.isNewer("V1.2.0-beta", "1.1.0"), "suffix is ignored");
    }

    private static void sameOrOlderAreNotUpdates() {
        assertTrue(!UpdateChecker.isNewer("v1.1.0", "1.1.0"), "same with prefix");
        assertTrue(!UpdateChecker.isNewer("1.1", "1.1.0"), "same with fewer parts");
        assertTrue(!UpdateChecker.isNewer("1.0.9", "1.1.0"), "older");
        assertTrue(!UpdateChecker.isNewer("1.1.0+build", "1.1.0"), "build metadata");
    }

    private static void garbageIsNotUpdate() {
        assertTrue(!UpdateChecker.isNewer("latest", "1.1.0"), "word");
        assertTrue(!UpdateChecker.isNewer("", "1.1.0"), "empty");
        assertTrue(!UpdateChecker.isNewer("1..2", "1.1.0"), "empty part");
        assertTrue(!UpdateChecker.isNewer("99999999999.0", "1.1.0"), "overflow");
    }

    private static void releaseJsonIsParsed() throws Exception {
        UpdateChecker.Release release = UpdateChecker.parseRelease(RELEASE_JSON);
        assertEquals("1.2.0", release.version());
        assertEquals("https://github.com/vanbujhm/freeturn-lite-windows/releases/tag/v1.2.0", release.pageUrl());
    }

    private static void releaseWithoutVersionIsRejected() {
        try {
            UpdateChecker.parseRelease("{\"tag_name\": \"nightly\"}");
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    private static void foreignPageIsReplaced() throws Exception {
        UpdateChecker.Release release = UpdateChecker.parseRelease(
                "{\"tag_name\": \"v1.2.0\", \"html_url\": \"https://evil.example/download\"}");
        assertEquals("https://github.com/" + AppInfo.REPOSITORY + "/releases/latest", release.pageUrl());
    }

    private static void jsonArraysAreSkipped() {
        Map<String, String> fields = JsonCursor.parseObject("{\"a\": [1, [2, \"]\"], {\"b\": 3}], \"c\": \"d\"}");
        assertEquals("[1, [2, \"]\"], {\"b\": 3}]", fields.get("a"));
        assertEquals("d", fields.get("c"));
    }

    private static void serverWithNewerRelease() throws Exception {
        AtomicReference<String> userAgent = new AtomicReference<>("");
        withServer(200, RELEASE_JSON, userAgent, endpoint -> {
            Optional<UpdateChecker.Release> release = UpdateChecker.newerRelease(endpoint, "1.1.0");
            assertTrue(release.isPresent(), "update expected");
            assertEquals("1.2.0", release.get().version());
        });
        assertEquals("FreeTurnLite/" + AppInfo.VERSION, userAgent.get());
    }

    private static void serverWithSameRelease() throws Exception {
        withServer(200, RELEASE_JSON, new AtomicReference<>(), endpoint ->
                assertTrue(UpdateChecker.newerRelease(endpoint, "1.2.0").isEmpty(), "no update expected"));
    }

    private static void noReleasesIsNotAnError() throws Exception {
        withServer(404, "{\"message\": \"Not Found\"}", new AtomicReference<>(), endpoint ->
                assertTrue(UpdateChecker.newerRelease(endpoint, "1.1.0").isEmpty(), "no update expected"));
    }

    private static void serverErrorIsReported() throws Exception {
        withServer(403, "{\"message\": \"API rate limit exceeded\"}", new AtomicReference<>(), endpoint -> {
            try {
                UpdateChecker.newerRelease(endpoint, "1.1.0");
                throw new AssertionError("expected IOException");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("403"), "status in message: " + expected.getMessage());
            }
        });
    }

    private static void settingIsSaved() throws Exception {
        assertTrue(new CoreConfig().checkUpdates, "on by default");
        Path file = Files.createTempFile("freeturn-config", ".properties");
        try {
            CoreConfig config = new CoreConfig();
            config.checkUpdates = false;
            config.save(file);
            assertTrue(!CoreConfig.load(file).checkUpdates, "saved off");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private interface EndpointCheck {
        void run(URI endpoint) throws Exception;
    }

    private static void withServer(int status, String body, AtomicReference<String> userAgent, EndpointCheck check)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/releases/latest", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            check.run(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/releases/latest"));
        } finally {
            server.stop(0);
        }
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
