package com.ardetrick.testcontainers;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers wrapper that launches an Ory Hydra instance with sensible defaults and helper
 * methods for the most common endpoints.
 *
 * <p>Runs database migration and the Hydra server in a single container using a compound command.
 * Defaults to an in-container SQLite database, so no external database is required.
 */
public class OryHydraContainer extends GenericContainer<OryHydraContainer> {

  static final int HYDRA_ADMIN_PORT = 4445;
  static final int HYDRA_PUBLIC_PORT = 4444;
  static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("oryd/hydra:v25.4.0");
  static final String DEFAULT_DSN = "sqlite:///tmp/db.sqlite?_fk=true";
  static final String DEFAULT_SECRETS_SYSTEM = "testcontainers-ory-hydra-secret";
  static final WaitStrategy DEFAULT_WAIT_STRATEGY =
      Wait.forHttp("/health/ready")
          .forPort(HYDRA_ADMIN_PORT)
          .forStatusCode(200)
          .withStartupTimeout(Duration.ofSeconds(30));

  /**
   * Creates a builder for configuring a Hydra container.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  private OryHydraContainer(
      DockerImageName imageName, Map<String, String> env, WaitStrategy waitStrategy) {
    super(imageName);
    this.withEnv(env);
    this.withExposedPorts(HYDRA_ADMIN_PORT, HYDRA_PUBLIC_PORT);
    // Override the image entrypoint so we can run migration before serving.
    this.withCreateContainerCmdModifier(
        cmd ->
            cmd.withEntrypoint("sh", "-c", "hydra migrate sql -e --yes && hydra serve all --dev"));
    this.waitingFor(waitStrategy);
  }

  /**
   * Returns the host that the Hydra public API is reachable on.
   *
   * @return mapped host for the Hydra public port
   */
  public String publicHost() {
    return getHost();
  }

  /**
   * Returns the mapped port for the Hydra public API.
   *
   * @return mapped port for the Hydra public port
   */
  public int publicPort() {
    return getMappedPort(HYDRA_PUBLIC_PORT);
  }

  /**
   * Returns the host that the Hydra admin API is reachable on.
   *
   * @return mapped host for the Hydra admin port
   */
  public String adminHost() {
    return getHost();
  }

  /**
   * Returns the mapped port for the Hydra admin API.
   *
   * @return mapped port for the Hydra admin port
   */
  public int adminPort() {
    return getMappedPort(HYDRA_ADMIN_PORT);
  }

  /**
   * Provides the base URL clients can use to reach the Hydra admin API.
   *
   * @return base URL for the Hydra admin API (host and mapped admin port).
   */
  public String adminBaseUriString() {
    return "http://" + adminHost() + ":" + adminPort();
  }

  /**
   * Provides the base URL clients can use to reach the Hydra public API.
   *
   * @return base URL for the Hydra public API (host and mapped public port).
   */
  public String publicBaseUriString() {
    return "http://" + publicHost() + ":" + publicPort();
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
   * Builds a convenience link to the OAuth 2.0 Authorization Server Metadata endpoint (RFC 8414).
   *
   * @return absolute URI pointing to {@code /.well-known/oauth-authorization-server} on the public
   *     endpoint.
   */
  public URI getOAuthAuthorizationServerDiscoveryUri() {
    return URI.create(publicBaseUriString() + "/.well-known/oauth-authorization-server");
  }

  /**
   * Builds a convenience link to the OAuth 2.0 token revocation endpoint.
   *
   * @return absolute URI pointing to {@code /oauth2/revoke} on the public endpoint.
   */
  public URI getOAuth2RevokeUri() {
    return URI.create(publicBaseUriString() + "/oauth2/revoke");
  }

  /**
   * Builds a convenience link to the OpenID Connect UserInfo endpoint.
   *
   * @return absolute URI pointing to {@code /userinfo} on the public endpoint.
   */
  public URI getUserInfoUri() {
    return URI.create(publicBaseUriString() + "/userinfo");
  }

  /**
   * Builds a convenience link to the OIDC front/back-channel logout endpoint.
   *
   * @return absolute URI pointing to {@code /oauth2/sessions/logout} on the public endpoint.
   */
  public URI getOAuth2SessionsLogoutUri() {
    return URI.create(publicBaseUriString() + "/oauth2/sessions/logout");
  }

  /**
   * Builds a convenience link to the Hydra admin client management endpoint.
   *
   * @return absolute URI pointing to {@code /admin/clients} on the admin endpoint.
   */
  public URI getAdminClientsUri() {
    return URI.create(adminBaseUriString() + "/admin/clients");
  }

  /**
   * Builds a convenience link to the OAuth 2.0 token introspection endpoint (RFC 7662).
   *
   * @return absolute URI pointing to {@code /admin/oauth2/introspect} on the admin endpoint.
   */
  public URI getAdminOAuth2IntrospectUri() {
    return URI.create(adminBaseUriString() + "/admin/oauth2/introspect");
  }

  /**
   * Builds a convenience link to the Hydra admin login request management endpoint.
   *
   * @return absolute URI pointing to {@code /admin/oauth2/auth/requests/login} on the admin
   *     endpoint.
   */
  public URI getAdminLoginRequestUri() {
    return URI.create(adminBaseUriString() + "/admin/oauth2/auth/requests/login");
  }

  /**
   * Builds a convenience link to the Hydra admin consent request management endpoint.
   *
   * @return absolute URI pointing to {@code /admin/oauth2/auth/requests/consent} on the admin
   *     endpoint.
   */
  public URI getAdminConsentRequestUri() {
    return URI.create(adminBaseUriString() + "/admin/oauth2/auth/requests/consent");
  }

  /** Fluent builder for configuring the Hydra container. */
  public static class Builder {

    private DockerImageName image = DEFAULT_IMAGE;
    private Map<String, String> env = new HashMap<>();
    private WaitStrategy waitStrategy = DEFAULT_WAIT_STRATEGY;

    /**
     * Creates an empty builder; configure it via the fluent setters before calling {@link
     * #build()}.
     */
    public Builder() {
      env.put("DSN", DEFAULT_DSN);
      env.put("SECRETS_SYSTEM", DEFAULT_SECRETS_SYSTEM);
    }

    /**
     * Overrides the Docker image used for the Hydra container.
     *
     * @param image Docker image name (e.g. {@code DockerImageName.parse("oryd/hydra:v2.3.0")})
     * @return this builder for chaining
     */
    public Builder image(DockerImageName image) {
      this.image = Objects.requireNonNull(image, "image must not be null");
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
     * Sets an environment variable that will be passed to the Hydra container.
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
     * Merges a map of environment variables that will be passed to the Hydra container.
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
     * Creates the configured Hydra container.
     *
     * <p>The returned container is not yet started. Call {@link OryHydraContainer#start()}
     * directly, or let the Testcontainers JUnit extension manage the lifecycle (e.g. with
     * {@code @Testcontainers} and {@code @Container}).
     *
     * @return configured but not yet started {@link OryHydraContainer}
     */
    public OryHydraContainer build() {
      return new OryHydraContainer(image, new HashMap<>(env), waitStrategy);
    }
  }
}
