package com.ardetrick.testcontainers.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonWriterTest {

  @Test
  void writesObjectsArraysAndPrimitivesInOrder() {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("s", "a\"b");
    object.put("n", 3);
    object.put("flag", true);
    object.put("nothing", null);
    object.put("list", List.of("x", "y"));

    assertThat(JsonWriter.write(object))
        .isEqualTo(
            "{\"s\":\"a\\\"b\",\"n\":3,\"flag\":true,\"nothing\":null,\"list\":[\"x\",\"y\"]}");
  }

  @Test
  void writesNestedObjects() {
    Map<String, Object> session = new LinkedHashMap<>();
    session.put("access_token", Map.of("email", "u@example.com"));

    assertThat(JsonWriter.write(session))
        .isEqualTo("{\"access_token\":{\"email\":\"u@example.com\"}}");
  }

  @Test
  void rejectsUnsupportedValueTypes() {
    assertThatThrownBy(() -> JsonWriter.write(Map.of("x", new Object())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
