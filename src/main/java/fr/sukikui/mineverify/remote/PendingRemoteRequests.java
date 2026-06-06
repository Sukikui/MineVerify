package fr.sukikui.mineverify.remote;

import java.util.List;
import java.util.Objects;

/**
 * Pending requests returned by one remote app call.
 */
public record PendingRemoteRequests(List<PendingRemoteRequest> requests, int statusCode) {

  /**
   * Creates immutable pending requests with their HTTP status code.
   */
  public PendingRemoteRequests {
    requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
  }
}
