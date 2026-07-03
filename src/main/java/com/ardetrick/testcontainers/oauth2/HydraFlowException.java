package com.ardetrick.testcontainers.oauth2;

/**
 * Thrown when a token request cannot be completed due to a transport- or protocol-level failure (a
 * network error, an interruption, an unparseable response, or an unexpected response during the
 * authorization-code flow).
 *
 * <p>OAuth 2.0 <em>protocol</em> errors (RFC 6749 §5.2) are not exceptions; they are returned as
 * {@link FlowResult.OAuthError}.
 */
public class HydraFlowException extends RuntimeException {

  /**
   * Creates a new exception.
   *
   * @param message description of the failure
   */
  public HydraFlowException(String message) {
    super(message);
  }

  /**
   * Creates a new exception.
   *
   * @param message description of the failure
   * @param cause underlying cause
   */
  public HydraFlowException(String message, Throwable cause) {
    super(message, cause);
  }
}
