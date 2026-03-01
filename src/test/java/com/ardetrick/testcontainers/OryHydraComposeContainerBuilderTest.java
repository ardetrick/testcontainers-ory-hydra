package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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
  void urlsLogoutRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsLogout(null))
        .withMessage("urlsLogout must not be null");
  }

  @Test
  void secretsSystemRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.secretsSystem(null))
        .withMessage("secretsSystem must not be null");
  }

  @Test
  void dsnRejectsNull() {
    var builder = OryHydraComposeContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.dsn(null))
        .withMessage("dsn must not be null");
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

  @Test
  void buildWithNoComposeFilesThrows() {
    var builder = OryHydraComposeContainer.builder();
    assertThatIllegalStateException()
        .isThrownBy(builder::build)
        .withMessage("At least one docker compose file must be provided");
  }
}
