package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OryHydraContainerIntrospectionAndDiscoveryTest {

  @Test
  public void introspectionReportsMintedTokenActiveWithSubjectAndClaims() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      var result =
          container
              .authorizationCodeFlow()
              .scopes("openid")
              .subject("introspected-user")
              .accessTokenClaims(Map.of("dept", "engineering"))
              .execute();
      var token = (FlowResult.TokenResponse) result;

      var introspection = container.introspect(token.accessToken());
      assertThat(introspection.active()).isTrue();
      assertThat(introspection.subject()).isEqualTo("introspected-user");
      assertThat(introspection.scope()).contains("openid");
      assertThat(introspection.clientId()).isNotBlank();
      // Custom access-token session claims surface under "ext" — nested JSON.
      assertThat(introspection.raw().get("ext")).isEqualTo(Map.of("dept", "engineering"));

      // Unknown tokens introspect as inactive rather than throwing.
      assertThat(container.introspect("not-a-real-token").active()).isFalse();
    }
  }

  @Test
  public void openIdConfigurationStripsPathBearingIssuerPrefix() {
    try (var container =
        OryHydraContainer.builder().urlsSelfIssuer("http://gateway.example/hydra-prefix").build()) {
      container.start();

      var config = container.openIdConfiguration();

      // Advertised values carry the issuer's path prefix, which nothing on the container serves...
      assertThat(config.issuer()).startsWith("http://gateway.example/hydra-prefix");
      assertThat(String.valueOf(config.raw().get("jwks_uri"))).contains("/hydra-prefix/");
      // ...so the accessors strip it, staying directly usable against the mapped port.
      assertThat(config.jwksUri())
          .hasToString(container.publicBaseUriString() + "/.well-known/jwks.json");
      assertThat(config.authorizationEndpoint())
          .hasToString(container.publicBaseUriString() + "/oauth2/auth");
    }
  }

  @Test
  @SuppressWarnings("removal") // asserts the deprecated helpers' documented replacements match
  public void openIdConfigurationResolvesEndpointsOnTheMappedPort() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();

      var config = container.openIdConfiguration();

      // Typed accessors are re-targeted at the mapped public port and must agree with the
      // (deprecated) 0.0.5 helpers they replace.
      assertThat(config.tokenEndpoint()).isEqualTo(container.getOAuth2TokenUri());
      assertThat(config.authorizationEndpoint()).isEqualTo(container.getOAuth2AuthUri());
      assertThat(config.jwksUri()).isEqualTo(container.getPublicJwksUri());
      assertThat(config.userinfoEndpoint()).isEqualTo(container.getUserInfoUri());
      assertThat(config.revocationEndpoint()).isEqualTo(container.getOAuth2RevokeUri());
      assertThat(config.endSessionEndpoint()).isEqualTo(container.getOAuth2SessionsLogoutUri());

      // The raw document keeps as-advertised values and nested members intact.
      assertThat(config.issuer()).isNotBlank();
      assertThat(config.raw().get("response_types_supported")).isInstanceOf(List.class);
    }
  }
}
