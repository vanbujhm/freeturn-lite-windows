package com.freeturn.lite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class CoreConfig {
    static final Path CONFIG_DIR = Path.of(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "FreeTurnLite"
    );
    static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    String corePath = defaultCorePath();
    String peer = "";
    String links = "";
    String listen = "127.0.0.1:9000";
    String provider = "vk";
    String platform = "desktop";
    String subUrl = "";
    int threads = 12;
    int streamsPerCred = 12;
    boolean tcpMode = false;
    boolean bond = false;
    String kcpFlags = "";
    boolean udpTransport = false;
    boolean manualCaptcha = false;
    boolean debug = false;
    boolean routes = false;
    String dnsMode = "auto";
    String dnsServers = "";
    String obfProfile = "none";
    String obfKey = "";
    int obfTimingMs = 0;
    String clientId = "";
    boolean useRawArgs = false;
    String rawArgs = "";
    String wireGuardConfig = "";
    String wireGuardName = "freeturn-wg";
    int wireGuardMtu = 1280;

    static CoreConfig load() {
        return load(CONFIG_FILE);
    }

    static CoreConfig load(Path file) {
        CoreConfig config = new CoreConfig();
        if (!Files.exists(file)) {
            return config;
        }
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            p.load(input);
        } catch (IOException ignored) {
            return config;
        }

        config.corePath = resolveCorePath(p.getProperty("corePath", config.corePath));
        config.peer = p.getProperty("peer", config.peer);
        config.links = p.getProperty("links", config.links);
        config.listen = p.getProperty("listen", config.listen);
        config.provider = p.getProperty("provider", config.provider);
        config.platform = p.getProperty("platform", config.platform);
        config.subUrl = p.getProperty("subUrl", config.subUrl);
        config.threads = intProperty(p, "threads", config.threads);
        config.streamsPerCred = intProperty(p, "streamsPerCred", config.streamsPerCred);
        config.tcpMode = boolProperty(p, "tcpMode", config.tcpMode);
        config.bond = boolProperty(p, "bond", config.bond);
        config.kcpFlags = p.getProperty("kcpFlags", config.kcpFlags);
        config.udpTransport = boolProperty(p, "udpTransport", config.udpTransport);
        config.manualCaptcha = boolProperty(p, "manualCaptcha", config.manualCaptcha);
        config.debug = boolProperty(p, "debug", config.debug);
        config.routes = boolProperty(p, "routes", config.routes);
        config.dnsMode = p.getProperty("dnsMode", config.dnsMode);
        config.dnsServers = p.getProperty("dnsServers", config.dnsServers);
        config.obfProfile = p.getProperty("obfProfile", config.obfProfile);
        config.obfKey = p.getProperty("obfKey", config.obfKey);
        config.obfTimingMs = intProperty(p, "obfTimingMs", config.obfTimingMs);
        config.clientId = p.getProperty("clientId", config.clientId);
        config.useRawArgs = boolProperty(p, "useRawArgs", config.useRawArgs);
        config.rawArgs = p.getProperty("rawArgs", config.rawArgs);
        config.wireGuardConfig = p.getProperty("wireGuardConfig", config.wireGuardConfig);
        config.wireGuardName = p.getProperty("wireGuardName", config.wireGuardName);
        config.wireGuardMtu = intProperty(p, "wireGuardMtu", config.wireGuardMtu);
        return config;
    }

    void save() throws IOException {
        Files.createDirectories(CONFIG_DIR);
        save(CONFIG_FILE);
    }

    void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("corePath", corePath);
        p.setProperty("peer", peer);
        p.setProperty("links", links);
        p.setProperty("listen", listen);
        p.setProperty("provider", provider);
        p.setProperty("platform", platform);
        p.setProperty("subUrl", subUrl);
        p.setProperty("threads", Integer.toString(threads));
        p.setProperty("streamsPerCred", Integer.toString(streamsPerCred));
        p.setProperty("tcpMode", Boolean.toString(tcpMode));
        p.setProperty("bond", Boolean.toString(bond));
        p.setProperty("kcpFlags", kcpFlags);
        p.setProperty("udpTransport", Boolean.toString(udpTransport));
        p.setProperty("manualCaptcha", Boolean.toString(manualCaptcha));
        p.setProperty("debug", Boolean.toString(debug));
        p.setProperty("routes", Boolean.toString(routes));
        p.setProperty("dnsMode", dnsMode);
        p.setProperty("dnsServers", dnsServers);
        p.setProperty("obfProfile", obfProfile);
        p.setProperty("obfKey", obfKey);
        p.setProperty("obfTimingMs", Integer.toString(obfTimingMs));
        p.setProperty("clientId", clientId);
        p.setProperty("useRawArgs", Boolean.toString(useRawArgs));
        p.setProperty("rawArgs", rawArgs);
        p.setProperty("wireGuardConfig", wireGuardConfig);
        p.setProperty("wireGuardName", wireGuardName);
        p.setProperty("wireGuardMtu", Integer.toString(wireGuardMtu));
        try (OutputStream output = Files.newOutputStream(file)) {
            p.store(output, "FreeTurnLite for Windows");
        }
    }

    List<String> command() {
        List<String> command = new ArrayList<>();
        command.add(corePath.trim());
        if (useRawArgs) {
            command.addAll(splitCommandLine(rawArgs));
            return command;
        }

        boolean vk = "vk".equals(provider);
        addPair(command, "-sub", subUrl);
        addPair(command, "-peer", peer);
        addPair(command, "-provider", provider);
        if (vk) addPair(command, "-links", normalizeList(links));
        addPair(command, "-listen", listen);
        if (threads > 0) addPair(command, "-n", Integer.toString(threads));
        if (vk && streamsPerCred > 0) {
            addPair(command, "-streams-per-cred", Integer.toString(streamsPerCred));
        }
        if (tcpMode) {
            addPair(command, "-mode", "tcp");
            if (bond) command.add("-bond");
            command.addAll(splitCommandLine(kcpFlags));
        }
        if (vk && udpTransport) addPair(command, "-transport", "udp");
        if (!"none".equals(obfProfile)) {
            addPair(command, "-obf-profile", obfProfile);
            addPair(command, "-obf-key", obfKey);
            if (obfTimingMs > 0) addPair(command, "-obf-timing", obfTimingMs + "ms");
        }
        if (vk && manualCaptcha) command.add("-manual-captcha");
        if (vk && !"desktop".equals(platform)) addPair(command, "-platform", platform);
        if (debug) command.add("-debug");
        String dns = normalizeList(dnsServers);
        if (!dns.isBlank()) addPair(command, "-dns-servers", dns);
        if ("plain".equals(dnsMode) || "doh".equals(dnsMode)) addPair(command, "-dns-mode", dnsMode);
        addPair(command, "-client-id", clientId);
        if (routes) command.add("-routes");
        return command;
    }

    String redactedCommand() {
        List<String> command = command();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < command.size(); i++) {
            if (sb.length() > 0) sb.append(' ');
            String part = command.get(i);
            sb.append(quoteIfNeeded(part));
            if (isSensitiveFlag(part) && i + 1 < command.size()) {
                sb.append(' ').append("******");
                i++;
            }
        }
        return sb.toString();
    }

    String validate() {
        if (corePath.trim().isEmpty()) return "Укажите путь к free-turn-proxy.exe.";
        if (useRawArgs) return rawArgs.trim().isEmpty() ? "Raw arguments пусты." : "";
        boolean subscription = !subUrl.trim().isEmpty();
        if (!subscription && peer.trim().isEmpty()) return "Укажите сервер FreeTurn (-peer).";
        if (!subscription && "vk".equals(provider) && normalizeList(links).isEmpty()) {
            return "Укажите хотя бы одну ссылку звонка VK (-links).";
        }
        if (!isHostPort(listen.trim())) return "Адрес listen должен быть в формате host:port.";
        if (!"none".equals(obfProfile) && !obfKey.matches("^[0-9a-fA-F]{64}$")) {
            return "Obfuscation key должен быть 64 hex символа.";
        }
        return "";
    }

    String wireGuardExport() {
        String raw = wireGuardConfig.trim();
        if (raw.isEmpty()) return "";
        String prepared = raw.replace("\r\n", "\n").replace('\r', '\n');
        prepared = withLocalEndpoint(prepared, wireGuardEndpoint());
        prepared = withMtu(prepared, wireGuardMtu);
        return prepared.stripTrailing() + System.lineSeparator();
    }

    String validateWireGuardExport() {
        if (wireGuardConfig.trim().isEmpty()) return "Вставьте исходный WireGuard .conf.";
        if (!isHostPort(listen.trim())) return "Адрес listen должен быть в формате host:port.";
        if (!wireGuardConfig.contains("[Interface]") || !wireGuardConfig.contains("[Peer]")) {
            return "WireGuard .conf должен содержать секции [Interface] и [Peer].";
        }
        if (wireGuardMtu != 0 && (wireGuardMtu < 1280 || wireGuardMtu > 1500)) {
            return "MTU должен быть 0 или в диапазоне 1280..1500.";
        }
        return "";
    }

    private String wireGuardEndpoint() {
        String value = listen.trim();
        int separator = value.lastIndexOf(':');
        if (separator <= 0) return value;
        String host = value.substring(0, separator);
        String port = value.substring(separator + 1);
        if ("0.0.0.0".equals(host) || "::".equals(host) || "[::]".equals(host)) {
            return "127.0.0.1:" + port;
        }
        return value;
    }

    private static String withLocalEndpoint(String config, String endpoint) {
        String[] lines = config.split("\n", -1);
        List<String> result = new ArrayList<>();
        boolean inPeer = false;
        boolean replaced = false;
        for (String line : lines) {
            String section = line.trim();
            if (section.startsWith("[") && section.endsWith("]")) {
                inPeer = section.equalsIgnoreCase("[Peer]");
            }
            if (inPeer && !replaced && section.regionMatches(true, 0, "Endpoint", 0, 8)
                    && section.contains("=")) {
                result.add("Endpoint = " + endpoint);
                replaced = true;
            } else {
                result.add(line);
            }
        }
        if (!replaced) {
            if (!result.isEmpty() && !result.get(result.size() - 1).isBlank()) result.add("");
            result.add("Endpoint = " + endpoint);
        }
        return String.join("\n", result);
    }

    private static String withMtu(String config, int mtu) {
        if (mtu <= 0) return config;
        String[] lines = config.split("\n", -1);
        List<String> result = new ArrayList<>();
        boolean inInterface = false;
        for (String line : lines) {
            String section = line.trim();
            if (section.startsWith("[") && section.endsWith("]")) {
                inInterface = section.equalsIgnoreCase("[Interface]");
            }
            boolean isMtu = inInterface && section.regionMatches(true, 0, "MTU", 0, 3)
                    && section.contains("=");
            if (!isMtu) result.add(line);
        }
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).trim().equalsIgnoreCase("[Interface]")) {
                result.add(i + 1, "MTU = " + mtu);
                break;
            }
        }
        return String.join("\n", result);
    }

    private static void addPair(List<String> command, String flag, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.isEmpty()) {
            command.add(flag);
            command.add(trimmed);
        }
    }

    private static boolean isSensitiveFlag(String part) {
        return "-links".equals(part) || "-link".equals(part) ||
                "-obf-key".equals(part) || "-client-id".equals(part) || "-sub".equals(part);
    }

    private static String normalizeList(String value) {
        if (value == null) return "";
        return String.join(",",
                value.lines()
                        .flatMap(line -> List.of(line.split("[,;\\s]+")).stream())
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .toList());
    }

    private static boolean isHostPort(String value) {
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) return false;
        try {
            int port = Integer.parseInt(value.substring(separator + 1));
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String quoteIfNeeded(String value) {
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0) return value;
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    static List<String> splitCommandLine(String raw) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaping = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static int intProperty(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean boolProperty(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
    }

    private static String defaultCorePath() {
        List<Path> candidates = List.of(
                appHome().resolve("free-turn-proxy.exe"),
                appHome().resolve("core").resolve("free-turn-proxy.exe"),
                appHome().resolve("app").resolve("free-turn-proxy.exe"),
                Path.of("free-turn-proxy.exe")
        );
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .map(Path::toString)
                .orElse("free-turn-proxy.exe");
    }

    private static String resolveCorePath(String configured) {
        String trimmed = configured == null ? "" : configured.trim();
        if (!trimmed.isEmpty() && Files.isRegularFile(Path.of(trimmed))) return trimmed;
        String detected = defaultCorePath();
        if (Files.isRegularFile(Path.of(detected))) return detected;
        return trimmed.isEmpty() ? detected : trimmed;
    }

    private static Path appHome() {
        try {
            Path location = Path.of(CoreConfig.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isRegularFile(location)) {
                Path parent = location.getParent();
                if (parent != null && "app".equalsIgnoreCase(parent.getFileName().toString())) {
                    return parent.getParent() == null ? parent : parent.getParent();
                }
                return parent == null ? Path.of(".") : parent;
            }
            return location;
        } catch (Exception ignored) {
            return Path.of(".");
        }
    }
}
