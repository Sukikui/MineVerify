package fr.sukikui.mineverify.remote;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Error raised while communicating with a remote app.
 */
public final class RemoteAppException extends Exception {

  private final Integer statusCode;
  private final String operation;
  private final String url;

  /**
   * Creates a remote app communication error.
   */
  public RemoteAppException(String message) {
    super(message);
    statusCode = null;
    operation = "";
    url = "";
  }

  /**
   * Creates a remote app communication error with an HTTP status code.
   */
  public RemoteAppException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
    operation = "";
    url = "";
  }

  /**
   * Creates a remote app communication error with a cause.
   */
  public RemoteAppException(String message, Throwable cause) {
    super(message, cause);
    statusCode = null;
    operation = "";
    url = "";
  }

  /**
   * Creates a remote app communication error with a cause and HTTP status code.
   */
  public RemoteAppException(String message, Throwable cause, int statusCode) {
    super(message, cause);
    this.statusCode = statusCode;
    operation = "";
    url = "";
  }

  /**
   * Creates a remote app communication error with request metadata.
   */
  public RemoteAppException(String message, Throwable cause, String operation, String url) {
    super(message, cause);
    statusCode = null;
    this.operation = operation;
    this.url = url;
  }

  /**
   * Creates a remote app communication error with request metadata and an HTTP status code.
   */
  public RemoteAppException(String message, String operation, String url, int statusCode) {
    super(message);
    this.statusCode = statusCode;
    this.operation = operation;
    this.url = url;
  }

  /**
   * Creates a remote app communication error with full request metadata.
   */
  public RemoteAppException(
      String message, Throwable cause, String operation, String url, int statusCode) {
    super(message, cause);
    this.statusCode = statusCode;
    this.operation = operation;
    this.url = url;
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

  /**
   * Returns the failed remote operation.
   */
  public Optional<String> operation() {
    if (operation.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(operation);
  }

  /**
   * Returns the URL called by MineVerify, without secret headers.
   */
  public Optional<String> url() {
    if (url.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(url);
  }

  /**
   * Returns a short cause label suitable for chat status.
   */
  public String shortCause() {
    Throwable cause = getCause();
    if (cause == null) {
      return getMessage();
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName();
    }
    return cause.getClass().getSimpleName() + ": " + message;
  }
}
