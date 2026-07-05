package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

/**
 * Guards the container-to-container setup documented in the README: Hydra joins a Docker network
 * under an alias with {@code URLS_SELF_ISSUER} set to the alias-based URL, an in-network consumer
 * (standing in for a dockerized app under test) reaches Hydra by alias on the unmapped internal
 * ports, and the host-side flow helpers keep working despite the custom issuer because they rewrite
 * every Hydra-bound redirect to the mapped port.
 */
public class OryHydraContainerDockerNetworkTest {

  @Test
  public void inNetworkConsumerAndHostFlowsBothWorkWithAliasIssuer() throws Exception {
    try (var network = Network.newNetwork();
        var hydra =
            OryHydraContainer.builder()
                .urlsSelfIssuer("http://hydra:4444")
                .build()
                .withNetwork(network)
                .withNetworkAliases("hydra")) {
      hydra.start();

      // Host-side minting still works: the flow driver rewrites Hydra-bound redirects to the
      // mapped port regardless of the configured issuer.
      var result = hydra.authorizationCodeFlow().scopes("openid").execute();
      assertThat(result).isInstanceOf(FlowResult.TokenResponse.class);
      var token = (FlowResult.TokenResponse) result;

      try (var appUnderTest =
          new GenericContainer<>("curlimages/curl:8.11.1")
              .withNetwork(network)
              .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sleep", "300"))) {
        appUnderTest.start();

        // The in-network consumer resolves the discovery document by alias, and the advertised
        // issuer is the alias URL it can actually reach.
        var discovery =
            appUnderTest.execInContainer(
                "curl", "-s", "http://hydra:4444/.well-known/openid-configuration");
        assertThat(discovery.getExitCode()).isZero();
        assertThat(discovery.getStdout()).contains("\"issuer\":\"http://hydra:4444");

        // A token minted from the host validates inside the network — the handoff a dockerized
        // resource server under test performs.
        var introspection =
            appUnderTest.execInContainer(
                "curl",
                "-s",
                "-X",
                "POST",
                "-d",
                "token=" + token.accessToken(),
                "http://hydra:4445/admin/oauth2/introspect");
        assertThat(introspection.getExitCode()).isZero();
        assertThat(introspection.getStdout()).contains("\"active\":true");
      }
    }
  }
}
