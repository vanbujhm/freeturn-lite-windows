package com.freeturn.lite;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Сравнивает версию приложения с последним релизом на GitHub. */
final class UpdateChecker {
    static final URI LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/" + AppInfo.REPOSITORY + "/releases/latest");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    record Release(String version, String pageUrl) {
    }

    private UpdateChecker() {
    }

    /**
     * Возвращает релиз, если он новее {@code currentVersion}. /releases/latest
     * не отдаёт черновики и пре-релизы, а без единого релиза отвечает 404.
     */
    static Optional<Release> newerRelease(URI endpoint, String currentVersion)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                // Без User-Agent GitHub API отвечает 403.
                .header("User-Agent", "FreeTurnLite/" + AppInfo.VERSION)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() != 200) {
            throw new IOException("GitHub ответил HTTP " + response.statusCode());
        }
        Release latest = parseRelease(response.body());
        return isNewer(latest.version(), currentVersion) ? Optional.of(latest) : Optional.empty();
    }

    static Release parseRelease(String json) throws IOException {
        Map<String, String> fields;
        try {
            fields = JsonCursor.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("не удалось разобрать ответ GitHub: " + e.getMessage(), e);
        }
        String tag = fields.getOrDefault("tag_name", "");
        if (parseVersion(tag).isEmpty()) throw new IOException("в релизе нет версии: " + tag);
        String page = fields.getOrDefault("html_url", "");
        if (!page.startsWith("https://github.com/")) {
            page = "https://github.com/" + AppInfo.REPOSITORY + "/releases/latest";
        }
        return new Release(stripPrefix(tag), page);
    }

    /** true, если candidate новее current; нечисловые версии не считаются новее. */
    static boolean isNewer(String candidate, String current) {
        List<Integer> a = parseVersion(candidate);
        List<Integer> b = parseVersion(current);
        if (a.isEmpty() || b.isEmpty()) return false;
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    /** "v1.2.3-beta+build" -> [1, 2, 3]; пустой список, если это не версия. */
    private static List<Integer> parseVersion(String raw) {
        String version = stripPrefix(raw == null ? "" : raw.trim());
        int suffix = indexOfAny(version, '-', '+');
        if (suffix >= 0) version = version.substring(0, suffix);
        List<Integer> parts = new ArrayList<>();
        for (String part : version.split("\\.", -1)) {
            if (!part.matches("\\d{1,9}")) return List.of();
            parts.add(Integer.parseInt(part));
        }
        return parts;
    }

    private static String stripPrefix(String tag) {
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    private static int indexOfAny(String value, char first, char second) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == first || ch == second) return i;
        }
        return -1;
    }
}
