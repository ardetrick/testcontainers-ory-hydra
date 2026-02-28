package com.ardetrick.testcontainers;

import org.junit.jupiter.api.Test;

import java.io.File;

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
