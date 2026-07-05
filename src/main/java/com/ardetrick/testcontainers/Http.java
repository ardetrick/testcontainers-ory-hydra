package com.ardetrick.testcontainers;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Small HTTP helpers shared by the OAuth flows. */
final class Http {

  private Http() {}

  static HttpResponse<String> send(HttpClient http, HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new HydraFlowException("Request to " + request.uri() + " failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HydraFlowException("Request to " + request.uri() + " was interrupted", e);
    }
  }

  static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  /** Returns whether the status code is in the 2xx (success) class. */
  static boolean is2xx(int statusCode) {
    return statusCode / 100 == 2;
  }

  /** Returns whether the status code is in the 3xx (redirection) class. */
  static boolean is3xx(int statusCode) {
    return statusCode / 100 == 3;
  }
}
