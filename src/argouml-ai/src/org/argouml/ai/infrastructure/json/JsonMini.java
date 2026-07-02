/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser used by
 * {@link org.argouml.ai.ops.PlannedOpParser}. We control the schema and the LLM emits
 * well-formed OpenAI responses, so we do not need a full general
 * library here.
 *
 * <p>Supported syntax:
 * <ul>
 *   <li>objects: {@code { "key": value, ... }} (key order preserved)</li>
 *   <li>arrays:  {@code [ value, ... ]}</li>
 *   <li>strings: double-quoted with the standard escape sequences
 *       (double-quote, backslash, forward slash, backspace, form
 *       feed, newline, carriage return, tab and a four-hex-digit
 *       Unicode escape of the form {@code \}{@code uXXXX})</li>
 *   <li>numbers: integer literals only (signed)</li>
 *   <li>booleans: {@code true}, {@code false}</li>
 *   <li>{@code null}</li>
 * </ul>
 *
 * <p>Throws {@link IllegalArgumentException} with a position-tagged
 * message for any malformed input. UTF-16 string handling means
 * non-ASCII characters (e.g. Chinese class names) round-trip
 * correctly as long as the source {@code String} was built from
 * properly-decoded UTF-8 bytes.
 */
public final class JsonMini {

    private final String src;
    private int pos;

    private JsonMini(String src) {
        this.src = src;
        this.pos = 0;
    }

    /**
     * Parse {@code json} into a generic Java value: {@code Map} for
     * objects, {@code List} for arrays, {@code String} for strings,
     * {@code Integer} for numbers, {@code Boolean} for booleans, or
     * {@code null}.
     *
     * @throws IllegalArgumentException for any syntax error.
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        JsonMini p = new JsonMini(json);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw p.err("trailing content after JSON value");
        }
        return v;
    }

    /**
     * Convenience wrapper: parse {@code json} and assert the result
     * is a JSON object. Useful for tool-call argument strings.
     */
    public static Map<String, Object> parseObject(String json) {
        Object v = parse(json);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException(
                    "expected JSON object, got "
                    + (v == null ? "null"
                       : v.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    /**
     * @return the string under {@code key}, or {@code null} if missing
     *         or not a string.
     */
    public static String getString(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof String ? (String) v : null;
    }

    /**
     * @return the int under {@code key}, or 0 if missing or not a
     *         number.
     */
    public static int getInt(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        if (v instanceof Integer) {
            return ((Integer) v).intValue();
        }
        if (v instanceof Long) {
            return ((Long) v).intValue();
        }
        return 0;
    }

    /**
     * @return the boolean under {@code key}, or {@code false} if
     *         missing or not a boolean.
     */
    public static boolean getBoolean(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof Boolean && ((Boolean) v).booleanValue();
    }

    /**
     * Serialize {@code o} back to a JSON string. The accepted shape is
     * the one produced by {@link #parse(String)}: {@code Map} for
     * objects, {@code List} for arrays, {@code String}, {@code Number}
     * (Integer/Long), {@code Boolean}, or {@code null}. Other types are
     * stringified via {@code toString()}.
     *
     * <p>String values are emitted with the same escape sequences that
     * {@code parse} understands (double-quote, backslash, {@code /},
     * {@code b}, {@code f}, {@code n}, {@code r}, {@code t}, and
     * {@code \\}{@code uXXXX} for other control characters).
     *
     * <p>This is the minimal counterpart to {@link #parse(String)}
     * used by {@code AiRequest.toJson()} and by
     * {@code AiResponse.fromJson} when re-serializing a nested
     * arguments object. It is not a general-purpose serializer.
     */
    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, o);
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Parser
    // -----------------------------------------------------------------

    private Object parseValue() {
        skipWs();
        if (pos >= src.length()) {
            throw err("unexpected end of input");
        }
        char c = src.charAt(pos);
        switch (c) {
        case '{': return parseObject();
        case '[': return parseArray();
        case '"': return parseString();
        case 't': return expectKeyword("true", Boolean.TRUE);
        case 'f': return expectKeyword("false", Boolean.FALSE);
        case 'n': return expectKeyword("null", null);
        default:
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw err("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        skipWs();
        if (peek() == '}') {
            pos++;
            return out;
        }
        while (true) {
            skipWs();
            Object k = parseString();
            if (!(k instanceof String)) {
                throw err("object key must be a string");
            }
            skipWs();
            expect(':');
            Object v = parseValue();
            out.put((String) k, v);
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return out;
            }
            throw err("expected ',' or '}' in object");
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> out = new ArrayList<Object>();
        skipWs();
        if (peek() == ']') {
            pos++;
            return out;
        }
        while (true) {
            Object v = parseValue();
            out.add(v);
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return out;
            }
            throw err("expected ',' or ']' in array");
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw err("unterminated escape");
                }
                char e = src.charAt(pos++);
                switch (e) {
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > src.length()) {
                        throw err("truncated \\u escape");
                    }
                    int cp = 0;
                    for (int i = 0; i < 4; i++) {
                        char h = src.charAt(pos++);
                        int d = hexDigit(h);
                        if (d < 0) {
                            throw err("invalid hex digit '" + h
                                    + "' in \\u escape");
                        }
                        cp = (cp << 4) | d;
                    }
                    sb.append((char) cp);
                    break;
                default:
                    throw err("unknown escape '\\" + e + "'");
                }
            } else {
                sb.append(c);
            }
        }
        throw err("unterminated string");
    }

