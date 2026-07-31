package fr.sukikui.mineverify.remote;

import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks polling times and the latest outbound response for each app.
 */
final class RemoteAppStatusTracker {

  private final MineVerifyConfig config;
  private final Map<String, Instant> lastPendingPollByApp = new ConcurrentHashMap<>();
  private final Map<String, RemoteAppCallStatus> lastResponseByApp = new ConcurrentHashMap<>();

  RemoteAppStatusTracker(MineVerifyConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  void clearPollTimes() {
    lastPendingPollByApp.clear();
  }

  boolean shouldPoll(RemoteAppConfig app, Instant now) {
    Instant lastPoll = lastPendingPollByApp.get(app.id());
    if (lastPoll != null && Duration.between(lastPoll, now).compareTo(app.pollInterval()) < 0) {
      return false;
    }
    lastPendingPollByApp.put(app.id(), now);
    return true;
  }

  Optional<Instant> lastPendingPoll(String appId) {
    return Optional.ofNullable(lastPendingPollByApp.get(appId));
  }

  Optional<Instant> nextPendingPoll(String appId, Instant now) {
    RemoteAppConfig app = config.apps().get(appId);
    if (app == null) {
      return Optional.empty();
    }
    Instant lastPoll = lastPendingPollByApp.get(appId);
    if (lastPoll == null) {
      return Optional.of(now);
    }
    Instant nextPoll = lastPoll.plus(app.pollInterval());
    return Optional.of(nextPoll.isBefore(now) ? now : nextPoll);
  }

  Optional<RemoteAppCallStatus> lastResponse(String appId) {
    return Optional.ofNullable(lastResponseByApp.get(appId));
  }

  boolean recordSuccess(
      RemoteAppConfig app, String endpoint, String path, int statusCode) {
    RemoteAppCallStatus status =
        RemoteAppCallStatus.success(endpoint, statusCode, app.endpoint(path), Instant.now());
    RemoteAppCallStatus previous = lastResponseByApp.put(app.id(), status);
    return previous != null && !previous.isSuccess();
  }

  boolean recordFailure(
      RemoteAppConfig app,
      String endpoint,
      String path,
      RemoteAppException exception) {
    RemoteAppCallStatus status =
        RemoteAppCallStatus.failure(
            endpoint,
            exception.statusCode(),
            exception.shortCause(),
            exception.url().orElse(app.endpoint(path)),
            Instant.now());
    RemoteAppCallStatus previous = lastResponseByApp.put(app.id(), status);
    return shouldLogFailure(previous, status);
  }

  private boolean shouldLogFailure(RemoteAppCallStatus previous, RemoteAppCallStatus status) {
    if (previous == null || previous.isSuccess()) {
      return true;
    }
    return !Objects.equals(previous.endpoint(), status.endpoint())
        || !Objects.equals(previous.statusCode(), status.statusCode())
        || !Objects.equals(previous.error(), status.error())
        || !Objects.equals(previous.url(), status.url());
  }
}
