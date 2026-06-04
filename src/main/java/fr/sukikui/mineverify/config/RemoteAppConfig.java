package fr.sukikui.mineverify.config;

import java.time.Duration;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuration for one remote app using MineVerify.
 */
public final class RemoteAppConfig {

  private static final long DEFAULT_POLL_INTERVAL_SECONDS = 3;

  private final String id;
  private final String name;
  private final String baseUrl;
  private final String token;
  private final Duration pollInterval;

  private RemoteAppConfig(
      String id, String name, String baseUrl, String token, Duration pollInterval) {
    this.id = id;
    this.name = name.isBlank() ? id : name;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.token = token;
    this.pollInterval = pollInterval;
  }

  /**
   * Loads one remote app from Bukkit config.
   */
  public static RemoteAppConfig load(String id, ConfigurationSection section) {
    long interval = section.getLong("poll-interval-seconds", DEFAULT_POLL_INTERVAL_SECONDS);
    return new RemoteAppConfig(
        id,
        string(section, "name"),
        string(section, "base-url"),
        string(section, "token"),
        Duration.ofSeconds(Math.max(1, interval)));
  }

  /**
   * Returns true when the app has enough settings to be polled.
   */
  public boolean isUsable() {
    return !id.isBlank() && !baseUrl.isBlank() && !token.isBlank();
  }

  /**
   * Returns the configured app id.
   */
  public String id() {
    return id;
  }

  /**
   * Returns the player-facing app name.
   */
  public String name() {
    return name;
  }

  /**
   * Returns the remote app base URL.
   */
  public String baseUrl() {
    return baseUrl;
  }

  /**
   * Returns the bearer token.
   */
  public String token() {
    return token;
  }

  /**
   * Returns the polling interval.
   */
  public Duration pollInterval() {
    return pollInterval;
  }

  /**
   * Builds a full endpoint URL from a path.
   */
  public String endpoint(String path) {
    return baseUrl + path;
  }

  private static String trimTrailingSlash(String value) {
    String trimmed = value.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String string(ConfigurationSection section, String path) {
    return Objects.requireNonNullElse(section.getString(path), "");
  }
}