    private Number parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else {
                break;
            }
        }
        String s = src.substring(start, pos);
        if (s.equals("-") || s.length() == 0) {
            throw err("malformed number");
        }
        try {
            long n = Long.parseLong(s);
            if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) n);
            }
            return Long.valueOf(n);
        } catch (NumberFormatException e) {
            throw err("malformed number '" + s + "'");
        }
    }

    private Object expectKeyword(String kw, Object value) {
        if (pos + kw.length() > src.length()) {
            throw err("unexpected end of input");
        }
        if (!src.regionMatches(pos, kw, 0, kw.length())) {
            throw err("expected '" + kw + "'");
        }
        pos += kw.length();
        return value;
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw err("expected '" + c + "'");
        }
        pos++;
    }

    private char peek() {
        if (pos >= src.length()) {
            return '\0';
        }
        return src.charAt(pos);
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        return -1;
    }

    private IllegalArgumentException err(String message) {
        return new IllegalArgumentException(
                "JsonMini: " + message + " at offset " + pos);
    }

    // -----------------------------------------------------------------
    // Serializer (stringify)
    // -----------------------------------------------------------------

    private static void writeValue(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString(sb, (String) o);
        } else if (o instanceof Number) {
            sb.append(o.toString());
        } else if (o instanceof Boolean) {
            sb.append(((Boolean) o).booleanValue() ? "true" : "false");
        } else if (o instanceof Map) {
            writeObject(sb, (Map<?, ?>) o);
        } else if (o instanceof List) {
            writeArray(sb, (List<?>) o);
        } else {
            // Fallback: best-effort stringification for unknown types.
            writeString(sb, o.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m) {
        sb.append('{');
        boolean first = true;
        for (java.util.Iterator<?> it = m.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<?, ?> e = (java.util.Map.Entry<?, ?>) it.next();
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list) {
        sb.append('[');
        boolean first = true;
        for (java.util.Iterator<?> it = list.iterator(); it.hasNext();) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(sb, it.next());
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':  sb.append("\\\""); break;
            case '\\': sb.append("\\\\"); break;
            case '\b': sb.append("\\b");  break;
            case '\f': sb.append("\\f");  break;
            case '\n': sb.append("\\n");  break;
            case '\r': sb.append("\\r");  break;
            case '\t': sb.append("\\t");  break;
            default:
                if (c < 0x20) {
                    sb.append("\\u");
                    appendHex4(sb, (int) c);
                } else {
                    sb.append(c);
                }
                break;
            }
        }
        sb.append('"');
    }

    private static final char[] HEX =
            {'0', '1', '2', '3', '4', '5', '6', '7',
             '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static void appendHex4(StringBuilder sb, int cp) {
        sb.append(HEX[(cp >>> 12) & 0xf]);
        sb.append(HEX[(cp >>> 8)  & 0xf]);
        sb.append(HEX[(cp >>> 4)  & 0xf]);
        sb.append(HEX[cp & 0xf]);
    }
}
