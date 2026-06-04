package fr.sukikui.mineverify.remote;

import java.util.Objects;

/**
 * Remote request waiting for a MineVerify-generated code.
 */
public final class PendingRemoteRequest {

  private final String appId;
  private final String requestId;

  /**
   * Creates a pending remote request.
   */
  public PendingRemoteRequest(String appId, String requestId) {
    this.appId = Objects.requireNonNull(appId, "appId");
    this.requestId = Objects.requireNonNull(requestId, "requestId");
  }

  /**
   * Returns the configured app id.
   */
  public String appId() {
    return appId;
  }

  /**
   * Returns the remote request id.
   */
  public String requestId() {
    return requestId;
  }
}
