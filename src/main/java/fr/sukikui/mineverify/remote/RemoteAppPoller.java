package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkCodeGenerator;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestStore;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Polls configured remote apps for pending MineVerify requests.
 */
public final class RemoteAppPoller {

  private static final String PENDING_REQUESTS_ENDPOINT = "pending-requests";
  private static final String CODE_CREATED_ENDPOINT = "code-created";
  private static final String VALIDATED_ENDPOINT = "validated";
  private static final String EXPIRED_ENDPOINT = "expired";

  private final MineVerifyConfig config;
  private final LinkRequestStore requestStore;
  private final LinkCodeGenerator codeGenerator;
  private final RemoteAppClient remoteClient;
  private final Logger logger;
  private final JavaPlugin plugin;
  private final RemoteAppStatusTracker statusTracker;
  private final Map<String, UUID> codeNotificationPlayers = new ConcurrentHashMap<>();
  private final RemoteOperationGate remoteOperationGate = new RemoteOperationGate();
  private CodeCreatedNotifier codeCreatedNotifier = (playerId, app) -> {
  };
  private BukkitTask task;
  private volatile boolean shuttingDown;
  private volatile UUID triggerPlayerId;

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
    statusTracker = new RemoteAppStatusTracker(config);
  }

  /**
   * Starts the on-demand polling loop when it is not already running.
   */
  public synchronized boolean trigger(UUID playerId) {
    if (shuttingDown || config.apps().isEmpty()) {
      return false;
    }

    triggerPlayerId = Objects.requireNonNull(playerId, "playerId");
    if (task != null) {
      return false;
    }

    statusTracker.clearPollTimes();
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
  }

  /**
   * Stops polling and waits for any active remote operation to finish.
   */
  public synchronized void stopForShutdown() {
    shuttingDown = true;
    stop();
    remoteOperationGate.waitUntilIdle();
  }

  /**
   * Reports a generated code to its owning app.
   */
  public void reportCodeCreated(LinkRequest request) {
    if (!remoteOperationGate.tryBegin(() -> shuttingDown)) {
      return;
    }
    try {
      RemoteAppConfig app = config.apps().get(request.appId());
      if (app == null || !request.needsCodeCreatedReport()) {
        return;
      }

      try {
        int statusCode = remoteClient.sendCodeCreated(app, request);
        recordSuccess(app, CODE_CREATED_ENDPOINT, RemoteAppClient.CODE_CREATED_PATH, statusCode);
        request.markCodeCreatedReported();
        notifyCodeCreated(request, app);
      } catch (RemoteAppException exception) {
        recordFailure(app, CODE_CREATED_ENDPOINT, RemoteAppClient.CODE_CREATED_PATH, exception,
            "report MineVerify code");
      }
    } finally {
      remoteOperationGate.end();
    }
  }

  /**
   * Reports a validated request to its owning app.
   */
  public void reportValidation(LinkRequest request) {
    if (!remoteOperationGate.tryBegin(() -> shuttingDown)) {
      return;
    }
    try {
      RemoteAppConfig app = config.apps().get(request.appId());
      if (app == null || !request.needsValidationReport()) {
        return;
      }

      try {
        int statusCode = remoteClient.sendValidated(app, request);
        recordSuccess(app, VALIDATED_ENDPOINT, RemoteAppClient.VALIDATED_PATH, statusCode);
        request.markValidationReported();
      } catch (RemoteAppException exception) {
        recordFailure(app, VALIDATED_ENDPOINT, RemoteAppClient.VALIDATED_PATH, exception,
            "report MineVerify validation");
      }
    } finally {
      remoteOperationGate.end();
    }
  }

  /**
   * Reports an expired request to its owning app.
   */
  public void reportExpiration(LinkRequest request) {
    if (!remoteOperationGate.tryBegin(() -> shuttingDown)) {
      return;
    }
    try {
      RemoteAppConfig app = config.apps().get(request.appId());
      if (app == null || !request.needsExpirationReport()) {
        return;
      }

      try {
        int statusCode = remoteClient.sendExpired(app, request);
        recordSuccess(app, EXPIRED_ENDPOINT, RemoteAppClient.EXPIRED_PATH, statusCode);
        request.markExpirationReported();
      } catch (RemoteAppException exception) {
        recordFailure(app, EXPIRED_ENDPOINT, RemoteAppClient.EXPIRED_PATH, exception,
            "report MineVerify expiration");
      }
    } finally {
      remoteOperationGate.end();
    }
  }

  private void pollAll() {
    if (!remoteOperationGate.tryBegin(() -> shuttingDown)) {
      return;
    }
    try {
      Instant now = Instant.now();
      for (RemoteAppConfig app : config.apps().values()) {
        if (statusTracker.shouldPoll(app, now)) {
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
    } finally {
      remoteOperationGate.end();
    }
  }

  private void pollPendingRequests(RemoteAppConfig app) {
    try {
      PendingRemoteRequests pendingRequests = remoteClient.fetchPendingRequests(app);
      recordSuccess(
          app,
          PENDING_REQUESTS_ENDPOINT,
          RemoteAppClient.PENDING_REQUESTS_PATH,
          pendingRequests.statusCode());
      for (PendingRemoteRequest pending : pendingRequests.requests()) {
        findOrCreateRequest(pending);
      }
    } catch (RemoteAppException exception) {
      recordFailure(
          app,
          PENDING_REQUESTS_ENDPOINT,
          RemoteAppClient.PENDING_REQUESTS_PATH,
          exception,
          "poll MineVerify app");
    }
  }

  private void recordSuccess(
      RemoteAppConfig app, String endpoint, String path, int statusCode) {
    if (statusTracker.recordSuccess(app, endpoint, path, statusCode)) {
      RemoteAppFailureLogger.logRecovered(logger, app);
    }
  }

  private void recordFailure(
      RemoteAppConfig app,
      String endpoint,
      String path,
      RemoteAppException exception,
      String action) {
    if (statusTracker.recordFailure(app, endpoint, path, exception)) {
      RemoteAppFailureLogger.log(logger, app, action, exception);
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
    LinkRequest request = requestStore.store(
        pending.appId(), pending.requestId(), code, now.plus(config.codeTtl()), now);
    UUID playerId = triggerPlayerId;
    if (playerId != null) {
      codeNotificationPlayers.put(request.code(), playerId);
    }
    return request;
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

  /**
   * Sets the generated code delivery notifier.
   */
  public void setCodeCreatedNotifier(CodeCreatedNotifier codeCreatedNotifier) {
    this.codeCreatedNotifier = Objects.requireNonNull(codeCreatedNotifier, "codeCreatedNotifier");
  }

  /**
   * Returns true when the player-triggered polling loop is currently running.
   */
  public synchronized boolean isRunning() {
    return task != null;
  }

  /**
   * Returns the last pending-request poll time for one app.
   */
  public Optional<Instant> lastPendingPoll(String appId) {
    return statusTracker.lastPendingPoll(appId);
  }

  /**
   * Returns when the next pending-request poll can run for one app.
   */
  public Optional<Instant> nextPendingPoll(String appId, Instant now) {
    return statusTracker.nextPendingPoll(appId, now);
  }

  /**
   * Returns the last outbound call result for one app.
   */
  public Optional<RemoteAppCallStatus> lastResponse(String appId) {
    return statusTracker.lastResponse(appId);
  }

  private void notifyCodeCreated(LinkRequest request, RemoteAppConfig app) {
    UUID playerId = codeNotificationPlayers.remove(request.code());
    if (playerId != null) {
      codeCreatedNotifier.notify(playerId, app);
    }
  }

}
