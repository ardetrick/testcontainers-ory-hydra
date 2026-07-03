package com.ardetrick.testcontainers.oauth2;

import com.ardetrick.testcontainers.oauth2.FlowResult.OAuthError;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives a full OAuth 2.0 authorization-code flow against Hydra, auto-accepting (or rejecting) the
 * login and consent challenges via Hydra's admin API — returning a real token without a browser or
 * a user-written login/consent app.
 *
 * <p>The flow follows redirects manually (no embedded server), short-circuiting the login/consent
 * hops by calling the admin API directly and rewriting each Hydra-bound redirect to the container's
 * mapped host and port. Hydra's redirects carry its configured issuer, which cannot know the
 * dynamically mapped port; rewriting each hop means nothing needs the issuer to be externally
 * reachable, so no fixed ports and no post-start reconfiguration — safe for parallel test runs.
 */
public final class AuthorizationCodeFlow {

  private static final String REDIRECT_URI = "http://localhost/callback";
  private static final int MAX_HOPS = 10;

  private final URI publicBaseUri;
  private final URI adminBaseUri;

  private String clientId;
  private String clientSecret;
  private List<String> scopes = new ArrayList<>(List.of("openid"));
  private String subject = "test-subject";
  private final List<String> audience = new ArrayList<>();
  private final Map<String, Object> accessTokenClaims = new LinkedHashMap<>();
  private final Map<String, Object> idTokenClaims = new LinkedHashMap<>();
  private String rejectLoginError;
  private String rejectLoginDescription;
  private String rejectConsentError;
  private String rejectConsentDescription;
  private boolean usePkce = false;

  /**
   * Creates a flow bound to a running Hydra container's endpoints.
   *
   * @param publicBaseUri the public API base URI
   * @param adminBaseUri the admin API base URI
   */
  public AuthorizationCodeFlow(URI publicBaseUri, URI adminBaseUri) {
    this.publicBaseUri = publicBaseUri;
    this.adminBaseUri = adminBaseUri;
  }

  /**
   * Uses an existing client instead of creating an ephemeral one. Requires {@link
   * #clientSecret(String)}.
   *
   * <p>The flow uses the fixed redirect URI {@code http://localhost/callback} (never actually
   * served), so the client must include it in its registered {@code redirect_uris}.
   *
   * @param clientId the client identifier
   * @return this flow
   */
  public AuthorizationCodeFlow clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  /**
   * Sets the secret for the client supplied via {@link #clientId(String)}.
   *
   * @param clientSecret the client secret
   * @return this flow
   */
  public AuthorizationCodeFlow clientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  /**
   * Sets the requested scopes (default {@code openid}).
   *
   * @param scopes requested scopes
   * @return this flow
   */
  public AuthorizationCodeFlow scopes(String... scopes) {
    this.scopes = new ArrayList<>(Arrays.asList(scopes));
    return this;
  }

  /**
   * Sets the subject the auto-accepted login asserts (default {@code test-subject}).
   *
   * @param subject the authenticated subject
   * @return this flow
   */
  public AuthorizationCodeFlow subject(String subject) {
    this.subject = subject;
    return this;
  }

  /**
   * Sets the requested token audience.
   *
   * @param audience requested audience values
   * @return this flow
   */
  public AuthorizationCodeFlow audience(String... audience) {
    this.audience.clear();
    this.audience.addAll(Arrays.asList(audience));
    return this;
  }

  /**
   * Adds claims to both the access token and ID token sessions.
   *
   * @param claims claims to add
   * @return this flow
   */
  public AuthorizationCodeFlow claims(Map<String, Object> claims) {
    this.accessTokenClaims.putAll(claims);
    this.idTokenClaims.putAll(claims);
    return this;
  }

  /**
   * Adds claims to the access token session only.
   *
   * @param claims claims to add
   * @return this flow
   */
  public AuthorizationCodeFlow accessTokenClaims(Map<String, Object> claims) {
    this.accessTokenClaims.putAll(claims);
    return this;
  }

