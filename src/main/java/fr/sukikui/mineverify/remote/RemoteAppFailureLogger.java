package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.RemoteAppConfig;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs outbound app state changes with request context.
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
    logger.warning("Unable to " + action + " " + app.id()
        + " (operation=" + operation + ", url=" + url + ", cause="
        + exception.shortCause() + ")");
  }

  /**
   * Logs when a remote app responds again after a failure.
   */
  public static void logRecovered(Logger logger, RemoteAppConfig app) {
    logger.log(Level.INFO, "MineVerify app " + app.id() + " is reachable again.");
  }
}
