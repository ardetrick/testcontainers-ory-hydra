package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ardetrick.testcontainers.oauth2.FlowResult;
import org.junit.jupiter.api.Test;

public class OryHydraContainerClientCredentialsTest {

  @Test
  public void clientCredentialsFlowWithEphemeralClientReturnsAccessTokenWithoutUserTokens() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      FlowResult result = container.clientCredentialsFlow().execute();

      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      var token = (FlowResult.TokenResponse) result;
      assertThat(token.accessToken()).isNotBlank();
      assertThat(token.tokenType()).isEqualToIgnoringCase("bearer");
      assertThat(token.expiresInSeconds()).isPositive();
      // client-credentials has no end-user: no ID token, no refresh token.
      assertThat(token.idToken()).isNull();
      assertThat(token.refreshToken()).isNull();
    }
  }

  @Test
  public void clientCredentialsFlowWithWrongSecretReturnsOAuthError() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();
      var create =
          container.execInContainer(
              "hydra",
              "create",
              "oauth2-client",
              "--endpoint",
              "http://127.0.0.1:4445",
              "--grant-type",
              "client_credentials",
              "--token-endpoint-auth-method",
              "client_secret_basic",
              "--id",
              "cc-known",
              "--secret",
              "right-secret");
      assertThat(create.getExitCode()).isZero();

      FlowResult result =
          container
              .clientCredentialsFlow()
              .clientId("cc-known")
              .clientSecret("wrong-secret")
              .execute();

      assertThat(result).isInstanceOf(FlowResult.OAuthError.class);
      assertThat(((FlowResult.OAuthError) result).error()).isEqualTo("invalid_client");
    }
  }
}
