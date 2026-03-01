package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;

public class OryHydraContainerTest {

  @Test
  public void containerStarts() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      assertThat(container.isRunning()).isTrue();
    }
  }

  @Test
  public void hostAndPortMethodsReturnValidValues() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      assertThat(container.publicHost()).isNotEmpty();
      assertThat(container.publicPort()).isPositive();
      assertThat(container.adminHost()).isNotEmpty();
      assertThat(container.adminPort()).isPositive();
    }
  }

  @Test
  public void convenienceUriMethodsReturnExpectedPaths() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      URI openIdDiscoveryUri = container.getOpenIdDiscoveryUri();
      assertThat(openIdDiscoveryUri).hasPath("/.well-known/openid-configuration");

      URI oAuth2AuthUri = container.getOAuth2AuthUri();
      assertThat(oAuth2AuthUri).hasPath("/oauth2/auth");

      URI oAuth2TokenUri = container.getOAuth2TokenUri();
      assertThat(oAuth2TokenUri).hasPath("/oauth2/token");

      URI publicJwksUri = container.getPublicJwksUri();
      assertThat(publicJwksUri).hasPath("/.well-known/jwks.json");

      URI adminClientsUri = container.getAdminClientsUri();
      assertThat(adminClientsUri).hasPath("/admin/clients");

      URI oAuthAuthServerUri = container.getOAuthAuthorizationServerDiscoveryUri();
      assertThat(oAuthAuthServerUri).hasPath("/.well-known/oauth-authorization-server");

      URI oAuth2RevokeUri = container.getOAuth2RevokeUri();
      assertThat(oAuth2RevokeUri).hasPath("/oauth2/revoke");

      URI userInfoUri = container.getUserInfoUri();
      assertThat(userInfoUri).hasPath("/userinfo");

      URI oAuth2SessionsLogoutUri = container.getOAuth2SessionsLogoutUri();
      assertThat(oAuth2SessionsLogoutUri).hasPath("/oauth2/sessions/logout");

      URI adminIntrospectUri = container.getAdminOAuth2IntrospectUri();
      assertThat(adminIntrospectUri).hasPath("/admin/oauth2/introspect");

      URI adminLoginRequestUri = container.getAdminLoginRequestUri();
      assertThat(adminLoginRequestUri).hasPath("/admin/oauth2/auth/requests/login");

      URI adminConsentRequestUri = container.getAdminConsentRequestUri();
      assertThat(adminConsentRequestUri).hasPath("/admin/oauth2/auth/requests/consent");
    }
  }

  @Test
  public void baseUriStringsAreWellFormed() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      assertThat(container.adminBaseUriString()).matches("http://.+:\\d+");
      assertThat(container.publicBaseUriString()).matches("http://.+:\\d+");
    }
  }

  @Test
  public void envVariableIsPassedToContainer() throws Exception {
    var customIssuer = "http://custom-issuer:1234";
    try (var container =
        OryHydraContainer.builder().env("URLS_SELF_ISSUER", customIssuer).build()) {
      container.start();

      var response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder().uri(container.getOpenIdDiscoveryUri()).build(),
                  HttpResponse.BodyHandlers.ofString());

      assertThat(response.body()).contains("\"issuer\":\"" + customIssuer + "\"");
    }
  }

  @Test
  public void customWaitStrategyIsApplied() {
    try (var container =
        OryHydraContainer.builder().waitStrategy(Wait.forListeningPort()).build()) {
      container.start();

      assertThat(container.isRunning()).isTrue();
    }
  }
}
