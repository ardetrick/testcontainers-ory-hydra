package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

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
                client ->
                    client
                        .clientId("fixture-web-app")
                        .clientSecret("web-secret")
                        .grantTypes("authorization_code", "refresh_token")
                        .responseTypes("code")
                        .redirectUris("http://localhost/callback")
                        .tokenEndpointAuthMethod("client_secret_basic")
                        .scope("openid"))
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

      // Mid-test registration on the running container: a client that was not declared
      // up front becomes usable immediately — the shared-container use case.
      container.createOrReplaceClient(
          client ->
              client
                  .clientId("mid-test-app")
                  .clientSecret("mid-test-secret")
                  .grantTypes("client_credentials")
                  .tokenEndpointAuthMethod("client_secret_basic")
                  .scope("read"));
      var midTest =
          container
              .clientCredentialsFlow()
              .clientId("mid-test-app")
              .clientSecret("mid-test-secret")
              .scopes("read")
              .execute();
      assertThat(midTest).isInstanceOf(FlowResult.TokenResponse.class);

      // Re-registering an existing client_id upserts instead of failing — the property that makes
      // registration safe for restarted, reused, or re-run shared containers.
      container.createOrReplaceClient(
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
