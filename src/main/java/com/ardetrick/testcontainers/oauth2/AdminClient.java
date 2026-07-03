package com.ardetrick.testcontainers.oauth2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
}
