package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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

      URI oAuth2TokenUri = container.getOAuth2TokenUri();
      assertThat(oAuth2TokenUri).hasPath("/oauth2/token");
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
  public void createOAuth2ClientSucceeds() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      container.createOAuth2Client(
          "test-client", "test-secret", List.of("http://localhost/callback"));

      var response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder()
                      .uri(
                          URI.create(container.adminBaseUriString() + "/admin/clients/test-client"))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"client_id\":\"test-client\"");
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
