package com.freeturn.lite;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Минимальный разбор JSON без внешних библиотек: верхний уровень объекта как
 * ключ -> значение. Строки раскодируются, вложенные объекты и массивы
 * возвращаются исходным текстом, остальное (числа, true, null) - как есть.
 */
final class JsonCursor {
    private final String source;
    private int index;

    private JsonCursor(String source) {
        this.source = source;
    }

    static Map<String, String> parseObject(String json) {
        JsonCursor cursor = new JsonCursor(json);
        Map<String, String> result = new LinkedHashMap<>();
        cursor.skipWhitespace();
        cursor.expect('{');
        cursor.skipWhitespace();
        if (cursor.peek('}')) {
            cursor.expect('}');
        } else {
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
        }
        cursor.skipWhitespace();
        require(cursor.isAtEnd(), "unexpected trailing data");
        return result;
    }

    private boolean isAtEnd() {
        return index >= source.length();
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(source.charAt(index))) index++;
    }

    private boolean peek(char expected) {
        return !isAtEnd() && source.charAt(index) == expected;
    }

    private void expect(char expected) {
        require(peek(expected), "expected '" + expected + "'");
        index++;
    }

    private String readValueAsString() {
        if (peek('"')) return readString();
        if (peek('{') || peek('[')) return readNested();
        int start = index;
        while (!isAtEnd()) {
            char ch = source.charAt(index);
            if (ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) break;
            index++;
        }
        require(index > start, "expected value");
        return source.substring(start, index);
    }

    private String readNested() {
        int start = index;
        int depth = 0;
        while (!isAtEnd()) {
            if (peek('"')) {
                readString();
                continue;
            }
            char ch = source.charAt(index++);
            if (ch == '{' || ch == '[') depth++;
            if ((ch == '}' || ch == ']') && --depth == 0) return source.substring(start, index);
        }
        throw new IllegalArgumentException("unterminated object");
    }

    private String readString() {
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
