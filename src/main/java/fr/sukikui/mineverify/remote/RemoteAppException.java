package fr.sukikui.mineverify.remote;

import java.util.OptionalInt;

/**
 * Error raised while communicating with a remote app.
 */
public final class RemoteAppException extends Exception {

  private final Integer statusCode;

  /**
   * Creates a remote app communication error.
   */
  public RemoteAppException(String message) {
    super(message);
    statusCode = null;
  }

  /**
   * Creates a remote app communication error with an HTTP status code.
   */
  public RemoteAppException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  /**
   * Creates a remote app communication error with a cause.
   */
  public RemoteAppException(String message, Throwable cause) {
    super(message, cause);
    statusCode = null;
  }

  /**
   * Creates a remote app communication error with a cause and HTTP status code.
   */
  public RemoteAppException(String message, Throwable cause, int statusCode) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /**
   * Returns the HTTP status code when the remote app responded.
   */
  public OptionalInt statusCode() {
    if (statusCode == null) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(statusCode);
  }
}
