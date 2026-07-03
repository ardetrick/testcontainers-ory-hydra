package com.ardetrick.testcontainers.oauth2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON reader for flat objects (no nested objects or arrays).
 *
 * <p>Sufficient for OAuth 2.0 token and error responses, whose members are all primitives. Kept
 * dependency-free on purpose; richer parsing can be added if a future feature needs nested values.
 */
final class Json {

  private final String src;
  private int pos;

  private Json(String src) {
    this.src = src;
  }

  /**
   * Parses a flat JSON object into an insertion-ordered map.
   *
   * @param json the JSON text
   * @return the parsed members (values are {@link String}, {@link Long}, {@link Double}, {@link
   *     Boolean}, or {@code null})
   * @throws JsonParseException if the text is not a flat JSON object
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
      case '{', '[' -> throw new JsonParseException("Nested JSON is not supported at index " + pos);
      default -> readNumber();
    };
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
            String hex = src.substring(pos, pos + 4);
            pos += 4;
            sb.append((char) Integer.parseInt(hex, 16));
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
