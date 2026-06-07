package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.RemoteAppConfig;
import java.util.UUID;

/**
 * Notifies that a generated code was delivered to its app.
 */
@FunctionalInterface
public interface CodeCreatedNotifier {

  /**
   * Handles a code-created delivery notification.
   */
  void notify(UUID playerId, RemoteAppConfig app);
}
