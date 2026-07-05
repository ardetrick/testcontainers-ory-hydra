package com.ardetrick.testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for an OAuth 2.0 client registration map.
 *
 * <p>The typed methods cover the standard client metadata fields (RFC 7591), so common values can
 * be set without knowing Hydra's JSON keys; anything else — including Hydra-specific fields — is
 * set with {@link #put(String, Object)} or {@link #putAll(Map)}. Later calls override earlier ones,
 * typed or not. {@link #toMap()} produces the plain registration map accepted by {@link
 * OryHydraContainer.Builder#client(Map)}.
 *
 * <pre>{@code
 * OryHydraContainer.builder()
 *     .client(OAuth2ClientRegistration.create()
 *         .clientId("my-app")
 *         .clientSecret("my-secret")
 *         .grantTypes("authorization_code", "refresh_token")
 *         .responseTypes("code")
 *         .redirectUris("https://app.example/callback")
 *         .scope("openid", "offline_access")
 *         .toMap())
 *     .build();
 * }</pre>
 */
public final class OAuth2ClientRegistration {

  private final Map<String, Object> values = new LinkedHashMap<>();

  private OAuth2ClientRegistration() {}

  /**
   * Starts an empty registration.
   *
   * @return a new registration builder
   */
  public static OAuth2ClientRegistration create() {
    return new OAuth2ClientRegistration();
  }

  /**
   * Sets {@code client_id}. Required for declarative fixtures, which upsert by client id.
   *
   * @param clientId the client identifier
   * @return this registration
   */
  public OAuth2ClientRegistration clientId(String clientId) {
    return put("client_id", clientId);
  }

  /**
   * Sets {@code client_secret}. Omit for public clients ({@link #tokenEndpointAuthMethod(String)}
   * {@code none}).
   *
   * @param clientSecret the client secret
   * @return this registration
   */
  public OAuth2ClientRegistration clientSecret(String clientSecret) {
    return put("client_secret", clientSecret);
  }

  /**
   * Sets {@code redirect_uris}.
   *
   * @param redirectUris the allowed redirect URIs
   * @return this registration
   */
  public OAuth2ClientRegistration redirectUris(String... redirectUris) {
    return put("redirect_uris", List.of(redirectUris));
  }

  /**
   * Sets {@code grant_types} (e.g. {@code authorization_code}, {@code refresh_token}, {@code
   * client_credentials}).
   *
   * @param grantTypes the allowed grant types
   * @return this registration
   */
  public OAuth2ClientRegistration grantTypes(String... grantTypes) {
    return put("grant_types", List.of(grantTypes));
  }

  /**
   * Sets {@code response_types} (e.g. {@code code}).
   *
   * @param responseTypes the allowed response types
   * @return this registration
   */
  public OAuth2ClientRegistration responseTypes(String... responseTypes) {
    return put("response_types", List.of(responseTypes));
  }

  /**
   * Sets {@code scope} as the space-delimited join of the given scopes.
   *
   * @param scopes the allowed scopes
   * @return this registration
   */
  public OAuth2ClientRegistration scope(String... scopes) {
    return put("scope", String.join(" ", scopes));
  }

  /**
   * Sets {@code token_endpoint_auth_method} (e.g. {@code client_secret_basic}, or {@code none} for
   * public clients).
   *
   * @param method the token endpoint authentication method
   * @return this registration
   */
  public OAuth2ClientRegistration tokenEndpointAuthMethod(String method) {
    return put("token_endpoint_auth_method", method);
  }

  /**
   * Sets any registration field by its JSON key — the escape hatch for fields without a typed
   * method, including Hydra-specific ones. Overrides a previously set value for the same key.
   *
   * @param key the Hydra client JSON key
   * @param value the value (primitives, {@code List}, and nested {@code Map} values are allowed)
   * @return this registration
   */
  public OAuth2ClientRegistration put(String key, Object value) {
    values.put(Objects.requireNonNull(key, "key must not be null"), value);
    return this;
  }

  /**
   * Sets all entries of the given map, overriding any previously set values for the same keys.
   *
   * @param values registration fields by their Hydra client JSON keys
   * @return this registration
   */
  public OAuth2ClientRegistration putAll(Map<String, Object> values) {
    Objects.requireNonNull(values, "values must not be null");
    this.values.putAll(values);
    return this;
  }

  /**
   * Returns the registration as a plain map, as accepted by {@link
   * OryHydraContainer.Builder#client(Map)}.
   *
   * @return a copy of the registration map
   */
  public Map<String, Object> toMap() {
    return new LinkedHashMap<>(values);
  }
}
