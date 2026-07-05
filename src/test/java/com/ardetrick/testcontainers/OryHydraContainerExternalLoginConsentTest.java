package com.ardetrick.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the "real login/consent app" use case: a user overrides {@code URLS_LOGIN}
 * and {@code URLS_CONSENT} to point at their own application and drives the authorization-code flow
 * externally (in production tests, with a browser), answering Hydra's challenges from their app via
 * the admin API.
 *
 * <p>This test plays both external roles itself — the user-agent and the login/consent app —
 * without using {@link AuthorizationCodeFlow}, which must never be required for this use case. See
 * <a href="https://github.com/ardetrick/ory-hydra-refrence-java">ory-hydra-refrence-java</a> for a
 * full reference implementation.
 */
public class OryHydraContainerExternalLoginConsentTest {

  static final String LOGIN_URL = "http://consent-app.example/login";
  static final String CONSENT_URL = "http://consent-app.example/consent";
  static final String CLIENT_REDIRECT_URI = "http://client-app.example/callback";
  static final String CLIENT_ID = "external-app-client";
  static final String CLIENT_SECRET = "external-app-secret";

  @Test
  public void overriddenLoginAndConsentUrlsReceiveChallengesAndExternalFlowCompletes()
      throws Exception {
    try (var container =
        OryHydraContainer.builder().urlsLogin(LOGIN_URL).urlsConsent(CONSENT_URL).build()) {
      container.start();
      createClient(container);

      var http =
          HttpClient.newBuilder()
              .cookieHandler(new CookieManager())
              .followRedirects(HttpClient.Redirect.NEVER)
              .build();
      var publicBase = URI.create(container.publicBaseUriString());
      var adminBase = URI.create(container.adminBaseUriString());

      // Acting as the user-agent: initiate the flow on the public endpoint.
      var state = "external-flow-state";
      var authorizeUri =
          publicBase.resolve(
              "/oauth2/auth?client_id="
                  + CLIENT_ID
                  + "&response_type=code&scope=openid&state="
                  + state
                  + "&redirect_uri="
                  + URLEncoder.encode(CLIENT_REDIRECT_URI, StandardCharsets.UTF_8));
      var loginRedirect = expectRedirect(http, authorizeUri);

      // Hydra must send the user to the overridden login URL, not the sentinel default.
      assertThat(loginRedirect.getHost()).isEqualTo("consent-app.example");
      assertThat(loginRedirect.getPath()).isEqualTo("/login");
      var loginChallenge = queryParam(loginRedirect, "login_challenge");
      assertThat(loginChallenge).isNotEmpty();

      // Acting as the login app: accept the challenge via the admin API.
      var afterLogin =
          acceptViaAdminApi(
              http,
              adminBase,
              "login",
              "login_challenge",
              loginChallenge,
              "{\"subject\":\"external-user\"}");
      var consentRedirect = expectRedirect(http, onPublicPort(afterLogin, publicBase));

      assertThat(consentRedirect.getHost()).isEqualTo("consent-app.example");
      assertThat(consentRedirect.getPath()).isEqualTo("/consent");
      var consentChallenge = queryParam(consentRedirect, "consent_challenge");
      assertThat(consentChallenge).isNotEmpty();

      // Acting as the consent app: grant the requested scope via the admin API.
      var afterConsent =
          acceptViaAdminApi(
              http,
              adminBase,
              "consent",
              "consent_challenge",
              consentChallenge,
              "{\"grant_scope\":[\"openid\"]}");
      var callback = expectRedirect(http, onPublicPort(afterConsent, publicBase));

      // The flow must complete at the client's registered redirect URI.
      assertThat(callback.getHost()).isEqualTo("client-app.example");
      assertThat(callback.getPath()).isEqualTo("/callback");
      assertThat(queryParam(callback, "state")).isEqualTo(state);
      var code = queryParam(callback, "code");
      assertThat(code).isNotEmpty();

      // Acting as the client app: exchange the code for tokens.
      var tokens = exchangeCode(http, publicBase, code);
      assertThat(tokens.get("access_token")).asString().isNotBlank();
      assertThat(tokens.get("id_token")).asString().isNotBlank();
    }
  }

  private static void createClient(OryHydraContainer container) throws Exception {
    var create =
        container.execInContainer(
            "hydra",
            "create",
            "oauth2-client",
            "--endpoint",
            "http://127.0.0.1:4445",
            "--grant-type",
            "authorization_code",
            "--response-type",
            "code",
            "--token-endpoint-auth-method",
            "client_secret_basic",
            "--scope",
            "openid",
            "--redirect-uri",
            CLIENT_REDIRECT_URI,
            "--id",
            CLIENT_ID,
            "--secret",
            CLIENT_SECRET);
    assertThat(create.getExitCode()).isZero();
  }

  private static URI expectRedirect(HttpClient http, URI uri) throws Exception {
    var response =
        http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(Http.is3xx(response.statusCode()))
        .as("expected a redirect from %s but got: %s", uri, response.body())
        .isTrue();
    return uri.resolve(response.headers().firstValue("location").orElseThrow());
  }

  /**
   * Answers a login/consent challenge the way a real login/consent app does: a PUT to the admin
   * accept endpoint, continuing the flow from the returned {@code redirect_to}.
   */
  private static URI acceptViaAdminApi(
      HttpClient http, URI adminBase, String flow, String param, String challenge, String jsonBody)
      throws Exception {
    var uri =
        adminBase.resolve(
            "/admin/oauth2/auth/requests/" + flow + "/accept?" + param + "=" + challenge);
    var response =
        http.send(
            HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return URI.create(Json.parseObject(response.body()).get("redirect_to").toString());
  }

  /**
   * Hydra's {@code redirect_to} values use its configured issuer, which cannot know the dynamically
   * mapped port — an externally driven flow re-targets them at the mapped public endpoint (the
   * reference app solves the same problem with a proxy).
   */
  private static URI onPublicPort(URI location, URI publicBase) {
    var query = location.getRawQuery() == null ? "" : "?" + location.getRawQuery();
    return URI.create(publicBase + location.getRawPath() + query);
  }

  private static String queryParam(URI uri, String name) {
    for (String pair : uri.getRawQuery().split("&")) {
      int idx = pair.indexOf('=');
      var key = idx < 0 ? pair : pair.substring(0, idx);
      if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
        return idx < 0 ? "" : URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
      }
    }
    return "";
  }

  private static java.util.Map<String, Object> exchangeCode(
      HttpClient http, URI publicBase, String code) throws Exception {
    var basic =
        Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    var form =
        "grant_type=authorization_code&code="
            + URLEncoder.encode(code, StandardCharsets.UTF_8)
            + "&redirect_uri="
            + URLEncoder.encode(CLIENT_REDIRECT_URI, StandardCharsets.UTF_8);
    var response =
        http.send(
            HttpRequest.newBuilder(publicBase.resolve("/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("token exchange failed: %s", response.body())
        .isEqualTo(200);
    return Json.parseObject(response.body());
  }
}
