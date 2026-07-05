package com.ardetrick.testcontainers;

import java.util.Map;

/**
 * Result of a token request against Hydra's token endpoint.
 *
 * <p>Models the two outcomes the OAuth 2.0 spec defines at the token endpoint: a successful
 * response ({@link TokenResponse}, RFC 6749 §5.1) or an error response ({@link OAuthError}, RFC
 * 6749 §5.2). The distinction is success vs. error, not per-grant, so the same type is returned by
 * every token helper.
 */
public sealed interface FlowResult {

  /**
   * Successful token response (RFC 6749 §5.1), extended with the OpenID Connect {@code id_token}
   * (OIDC Core §3.1.3.3).
   *
   * <p>Grant-specific absences follow the spec's field-presence constraints rather than introducing
   * distinct types:
   *
   * <ul>
   *   <li>{@code client_credentials}: {@code idToken} and {@code refreshToken} are always {@code
   *       null} (no end-user; §4.4.3 omits the refresh token).
   *   <li>{@code authorization_code}: {@code idToken} is present when the {@code openid} scope was
   *       requested; {@code refreshToken} when {@code offline}/{@code offline_access} was granted.
   * </ul>
   *
   * @param accessToken the access token (opaque by default, or a JWT when Hydra is configured with
   *     {@code STRATEGIES_ACCESS_TOKEN=jwt})
   * @param idToken the OIDC ID token, or {@code null} when not applicable
   * @param refreshToken the refresh token, or {@code null} when not issued
   * @param tokenType the token type (typically {@code bearer})
   * @param expiresInSeconds the access token lifetime in seconds
   * @param scope the granted scope (space-delimited), or {@code null} if omitted by the server
   * @param raw the full parsed token response
   */
  record TokenResponse(
      String accessToken,
      String idToken,
      String refreshToken,
      String tokenType,
      long expiresInSeconds,
      String scope,
      Map<String, Object> raw)
      implements FlowResult {}

  /**
   * Error response from the token endpoint (RFC 6749 §5.2).
   *
   * @param error the RFC 6749 error code (e.g. {@code invalid_client}, {@code invalid_scope})
   * @param errorDescription human-readable description, or {@code null} if absent
   * @param errorUri URI with error information, or {@code null} if absent
   */
  record OAuthError(String error, String errorDescription, String errorUri) implements FlowResult {}
}
