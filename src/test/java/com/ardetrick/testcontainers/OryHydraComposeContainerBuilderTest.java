package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class OryHydraComposeContainerBuilderTest {

  @Test
  void dockerComposeFileRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.dockerComposeFile(null))
        .withMessage("file must not be null");
  }

  @Test
  void urlsLoginRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsLogin(null))
        .withMessage("urlsLogin must not be null");
  }

  @Test
  void urlsConsentRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsConsent(null))
        .withMessage("urlsConsent must not be null");
  }

  @Test
  void urlsSelfIssuerRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsSelfIssuer(null))
        .withMessage("urlsSelfIssuer must not be null");
  }

  @Test
  void envRejectsNullKey() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.env(null, "value"))
        .withMessage("key must not be null");
  }

  @Test
  void envRejectsNullValue() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.env("key", null))
        .withMessage("value must not be null");
  }

  @Test
  void envMapRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.env(null))
        .withMessage("env must not be null");
  }

  @Test
  void waitStrategyRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.waitStrategy(null))
        .withMessage("waitStrategy must not be null");
  }
}
