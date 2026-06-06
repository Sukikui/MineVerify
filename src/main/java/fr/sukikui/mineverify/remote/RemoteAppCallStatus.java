package fr.sukikui.mineverify.remote;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Last known result for one outbound app call.
 */
public final class RemoteAppCallStatus {

  private final String endpoint;
  private final Instant at;
  private final Integer statusCode;
  private final String error;
  private final String url;
  private final boolean success;

  private RemoteAppCallStatus(
      String endpoint, Instant at, Integer statusCode, String error, String url, boolean success) {
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.at = Objects.requireNonNull(at, "at");
    this.statusCode = statusCode;
    this.error = error;
    this.url = url;
    this.success = success;
  }

  /**
   * Creates a successful remote call status.
   */
  public static RemoteAppCallStatus success(
      String endpoint, int statusCode, String url, Instant at) {
    return new RemoteAppCallStatus(endpoint, at, statusCode, null, url, true);
  }

  /**
   * Creates a failed remote call status.
   */
  public static RemoteAppCallStatus failure(
      String endpoint, OptionalInt statusCode, String error, String url, Instant at) {
    return new RemoteAppCallStatus(endpoint, at, boxed(statusCode), error, url, false);
  }

  /**
   * Returns the app endpoint label.
   */
  public String endpoint() {
    return endpoint;
  }

  /**
   * Returns when the call happened.
   */
  public Instant at() {
    return at;
  }

  /**
   * Returns the HTTP status code when the app responded.
   */
  public OptionalInt statusCode() {
    if (statusCode == null) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(statusCode);
  }

  /**
   * Returns the error text when the call failed.
   */
  public Optional<String> error() {
    return Optional.ofNullable(error);
  }

  /**
   * Returns the URL called by MineVerify, without secret headers.
   */
  public Optional<String> url() {
    return Optional.ofNullable(url);
  }

  /**
   * Returns true when the call succeeded.
   */
  public boolean isSuccess() {
    return success;
  }

  private static Integer boxed(OptionalInt value) {
    if (value.isEmpty()) {
      return null;
    }
    return value.getAsInt();
  }
}
