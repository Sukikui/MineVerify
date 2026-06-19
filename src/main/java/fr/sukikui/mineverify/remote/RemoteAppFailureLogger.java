package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.RemoteAppConfig;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs outbound app failures with request context and stacktrace.
 */
public final class RemoteAppFailureLogger {

  private RemoteAppFailureLogger() {
  }

  /**
   * Logs a failed remote app action.
   */
  public static void log(
      Logger logger, RemoteAppConfig app, String action, RemoteAppException exception) {
    String operation = exception.operation().orElse("unknown operation");
    String url = exception.url().orElse(app.baseUrl());
    logger.log(
        Level.WARNING,
        "Unable to " + action + " " + app.id()
            + " (operation=" + operation + ", url=" + url + ", cause="
            + exception.shortCause() + ")",
        exception);
  }
}
