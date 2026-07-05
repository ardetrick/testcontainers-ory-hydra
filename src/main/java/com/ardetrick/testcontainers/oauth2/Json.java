package com.ardetrick.testcontainers.oauth2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader.
 *
 * <p>Covers the documents this library consumes: OAuth 2.0 token and error responses, RFC 7662
 * introspection responses ({@code ext}, {@code aud}), and the OpenID Connect discovery document.
 * Kept dependency-free on purpose.
 */
final class Json {

  private final String src;
  private int pos;

  private Json(String src) {
    this.src = src;
  }

  /**
   * Parses a JSON object into an insertion-ordered map.
   *
   * @param json the JSON text
   * @return the parsed members (values are {@link String}, {@link Long}, {@link Double}, {@link
   *     Boolean}, {@code null}, {@link List}, or nested {@link Map})
   * @throws JsonParseException if the text is not a JSON object
   */
  static Map<String, Object> parseObject(String json) {
    Json parser = new Json(json);
    parser.skipWhitespace();
    Map<String, Object> result = parser.object();
    parser.skipWhitespace();
    if (parser.pos != parser.src.length()) {
      throw new JsonParseException("Unexpected trailing content at index " + parser.pos);
    }
    return result;
  }

  private Map<String, Object> object() {
    expect('{');
    Map<String, Object> map = new LinkedHashMap<>();
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return map;
    }
    while (true) {
      skipWhitespace();
      String key = readString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      map.put(key, readValue());
      skipWhitespace();
      char c = nextChar();
      if (c == '}') {
        return map;
      }
      if (c != ',') {
        throw new JsonParseException("Expected ',' or '}' at index " + (pos - 1));
      }
    }
  }

  private Object readValue() {
    char c = peek();
    return switch (c) {
      case '"' -> readString();
      case 't', 'f' -> readBoolean();
      case 'n' -> readNull();
      case '{' -> object();
      case '[' -> array();
      default -> readNumber();
    };
  }

  private List<Object> array() {
    expect('[');
    List<Object> list = new ArrayList<>();
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return list;
    }
    while (true) {
      skipWhitespace();
      list.add(readValue());
      skipWhitespace();
      char c = nextChar();
      if (c == ']') {
        return list;
      }
      if (c != ',') {
        throw new JsonParseException("Expected ',' or ']' at index " + (pos - 1));
      }
    }
  }

  private String readString() {
    expect('"');
    StringBuilder sb = new StringBuilder();
    while (true) {
      char c = nextChar();
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\') {
        char esc = nextChar();
        switch (esc) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > src.length()) {
              throw new JsonParseException("Truncated unicode escape at index " + pos);
            }
            String hex = src.substring(pos, pos + 4);
            pos += 4;
            try {
              sb.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
              throw new JsonParseException(
                  "Invalid unicode escape '\\u" + hex + "' at index " + (pos - 4));
            }
          }
          default -> throw new JsonParseException("Invalid escape '\\" + esc + "' at index " + pos);
        }
      } else {
        sb.append(c);
      }
    }
  }

  private Object readNumber() {
    int start = pos;
    while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
      pos++;
    }
    String num = src.substring(start, pos);
    if (num.isEmpty()) {
      throw new JsonParseException("Expected value at index " + start);
    }
    if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
      try {
        return Long.parseLong(num);
      } catch (NumberFormatException ignored) {
        // Falls through to double parsing below.
      }
    }
    try {
      return Double.parseDouble(num);
    } catch (NumberFormatException e) {
      throw new JsonParseException("Invalid number '" + num + "' at index " + start);
    }
  }

  private Boolean readBoolean() {
    if (src.startsWith("true", pos)) {
      pos += 4;
      return Boolean.TRUE;
    }
    if (src.startsWith("false", pos)) {
      pos += 5;
      return Boolean.FALSE;
    }
    throw new JsonParseException("Invalid literal at index " + pos);
  }

  private Object readNull() {
    if (src.startsWith("null", pos)) {
      pos += 4;
      return null;
    }
    throw new JsonParseException("Invalid literal at index " + pos);
  }

  private void skipWhitespace() {
    while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
      pos++;
    }
  }

  private char peek() {
    if (pos >= src.length()) {
      throw new JsonParseException("Unexpected end of input");
    }
    return src.charAt(pos);
  }

  private char nextChar() {
    char c = peek();
    pos++;
    return c;
  }

  private void expect(char expected) {
    char c = nextChar();
    if (c != expected) {
      throw new JsonParseException(
          "Expected '" + expected + "' but found '" + c + "' at index " + (pos - 1));
    }
  }
}
