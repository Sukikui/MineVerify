package fr.sukikui.mineverify.remote;

/**
 * Error raised while communicating with a remote app.
 */
public final class RemoteAppException extends Exception {

  /**
   * Creates a remote app communication error.
   */
  public RemoteAppException(String message) {
    super(message);
  }

  /**
   * Creates a remote app communication error with a cause.
   */
  public RemoteAppException(String message, Throwable cause) {
    super(message, cause);
  }
}