  /**
   * Adds claims to the ID token session only.
   *
   * @param claims claims to add
   * @return this flow
   */
  public AuthorizationCodeFlow idTokenClaims(Map<String, Object> claims) {
    this.idTokenClaims.putAll(claims);
    return this;
  }

  /**
   * Rejects the login challenge instead of accepting it, producing an error response.
   *
   * @param error the OAuth error code
   * @param description a human-readable description
   * @return this flow
   */
  public AuthorizationCodeFlow rejectLogin(String error, String description) {
    this.rejectLoginError = error;
    this.rejectLoginDescription = description;
    return this;
  }

  /**
   * Rejects the consent challenge instead of accepting it, producing an error response.
   *
   * @param error the OAuth error code
   * @param description a human-readable description
   * @return this flow
   */
  public AuthorizationCodeFlow rejectConsent(String error, String description) {
    this.rejectConsentError = error;
    this.rejectConsentDescription = description;
    return this;
  }

  /**
   * Enables PKCE (RFC 7636) with the {@code S256} method (default disabled).
   *
   * @param enabled whether to use PKCE
   * @return this flow
   */
  public AuthorizationCodeFlow usePkce(boolean enabled) {
    this.usePkce = enabled;
    return this;
  }

  /**
   * Runs the flow and returns the result.
   *
   * @return a {@link FlowResult.TokenResponse} on success, or a {@link OAuthError} if Hydra
   *     returned an OAuth error (e.g. from a rejected login/consent)
   * @throws HydraFlowException if the flow cannot be completed
   */
  public FlowResult execute() {
    HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    AdminClient admin = new AdminClient(http, adminBaseUri);

    resolveClient(admin);

    String state = UUID.randomUUID().toString();
    String codeVerifier = usePkce ? randomUrlSafe() : null;
    URI current = buildAuthorizeUri(state, codeVerifier == null ? null : s256(codeVerifier));

    // Each hop is one of: an OAuth error, a login/consent challenge to answer via the admin API,
    // the client callback carrying the code, or another Hydra-bound redirect to follow.
    for (int hop = 0; hop < MAX_HOPS; hop++) {
      URI location = followRedirect(http, current);
      Map<String, String> query = parseQuery(location.getRawQuery());

      if (query.containsKey("error")) {
        return new OAuthError(
            query.get("error"), query.get("error_description"), query.get("error_uri"));
      }
      if (query.containsKey("login_challenge")) {
        current = rewrite(answerLogin(admin, query.get("login_challenge")));
      } else if (query.containsKey("consent_challenge")) {
        current = rewrite(answerConsent(admin, query.get("consent_challenge")));
      } else if (isRedirectUri(location)) {
        return exchangeCode(query, state, codeVerifier);
      } else {
        current = rewrite(location);
      }
    }

    throw new HydraFlowException(
        "Authorization code not received within " + MAX_HOPS + " redirects");
  }

  private static URI followRedirect(HttpClient http, URI current) {
    HttpResponse<String> response = Http.send(http, HttpRequest.newBuilder(current).GET().build());
    if (!Http.is3xx(response.statusCode())) {
      throw new HydraFlowException(
          "Unexpected non-redirect response (HTTP "
              + response.statusCode()
              + ") during authorization flow: "
              + response.body());
    }
    return current.resolve(
        response
            .headers()
            .firstValue("location")
            .orElseThrow(
                () -> new HydraFlowException("Redirect response without Location header")));
  }

  private URI answerLogin(AdminClient admin, String challenge) {
    return rejectLoginError != null
        ? admin.rejectLogin(challenge, rejectLoginError, rejectLoginDescription)
        : admin.acceptLogin(challenge, subject);
  }

