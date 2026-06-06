package fr.sukikui.mineverify.link;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-safe in-memory store for MineVerify request lifecycle events.
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
   * Returns generated code reports that still need to be sent to a remote app.
   */
  public synchronized List<LinkRequest> pendingCodeCreatedReports(String appId) {
    List<LinkRequest> requests = new ArrayList<>();
    for (LinkRequest request : requestsByCode.values()) {
      if (request.appId().equals(appId) && request.needsCodeCreatedReport()) {
        requests.add(request);
      }
    }
    return requests;
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
   * Returns expired requests that still need to be sent to a remote app.
   */
  public synchronized List<LinkRequest> pendingExpirationReports(String appId) {
    List<LinkRequest> requests = new ArrayList<>();
    for (LinkRequest request : requestsByCode.values()) {
      if (request.appId().equals(appId) && request.needsExpirationReport()) {
        requests.add(request);
      }
    }
    return requests;
  }

  /**
   * Expires pending requests and returns the number of newly expired requests.
   */
  public synchronized int expirePending(Instant now) {
    int expired = 0;
    for (LinkRequest request : requestsByCode.values()) {
      if (request.expireIfNeeded(now)) {
        expired++;
      }
    }
    return expired;
  }

  /**
   * Removes terminal requests already reported to remote apps.
   */
  public synchronized int removeReportedTerminals() {
    List<String> removableCodes = new ArrayList<>();
    for (Map.Entry<String, LinkRequest> entry : requestsByCode.entrySet()) {
      if (entry.getValue().isReportedTerminal()) {
        removableCodes.add(entry.getKey());
      }
    }
    for (String code : removableCodes) {
      removeByCode(code);
    }
    return removableCodes.size();
  }

  /**
   * Returns the number of stored requests.
   */
  public synchronized int size() {
    return requestsByCode.size();
  }

  /**
   * Returns true when at least one request is still stored.
   */
  public synchronized boolean hasRequests() {
    return !requestsByCode.isEmpty();
  }

  /**
   * Returns all requests currently stored in memory.
   */
  public synchronized List<LinkRequest> requests() {
    return new ArrayList<>(requestsByCode.values());
  }

  private static String remoteKey(String appId, String requestId) {
    return appId + '\n' + requestId;
  }

  private void removeByCode(String code) {
    LinkRequest request = requestsByCode.remove(code);
    if (request != null) {
      codeByRemoteRequest.remove(remoteKey(request.appId(), request.requestId()));
    }
  }
}
