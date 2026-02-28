package com.ardetrick.testcontainers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
    void waitStrategyRejectsNull() {
        var builder = OryHydraComposeContainer.builder();
        assertThatNullPointerException()
                .isThrownBy(() -> builder.waitStrategy(null))
                .withMessage("waitStrategy must not be null");
    }

}
