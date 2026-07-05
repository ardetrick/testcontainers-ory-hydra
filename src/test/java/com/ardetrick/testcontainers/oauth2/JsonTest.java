package com.ardetrick.testcontainers.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

  @Test
  void parsesFlatObjectWithMixedValueTypes() {
    var parsed =
        Json.parseObject(
            "{\"access_token\":\"abc\",\"expires_in\":3599,\"scope\":\"read write\","
                + "\"active\":true,\"missing\":null}");

    assertThat(parsed.get("access_token")).isEqualTo("abc");
    assertThat(parsed.get("expires_in")).isEqualTo(3599L);
    assertThat(parsed.get("scope")).isEqualTo("read write");
    assertThat(parsed.get("active")).isEqualTo(true);
    assertThat(parsed).containsKey("missing");
    assertThat(parsed.get("missing")).isNull();
  }

  @Test
  void handlesStringEscapesAndUnicode() {
    var parsed = Json.parseObject("{\"k\":\"a\\\"b\\n\\u0041\"}");

    assertThat(parsed.get("k")).isEqualTo("a\"b\nA");
  }

  @Test
  void parsesEmptyObject() {
    assertThat(Json.parseObject("  {}  ")).isEmpty();
  }

  @Test
  void parsesFloatingPointNumber() {
    assertThat(Json.parseObject("{\"n\":1.5}").get("n")).isEqualTo(1.5d);
  }

  @Test
  void parsesNestedObjectsAndArrays() {
    var parsed =
        Json.parseObject(
            "{\"ext\":{\"dept\":\"eng\",\"level\":3},"
                + "\"aud\":[\"https://api.example\",\"https://other.example\"],"
                + "\"grant_types_supported\":[],"
                + "\"deep\":[{\"a\":[1,2]},{\"b\":null}]}");

    assertThat(parsed.get("ext")).isEqualTo(Map.of("dept", "eng", "level", 3L));
    assertThat(parsed.get("aud"))
        .isEqualTo(List.of("https://api.example", "https://other.example"));
    assertThat(parsed.get("grant_types_supported")).isEqualTo(List.of());
    assertThat(parsed.get("deep"))
        .isEqualTo(
            List.of(Map.of("a", List.of(1L, 2L)), java.util.Collections.singletonMap("b", null)));
  }

  @Test
  void rejectsUnterminatedArray() {
    assertThatThrownBy(() -> Json.parseObject("{\"a\":[1,2"))
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  void rejectsTrailingContent() {
    assertThatThrownBy(() -> Json.parseObject("{} extra")).isInstanceOf(JsonParseException.class);
  }

  @Test
  void rejectsTruncatedUnicodeEscape() {
    assertThatThrownBy(() -> Json.parseObject("{\"k\":\"\\u00"))
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  void rejectsInvalidUnicodeEscape() {
    assertThatThrownBy(() -> Json.parseObject("{\"k\":\"\\uZZZZ\"}"))
        .isInstanceOf(JsonParseException.class);
  }
}
