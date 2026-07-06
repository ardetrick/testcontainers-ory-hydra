package com.ardetrick.testcontainers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
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
  static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("oryd/hydra:v26.2.0");
  static final String DEFAULT_DSN = "sqlite:///tmp/db.sqlite?_fk=true";
  static final String DEFAULT_SECRETS_SYSTEM = "testcontainers-ory-hydra-secret";
  // Non-resolvable sentinels: the authorization-code flow intercepts the login/consent redirects by
  // their challenge query parameter, so these hosts are never actually contacted.
  static final String DEFAULT_URLS_LOGIN = "http://hydra-login.invalid/login";
  static final String DEFAULT_URLS_CONSENT = "http://hydra-consent.invalid/consent";
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

  private final List<Map<String, Object>> declaredClients;

  private OryHydraContainer(
      DockerImageName imageName,
      Map<String, String> env,
      WaitStrategy waitStrategy,
      List<Map<String, Object>> declaredClients) {
    super(imageName);
    this.declaredClients = declaredClients;
    this.withEnv(env);
    this.withExposedPorts(HYDRA_ADMIN_PORT, HYDRA_PUBLIC_PORT);
    // Override the image entrypoint so we can run migration before serving.
    this.withCreateContainerCmdModifier(
        cmd ->
            cmd.withEntrypoint("sh", "-c", "hydra migrate sql -e --yes && hydra serve all --dev"));
    this.waitingFor(waitStrategy);
  }

  // Runs after the wait strategy: the admin API is ready, so declared client fixtures exist
  // before start() returns — including for containers shared across test classes.
  @Override
  protected void containerIsStarted(InspectContainerResponse containerInfo) {
    for (Map<String, Object> registration : declaredClients) {
      OAuth2Clients.createOrReplace(URI.create(adminBaseUriString()), registration);
    }
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
   * Creates an OAuth 2.0 client in the running Hydra instance using the Hydra CLI inside the
   * container.
   *
   * @param clientId the client identifier
   * @param clientSecret the client secret
   * @param redirectUris list of allowed redirect URIs
   * @throws IOException if an I/O error occurs communicating with the container
   * @throws InterruptedException if the thread is interrupted while waiting for the command
   * @throws IllegalStateException if the Hydra CLI exits with a non-zero status
   * @deprecated use {@link #createOrReplaceClient(Consumer)} (or the {@code Map} overload) for
   *     full-featured, upserting registration, or declare fixtures via {@link
   *     Builder#client(Consumer)}; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public void createOAuth2Client(String clientId, String clientSecret, List<String> redirectUris)
      throws IOException, InterruptedException {
    Objects.requireNonNull(clientId, "clientId must not be null");
    Objects.requireNonNull(clientSecret, "clientSecret must not be null");
    Objects.requireNonNull(redirectUris, "redirectUris must not be null");

    List<String> command = new ArrayList<>();
    command.add("hydra");
    command.add("create");
    command.add("oauth2-client");
    command.add("--endpoint");
    command.add("http://127.0.0.1:4445");
    command.add("--id");
    command.add(clientId);
    command.add("--secret");
    command.add(clientSecret);
    for (String redirectUri : redirectUris) {
      command.add("--redirect-uri");
      command.add(redirectUri);
    }

    var result = execInContainer(command.toArray(new String[0]));
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(
          "Failed to create OAuth2 client (exit code "
              + result.getExitCode()
              + "): "
              + result.getStderr());
    }
  }

  /**
   * Registers an OAuth 2.0 client on the running container, or replaces the existing client with
   * the same {@code client_id} — an upsert, so re-running test classes against a shared container
   * converge on the declared state instead of failing on duplicates.
   *
   * <p>The map is Hydra's client JSON, passed through verbatim (any field Hydra accepts works;
   * nested values are allowed); it must contain {@code client_id}.
   *
   * @param registration Hydra client JSON as a map, including {@code client_id}
   */
  public void createOrReplaceClient(Map<String, Object> registration) {
    OAuth2Clients.createOrReplace(URI.create(adminBaseUriString()), registration);
  }

  /**
   * Registers an OAuth 2.0 client on the running container using the typed registration builder —
   * or replaces the existing client with the same {@code client_id} (an upsert).
   *
   * <pre>{@code
   * hydra.createOrReplaceClient(client -> client
   *     .clientId("my-app")
   *     .clientSecret("my-secret")
   *     .grantTypes("client_credentials"));
   * }</pre>
   *
   * @param customizer receives a fresh {@link OAuth2ClientRegistration} to populate; must set
   *     {@code client_id}
   */
  public void createOrReplaceClient(Consumer<OAuth2ClientRegistration> customizer) {
    Objects.requireNonNull(customizer, "customizer must not be null");
    OAuth2ClientRegistration registration = OAuth2ClientRegistration.create();
    customizer.accept(registration);
    createOrReplaceClient(registration.toMap());
  }

  /**
   * Starts a fluent client-credentials flow (RFC 6749 §4.4) against this container.
   *
   * <p>The grant has no end-user, so a successful result never carries an ID token or refresh
   * token. If no client is supplied, an ephemeral one is created for the request.
   *
   * @return a new {@link ClientCredentialsFlow} bound to this container's endpoints
   */
  public ClientCredentialsFlow clientCredentialsFlow() {
    return new ClientCredentialsFlow(
        URI.create(publicBaseUriString()), URI.create(adminBaseUriString()));
  }

  /**
   * Starts a fluent authorization-code flow against this container.
   *
   * <p>The flow auto-accepts (or, on request, rejects) Hydra's login and consent challenges via the
   * admin API, so a real token can be obtained without a browser or a user-written login/consent
   * app. Login and consent redirects are intercepted by their challenge query parameter, not by
   * host, so the configured {@code URLS_LOGIN}/{@code URLS_CONSENT} values (non-resolvable
   * sentinels by default) are never contacted — the flow also works when they are overridden via
   * {@link Builder#urlsLogin(String)} / {@link Builder#urlsConsent(String)}.
   *
   * @return a new {@link AuthorizationCodeFlow} bound to this container's endpoints
   */
  public AuthorizationCodeFlow authorizationCodeFlow() {
    return new AuthorizationCodeFlow(
        URI.create(publicBaseUriString()), URI.create(adminBaseUriString()));
  }

  /**
   * Fetches and parses the OpenID Connect discovery document from this container.
   *
   * <p>Endpoint accessors are re-targeted at the mapped public host and port (the document
   * advertises Hydra's configured issuer, which cannot know the mapped port), so values like {@link
   * OpenIdConfiguration#jwksUri()} are directly usable from the test.
   *
   * @return the parsed discovery document
   */
  public OpenIdConfiguration openIdConfiguration() {
    return OpenIdConfiguration.fetch(URI.create(publicBaseUriString()));
  }

  /**
   * Introspects a token via Hydra's admin API (RFC 7662).
   *
   * <p>Hydra issues opaque access tokens by default, so this is how a test asserts a minted token
   * is real, active, and attributed to the expected subject — the same check a resource server
   * under test performs.
   *
   * @param token the access or refresh token to introspect
   * @return the introspection response; inactive or unknown tokens return {@code active == false}
   */
  public IntrospectionResponse introspect(String token) {
    return IntrospectionResponse.request(URI.create(adminBaseUriString()), token);
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

  // The helpers below were released in 0.0.5 and are retained for backwards compatibility only.
  // Their endpoint URIs are all discoverable via the document at getOpenIdDiscoveryUri().

  /**
   * Builds a convenience link to the OAuth2 authorization endpoint.
   *
   * @return absolute URI pointing to {@code /oauth2/auth} on the public endpoint.
   * @deprecated use {@code openIdConfiguration().authorizationEndpoint()} instead; scheduled for
   *     removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getOAuth2AuthUri() {
    return URI.create(publicBaseUriString() + "/oauth2/auth");
  }

  /**
   * Builds a convenience link to the JSON Web Key Set endpoint.
   *
   * @return absolute URI pointing to {@code /.well-known/jwks.json} on the public endpoint.
   * @deprecated use {@code openIdConfiguration().jwksUri()} instead; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getPublicJwksUri() {
    return URI.create(publicBaseUriString() + "/.well-known/jwks.json");
  }

  /**
   * Builds a convenience link to the OAuth 2.0 authorization server metadata endpoint.
   *
   * @return absolute URI pointing to {@code /.well-known/oauth-authorization-server} on the public
   *     endpoint.
   * @deprecated use {@link #openIdConfiguration()} or build the path from {@link
   *     #publicBaseUriString()}; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getOAuthAuthorizationServerDiscoveryUri() {
    return URI.create(publicBaseUriString() + "/.well-known/oauth-authorization-server");
  }

  /**
   * Builds a convenience link to the OAuth2 token revocation endpoint.
   *
   * @return absolute URI pointing to {@code /oauth2/revoke} on the public endpoint.
   * @deprecated use {@code openIdConfiguration().revocationEndpoint()} instead; scheduled for
   *     removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getOAuth2RevokeUri() {
    return URI.create(publicBaseUriString() + "/oauth2/revoke");
  }

  /**
   * Builds a convenience link to the OpenID Connect userinfo endpoint.
   *
   * @return absolute URI pointing to {@code /userinfo} on the public endpoint.
   * @deprecated use {@code openIdConfiguration().userinfoEndpoint()} instead; scheduled for
   *     removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getUserInfoUri() {
    return URI.create(publicBaseUriString() + "/userinfo");
  }

  /**
   * Builds a convenience link to the OpenID Connect logout endpoint.
   *
   * @return absolute URI pointing to {@code /oauth2/sessions/logout} on the public endpoint.
   * @deprecated use {@code openIdConfiguration().endSessionEndpoint()} instead; scheduled for
   *     removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getOAuth2SessionsLogoutUri() {
    return URI.create(publicBaseUriString() + "/oauth2/sessions/logout");
  }

  /**
   * Builds a convenience link to the admin clients endpoint.
   *
   * @return absolute URI pointing to {@code /admin/clients} on the admin endpoint.
   * @deprecated build the path from {@link #adminBaseUriString()}; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getAdminClientsUri() {
    return URI.create(adminBaseUriString() + "/admin/clients");
  }

  /**
   * Builds a convenience link to the admin token introspection endpoint.
   *
   * @return absolute URI pointing to {@code /admin/oauth2/introspect} on the admin endpoint.
   * @deprecated use {@link #introspect(String)} instead, or build the path from {@link
   *     #adminBaseUriString()}; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getAdminOAuth2IntrospectUri() {
    return URI.create(adminBaseUriString() + "/admin/oauth2/introspect");
  }

  /**
   * Builds a convenience link to the admin login request endpoint.
   *
   * @return absolute URI pointing to {@code /admin/oauth2/auth/requests/login} on the admin
   *     endpoint.
   * @deprecated build the path from {@link #adminBaseUriString()}; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getAdminLoginRequestUri() {
    return URI.create(adminBaseUriString() + "/admin/oauth2/auth/requests/login");
  }

  /**
   * Builds a convenience link to the admin consent request endpoint.
   *
   * @return absolute URI pointing to {@code /admin/oauth2/auth/requests/consent} on the admin
   *     endpoint.
   * @deprecated build the path from {@link #adminBaseUriString()}; scheduled for removal.
   */
  @Deprecated(since = "0.0.6", forRemoval = true)
  public URI getAdminConsentRequestUri() {
    return URI.create(adminBaseUriString() + "/admin/oauth2/auth/requests/consent");
  }

  /** Fluent builder for configuring the Hydra container. */
  public static class Builder {

    private DockerImageName image = DEFAULT_IMAGE;
    private Map<String, String> env = new HashMap<>();
    private WaitStrategy waitStrategy = DEFAULT_WAIT_STRATEGY;
    private final List<Map<String, Object>> clients = new ArrayList<>();

    /**
     * Creates an empty builder; configure it via the fluent setters before calling {@link
     * #build()}.
     */
    public Builder() {
      env.put("DSN", DEFAULT_DSN);
      env.put("SECRETS_SYSTEM", DEFAULT_SECRETS_SYSTEM);
      env.put("URLS_LOGIN", DEFAULT_URLS_LOGIN);
      env.put("URLS_CONSENT", DEFAULT_URLS_CONSENT);
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
     * <p>Hydra's internal ports are fixed to the image defaults (public 4444, admin 4445) — the
     * exposed ports, wait strategy, and mapped-port accessors all assume them — so {@code
     * SERVE_PUBLIC_PORT} and {@code SERVE_ADMIN_PORT} are rejected rather than allowed to present
     * as a startup timeout.
     *
     * @param key environment variable name
     * @param value environment variable value
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code key} would change Hydra's internal ports
     */
    public Builder env(String key, String value) {
      Objects.requireNonNull(key, "key must not be null");
      Objects.requireNonNull(value, "value must not be null");
      if (key.equals("SERVE_PUBLIC_PORT") || key.equals("SERVE_ADMIN_PORT")) {
        throw new IllegalArgumentException(
            key
                + " is not supported: the container fixes Hydra's internal ports to the image"
                + " defaults (public 4444, admin 4445); the exposed ports, wait strategy, and"
                + " mapped-port accessors all assume them. Use the mapped ports for access"
                + " instead.");
      }
      this.env.put(key, value);
      return this;
    }

    /**
     * Merges a map of environment variables that will be passed to the Hydra container.
     *
     * <p>Applies the same validation as {@link #env(String, String)} to every entry.
     *
     * @param env environment variables to add
     * @return this builder for chaining
     * @throws IllegalArgumentException if any key would change Hydra's internal ports
     */
    public Builder env(Map<String, String> env) {
      Objects.requireNonNull(env, "env must not be null");
      env.forEach(this::env);
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
     * Declares an OAuth 2.0 client that will exist as soon as the container has started — a
     * declarative fixture, useful when a shared container serves many test classes.
     *
     * <p>The map is Hydra's client JSON, passed through verbatim (any field Hydra accepts works;
     * nested values are allowed). It must contain {@code client_id}: fixtures are upserted, so a
     * restarted or reused container converges on the declared state instead of failing on
     * duplicates. May be called multiple times to declare multiple clients.
     *
     * @param registration Hydra client JSON as a map, including {@code client_id}
     * @return this builder for chaining
     */
    public Builder client(Map<String, Object> registration) {
      Objects.requireNonNull(registration, "registration must not be null");
      this.clients.add(new LinkedHashMap<>(registration));
      return this;
    }

    /**
     * Declares an OAuth 2.0 client fixture using the typed registration builder — equivalent to
     * {@link #client(Map)} with the customized registration's map.
     *
     * <pre>{@code
     * OryHydraContainer.builder()
     *     .client(client -> client
     *         .clientId("my-app")
     *         .clientSecret("my-secret")
     *         .grantTypes("authorization_code", "refresh_token")
     *         .responseTypes("code")
     *         .redirectUris("https://app.example/callback"))
     *     .build();
     * }</pre>
     *
     * @param customizer receives a fresh {@link OAuth2ClientRegistration} to populate; must set
     *     {@code client_id}
     * @return this builder for chaining
     */
    public Builder client(Consumer<OAuth2ClientRegistration> customizer) {
      Objects.requireNonNull(customizer, "customizer must not be null");
      OAuth2ClientRegistration registration = OAuth2ClientRegistration.create();
      customizer.accept(registration);
      return client(registration.toMap());
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
      return new OryHydraContainer(image, new HashMap<>(env), waitStrategy, List.copyOf(clients));
    }
  }
}
