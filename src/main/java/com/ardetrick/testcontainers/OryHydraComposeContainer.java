package com.ardetrick.testcontainers;

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compose-based Testcontainers wrapper that launches the Ory Hydra reference stack
 * with sensible defaults and helper methods for the most common endpoints.
 */
public class OryHydraComposeContainer extends ComposeContainer {

    static final int HYDRA_ADMIN_PORT = 4445;
    static final int HYDRA_PUBLIC_PORT = 4444;
    static final String SERVICE_NAME = "hydra";
    static final WaitStrategy DEFAULT_WAIT_STRATEGY = Wait.forListeningPort()
                                                          .withStartupTimeout(Duration.ofSeconds(10));

    /**
     * Creates a builder for configuring a Hydra compose environment.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private OryHydraComposeContainer(
            Map<String, String> env,
            File... composeFiles
    ) {
        super(composeFiles);
        this.withEnv(env);
        this.withExposedService(SERVICE_NAME, HYDRA_ADMIN_PORT, DEFAULT_WAIT_STRATEGY);
        this.withExposedService(SERVICE_NAME, HYDRA_PUBLIC_PORT, DEFAULT_WAIT_STRATEGY);
    }

    /**
     * Provides the base URL clients can use to reach the Hydra admin API.
     *
     * @return base URL for the Hydra admin API (host and mapped admin port).
     */
    public String adminBaseUriString() {
        return baseUriString(HYDRA_ADMIN_PORT);
    }

    /**
     * Provides the base URL clients can use to reach the Hydra public API.
     *
     * @return base URL for the Hydra public API (host and mapped public port).
     */
    public String publicBaseUriString() {
        return baseUriString(HYDRA_PUBLIC_PORT);
    }

    private String baseUriString(int port) {
        return "http://" +
                getServiceHost(SERVICE_NAME, port) +
                ":" +
                getServicePort(SERVICE_NAME, port);
    }

    /**
     * Builds a convenience link to the Hydra OAuth2 authorization endpoint.
     *
     * @return absolute URI pointing to {@code /oauth2/auth} on the public endpoint.
     */
    public URI getOAuth2AuthUri() {
        return URI.create(publicBaseUriString() + "/oauth2/auth");
    }

    /**
     * Builds a convenience link to the Hydra JWKS discovery document.
     *
     * @return absolute URI for the JWKS document exposed by Hydra.
     */
    public URI getPublicJwksUri() {
        return URI.create(publicBaseUriString() + "/.well-known/jwks.json");
    }

    /**
     * Fluent builder for composing the Hydra environment.
     */
    public static class Builder {

        List<File> dockerComposeFile = new ArrayList<>();
        Map<String, String> env = new HashMap<>();

        /**
         * Creates an empty builder; configure it via the fluent setters before calling {@link #start()}.
         */
        public Builder() {
        }

        /**
         * Adds a docker compose file that defines part of the stack.
         *
         * @param file compose file to load
         * @return this builder for chaining
         */
        public Builder dockerComposeFile(File file) {
            dockerComposeFile.add(file);
            return this;
        }

        /**
         * Overrides the {@code URLS_LOGIN} value passed to Hydra.
         *
         * @param s login URL consumers should be redirected to
         * @return this builder for chaining
         */
        public Builder urlsLogin(String s) {
            this.env.put("URLS_LOGIN", s);
            return this;
        }

        /**
         * Overrides the {@code URLS_CONSENT} value passed to Hydra.
         *
         * @param s consent URL consumers should be redirected to
         * @return this builder for chaining
         */
        public Builder urlsConsent(String s) {
            this.env.put("URLS_CONSENT", s);
            return this;
        }

        /**
         * Overrides the {@code URLS_SELF_ISSUER} value passed to Hydra.
         *
         * @param s public issuer URL Hydra should advertise
         * @return this builder for chaining
         */
        public Builder urlsSelfIssuer(String s) {
            this.env.put("URLS_SELF_ISSUER", s);
            return this;
        }

        /**
         * Starts the configured compose environment.
         *
         * @return running {@link OryHydraComposeContainer}
         * @throws IllegalStateException if no compose files were provided
         */
        public OryHydraComposeContainer start() {
            if (dockerComposeFile.isEmpty()) {
                throw new IllegalStateException("At least one docker compose file must be provided");
            }

            var compose = new OryHydraComposeContainer(env, dockerComposeFile.toArray(new File[0]));
            compose.start();

            return compose;
        }

    }

}
