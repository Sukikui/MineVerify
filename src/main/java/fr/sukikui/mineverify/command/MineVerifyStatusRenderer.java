package fr.sukikui.mineverify.command;

import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestState;
import fr.sukikui.mineverify.link.LinkRequestStore;
import fr.sukikui.mineverify.message.MineVerifyChatStyle;
import fr.sukikui.mineverify.remote.RemoteAppCallStatus;
import fr.sukikui.mineverify.remote.RemoteAppPoller;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.command.CommandSender;

/**
 * Renders MineVerify admin status messages.
 */
public final class MineVerifyStatusRenderer {

  private static final String GOOD = "§a";
  private static final String WARN = "§e";
  private static final String BAD = "§c";
  private static final String LABEL = "§7";
  private static final String VALUE = "§f";
  private static final String MUTED = "§8";

  private final RemoteAppPoller poller;
  private final LinkRequestStore requestStore;

  /**
   * Creates a status renderer.
   */
  public MineVerifyStatusRenderer(RemoteAppPoller poller, LinkRequestStore requestStore) {
    this.poller = Objects.requireNonNull(poller, "poller");
    this.requestStore = Objects.requireNonNull(requestStore, "requestStore");
  }

  /**
   * Sends the status view.
   */
  public void sendStatus(CommandSender sender, boolean includeRequests) {
    final Instant now = Instant.now();
    sendLine(sender, "§b§lStatus");
    sendLine(sender, LABEL + "Polling: " + pollingState());
    sendLine(sender, LABEL + "Configured apps: " + VALUE + poller.apps().size());
    sendLine(sender, LABEL + "Stored requests: " + VALUE + requestStore.size());
    sendLine(sender, "");
    sendApps(sender, now);
    if (includeRequests) {
      sendLine(sender, "");
      sendRequests(sender, now);
    }
  }

  /**
   * Sends an admin permission error.
   */
  public void sendNoPermission(CommandSender sender) {
    sendLine(sender, BAD + "§lError: " + BAD + "You do not have permission to use this command.");
  }

  /**
   * Sends the status command usage.
   */
  public void sendUsage(CommandSender sender) {
    sendLine(sender, WARN + "Usage: /mineverify status [requests]");
  }

  private void sendApps(CommandSender sender, Instant now) {
    sendLine(sender, LABEL + "Apps:");
    if (poller.apps().isEmpty()) {
      sendLine(sender, MUTED + "- none");
      return;
    }

    for (RemoteAppConfig app : poller.apps().values()) {
      sendLine(sender, MUTED + "- " + VALUE + app.id() + displayName(app));
      sendLine(sender, "  " + LABEL + "interval: " + VALUE + formatDuration(app.pollInterval()));
      sendLine(sender, "  " + LABEL + "last poll: " + VALUE + formatAgo(
          poller.lastPendingPoll(app.id()), now));
      sendLine(sender, "  " + LABEL + "next poll: " + formatNextPoll(app.id(), now));
      sendLastResponse(sender, app, now);
      sendLine(sender, "");
    }
  }

  private void sendLastResponse(CommandSender sender, RemoteAppConfig app, Instant now) {
    Optional<RemoteAppCallStatus> status = poller.lastResponse(app.id());
    if (status.isEmpty()) {
      sendLine(sender, "  " + LABEL + "last response: " + MUTED + "none");
      sendLine(sender, "  " + LABEL + "last call: " + MUTED + "never");
      return;
    }

    RemoteAppCallStatus callStatus = status.get();
    sendLine(sender, "  " + LABEL + "last response: " + formatResponse(callStatus));
    sendLine(sender, "  " + LABEL + "last call: " + VALUE + callStatus.endpoint()
        + MUTED + " (" + formatAgo(Optional.of(callStatus.at()), now) + ")");
  }

  private void sendRequests(CommandSender sender, Instant now) {
    sendLine(sender, LABEL + "Requests:");
    List<LinkRequest> requests = requestStore.requests();
    requests.sort(Comparator.comparing(LinkRequest::appId).thenComparing(LinkRequest::requestId));
    if (requests.isEmpty()) {
      sendLine(sender, MUTED + "- none");
      return;
    }

    for (LinkRequest request : requests) {
      sendLine(sender, MUTED + "- " + VALUE + request.appId() + MUTED + " / "
          + VALUE + shortRequestId(request.requestId()));
      sendLine(sender, "  " + LABEL + "state: " + formatState(request.state()));
      sendLine(sender, "  " + LABEL + "code: " + VALUE + request.code());
      sendRequestDetails(sender, request, now);
      sendLine(sender, "");
    }
  }

