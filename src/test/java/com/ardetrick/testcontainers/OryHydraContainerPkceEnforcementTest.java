package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves Hydra actually enforces the PKCE S256 challenge the flow driver sends — a happy-path PKCE
 * test alone cannot distinguish enforcement from the challenge being silently ignored.
 *
 * <p>Drives the flow manually (the driver's verifier is internal, so a wrong verifier cannot be
 * injected through the public API) and exchanges the code with a verifier that does not match the
 * challenge, expecting rejection. Pinning this behavior guards it across Hydra image upgrades.
 */
public class OryHydraContainerPkceEnforcementTest {

  static final String REDIRECT_URI = "http://localhost/callback";

  @Test
  public void wrongPkceVerifierIsRejected() throws Exception {
    try (var container = OryHydraContainer.builder().build()) {
      container.start();
      var publicBase = URI.create(container.publicBaseUriString());
      var http =
          HttpClient.newBuilder()
              .cookieHandler(new CookieManager())
              .followRedirects(HttpClient.Redirect.NEVER)
              .build();
      var admin = new AdminClient(http, URI.create(container.adminBaseUriString()));

      Map<String, Object> registration = new LinkedHashMap<>();
      registration.put("client_id", "pkce-client");
      registration.put("client_secret", "pkce-secret");
      registration.put("grant_types", List.of("authorization_code"));
      registration.put("response_types", List.of("code"));
      registration.put("redirect_uris", List.of(REDIRECT_URI));
      registration.put("scope", "openid");
      registration.put("token_endpoint_auth_method", "client_secret_basic");
      admin.createClient(registration);

      var verifier = "correct-verifier-correct-verifier-correct-verifier-1234";
      var challenge =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  MessageDigest.getInstance("SHA-256")
                      .digest(verifier.getBytes(StandardCharsets.US_ASCII)));

      var current =
          publicBase.resolve(
              "/oauth2/auth?client_id=pkce-client&response_type=code&scope=openid"
                  + "&state=pkce-enforcement-state"
                  + "&redirect_uri="
                  + Http.encode(REDIRECT_URI)
                  + "&code_challenge="
                  + challenge
                  + "&code_challenge_method=S256");

      String code = null;
      for (int hop = 0; hop < 10 && code == null; hop++) {
        var response =
            http.send(
                HttpRequest.newBuilder(current).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(Http.is3xx(response.statusCode()))
            .as("expected redirect, got: %s", response.body())
            .isTrue();
        var location = current.resolve(response.headers().firstValue("location").orElseThrow());
        var query = location.getRawQuery() == null ? "" : location.getRawQuery();
        if (query.contains("login_challenge=")) {
          current =
              onPublicPort(admin.acceptLogin(param(query, "login_challenge"), "u"), publicBase);
        } else if (query.contains("consent_challenge=")) {
          current =
              onPublicPort(
                  admin.acceptConsent(
                      param(query, "consent_challenge"), List.of("openid"), List.of(), Map.of()),
                  publicBase);
        } else if ("localhost".equals(location.getHost())
            && "/callback".equals(location.getPath())) {
          code = param(query, "code");
        } else {
          current = onPublicPort(location, publicBase);
        }
      }
      assertThat(code).isNotNull();

      // Exchange the code with a verifier that does NOT match the challenge above.
      var result =
          TokenEndpointClient.authorizationCode(
              publicBase.resolve("/oauth2/token"),
              "pkce-client",
              "pkce-secret",
              code,
              REDIRECT_URI,
              "wrong-verifier-wrong-verifier-wrong-verifier-9999999999");

      assertThat(result)
          .as("Hydra must reject a code exchange whose verifier does not match the challenge")
          .isInstanceOf(FlowResult.OAuthError.class);
    }
  }

  private static String param(String rawQuery, String name) {
    for (String pair : rawQuery.split("&")) {
      if (pair.startsWith(name + "=")) {
        return Http.decode(pair.substring(name.length() + 1));
      }
    }
    return null;
  }

  private static URI onPublicPort(URI location, URI publicBase) {
    var query = location.getRawQuery() == null ? "" : "?" + location.getRawQuery();
    return URI.create(publicBase + location.getRawPath() + query);
  }
}
