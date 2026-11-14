package com.freeturn.lite;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

final class FreeturnUri {
    private static final String SCHEME = "freeturn://";
    private static final List<String> KCP_NUMBER_KEYS = List.of(
            "nodelay", "interval", "resend", "nc", "sndwnd", "rcvwnd", "mtu"
    );

    private FreeturnUri() {
    }

    static void applyTo(CoreConfig config, String rawLink) {
        Map<String, String> values = decode(rawLink);
        require("1".equals(values.get("v")), "unsupported link version");
        require(notBlank(values.get("provider")), "missing provider");
        require(notBlank(values.get("peer")), "missing peer");

        config.provider = values.get("provider");
        config.peer = values.get("peer");
        config.udpTransport = "udp".equalsIgnoreCase(values.getOrDefault("transport", ""));
        config.tcpMode = "tcp".equalsIgnoreCase(values.getOrDefault("mode", ""));
        config.bond = "true".equalsIgnoreCase(values.getOrDefault("bond", "false"));
        config.kcpFlags = kcpFlags(values.get("kcp"));
        String obf = values.getOrDefault("obf", "");
        config.obfProfile = obf.isBlank() ? "none" : obf;
        config.obfKey = values.getOrDefault("key", "");
        config.obfTimingMs = intValue(values.get("timing"), 0);
        config.threads = intValue(values.get("n"), "direct".equals(config.provider) ? 0 : config.threads);
        config.streamsPerCred = intValue(values.get("spc"), config.streamsPerCred);
        config.clientId = values.getOrDefault("cid", "");
        config.subUrl = "";
        String callLink = values.getOrDefault("vk", "");
        if (!callLink.isBlank()) config.links = callLink;
        String listen = values.getOrDefault("listen", "");
        if (!listen.isBlank()) config.listen = listen;
        String dnsMode = values.getOrDefault("dns", "");
        if (!dnsMode.isBlank()) config.dnsMode = dnsMode;
        config.dnsServers = values.getOrDefault("dnss", "");
        config.manualCaptcha = "true".equalsIgnoreCase(values.getOrDefault("mcap", "false"));
        String name = values.getOrDefault("name", "");
        if (!name.isBlank()) config.wireGuardName = sanitizeTunnelName(name);
        String wg = values.getOrDefault("wg", "");
        if (!wg.isBlank()) config.wireGuardConfig = wg;
        config.wireGuardMtu = intValue(values.get("mtu"), config.wireGuardMtu);
    }

    static Map<String, String> decode(String rawLink) {
        String trimmed = rawLink == null ? "" : rawLink.trim();
        require(trimmed.regionMatches(true, 0, SCHEME, 0, SCHEME.length()), "invalid scheme");
        String payload = trimmed.substring(SCHEME.length()).trim();
        int fragment = payload.indexOf('#');
        if (fragment >= 0) payload = payload.substring(0, fragment);
        int query = payload.indexOf('?');
        if (query >= 0) payload = payload.substring(0, query);
        require(!payload.isBlank(), "empty payload");
        while (payload.length() % 4 != 0) payload += "=";
        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        return JsonCursor.parseObject(json);
    }

    private static String kcpFlags(String rawKcp) {
        if (rawKcp == null || rawKcp.isBlank()) return "";
        Map<String, String> kcp = JsonCursor.parseObject(rawKcp);
        StringBuilder flags = new StringBuilder();
        for (String key : KCP_NUMBER_KEYS) {
            if (kcp.containsKey(key)) flags.append("-kcp-").append(key).append(' ').append(kcp.get(key)).append(' ');
        }
        // Булевый флаг Go принимает значение только через "=".
        if (kcp.containsKey("acknodelay")) flags.append("-kcp-acknodelay=").append(kcp.get("acknodelay"));
        return flags.toString().trim();
    }

    private static int intValue(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String sanitizeTunnelName(String raw) {
        String name = raw.trim().replaceAll("[^A-Za-z0-9_.-]+", "-");
        return name.isBlank() ? "freeturn-wg" : name;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
