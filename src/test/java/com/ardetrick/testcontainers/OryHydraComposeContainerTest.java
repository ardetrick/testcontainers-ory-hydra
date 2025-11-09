package com.ardetrick.testcontainers;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class OryHydraComposeContainerTest {

    @Test
    public void containerStartsWithDockerComposeFile() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
                                                     .start()) {
            container.stop();
        }
    }

    @Test
    public void startContainerWithNoConfigVolume() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose-no-config-volume.yml"))
                                                     .start()) {
            container.stop();
        }
    }

    @Test
    public void startWithMultipleDockerFiles() {
        try (var container = OryHydraComposeContainer.builder()
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose-no-config-volume.yml"))
                                                     .dockerComposeFile(new File("src/test/resources/docker-compose-unrelated-container.yml"))
                                                     .start()) {

            assertThat(container.getContainerByServiceName("random"))
                    .isPresent();
            assertThat(container.getContainerByServiceName("hydra"))
                    .isPresent();

            container.stop();
        }
    }

}
