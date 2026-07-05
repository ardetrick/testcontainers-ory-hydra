package com.ardetrick.testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Registers OAuth 2.0 clients for test setup.
 *
 * <p>The registration is Hydra's own client JSON, passed through verbatim — this library
 * deliberately does not model the client schema, so any field Hydra accepts works and none can
 * drift out of date. Used by the container's declarative client fixtures and usable directly.
 */
final class OAuth2Clients {

  private OAuth2Clients() {}

  /**
   * Creates the client, or replaces the existing client with the same {@code client_id} — an
   * upsert, so declared fixtures are idempotent across container restarts and reuse.
   *
   * @param adminBaseUri the admin API base URI
   * @param registration Hydra client JSON as a map (nested values allowed); must contain {@code
   *     client_id}
   * @throws HydraFlowException if {@code client_id} is missing or the request fails
   */
  public static void createOrReplace(URI adminBaseUri, Map<String, Object> registration) {
    Object clientId = registration.get("client_id");
    if (clientId == null) {
      throw new HydraFlowException(
          "client registration requires a client_id so it can be upserted deterministically");
    }
    HttpClient http = HttpClient.newHttpClient();
    String body = JsonWriter.write(registration);

    HttpResponse<String> created =
        Http.send(
            http,
            HttpRequest.newBuilder(adminBaseUri.resolve("/admin/clients"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    if (Http.is2xx(created.statusCode())) {
      return;
    }
    if (created.statusCode() == 409) {
      HttpResponse<String> replaced =
          Http.send(
              http,
              HttpRequest.newBuilder(
                      adminBaseUri.resolve("/admin/clients/" + Http.encode(clientId.toString())))
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json")
                  .PUT(HttpRequest.BodyPublishers.ofString(body))
                  .build());
      if (Http.is2xx(replaced.statusCode())) {
        return;
      }
      throw new HydraFlowException(
          "Failed to replace OAuth2 client '"
              + clientId
              + "' (HTTP "
              + replaced.statusCode()
              + "): "
              + replaced.body());
    }
    throw new HydraFlowException(
        "Failed to create OAuth2 client '"
            + clientId
            + "' (HTTP "
            + created.statusCode()
            + "): "
            + created.body());
  }
}
