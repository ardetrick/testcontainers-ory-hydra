package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ardetrick.testcontainers.oauth2.FlowResult;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OryHydraContainerAuthorizationCodeFlowTest {

  @Test
  public void authorizationCodeFlowReturnsUsableTokensCarryingSubjectAndClaims() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      FlowResult result =
          container
              .authorizationCodeFlow()
              .scopes("openid", "offline_access")
              .subject("user-123")
              .claims(Map.of("email", "user-123@example.com"))
              .execute();

      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      var token = (FlowResult.TokenResponse) result;
      assertThat(token.accessToken()).isNotBlank();
      assertThat(token.idToken()).isNotBlank(); // openid scope granted
      assertThat(token.refreshToken()).isNotBlank(); // offline_access granted
      assertThat(token.tokenType()).isEqualToIgnoringCase("bearer");

      // The configured subject and claims must actually land in the ID token.
      var idTokenPayload =
          new String(
              Base64.getUrlDecoder().decode(token.idToken().split("\\.")[1]),
              StandardCharsets.UTF_8);
      assertThat(idTokenPayload).contains("\"sub\":\"user-123\"");
      assertThat(idTokenPayload).contains("\"email\":\"user-123@example.com\"");

      // The access token must be usable: Hydra's introspection endpoint reports it active,
      // attributed to the configured subject — what a resource server under test would see.
      var introspection =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(
                          URI.create(container.adminBaseUriString() + "/admin/oauth2/introspect"))
                      .header("Content-Type", "application/x-www-form-urlencoded")
                      .POST(
                          HttpRequest.BodyPublishers.ofString(
                              "token="
                                  + URLEncoder.encode(token.accessToken(), StandardCharsets.UTF_8)))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertThat(introspection.statusCode()).isEqualTo(200);
      assertThat(introspection.body()).contains("\"active\":true");
      assertThat(introspection.body()).contains("\"sub\":\"user-123\"");
    }
  }

  @Test
  public void authorizationCodeFlowWithPkceSucceeds() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      FlowResult result =
          container.authorizationCodeFlow().scopes("openid").usePkce(true).execute();

      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      assertThat(((FlowResult.TokenResponse) result).accessToken()).isNotBlank();
    }
  }

  @Test
  public void authorizationCodeFlowWithCustomRedirectUriSucceeds() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();
      var customRedirectUri = "http://my-app.example:8443/oauth/callback";
      createClient(container, "custom-redirect-app", "custom-secret", "openid", customRedirectUri);

      FlowResult result =
          container
              .authorizationCodeFlow()
              .clientId("custom-redirect-app")
              .clientSecret("custom-secret")
              .scopes("openid")
              .redirectUri(customRedirectUri)
              .execute();

      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      assertThat(((FlowResult.TokenResponse) result).accessToken()).isNotBlank();
    }
  }

  @Test
  public void publicClientFlowWithImpliedPkceSucceeds() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      FlowResult result =
          container.authorizationCodeFlow().scopes("openid").publicClient(true).execute();

      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      var token = (FlowResult.TokenResponse) result;
      assertThat(token.accessToken()).isNotBlank();
      // The secret-less client's token is real and active.
      assertThat(container.introspect(token.accessToken()).active()).isTrue();
    }
  }

  @Test
  public void authorizationCodeFlowWithPreRegisteredClientSucceeds() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();
      createClient(container, "known-app", "known-secret", "openid");

      FlowResult result =
          container
              .authorizationCodeFlow()
              .clientId("known-app")
              .clientSecret("known-secret")
              .scopes("openid")
              .execute();

      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      assertThat(((FlowResult.TokenResponse) result).accessToken()).isNotBlank();
    }
  }

  @Test
  public void requestingScopeNotRegisteredOnClientReturnsOAuthError() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();
      createClient(container, "narrow-app", "narrow-secret", "openid");

      FlowResult result =
          container
              .authorizationCodeFlow()
              .clientId("narrow-app")
              .clientSecret("narrow-secret")
              .scopes("openid", "email")
              .execute();

      assertThat(result).isInstanceOf(FlowResult.OAuthError.class);
      assertThat(((FlowResult.OAuthError) result).error()).isEqualTo("invalid_scope");
    }
  }

  @Test
  public void rejectedLoginReturnsOAuthError() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      FlowResult result =
          container
              .authorizationCodeFlow()
              .scopes("openid")
              .rejectLogin("login_required", "user could not be authenticated")
              .execute();

      assertThat(result).isInstanceOf(FlowResult.OAuthError.class);
      assertThat(((FlowResult.OAuthError) result).error()).isEqualTo("login_required");
    }
  }

  @Test
  public void rejectedConsentReturnsOAuthError() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      FlowResult result =
          container
              .authorizationCodeFlow()
              .scopes("openid")
              .rejectConsent("access_denied", "user declined")
              .execute();

      assertThat(result).isInstanceOf(FlowResult.OAuthError.class);
      assertThat(((FlowResult.OAuthError) result).error()).isEqualTo("access_denied");
    }
  }

  private static void createClient(
      OryHydraContainer container, String clientId, String clientSecret, String scope)
      throws Exception {
    createClient(container, clientId, clientSecret, scope, "http://localhost/callback");
  }

  private static void createClient(
      OryHydraContainer container,
      String clientId,
      String clientSecret,
      String scope,
      String redirectUri)
      throws Exception {
    var create =
        container.execInContainer(
            "hydra",
            "create",
            "oauth2-client",
            "--endpoint",
            "http://127.0.0.1:4445",
            "--grant-type",
            "authorization_code",
            "--response-type",
            "code",
            "--token-endpoint-auth-method",
            "client_secret_basic",
            "--scope",
            scope,
            "--redirect-uri",
            redirectUri,
            "--id",
            clientId,
            "--secret",
            clientSecret);
    assertThat(create.getExitCode()).isZero();
  }
}
