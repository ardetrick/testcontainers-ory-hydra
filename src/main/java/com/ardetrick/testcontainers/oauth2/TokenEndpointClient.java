package com.ardetrick.testcontainers.oauth2;

import com.ardetrick.testcontainers.oauth2.FlowResult.OAuthError;
import com.ardetrick.testcontainers.oauth2.FlowResult.TokenResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Performs requests against Hydra's OAuth 2.0 token endpoint. */
final class TokenEndpointClient {

  private TokenEndpointClient() {}

  /** Client-credentials grant (RFC 6749 §4.4) using {@code client_secret_basic} authentication. */
  static FlowResult clientCredentials(
      URI tokenEndpoint, String clientId, String clientSecret, List<String> scopes) {
    StringBuilder form = new StringBuilder("grant_type=client_credentials");
    if (scopes != null && !scopes.isEmpty()) {
      form.append("&scope=").append(Http.encode(String.join(" ", scopes)));
    }
    return post(tokenEndpoint, form.toString(), clientId, clientSecret);
  }

  /** Authorization-code exchange (RFC 6749 §4.1.3) using {@code client_secret_basic}. */
  static FlowResult authorizationCode(
      URI tokenEndpoint,
      String clientId,
      String clientSecret,
      String code,
      String redirectUri,
      String codeVerifier) {
    StringBuilder form = new StringBuilder("grant_type=authorization_code");
    form.append("&code=").append(Http.encode(code));
    form.append("&redirect_uri=").append(Http.encode(redirectUri));
    if (codeVerifier != null) {
      form.append("&code_verifier=").append(Http.encode(codeVerifier));
    }
    return post(tokenEndpoint, form.toString(), clientId, clientSecret);
  }

  private static FlowResult post(
      URI tokenEndpoint, String form, String clientId, String clientSecret) {
    String credentials =
        Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    HttpResponse<String> response =
        Http.send(
            HttpClient.newHttpClient(),
            HttpRequest.newBuilder(tokenEndpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + credentials)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());

    Map<String, Object> json;
    try {
      json = Json.parseObject(response.body());
    } catch (JsonParseException e) {
      throw new HydraFlowException(
          "Unparseable token response (HTTP " + response.statusCode() + "): " + response.body(), e);
    }

    if (Http.is2xx(response.statusCode())) {
      return new TokenResponse(
          string(json, "access_token"),
          string(json, "id_token"),
          string(json, "refresh_token"),
          string(json, "token_type"),
          longValue(json, "expires_in"),
          string(json, "scope"),
          Collections.unmodifiableMap(json));
    }
    return new OAuthError(
        string(json, "error"), string(json, "error_description"), string(json, "error_uri"));
  }

  private static String string(Map<String, Object> json, String key) {
    Object value = json.get(key);
    return value == null ? null : value.toString();
  }

  private static long longValue(Map<String, Object> json, String key) {
    Object value = json.get(key);
    return value instanceof Number number ? number.longValue() : 0L;
  }
}
