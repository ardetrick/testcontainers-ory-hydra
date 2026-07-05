package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the URI helpers released in 0.0.5 and deprecated since 0.0.6. Delete this class together
 * with the helpers when the deprecation cycle completes.
 */
@SuppressWarnings("removal")
public class OryHydraContainerDeprecatedUriHelpersTest {

  @Test
  public void deprecatedUriHelpersStillResolveTheir005Paths() {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();
      var publicBase = container.publicBaseUriString();
      var adminBase = container.adminBaseUriString();

      assertThat(container.getOAuth2AuthUri()).hasToString(publicBase + "/oauth2/auth");
      assertThat(container.getPublicJwksUri()).hasToString(publicBase + "/.well-known/jwks.json");
      assertThat(container.getOAuthAuthorizationServerDiscoveryUri())
          .hasToString(publicBase + "/.well-known/oauth-authorization-server");
      assertThat(container.getOAuth2RevokeUri()).hasToString(publicBase + "/oauth2/revoke");
      assertThat(container.getUserInfoUri()).hasToString(publicBase + "/userinfo");
      assertThat(container.getOAuth2SessionsLogoutUri())
          .hasToString(publicBase + "/oauth2/sessions/logout");
      assertThat(container.getAdminClientsUri()).hasToString(adminBase + "/admin/clients");
      assertThat(container.getAdminOAuth2IntrospectUri())
          .hasToString(adminBase + "/admin/oauth2/introspect");
      assertThat(container.getAdminLoginRequestUri())
          .hasToString(adminBase + "/admin/oauth2/auth/requests/login");
      assertThat(container.getAdminConsentRequestUri())
          .hasToString(adminBase + "/admin/oauth2/auth/requests/consent");
    }
  }
}
