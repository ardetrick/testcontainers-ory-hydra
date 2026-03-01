package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;

public class OryHydraComposeContainerTest {

  @Test
  public void containerStartsWithDockerComposeFile() {
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .build()) {
      container.start();

      assertThat(container.getContainerByServiceName("hydra")).isPresent();
    }
  }

  @Test
  public void startContainerWithNoConfigVolume() {
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose-no-config-volume.yml"))
            .build()) {
      container.start();
    }
  }

  @Test
  public void hostAndPortMethodsReturnValidValues() {
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .build()) {
      container.start();

      assertThat(container.publicHost()).isNotEmpty();
      assertThat(container.publicPort()).isPositive();
      assertThat(container.adminHost()).isNotEmpty();
      assertThat(container.adminPort()).isPositive();
    }
  }

  @Test
  public void convenienceUriMethodsReturnExpectedPaths() {
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .build()) {
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
    }
  }

  @Test
  public void baseUriStringsAreWellFormed() {
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .build()) {
      container.start();

      assertThat(container.adminBaseUriString()).matches("http://.+:\\d+");
      assertThat(container.publicBaseUriString()).matches("http://.+:\\d+");
    }
  }

  @Test
  public void envVariableIsPassedToContainer() throws Exception {
    var customIssuer = "http://custom-issuer:1234";
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .env("URLS_SELF_ISSUER", customIssuer)
            .build()) {
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
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .waitStrategy(Wait.forListeningPort())
            .build()) {
      container.start();

      assertThat(container.getContainerByServiceName("hydra")).isPresent();
    }
  }

  @Test
  public void startWithMultipleDockerFiles() {
    try (var container =
        OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose-no-config-volume.yml"))
            .dockerComposeFile(
                new File("src/test/resources/docker-compose-unrelated-container.yml"))
            .build()) {
      container.start();

      assertThat(container.getContainerByServiceName("random")).isPresent();
      assertThat(container.getContainerByServiceName("hydra")).isPresent();
    }
  }
}
