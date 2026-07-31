package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestStore;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Reports unfinished requests during a graceful plugin shutdown.
 */
public final class RemoteAppShutdownHandler {

  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

  private final MineVerifyConfig config;
  private final LinkRequestStore requestStore;
  private final RemoteAppClient remoteClient;
  private final Logger logger;

  /**
   * Creates a graceful shutdown handler.
   */
  public RemoteAppShutdownHandler(
      MineVerifyConfig config,
      LinkRequestStore requestStore,
      RemoteAppClient remoteClient,
      Logger logger) {
    this.config = Objects.requireNonNull(config, "config");
    this.requestStore = Objects.requireNonNull(requestStore, "requestStore");
    this.remoteClient = Objects.requireNonNull(remoteClient, "remoteClient");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  /**
   * Reports every unfinished request and clears the in-memory store.
   */
  public void reportAndClear() {
    List<LinkRequest> reports = requestStore.prepareShutdownReports();
    if (reports.isEmpty()) {
      requestStore.clear();
      return;
    }

    logger.info("Reporting " + reports.size()
        + " unfinished verification request(s) before shutdown...");
    final long startedAt = System.nanoTime();
    AtomicInteger delivered = new AtomicInteger();
    Set<String> loggedFailures = ConcurrentHashMap.newKeySet();
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    for (LinkRequest request : reports) {
      executor.submit(() -> {
        if (report(request, loggedFailures)) {
          delivered.incrementAndGet();
        }
      });
    }
    executor.shutdown();
    awaitReports(executor, reports.size());
    logger.info("Shutdown reports completed: " + delivered.get() + "/" + reports.size()
        + " delivered in " + formatElapsed(startedAt) + ".");
    requestStore.clear();
  }

  private boolean report(LinkRequest request, Set<String> loggedFailures) {
    RemoteAppConfig app = config.apps().get(request.appId());
    if (app == null) {
      logUnknownApp(request, loggedFailures);
      return false;
    }

    try {
      if (request.needsValidationReport()) {
        remoteClient.sendValidated(app, request);
        return true;
      } else if (request.needsExpirationReport()) {
        remoteClient.sendExpired(app, request);
        return true;
      }
      return false;
    } catch (RemoteAppException exception) {
      logFailure(app, exception, loggedFailures);
      return false;
    }
  }

  private void logUnknownApp(LinkRequest request, Set<String> loggedFailures) {
    if (loggedFailures.add("unknown-app:" + request.appId())) {
      logger.warning("Unable to report MineVerify shutdown request: unknown app "
          + request.appId());
    }
  }

  private void logFailure(
      RemoteAppConfig app, RemoteAppException exception, Set<String> loggedFailures) {
    String key = app.id() + ":" + exception.shortCause();
    if (loggedFailures.add(key)) {
      RemoteAppFailureLogger.log(logger, app, "report MineVerify shutdown request", exception);
    }
  }

  private void awaitReports(ExecutorService executor, int reportCount) {
    try {
      if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
        logger.warning("MineVerify shutdown report timeout after "
            + SHUTDOWN_TIMEOUT.toSeconds() + "s for " + reportCount + " request(s)");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
      logger.warning("MineVerify shutdown reports interrupted");
    }
  }

  private String formatElapsed(long startedAt) {
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    if (elapsedMillis < 1_000L) {
      return elapsedMillis + " ms";
    }
    return String.format(Locale.ROOT, "%.1f s", elapsedMillis / 1_000.0D);
  }
}
