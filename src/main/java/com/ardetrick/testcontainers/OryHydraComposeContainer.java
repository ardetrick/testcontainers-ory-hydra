package com.ardetrick.testcontainers;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

/**
 * Compose-based Testcontainers wrapper that launches the Ory Hydra reference stack with sensible
 * defaults and helper methods for the most common endpoints.
 */
public class OryHydraComposeContainer extends ComposeContainer {

  static final int HYDRA_ADMIN_PORT = 4445;
  static final int HYDRA_PUBLIC_PORT = 4444;
  static final String SERVICE_NAME = "hydra";
  static final WaitStrategy DEFAULT_WAIT_STRATEGY =
      Wait.forHttp("/health/ready").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(30));

  /**
   * Creates a builder for configuring a Hydra compose environment.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  private OryHydraComposeContainer(
      Map<String, String> env, WaitStrategy waitStrategy, File... composeFiles) {
    super(composeFiles);
    this.withEnv(env);
    // Both ports are served by the same Hydra process, so a single wait strategy suffices.
    this.withExposedService(SERVICE_NAME, HYDRA_ADMIN_PORT, waitStrategy);
    this.withExposedService(SERVICE_NAME, HYDRA_PUBLIC_PORT);
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
    return "http://"
        + getServiceHost(SERVICE_NAME, port)
        + ":"
        + getServicePort(SERVICE_NAME, port);
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
   * Builds a convenience link to the OpenID Connect discovery endpoint.
   *
   * @return absolute URI pointing to {@code /.well-known/openid-configuration} on the public
   *     endpoint.
   */
  public URI getOpenIdDiscoveryUri() {
    return URI.create(publicBaseUriString() + "/.well-known/openid-configuration");
  }

  /**
   * Builds a convenience link to the Hydra OAuth2 token endpoint.
   *
   * @return absolute URI pointing to {@code /oauth2/token} on the public endpoint.
   */
  public URI getOAuth2TokenUri() {
    return URI.create(publicBaseUriString() + "/oauth2/token");
  }

  /**
   * Builds a convenience link to the Hydra admin client management endpoint.
   *
   * @return absolute URI pointing to {@code /admin/clients} on the admin endpoint.
   */
  public URI getAdminClientsUri() {
    return URI.create(adminBaseUriString() + "/admin/clients");
  }

  /** Fluent builder for composing the Hydra environment. */
  public static class Builder {

    private List<File> dockerComposeFile = new ArrayList<>();
    private Map<String, String> env = new HashMap<>();
    private WaitStrategy waitStrategy = DEFAULT_WAIT_STRATEGY;

    /**
     * Creates an empty builder; configure it via the fluent setters before calling {@link
     * #build()}.
     */
    public Builder() {}

    /**
     * Adds a docker compose file that defines part of the stack.
     *
     * @param file compose file to load
     * @return this builder for chaining
     */
    public Builder dockerComposeFile(File file) {
      Objects.requireNonNull(file, "file must not be null");
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
      Objects.requireNonNull(s, "urlsLogin must not be null");
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
      Objects.requireNonNull(s, "urlsConsent must not be null");
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
      Objects.requireNonNull(s, "urlsSelfIssuer must not be null");
      this.env.put("URLS_SELF_ISSUER", s);
      return this;
    }

    /**
     * Overrides the {@code URLS_LOGOUT} value passed to Hydra.
     *
     * @param s logout URL consumers should be redirected to
     * @return this builder for chaining
     */
    public Builder urlsLogout(String s) {
      Objects.requireNonNull(s, "urlsLogout must not be null");
      this.env.put("URLS_LOGOUT", s);
      return this;
    }

    /**
     * Overrides the {@code SECRETS_SYSTEM} value passed to Hydra.
     *
     * @param s system secret used for encryption
     * @return this builder for chaining
     */
    public Builder secretsSystem(String s) {
      Objects.requireNonNull(s, "secretsSystem must not be null");
      this.env.put("SECRETS_SYSTEM", s);
      return this;
    }

    /**
     * Overrides the {@code DSN} value passed to Hydra.
     *
     * @param s database connection string
     * @return this builder for chaining
     */
    public Builder dsn(String s) {
      Objects.requireNonNull(s, "dsn must not be null");
      this.env.put("DSN", s);
      return this;
    }

    /**
     * Sets an environment variable that will be passed to the compose services.
     *
     * @param key environment variable name
     * @param value environment variable value
     * @return this builder for chaining
     */
    public Builder env(String key, String value) {
      Objects.requireNonNull(key, "key must not be null");
      Objects.requireNonNull(value, "value must not be null");
      this.env.put(key, value);
      return this;
    }

    /**
     * Merges a map of environment variables that will be passed to the compose services.
     *
     * @param env environment variables to add
     * @return this builder for chaining
     */
    public Builder env(Map<String, String> env) {
      Objects.requireNonNull(env, "env must not be null");
      this.env.putAll(env);
      return this;
    }

    /**
     * Overrides the wait strategy used to determine when Hydra is ready.
     *
     * @param waitStrategy wait strategy supplied to Testcontainers
     * @return this builder for chaining
     */
    public Builder waitStrategy(WaitStrategy waitStrategy) {
      this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy must not be null");
      return this;
    }

    /**
     * Creates the configured compose environment.
     *
     * <p>The returned container is not yet started. Call {@link OryHydraComposeContainer#start()}
     * directly, or let the Testcontainers JUnit extension manage the lifecycle (e.g. with
     * {@code @Testcontainers} and {@code @Container}).
     *
     * @return configured but not yet started {@link OryHydraComposeContainer}
     * @throws IllegalStateException if no compose files were provided
     */
    public OryHydraComposeContainer build() {
      if (dockerComposeFile.isEmpty()) {
        throw new IllegalStateException("At least one docker compose file must be provided");
      }
      // Defensive copies so the builder is safe for reuse.
      return new OryHydraComposeContainer(
          new HashMap<>(env), waitStrategy, dockerComposeFile.toArray(new File[0]));
    }
  }
}
