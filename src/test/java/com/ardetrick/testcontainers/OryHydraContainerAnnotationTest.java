package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OryHydraContainerAnnotationTest {

  @Container static OryHydraContainer container = OryHydraContainer.builder().build();

  @Test
  void containerIsRunning() {
    assertThat(container.isRunning()).isTrue();
  }
}