  private URI answerConsent(AdminClient admin, String challenge) {
    return rejectConsentError != null
        ? admin.rejectConsent(challenge, rejectConsentError, rejectConsentDescription)
        : admin.acceptConsent(
            challenge, new ArrayList<>(scopes), new ArrayList<>(audience), session());
  }

  private FlowResult exchangeCode(Map<String, String> query, String state, String codeVerifier) {
    if (!state.equals(query.get("state"))) {
      throw new HydraFlowException("State mismatch in authorization response");
    }
    String code = query.get("code");
    if (code == null) {
      throw new HydraFlowException("Authorization response contains neither code nor error");
    }
    return TokenEndpointClient.authorizationCode(
        publicBaseUri.resolve("/oauth2/token"),
        clientId,
        clientSecret,
        code,
        REDIRECT_URI,
        codeVerifier);
  }

  private void resolveClient(AdminClient admin) {
    if (clientId != null) {
      if (clientSecret == null) {
        throw new HydraFlowException("clientSecret must be set when clientId is provided");
      }
      return;
    }
    String id = "tc-" + UUID.randomUUID();
    String secret = UUID.randomUUID().toString();
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("client_id", id);
    registration.put("client_secret", secret);
    registration.put("grant_types", List.of("authorization_code", "refresh_token"));
    registration.put("response_types", List.of("code"));
    registration.put("redirect_uris", List.of(REDIRECT_URI));
    registration.put("scope", String.join(" ", scopes));
    registration.put("token_endpoint_auth_method", "client_secret_basic");
    if (!audience.isEmpty()) {
      registration.put("audience", new ArrayList<>(audience));
    }
    admin.createClient(registration);
    this.clientId = id;
    this.clientSecret = secret;
  }

  private Map<String, Object> session() {
    Map<String, Object> session = new LinkedHashMap<>();
    if (!accessTokenClaims.isEmpty()) {
      session.put("access_token", accessTokenClaims);
    }
    if (!idTokenClaims.isEmpty()) {
      session.put("id_token", idTokenClaims);
    }
    return session;
  }

  private URI buildAuthorizeUri(String state, String codeChallenge) {
    StringBuilder query = new StringBuilder();
    query.append("client_id=").append(Http.encode(clientId));
    query.append("&response_type=code");
    query.append("&redirect_uri=").append(Http.encode(REDIRECT_URI));
    query.append("&scope=").append(Http.encode(String.join(" ", scopes)));
    query.append("&state=").append(Http.encode(state));
    for (String aud : audience) {
      query.append("&audience=").append(Http.encode(aud));
    }
    if (codeChallenge != null) {
      query.append("&code_challenge=").append(Http.encode(codeChallenge));
      query.append("&code_challenge_method=S256");
    }
    return publicBaseUri.resolve("/oauth2/auth?" + query);
  }

  private boolean isRedirectUri(URI location) {
    URI redirect = URI.create(REDIRECT_URI);
    return redirect.getHost().equals(location.getHost())
        && redirect.getPath().equals(location.getPath());
  }

  private URI rewrite(URI location) {
    try {
      return new URI(
          publicBaseUri.getScheme(),
          null,
          publicBaseUri.getHost(),
          publicBaseUri.getPort(),
          location.getPath(),
          location.getQuery(),
          location.getFragment());
    } catch (URISyntaxException e) {
      throw new HydraFlowException("Failed to rewrite redirect URI: " + location, e);
    }
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> map = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return map;
    }
    for (String pair : rawQuery.split("&")) {
      int idx = pair.indexOf('=');
      if (idx < 0) {
        map.put(Http.decode(pair), "");
      } else {
        map.put(Http.decode(pair.substring(0, idx)), Http.decode(pair.substring(idx + 1)));
      }
    }
    return map;
  }

  private static String randomUrlSafe() {
    byte[] buffer = new byte[64];
    new SecureRandom().nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }

  private static String s256(String verifier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new HydraFlowException("SHA-256 is not available", e);
    }
  }
}
