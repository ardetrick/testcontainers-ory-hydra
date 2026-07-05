package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuth2ClientRegistrationTest {

  @Test
  void typedMethodsProduceStandardMetadataKeys() {
    var map =
        OAuth2ClientRegistration.create()
            .clientId("app")
            .clientSecret("secret")
            .grantTypes("authorization_code", "refresh_token")
            .responseTypes("code")
            .redirectUris("https://app.example/callback")
            .scope("openid", "offline_access")
            .tokenEndpointAuthMethod("client_secret_basic")
            .toMap();

    assertThat(map)
        .containsEntry("client_id", "app")
        .containsEntry("client_secret", "secret")
        .containsEntry("grant_types", List.of("authorization_code", "refresh_token"))
        .containsEntry("response_types", List.of("code"))
        .containsEntry("redirect_uris", List.of("https://app.example/callback"))
        .containsEntry("scope", "openid offline_access")
        .containsEntry("token_endpoint_auth_method", "client_secret_basic");
  }

  @Test
  void putAndPutAllOverrideTypedValuesAndCarryArbitraryFields() {
    var map =
        OAuth2ClientRegistration.create()
            .clientId("app")
            .scope("openid")
            .put("skip_consent", true)
            .putAll(Map.of("scope", "read", "client_name", "My App"))
            .toMap();

    assertThat(map)
        .containsEntry("client_id", "app")
        .containsEntry("scope", "read") // putAll overrode the typed value
        .containsEntry("skip_consent", true)
        .containsEntry("client_name", "My App");
  }

  @Test
  void toMapReturnsAnIndependentCopy() {
    var registration = OAuth2ClientRegistration.create().clientId("app");
    var first = registration.toMap();
    registration.clientSecret("added-later");

    assertThat(first).doesNotContainKey("client_secret");
    assertThat(registration.toMap()).containsKey("client_secret");
  }
}
