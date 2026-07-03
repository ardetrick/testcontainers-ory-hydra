package com.ardetrick.testcontainers.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void rejectsNestedValues() {
    assertThatThrownBy(() -> Json.parseObject("{\"a\":{\"b\":1}}"))
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  void rejectsTrailingContent() {
    assertThatThrownBy(() -> Json.parseObject("{} extra")).isInstanceOf(JsonParseException.class);
  }
}
