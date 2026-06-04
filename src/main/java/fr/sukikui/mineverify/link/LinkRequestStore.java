package fr.sukikui.mineverify.link;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-safe in-memory store for active MineVerify requests.
 */
public final class LinkRequestStore {

  private final Map<String, LinkRequest> requestsByCode = new HashMap<>();
  private final Map<String, String> codeByRemoteRequest = new HashMap<>();

  /**
   * Finds an existing request for one remote app request.
   */
  public synchronized Optional<LinkRequest> findByRemoteRequest(String appId, String requestId) {
    String code = codeByRemoteRequest.get(remoteKey(appId, requestId));
    return Optional.ofNullable(code).map(requestsByCode::get);
  }

  /**
   * Returns true when a code is currently active.
   */
  public synchronized boolean hasActiveCode(String code, Instant now) {
    LinkRequest request = requestsByCode.get(LinkCodeGenerator.normalize(code));
    return request != null
        && request.state() == LinkRequestState.PENDING_VALIDATION
        && !request.isExpired(now);
  }

  /**
   * Stores a pending request unless it already exists.
   */
  public synchronized LinkRequest store(
      String appId, String requestId, String code, Instant expiresAt, Instant now) {
    Optional<LinkRequest> existing = findByRemoteRequest(appId, requestId);
    if (existing.isPresent()) {
      return existing.get();
    }

    String normalizedCode = LinkCodeGenerator.normalize(code);
    if (hasActiveCode(normalizedCode, now)) {
      throw new IllegalArgumentException("Code is already active");
    }

    LinkRequest request = new LinkRequest(appId, requestId, normalizedCode, expiresAt);
    requestsByCode.put(normalizedCode, request);
    codeByRemoteRequest.put(remoteKey(appId, requestId), normalizedCode);
    return request;
  }

  /**
   * Validates a player-entered code as one-time use.
   */
  public synchronized Optional<LinkRequest> validateCode(
      String code, UUID minecraftUuid, String minecraftName, Instant now) {
    LinkRequest request = requestsByCode.get(LinkCodeGenerator.normalize(code));
    if (request == null || !request.validate(minecraftUuid, minecraftName, now)) {
      return Optional.empty();
    }
    return Optional.of(request);
  }

  /**
   * Returns validated requests that still need to be sent to a remote app.
   */
  public synchronized List<LinkRequest> pendingValidationReports(String appId) {
    List<LinkRequest> requests = new ArrayList<>();
    for (LinkRequest request : requestsByCode.values()) {
      if (request.appId().equals(appId) && request.needsValidationReport()) {
        requests.add(request);
      }
    }
    return requests;
  }

  /**
   * Removes expired requests and returns the number removed.
   */
  public synchronized int removeExpired(Instant now) {
    List<String> expiredCodes = new ArrayList<>();
    for (Map.Entry<String, LinkRequest> entry : requestsByCode.entrySet()) {
      LinkRequest request = entry.getValue();
      request.expireIfNeeded(now);
      if (request.state() == LinkRequestState.EXPIRED) {
        expiredCodes.add(entry.getKey());
      }
    }

    for (String code : expiredCodes) {
      LinkRequest request = requestsByCode.remove(code);
      if (request != null) {
        codeByRemoteRequest.remove(remoteKey(request.appId(), request.requestId()));
      }
    }
    return expiredCodes.size();
  }

  /**
   * Returns the number of stored requests.
   */
  public synchronized int size() {
    return requestsByCode.size();
  }

  private static String remoteKey(String appId, String requestId) {
    return appId + '\n' + requestId;
  }
}
