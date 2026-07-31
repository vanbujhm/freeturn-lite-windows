package com.freeturn.lite;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class FreeturnUri {
    private static final String SCHEME = "freeturn://";

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
        String obf = values.getOrDefault("obf", "");
        config.obfProfile = obf.isBlank() ? "none" : obf;
        config.obfKey = values.getOrDefault("key", "");
        config.threads = intValue(values.get("n"), config.threads);
        config.streamsPerCred = intValue(values.get("spc"), config.streamsPerCred);
        config.clientId = values.getOrDefault("cid", "");
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
        return parseFlatJsonObject(json);
    }

    private static Map<String, String> parseFlatJsonObject(String json) {
        JsonCursor cursor = new JsonCursor(json);
        Map<String, String> result = new LinkedHashMap<>();
        cursor.skipWhitespace();
        cursor.expect('{');
        cursor.skipWhitespace();
        if (cursor.peek('}')) {
            cursor.expect('}');
            return result;
        }
        while (true) {
            cursor.skipWhitespace();
            String key = cursor.readString();
            cursor.skipWhitespace();
            cursor.expect(':');
            cursor.skipWhitespace();
            result.put(key, cursor.readValueAsString());
            cursor.skipWhitespace();
            if (cursor.peek(',')) {
                cursor.expect(',');
                continue;
            }
            cursor.expect('}');
            break;
        }
        cursor.skipWhitespace();
        require(cursor.isAtEnd(), "unexpected trailing data");
        return result;
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

    private static final class JsonCursor {
        private final String source;
        private int index;

        JsonCursor(String source) {
            this.source = source;
        }

        boolean isAtEnd() {
            return index >= source.length();
        }

        void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(source.charAt(index))) index++;
        }

        boolean peek(char expected) {
            return !isAtEnd() && source.charAt(index) == expected;
        }

        void expect(char expected) {
            require(peek(expected), "expected '" + expected + "'");
            index++;
        }

        String readValueAsString() {
            if (peek('"')) return readString();
            int start = index;
            while (!isAtEnd()) {
                char ch = source.charAt(index);
                if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) break;
                index++;
            }
            require(index > start, "expected value");
            return source.substring(start, index);
        }

        String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!isAtEnd()) {
                char ch = source.charAt(index++);
                if (ch == '"') return builder.toString();
                if (ch != '\\') {
                    builder.append(ch);
                    continue;
                }
                require(!isAtEnd(), "bad escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        require(index + 4 <= source.length(), "bad unicode escape");
                        String hex = source.substring(index, index + 4);
                        builder.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape");
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }
    }
}
