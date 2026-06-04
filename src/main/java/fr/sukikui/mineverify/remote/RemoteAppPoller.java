package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkCodeGenerator;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Polls configured remote apps for pending MineVerify requests.
 */
public final class RemoteAppPoller {

  private final MineVerifyConfig config;
  private final LinkRequestStore requestStore;
  private final LinkCodeGenerator codeGenerator;
  private final RemoteAppClient remoteClient;
  private final Logger logger;
  private final List<BukkitTask> tasks = new ArrayList<>();

  /**
   * Creates a remote app poller.
   */
  public RemoteAppPoller(
      MineVerifyConfig config,
      LinkRequestStore requestStore,
      LinkCodeGenerator codeGenerator,
      RemoteAppClient remoteClient,
      Logger logger) {
    this.config = Objects.requireNonNull(config, "config");
    this.requestStore = Objects.requireNonNull(requestStore, "requestStore");
    this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator");
    this.remoteClient = Objects.requireNonNull(remoteClient, "remoteClient");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  /**
   * Starts one async polling task per configured app.
   */
  public void start(JavaPlugin plugin) {
    for (RemoteAppConfig app : config.apps().values()) {
      long intervalTicks = app.pollInterval().toSeconds() * 20L;
      BukkitTask task =
          plugin
              .getServer()
              .getScheduler()
              .runTaskTimerAsynchronously(plugin, () -> poll(app), 20L, intervalTicks);
      tasks.add(task);
    }
  }

  /**
   * Stops all polling tasks.
   */
  public void stop() {
    for (BukkitTask task : tasks) {
      task.cancel();
    }
    tasks.clear();
  }

  /**
   * Reports a validated request to its owning app.
   */
  public void reportValidation(LinkRequest request) {
    RemoteAppConfig app = config.apps().get(request.appId());
    if (app == null || !request.needsValidationReport()) {
      return;
    }

    try {
      remoteClient.sendValidated(app, request);
      request.markValidationReported();
    } catch (RemoteAppException exception) {
      logger.warning("Unable to report MineVerify validation for app " + app.id() + ": "
          + exception.getMessage());
    }
  }

  private void poll(RemoteAppConfig app) {
    pollPendingRequests(app);
    reportPendingValidations(app);
  }

  private void pollPendingRequests(RemoteAppConfig app) {
    try {
      for (PendingRemoteRequest pending : remoteClient.fetchPendingRequests(app)) {
        LinkRequest request = findOrCreateRequest(pending);
        remoteClient.sendCodeCreated(app, request);
      }
    } catch (RemoteAppException exception) {
      logger.warning("Unable to poll MineVerify app " + app.id() + ": " + exception.getMessage());
    }
  }

  private LinkRequest findOrCreateRequest(PendingRemoteRequest pending) {
    return requestStore
        .findByRemoteRequest(pending.appId(), pending.requestId())
        .orElseGet(() -> createRequest(pending));
  }

  private LinkRequest createRequest(PendingRemoteRequest pending) {
    Instant now = Instant.now();
    String code = codeGenerator.generate(candidate -> requestStore.hasActiveCode(candidate, now));
    return requestStore.store(
        pending.appId(), pending.requestId(), code, now.plus(config.codeTtl()), now);
  }

  private void reportPendingValidations(RemoteAppConfig app) {
    for (LinkRequest request : requestStore.pendingValidationReports(app.id())) {
      reportValidation(request);
    }
  }

  /**
   * Returns configured apps.
   */
  public Map<String, RemoteAppConfig> apps() {
    return config.apps();
  }
}
