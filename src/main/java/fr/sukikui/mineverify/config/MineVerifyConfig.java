package fr.sukikui.mineverify.config;

import fr.sukikui.mineverify.message.MineVerifyMessages;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed MineVerify plugin configuration.
 */
public final class MineVerifyConfig {

  private static final long DEFAULT_CODE_TTL_SECONDS = 300;

  private final Map<String, RemoteAppConfig> apps;
  private final List<IgnoredRemoteApp> ignoredApps;
  private final Duration codeTtl;
  private final MineVerifyMessages messages;

  private MineVerifyConfig(
      Map<String, RemoteAppConfig> apps,
      List<IgnoredRemoteApp> ignoredApps,
      Duration codeTtl,
      MineVerifyMessages messages) {
    this.apps = Collections.unmodifiableMap(new LinkedHashMap<>(apps));
    this.ignoredApps = List.copyOf(ignoredApps);
    this.codeTtl = codeTtl;
    this.messages = messages;
  }

  /**
   * Loads typed configuration from Bukkit config.
   */
  public static MineVerifyConfig load(FileConfiguration config) {
    LoadedApps loadedApps = loadApps(config);
    return new MineVerifyConfig(
        loadedApps.apps(),
        loadedApps.ignoredApps(),
        positiveDuration(config, "linking.code-ttl-seconds", DEFAULT_CODE_TTL_SECONDS),
        MineVerifyMessages.load(config.getString("language", "en_us")));
  }

  /**
   * Returns remote apps keyed by configured app id.
   */
  public Map<String, RemoteAppConfig> apps() {
    return apps;
  }

  /**
   * Returns remote apps ignored while loading the configuration.
   */
  public List<IgnoredRemoteApp> ignoredApps() {
    return ignoredApps;
  }

  /**
   * Returns generated code validity duration.
   */
  public Duration codeTtl() {
    return codeTtl;
  }

  /**
   * Returns loaded player-facing messages.
   */
  public MineVerifyMessages messages() {
    return messages;
  }

  private static LoadedApps loadApps(FileConfiguration config) {
    ConfigurationSection section = config.getConfigurationSection("apps");
    if (section == null) {
      return new LoadedApps(Map.of(), List.of());
    }

    Map<String, RemoteAppConfig> apps = new LinkedHashMap<>();
    List<IgnoredRemoteApp> ignoredApps = new ArrayList<>();
    for (String appId : section.getKeys(false)) {
      ConfigurationSection appSection = section.getConfigurationSection(appId);
      if (appSection == null) {
        ignoredApps.add(new IgnoredRemoteApp(appId, "app config must be a section"));
        continue;
      }
      RemoteAppConfig app = RemoteAppConfig.load(appId, appSection);
      if (app.isUsable()) {
        apps.put(appId, app);
      } else {
        ignoredApps.add(new IgnoredRemoteApp(
            appId, app.unusableReason().orElse("invalid app config")));
      }
    }
    return new LoadedApps(apps, ignoredApps);
  }

  private static Duration positiveDuration(
      FileConfiguration config, String path, long defaultSeconds) {
    long seconds = config.getLong(path, defaultSeconds);
    return Duration.ofSeconds(Math.max(1, seconds));
  }

  private record LoadedApps(Map<String, RemoteAppConfig> apps, List<IgnoredRemoteApp> ignoredApps) {
  }
}
