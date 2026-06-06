package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkCodeGenerator;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestStore;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
  private final JavaPlugin plugin;
  private final Map<String, Instant> lastPendingPollByApp = new HashMap<>();
  private BukkitTask task;

  /**
   * Creates a remote app poller.
   */
  public RemoteAppPoller(
      MineVerifyConfig config,
      LinkRequestStore requestStore,
      LinkCodeGenerator codeGenerator,
      RemoteAppClient remoteClient,
      JavaPlugin plugin,
      Logger logger) {
    this.config = Objects.requireNonNull(config, "config");
    this.requestStore = Objects.requireNonNull(requestStore, "requestStore");
    this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator");
    this.remoteClient = Objects.requireNonNull(remoteClient, "remoteClient");
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  /**
   * Starts the on-demand polling loop when it is not already running.
   */
  public synchronized boolean trigger() {
    if (config.apps().isEmpty() || task != null) {
      return false;
    }

    lastPendingPollByApp.clear();
    task =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(plugin, this::pollAll, 1L, pollIntervalTicks());
    return true;
  }

  /**
   * Stops the polling task.
   */
  public synchronized void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    lastPendingPollByApp.clear();
  }

  /**
   * Reports a generated code to its owning app.
   */
  public void reportCodeCreated(LinkRequest request) {
    RemoteAppConfig app = config.apps().get(request.appId());
    if (app == null || !request.needsCodeCreatedReport()) {
      return;
    }

    try {
      remoteClient.sendCodeCreated(app, request);
      request.markCodeCreatedReported();
    } catch (RemoteAppException exception) {
      logger.warning("Unable to report MineVerify code for app " + app.id() + ": "
          + exception.getMessage());
    }
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

  /**
   * Reports an expired request to its owning app.
   */
  public void reportExpiration(LinkRequest request) {
    RemoteAppConfig app = config.apps().get(request.appId());
    if (app == null || !request.needsExpirationReport()) {
      return;
    }

    try {
      remoteClient.sendExpired(app, request);
      request.markExpirationReported();
    } catch (RemoteAppException exception) {
      logger.warning("Unable to report MineVerify expiration for app " + app.id() + ": "
          + exception.getMessage());
    }
  }

  private void pollAll() {
    Instant now = Instant.now();
    for (RemoteAppConfig app : config.apps().values()) {
      if (shouldPollPendingRequests(app, now)) {
        pollPendingRequests(app);
      }
    }
    expirePendingRequests();
    for (RemoteAppConfig app : config.apps().values()) {
      reportPendingCodeCreated(app);
      reportPendingValidations(app);
      reportPendingExpirations(app);
    }
    requestStore.removeReportedTerminals();
    stopIfIdle();
  }

  private void pollPendingRequests(RemoteAppConfig app) {
    try {
      for (PendingRemoteRequest pending : remoteClient.fetchPendingRequests(app)) {
        findOrCreateRequest(pending);
      }
    } catch (RemoteAppException exception) {
      logger.warning("Unable to poll MineVerify app " + app.id() + ": " + exception.getMessage());
    }
  }

  private boolean shouldPollPendingRequests(RemoteAppConfig app, Instant now) {
    Instant lastPoll = lastPendingPollByApp.get(app.id());
    if (lastPoll != null && Duration.between(lastPoll, now).compareTo(app.pollInterval()) < 0) {
      return false;
    }
    lastPendingPollByApp.put(app.id(), now);
    return true;
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

  private void expirePendingRequests() {
    requestStore.expirePending(Instant.now());
  }

  private void reportPendingCodeCreated(RemoteAppConfig app) {
    for (LinkRequest request : requestStore.pendingCodeCreatedReports(app.id())) {
      reportCodeCreated(request);
    }
  }

  private void reportPendingValidations(RemoteAppConfig app) {
    for (LinkRequest request : requestStore.pendingValidationReports(app.id())) {
      reportValidation(request);
    }
  }

  private void reportPendingExpirations(RemoteAppConfig app) {
    for (LinkRequest request : requestStore.pendingExpirationReports(app.id())) {
      reportExpiration(request);
    }
  }

  private void stopIfIdle() {
    if (!requestStore.hasRequests()) {
      stop();
    }
  }

  private long pollIntervalTicks() {
    long seconds =
        config.apps().values().stream()
            .mapToLong(app -> app.pollInterval().toSeconds())
            .min()
            .orElse(1L);
    return Math.max(1L, seconds * 20L);
  }

  /**
   * Returns configured apps.
   */
  public Map<String, RemoteAppConfig> apps() {
    return config.apps();
  }
}
