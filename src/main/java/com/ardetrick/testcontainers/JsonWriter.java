package com.ardetrick.testcontainers;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal JSON writer for request bodies.
 *
 * <p>Supports the JSON value types needed by Hydra's admin API: {@link Map} (objects), {@link List}
 * (arrays), {@link String}, {@link Number}, {@link Boolean}, and {@code null}. Kept dependency-free
 * on purpose, mirroring {@link Json}.
 */
final class JsonWriter {

  private JsonWriter() {}

  /**
   * Serializes a value to JSON.
   *
   * @param value the value to serialize
   * @return the JSON text
   * @throws IllegalArgumentException if a value of an unsupported type is encountered
   */
  static String write(Object value) {
    StringBuilder sb = new StringBuilder();
    append(sb, value);
    return sb.toString();
  }

  private static void append(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String s) {
      appendString(sb, s);
    } else if (value instanceof Boolean b) {
      sb.append(b);
    } else if (value instanceof Number n) {
      sb.append(n);
    } else if (value instanceof Map<?, ?> map) {
      appendObject(sb, map);
    } else if (value instanceof List<?> list) {
      appendArray(sb, list);
    } else {
      throw new IllegalArgumentException(
          "Unsupported JSON value type: " + value.getClass().getName());
    }
  }

  private static void appendObject(StringBuilder sb, Map<?, ?> map) {
    sb.append('{');
    boolean first = true;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      appendString(sb, String.valueOf(entry.getKey()));
      sb.append(':');
      append(sb, entry.getValue());
    }
    sb.append('}');
  }

  private static void appendArray(StringBuilder sb, List<?> list) {
    sb.append('[');
    boolean first = true;
    for (Object item : list) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      append(sb, item);
    }
    sb.append(']');
  }

  private static void appendString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
