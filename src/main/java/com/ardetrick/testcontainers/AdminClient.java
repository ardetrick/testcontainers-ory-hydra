package com.ardetrick.testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operations against Hydra's admin API used by the OAuth flows. */
final class AdminClient {

  private final HttpClient http;
  private final URI adminBaseUri;

  AdminClient(HttpClient http, URI adminBaseUri) {
    this.http = http;
    this.adminBaseUri = adminBaseUri;
  }

  /** Registers an OAuth 2.0 client via {@code POST /admin/clients}. */
  void createClient(Map<String, Object> registration) {
    HttpResponse<String> response =
        Http.send(
            http,
            HttpRequest.newBuilder(adminBaseUri.resolve("/admin/clients"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonWriter.write(registration)))
                .build());
    if (!Http.is2xx(response.statusCode())) {
      throw new HydraFlowException(
          "Failed to create OAuth2 client (HTTP "
              + response.statusCode()
              + "): "
              + response.body());
    }
  }

  URI acceptLogin(String challenge, String subject) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("subject", subject);
    body.put("remember", false);
    body.put("remember_for", 0);
    return redirectTarget(put("login", "accept", "login_challenge", challenge, body));
  }

  URI rejectLogin(String challenge, String error, String description) {
    return redirectTarget(
        put("login", "reject", "login_challenge", challenge, errorBody(error, description)));
  }

  URI acceptConsent(
      String challenge,
      List<String> grantScope,
      List<String> grantAudience,
      Map<String, Object> session) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("grant_scope", grantScope);
    body.put("grant_access_token_audience", grantAudience);
    body.put("remember", false);
    body.put("remember_for", 0);
    if (session != null && !session.isEmpty()) {
      body.put("session", session);
    }
    return redirectTarget(put("consent", "accept", "consent_challenge", challenge, body));
  }

  URI rejectConsent(String challenge, String error, String description) {
    return redirectTarget(
        put("consent", "reject", "consent_challenge", challenge, errorBody(error, description)));
  }

  private HttpResponse<String> put(
      String flow, String action, String param, String challenge, Map<String, Object> body) {
    URI uri =
        adminBaseUri.resolve(
            "/admin/oauth2/auth/requests/"
                + flow
                + "/"
                + action
                + "?"
                + param
                + "="
                + Http.encode(challenge));
    return Http.send(
        http,
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(JsonWriter.write(body)))
            .build());
  }

  private static Map<String, Object> errorBody(String error, String description) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", error);
    body.put("error_description", description == null ? "" : description);
    return body;
  }

  private static URI redirectTarget(HttpResponse<String> response) {
    if (!Http.is2xx(response.statusCode())) {
      throw new HydraFlowException(
          "Admin request failed (HTTP " + response.statusCode() + "): " + response.body());
    }
    Map<String, Object> json;
    try {
      json = Json.parseObject(response.body());
    } catch (JsonParseException e) {
      throw new HydraFlowException("Unparseable admin response: " + response.body(), e);
    }
    Object redirect = json.get("redirect_to");
    if (redirect == null) {
      throw new HydraFlowException("Admin response missing redirect_to: " + response.body());
    }
    return URI.create(redirect.toString());
  }
}
