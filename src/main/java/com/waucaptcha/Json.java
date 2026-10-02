package com.waucaptcha;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the small JSON replies of WAU Captcha into maps, lists, strings, numbers, booleans and
 * nulls, so the SDK needs no JSON library.
 */
final class Json {
    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /** Parses a whole document; anything malformed throws {@link IllegalArgumentException}. */
    static Object parse(String text) {
        Json reader = new Json(text);
        reader.skipWhitespace();
        Object value = reader.readValue();
        reader.skipWhitespace();
        if (reader.position != text.length()) {
            throw reader.error("unexpected trailing content");
        }
        return value;
    }

    private Object readValue() {
        if (position >= text.length()) {
            throw error("unexpected end of input");
        }
        char next = text.charAt(position);
        switch (next) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                return readLiteral("true", Boolean.TRUE);
            case 'f':
                return readLiteral("false", Boolean.FALSE);
            case 'n':
                return readLiteral("null", null);
            default:
                if (next == '-' || (next >= '0' && next <= '9')) {
                    return readNumber();
                }
                throw error("unexpected character '" + next + "'");
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        position++; // {
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a property name");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();
            char separator = next();
            if (separator == '}') {
                return object;
            }
            if (separator != ',') {
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> array = new ArrayList<>();
        position++; // [
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();
            char separator = next();
            if (separator == ']') {
                return array;
            }
            if (separator != ',') {
                throw error("expected ',' or ']'");
            }
        }
    }

    private String readString() {
        position++; // opening quote
        StringBuilder value = new StringBuilder();
        while (true) {
            char current = next();
            if (current == '"') {
                return value.toString();
            }
            if (current != '\\') {
                value.append(current);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) {
                        throw error("incomplete unicode escape");
                    }
                    value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> throw error("invalid escape '\\" + escaped + "'");
            }
        }
    }

    private Number readNumber() {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        boolean fractional = false;
        while (position < text.length()) {
            char current = text.charAt(position);
            if (current >= '0' && current <= '9') {
                position++;
            } else if (current == '.' || current == 'e' || current == 'E' || current == '+' || current == '-') {
                fractional = true;
                position++;
            } else {
                break;
            }
        }
        String literal = text.substring(start, position);
        try {
            return fractional ? (Number) Double.parseDouble(literal) : (Number) Long.parseLong(literal);
        } catch (NumberFormatException e) {
            throw error("invalid number '" + literal + "'");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) {
            throw error("expected " + literal);
        }
        position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(position);
    }

    private char next() {
        char current = peek();
        position++;
        return current;
    }

    private void expect(char expected) {
        if (next() != expected) {
            throw error("expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String problem) {
        return new IllegalArgumentException("invalid JSON at " + position + ": " + problem);
    }
}
