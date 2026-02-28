package com.ardetrick.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OryHydraComposeContainerAnnotationTest {

    @Container
    static OryHydraComposeContainer container = OryHydraComposeContainer.builder()
            .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
            .build();

    @Test
    void containerIsRunning() {
        assertThat(container.getContainerByServiceName("hydra")).isPresent();
    }

}
