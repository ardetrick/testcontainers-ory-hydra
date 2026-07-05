package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class OryHydraContainerBuilderTest {

  @Test
  void envRejectsInternalPortOverrides() {
    var builder = OryHydraContainer.builder();
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.env("SERVE_ADMIN_PORT", "5555"))
        .withMessageContaining("SERVE_ADMIN_PORT is not supported");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.env("SERVE_PUBLIC_PORT", "5555"))
        .withMessageContaining("SERVE_PUBLIC_PORT is not supported");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.env(java.util.Map.of("SERVE_PUBLIC_PORT", "5555")))
        .withMessageContaining("SERVE_PUBLIC_PORT is not supported");
    // Other environment variables remain accepted.
    builder.env("LOG_LEVEL", "debug");
    builder.env(java.util.Map.of("STRATEGIES_ACCESS_TOKEN", "jwt"));
  }

  @Test
  void imageRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.image(null))
        .withMessage("image must not be null");
  }

  @Test
  void urlsLoginRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsLogin(null))
        .withMessage("urlsLogin must not be null");
  }

  @Test
  void urlsConsentRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsConsent(null))
        .withMessage("urlsConsent must not be null");
  }

  @Test
  void urlsSelfIssuerRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsSelfIssuer(null))
        .withMessage("urlsSelfIssuer must not be null");
  }

  @Test
  void urlsLogoutRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.urlsLogout(null))
        .withMessage("urlsLogout must not be null");
  }

  @Test
  void secretsSystemRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.secretsSystem(null))
        .withMessage("secretsSystem must not be null");
  }

  @Test
  void dsnRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.dsn(null))
        .withMessage("dsn must not be null");
  }

  @Test
  void envRejectsNullKey() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.env(null, "value"))
        .withMessage("key must not be null");
  }

  @Test
  void envRejectsNullValue() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.env("key", null))
        .withMessage("value must not be null");
  }

  @Test
  void envMapRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.env(null))
        .withMessage("env must not be null");
  }

  @Test
  void waitStrategyRejectsNull() {
    var builder = OryHydraContainer.builder();
    assertThatNullPointerException()
        .isThrownBy(() -> builder.waitStrategy(null))
        .withMessage("waitStrategy must not be null");
  }
}
