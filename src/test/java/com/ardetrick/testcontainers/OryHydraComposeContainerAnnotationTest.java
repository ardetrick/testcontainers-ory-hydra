package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OryHydraComposeContainerAnnotationTest {

  @Container
  static OryHydraComposeContainer container =
      OryHydraComposeContainer.builder()
          .dockerComposeFile(new File("src/test/resources/docker-compose.yml"))
          .build();

  @Test
  void containerIsRunning() {
    assertThat(container.getContainerByServiceName("hydra")).isPresent();
  }
}
