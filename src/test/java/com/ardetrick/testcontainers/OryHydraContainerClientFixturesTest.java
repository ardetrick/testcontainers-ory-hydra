package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OryHydraContainerClientFixturesTest {

  @Test
  public void declaredClientsExistImmediatelyAfterStartAndUpsertOnRedeclare() {
    try (var container =
        OryHydraContainer.builder()
            .client(
                Map.of(
                    "client_id", "fixture-service",
                    "client_secret", "fixture-secret",
                    "grant_types", List.of("client_credentials"),
                    "token_endpoint_auth_method", "client_secret_basic",
                    "scope", "read"))
            .client(
                OAuth2ClientRegistration.create()
                    .clientId("fixture-web-app")
                    .clientSecret("web-secret")
                    .grantTypes("authorization_code", "refresh_token")
                    .responseTypes("code")
                    .redirectUris("http://localhost/callback")
                    .tokenEndpointAuthMethod("client_secret_basic")
                    .scope("openid")
                    .toMap())
            .build()) {
      container.start();

      // Both declared clients are usable with no further setup.
      var machineToken =
          container
              .clientCredentialsFlow()
              .clientId("fixture-service")
              .clientSecret("fixture-secret")
              .scopes("read")
              .execute();
      assertThat(machineToken).isInstanceOf(FlowResult.TokenResponse.class);

      var userToken =
          container
              .authorizationCodeFlow()
              .clientId("fixture-web-app")
              .clientSecret("web-secret")
              .scopes("openid")
              .execute();
      assertThat(userToken).isInstanceOf(FlowResult.TokenResponse.class);

      // Redeclaring an existing client_id upserts instead of failing — the property that makes
      // fixtures safe for restarted or reused containers.
      OAuth2Clients.createOrReplace(
          URI.create(container.adminBaseUriString()),
          Map.of(
              "client_id", "fixture-service",
              "client_secret", "rotated-secret",
              "grant_types", List.of("client_credentials"),
              "token_endpoint_auth_method", "client_secret_basic",
              "scope", "read"));
      var rotated =
          container
              .clientCredentialsFlow()
              .clientId("fixture-service")
              .clientSecret("rotated-secret")
              .scopes("read")
              .execute();
      assertThat(rotated).isInstanceOf(FlowResult.TokenResponse.class);
    }
  }
}
