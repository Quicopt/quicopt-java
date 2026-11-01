// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

/**
 * Reads one string field of a JSON object: enough for the service's error answers, without a JSON library.
 */
final class JsonField {

    /**
     * Not instantiable.
     */
    private JsonField() {
    }

    /**
     * @param json a JSON object
     * @param name the field's name
     * @return the field's value, unescaped, or null when the object has no string field of that name or the text is
     *         not JSON of the expected shape
     */
    static String string(String json, String name) {
        var key = "\"" + name + "\"";
        for (int at = json.indexOf(key); at >= 0; at = json.indexOf(key, at + 1)) {
            int i = skipSpace(json, at + key.length());
            if (i < json.length() && json.charAt(i) == ':') {
                i = skipSpace(json, i + 1);
                return i < json.length() && json.charAt(i) == '"' ? unescaped(json, i + 1) : null;
            }
        }
        return null;
    }

    /**
     * @return the index of the first character at or after {@code i} that is not white space
     */
    private static int skipSpace(String json, int i) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * @param start the index just after a string's opening quote
     * @return the string's text with its escapes resolved, or null when it does not end
     */
    private static String unescaped(String json, int start) {
        var text = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return text.toString();
            }
            if (c != '\\') {
                text.append(c);
                continue;
            }
            if (++i >= json.length()) {
                return null;
            }
            char escaped = json.charAt(i);
            switch (escaped) {
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'n' -> text.append('\n');
                case 'r' -> text.append('\r');
                case 't' -> text.append('\t');
                case 'u' -> {
                    if (i + 4 >= json.length()) {
                        return null;
                    }
                    text.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> text.append(escaped);
            }
        }
        return null;
    }
}
