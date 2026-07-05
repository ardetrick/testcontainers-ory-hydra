package com.ardetrick.testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;

/**
 * The OpenID Connect discovery document, fetched from {@code /.well-known/openid-configuration} and
 * parsed into typed endpoint accessors.
 *
 * <p>The document advertises endpoints using Hydra's configured issuer, which cannot know the
 * container's dynamically mapped port — so each endpoint accessor is re-targeted at the mapped
 * public host and port (any issuer path prefix is stripped, since Hydra serves its routes at the
 * root of its port) and is directly usable from the test. The as-advertised values are preserved in
 * {@link #raw()}.
 *
 * @param issuer the advertised issuer identifier, as-is (not re-targeted)
 * @param authorizationEndpoint the OAuth 2.0 authorization endpoint, or {@code null} if absent
 * @param tokenEndpoint the OAuth 2.0 token endpoint, or {@code null} if absent
 * @param jwksUri the JSON Web Key Set endpoint, or {@code null} if absent
 * @param userinfoEndpoint the OIDC userinfo endpoint, or {@code null} if absent
 * @param revocationEndpoint the token revocation endpoint, or {@code null} if absent
 * @param endSessionEndpoint the OIDC logout endpoint, or {@code null} if absent
 * @param raw the full parsed document with as-advertised values
 */
public record OpenIdConfiguration(
    String issuer,
    URI authorizationEndpoint,
    URI tokenEndpoint,
    URI jwksUri,
    URI userinfoEndpoint,
    URI revocationEndpoint,
    URI endSessionEndpoint,
    Map<String, Object> raw) {

  /**
   * Fetches and parses the discovery document from the given public API base URI.
   *
   * @param publicBaseUri the public API base URI (host and mapped port)
   * @return the parsed document with endpoint accessors re-targeted at {@code publicBaseUri}
   * @throws HydraFlowException if the request cannot be completed or the response cannot be parsed
   */
  public static OpenIdConfiguration fetch(URI publicBaseUri) {
    HttpResponse<String> response =
        Http.send(
            HttpClient.newHttpClient(),
            HttpRequest.newBuilder(publicBaseUri.resolve("/.well-known/openid-configuration"))
                .header("Accept", "application/json")
                .GET()
                .build());
    if (!Http.is2xx(response.statusCode())) {
      throw new HydraFlowException(
          "Fetching the discovery document failed (HTTP "
              + response.statusCode()
              + "): "
              + response.body());
    }
    Map<String, Object> json;
    try {
      json = Json.parseObject(response.body());
    } catch (JsonParseException e) {
      throw new HydraFlowException("Unparseable discovery document: " + response.body(), e);
    }
    return new OpenIdConfiguration(
        string(json, "issuer"),
        endpoint(json, "authorization_endpoint", publicBaseUri),
        endpoint(json, "token_endpoint", publicBaseUri),
        endpoint(json, "jwks_uri", publicBaseUri),
        endpoint(json, "userinfo_endpoint", publicBaseUri),
        endpoint(json, "revocation_endpoint", publicBaseUri),
        endpoint(json, "end_session_endpoint", publicBaseUri),
        Collections.unmodifiableMap(json));
  }

  private static String string(Map<String, Object> json, String key) {
    Object value = json.get(key);
    return value == null ? null : value.toString();
  }

  private static URI endpoint(Map<String, Object> json, String key, URI publicBaseUri) {
    String value = string(json, key);
    if (value == null) {
      return null;
    }
    return retarget(URI.create(value), issuerPathPrefix(string(json, "issuer")), publicBaseUri);
  }

  // Shared by the authorization-code flow driver so the two issuer-to-container translation
  // layers cannot drift apart.

  /**
   * Extracts the path prefix of the given issuer, or {@code ""} when the issuer has none — a
   * path-bearing issuer (e.g. {@code http://gateway.example/hydra}) makes Hydra advertise endpoints
   * and issue redirects under a prefix it does not itself serve.
   */
  static String issuerPathPrefix(String issuer) {
    if (issuer == null) {
      return "";
    }
    String path = URI.create(issuer).getRawPath();
    if (path == null || path.isEmpty() || path.equals("/")) {
      return "";
    }
    // Normalize a trailing slash so an issuer of ".../prefix/" strips the same as ".../prefix".
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  /**
   * Re-targets an issuer-derived URI at the mapped public authority: swaps in the authority, strips
   * the issuer's path prefix, and keeps the raw path/query/fragment otherwise untouched (decoding
   * and re-encoding could corrupt encoded separators).
   */
  static URI retarget(URI advertised, String issuerPathPrefix, URI publicBaseUri) {
    String path = advertised.getRawPath() == null ? "" : advertised.getRawPath();
    if (!issuerPathPrefix.isEmpty() && path.startsWith(issuerPathPrefix)) {
      path = path.substring(issuerPathPrefix.length());
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    String query = advertised.getRawQuery() == null ? "" : "?" + advertised.getRawQuery();
    String fragment = advertised.getRawFragment() == null ? "" : "#" + advertised.getRawFragment();
    return URI.create(
        publicBaseUri.getScheme() + "://" + publicBaseUri.getAuthority() + path + query + fragment);
  }
}
