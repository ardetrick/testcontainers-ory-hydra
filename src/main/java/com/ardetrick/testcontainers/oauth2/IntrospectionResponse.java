package com.ardetrick.testcontainers.oauth2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;

/**
 * Result of introspecting a token via Hydra's admin API (RFC 7662).
 *
 * <p>Hydra issues opaque access tokens by default, so introspection is the way a resource server
 * validates them — introspecting a minted token is how a test asserts the token is real, active,
 * and attributed to the expected subject.
 *
 * @param active whether the token is currently active (RFC 7662 §2.2); {@code false} for unknown,
 *     expired, or revoked tokens
 * @param subject the token's subject ({@code sub}), or {@code null} when absent (e.g. inactive
 *     tokens, or client-credentials tokens attribute the client instead)
 * @param scope the granted scope (space-delimited), or {@code null} when absent
 * @param clientId the client the token was issued to, or {@code null} when absent
 * @param raw the full parsed introspection response; custom session claims appear under {@code ext}
 */
public record IntrospectionResponse(
    boolean active, String subject, String scope, String clientId, Map<String, Object> raw) {

  /**
   * Introspects a token via {@code POST /admin/oauth2/introspect}.
   *
   * @param adminBaseUri the admin API base URI
   * @param token the access or refresh token to introspect
   * @return the parsed introspection response; inactive tokens return {@code active == false}
   *     rather than throwing
   * @throws HydraFlowException if the request cannot be completed or the response cannot be parsed
   */
  public static IntrospectionResponse request(URI adminBaseUri, String token) {
    HttpResponse<String> response =
        Http.send(
            HttpClient.newHttpClient(),
            HttpRequest.newBuilder(adminBaseUri.resolve("/admin/oauth2/introspect"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("token=" + Http.encode(token)))
                .build());
    if (!Http.is2xx(response.statusCode())) {
      throw new HydraFlowException(
          "Introspection failed (HTTP " + response.statusCode() + "): " + response.body());
    }
    Map<String, Object> json;
    try {
      json = Json.parseObject(response.body());
    } catch (JsonParseException e) {
      throw new HydraFlowException("Unparseable introspection response: " + response.body(), e);
    }
    return new IntrospectionResponse(
        Boolean.TRUE.equals(json.get("active")),
        string(json, "sub"),
        string(json, "scope"),
        string(json, "client_id"),
        Collections.unmodifiableMap(json));
  }

  private static String string(Map<String, Object> json, String key) {
    Object value = json.get(key);
    return value == null ? null : value.toString();
  }
}
