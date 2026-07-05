package com.ardetrick.testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives an OAuth 2.0 client-credentials grant (RFC 6749 §4.4) against Hydra.
 *
 * <p>The grant has no end-user, so a successful {@link FlowResult.TokenResponse} never carries an
 * ID token or refresh token. If no client is supplied via {@link #clientId(String)}, an ephemeral
 * client is created for the request.
 */
public final class ClientCredentialsFlow {

  private final URI publicBaseUri;
  private final URI adminBaseUri;

  private String clientId;
  private String clientSecret;
  private final List<String> scopes = new ArrayList<>();

  /**
   * Creates a flow bound to a running Hydra container's endpoints.
   *
   * @param publicBaseUri the public API base URI
   * @param adminBaseUri the admin API base URI
   */
  public ClientCredentialsFlow(URI publicBaseUri, URI adminBaseUri) {
    this.publicBaseUri = publicBaseUri;
    this.adminBaseUri = adminBaseUri;
  }

  /**
   * Uses an existing client instead of creating an ephemeral one. Requires {@link
   * #clientSecret(String)}.
   *
   * @param clientId the client identifier
   * @return this flow
   */
  public ClientCredentialsFlow clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  /**
   * Sets the secret for the client supplied via {@link #clientId(String)}.
   *
   * @param clientSecret the client secret
   * @return this flow
   */
  public ClientCredentialsFlow clientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  /**
   * Sets the requested scopes (default none).
   *
   * @param scopes requested scopes
   * @return this flow
   */
  public ClientCredentialsFlow scopes(String... scopes) {
    this.scopes.clear();
    this.scopes.addAll(Arrays.asList(scopes));
    return this;
  }

  /**
   * Runs the flow and returns the result.
   *
   * @return a {@link FlowResult.TokenResponse} on success, or a {@link FlowResult.OAuthError} on an
   *     OAuth error response
   * @throws HydraFlowException if the request cannot be completed
   */
  public FlowResult execute() {
    if (clientId == null) {
      createEphemeralClient();
    } else if (clientSecret == null) {
      throw new HydraFlowException("clientSecret must be set when clientId is provided");
    }
    return TokenEndpointClient.clientCredentials(
        publicBaseUri.resolve("/oauth2/token"), clientId, clientSecret, scopes);
  }

  private void createEphemeralClient() {
    String id = "tc-" + UUID.randomUUID();
    String secret = UUID.randomUUID().toString();
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("client_id", id);
    registration.put("client_secret", secret);
    registration.put("grant_types", List.of("client_credentials"));
    registration.put("token_endpoint_auth_method", "client_secret_basic");
    if (!scopes.isEmpty()) {
      registration.put("scope", String.join(" ", scopes));
    }
    new AdminClient(HttpClient.newHttpClient(), adminBaseUri).createClient(registration);
    this.clientId = id;
    this.clientSecret = secret;
  }
}
