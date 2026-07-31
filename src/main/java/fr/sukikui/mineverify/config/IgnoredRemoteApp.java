package fr.sukikui.mineverify.config;

/**
 * Remote app ignored while loading the plugin configuration.
 */
public record IgnoredRemoteApp(String id, String reason) {
}
