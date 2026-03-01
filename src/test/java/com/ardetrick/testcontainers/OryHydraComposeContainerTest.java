package com.ardetrick.testcontainers;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class OryHydraComposeContainerTest {

    @Test
    public void containerStartsWithDockerComposeFile() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
                                                     .build()) {
            container.start();
        }
    }

    @Test
    public void startContainerWithNoConfigVolume() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose-no-config-volume.yml"))
                                                     .build()) {
            container.start();
        }
    }

    @Test
    public void convenienceUriMethodsReturnExpectedPaths() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
                                                     .build()) {
            container.start();

            URI openIdDiscoveryUri = container.getOpenIdDiscoveryUri();
            assertThat(openIdDiscoveryUri).hasPath("/.well-known/openid-configuration");

            URI oAuth2TokenUri = container.getOAuth2TokenUri();
            assertThat(oAuth2TokenUri).hasPath("/oauth2/token");

            URI adminClientsUri = container.getAdminClientsUri();
            assertThat(adminClientsUri).hasPath("/admin/clients");
        }
    }

    @Test
    public void envVariableIsPassedToContainer() throws Exception {
        var customIssuer = "http://custom-issuer:1234";
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
                                                     .env("URLS_SELF_ISSUER", customIssuer)
                                                     .build()) {
            container.start();

            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                               .uri(container.getOpenIdDiscoveryUri())
                               .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).contains("\"issuer\":\"" + customIssuer + "\"");
        }
    }

    @Test
    public void startWithMultipleDockerFiles() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose-no-config-volume.yml"))
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose-unrelated-container.yml"))
                                                     .build()) {
            container.start();

            assertThat(container.getContainerByServiceName("random"))
                    .isPresent();
            assertThat(container.getContainerByServiceName("hydra"))
                    .isPresent();
        }
    }

}