  private void sendRequestDetails(CommandSender sender, LinkRequest request, Instant now) {
    LinkRequestState state = request.state();
    if (state == LinkRequestState.PENDING_VALIDATION) {
      sendLine(sender, "  " + LABEL + "expires in: " + VALUE + formatClock(
          Duration.between(now, request.expiresAt())));
      sendLine(sender, "  " + LABEL + "code-created: " + reportState(
          request.isCodeCreatedReported()));
      sendLine(sender, "  " + LABEL + "validated: " + WARN + "waiting");
      sendLine(sender, "  " + LABEL + "expired: " + WARN + "waiting");
      return;
    }

    if (state == LinkRequestState.VALIDATED) {
      sendPlayer(sender, request);
      sendLine(sender, "  " + LABEL + "validated: " + reportState(
          request.isValidationReported()));
      return;
    }

    sendLine(sender, "  " + LABEL + "expired: " + reportState(
        request.isExpirationReported()));
  }

  private void sendPlayer(CommandSender sender, LinkRequest request) {
    if (request.minecraftName().isEmpty() || request.minecraftUuid().isEmpty()) {
      return;
    }

    sendLine(sender, "  " + LABEL + "player: " + VALUE + request.minecraftName().get()
        + MUTED + " (" + request.minecraftUuid().get() + ")");
  }

  private String pollingState() {
    if (poller.isRunning()) {
      return GOOD + "running";
    }
    return BAD + "stopped";
  }

  private String formatNextPoll(String appId, Instant now) {
    if (!poller.isRunning()) {
      return MUTED + "inactive";
    }
    return VALUE + formatUntil(poller.nextPendingPoll(appId, now), now);
  }

  private String formatResponse(RemoteAppCallStatus status) {
    OptionalInt statusCode = status.statusCode();
    if (statusCode.isEmpty()) {
      return BAD + shortError(status.error());
    }

    int code = statusCode.getAsInt();
    String response = statusColor(status, code) + "HTTP " + code;
    if (!status.isSuccess()) {
      String error = shortError(status.error());
      if (!error.isBlank() && !error.startsWith("HTTP ")) {
        response += MUTED + " (" + error + ")";
      }
    }
    return response;
  }

  private String statusColor(RemoteAppCallStatus status, int code) {
    if (!status.isSuccess() && code < 300) {
      return BAD;
    }
    if (code >= 200 && code < 300) {
      return GOOD;
    }
    if (code >= 300 && code < 400) {
      return WARN;
    }
    return BAD;
  }

  private String formatState(LinkRequestState state) {
    String value = state.name().toLowerCase(Locale.ROOT);
    if (state == LinkRequestState.PENDING_VALIDATION) {
      return WARN + value;
    }
    if (state == LinkRequestState.VALIDATED) {
      return GOOD + value;
    }
    return BAD + value;
  }

  private String reportState(boolean reported) {
    if (reported) {
      return GOOD + "reported";
    }
    return WARN + "waiting report";
  }

  private String displayName(RemoteAppConfig app) {
    if (app.name().equals(app.id())) {
      return "";
    }
    return MUTED + " (" + app.name() + ")";
  }

  private String formatAgo(Optional<Instant> instant, Instant now) {
    if (instant.isEmpty()) {
      return "never";
    }
    Duration duration = Duration.between(instant.get(), now);
    if (duration.isNegative()) {
      duration = Duration.ZERO;
    }
    return formatDuration(duration) + " ago";
  }

  private String formatUntil(Optional<Instant> instant, Instant now) {
    if (instant.isEmpty()) {
      return "unknown";
    }
    Duration duration = Duration.between(now, instant.get());
    if (duration.isZero() || duration.isNegative()) {
      return "now";
    }
    return formatDuration(duration);
  }

  private String formatClock(Duration duration) {
    if (duration.isNegative()) {
      duration = Duration.ZERO;
    }
    long seconds = duration.toSeconds();
    return "%02d:%02d".formatted(seconds / 60L, seconds % 60L);
  }

  private String formatDuration(Duration duration) {
    long seconds = Math.max(0L, duration.toSeconds());
    if (seconds < 60L) {
      return seconds + "s";
    }
    long minutes = seconds / 60L;
    seconds %= 60L;
    if (seconds == 0L) {
      return minutes + "m";
    }
    return minutes + "m " + seconds + "s";
  }

  private String shortRequestId(String requestId) {
    if (requestId.length() <= 16) {
      return requestId;
    }
    return requestId.substring(0, 8) + "..." + requestId.substring(requestId.length() - 4);
  }

  private String shortError(Optional<String> error) {
    if (error.isEmpty()) {
      return "remote error";
    }
    String message = error.get();
    if (message.contains("Invalid pending request response")) {
      return "invalid response";
    }
    if (message.contains("HTTP ")) {
      return "";
    }
    if (message.contains("interrupted")) {
      return "interrupted";
    }
    if (message.contains("failed")) {
      return "network error";
    }
    return message;
  }

  private void sendLine(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + message);
  }
}
