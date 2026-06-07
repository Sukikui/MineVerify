package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkCodeGenerator;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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
  private final Map<String, Instant> lastPendingPollByApp = new ConcurrentHashMap<>();
  private final Map<String, RemoteAppCallStatus> lastResponseByApp = new ConcurrentHashMap<>();
  private final Map<String, UUID> codeNotificationPlayers = new ConcurrentHashMap<>();
  private CodeCreatedNotifier codeCreatedNotifier = (playerId, app) -> {
  };
  private BukkitTask task;
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
  }

  /**
   * Starts the on-demand polling loop when it is not already running.
   */
  public synchronized boolean trigger(UUID playerId) {
    if (config.apps().isEmpty()) {
      return false;
    }

    triggerPlayerId = Objects.requireNonNull(playerId, "playerId");
    if (task != null) {
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
      int statusCode = remoteClient.sendCodeCreated(app, request);
      recordSuccess(app, CODE_CREATED_ENDPOINT, statusCode);
      request.markCodeCreatedReported();
      notifyCodeCreated(request, app);
    } catch (RemoteAppException exception) {
      recordFailure(app, CODE_CREATED_ENDPOINT, exception);
      logRemoteFailure(app, "report MineVerify code", exception);
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
      int statusCode = remoteClient.sendValidated(app, request);
      recordSuccess(app, VALIDATED_ENDPOINT, statusCode);
      request.markValidationReported();
    } catch (RemoteAppException exception) {
      recordFailure(app, VALIDATED_ENDPOINT, exception);
      logRemoteFailure(app, "report MineVerify validation", exception);
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
      int statusCode = remoteClient.sendExpired(app, request);
      recordSuccess(app, EXPIRED_ENDPOINT, statusCode);
      request.markExpirationReported();
    } catch (RemoteAppException exception) {
      recordFailure(app, EXPIRED_ENDPOINT, exception);
      logRemoteFailure(app, "report MineVerify expiration", exception);
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
      PendingRemoteRequests pendingRequests = remoteClient.fetchPendingRequests(app);
      recordSuccess(app, PENDING_REQUESTS_ENDPOINT, pendingRequests.statusCode());
      for (PendingRemoteRequest pending : pendingRequests.requests()) {
        findOrCreateRequest(pending);
      }
    } catch (RemoteAppException exception) {
      recordFailure(app, PENDING_REQUESTS_ENDPOINT, exception);
      logRemoteFailure(app, "poll MineVerify app", exception);
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
    return Optional.ofNullable(lastPendingPollByApp.get(appId));
  }

  /**
   * Returns when the next pending-request poll can run for one app.
   */
  public Optional<Instant> nextPendingPoll(String appId, Instant now) {
    RemoteAppConfig app = config.apps().get(appId);
    if (app == null) {
      return Optional.empty();
    }

    Instant lastPoll = lastPendingPollByApp.get(appId);
    if (lastPoll == null) {
      return Optional.of(now);
    }

    Instant nextPoll = lastPoll.plus(app.pollInterval());
    if (nextPoll.isBefore(now)) {
      return Optional.of(now);
    }
    return Optional.of(nextPoll);
  }

  /**
   * Returns the last outbound call result for one app.
   */
  public Optional<RemoteAppCallStatus> lastResponse(String appId) {
    return Optional.ofNullable(lastResponseByApp.get(appId));
  }

  private void recordSuccess(RemoteAppConfig app, String endpoint, int statusCode) {
    lastResponseByApp.put(
        app.id(),
        RemoteAppCallStatus.success(
            endpoint, statusCode, app.endpoint(endpointPath(endpoint)), Instant.now()));
  }

  private void recordFailure(RemoteAppConfig app, String endpoint, RemoteAppException exception) {
    lastResponseByApp.put(
        app.id(),
        RemoteAppCallStatus.failure(
            endpoint,
            exception.statusCode(),
            exception.shortCause(),
            exception.url().orElse(app.endpoint(endpointPath(endpoint))),
            Instant.now()));
  }

  private void logRemoteFailure(
      RemoteAppConfig app, String action, RemoteAppException exception) {
    String operation = exception.operation().orElse("unknown operation");
    String url = exception.url().orElse(app.baseUrl());
    logger.log(
        Level.WARNING,
        "Unable to " + action + " " + app.id()
            + " (operation=" + operation + ", url=" + url + ", cause="
            + exception.shortCause() + ")",
        exception);
  }

  private void notifyCodeCreated(LinkRequest request, RemoteAppConfig app) {
    UUID playerId = codeNotificationPlayers.remove(request.code());
    if (playerId != null) {
      codeCreatedNotifier.notify(playerId, app);
    }
  }

  private String endpointPath(String endpoint) {
    return switch (endpoint) {
      case PENDING_REQUESTS_ENDPOINT -> RemoteAppClient.PENDING_REQUESTS_PATH;
      case CODE_CREATED_ENDPOINT -> RemoteAppClient.CODE_CREATED_PATH;
      case VALIDATED_ENDPOINT -> RemoteAppClient.VALIDATED_PATH;
      case EXPIRED_ENDPOINT -> RemoteAppClient.EXPIRED_PATH;
      default -> "";
    };
  }
}
