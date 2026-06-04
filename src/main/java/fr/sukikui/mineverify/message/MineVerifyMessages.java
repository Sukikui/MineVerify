package fr.sukikui.mineverify.message;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bundled player-facing MineVerify translations.
 */
public final class MineVerifyMessages {

  private static final String DEFAULT_LANGUAGE = "en_us";

  private final String language;
  private final Map<String, String> values;

  private MineVerifyMessages(String language, Map<String, String> values) {
    this.language = language;
    this.values = Map.copyOf(values);
  }

  /**
   * Loads bundled translations by language id.
   */
  public static MineVerifyMessages load(String language) {
    String normalizedLanguage = normalizeLanguage(language);
    Map<String, String> fallbackValues = readLanguage(DEFAULT_LANGUAGE);
    if (DEFAULT_LANGUAGE.equals(normalizedLanguage)) {
      return new MineVerifyMessages(DEFAULT_LANGUAGE, fallbackValues);
    }

    Map<String, String> selectedValues = new HashMap<>(fallbackValues);
    selectedValues.putAll(readLanguage(normalizedLanguage));
    return new MineVerifyMessages(normalizedLanguage, selectedValues);
  }

  /**
   * Returns the loaded language id.
   */
  public String language() {
    return language;
  }

  /**
   * Returns the usage message.
   */
  public String usage() {
    return value("usage");
  }

  /**
   * Returns the non-player sender rejection message.
   */
  public String playerOnly() {
    return value("player-only");
  }

  /**
   * Returns the invalid code message.
   */
  public String invalidCode() {
    return value("invalid-code");
  }

  /**
   * Returns the accepted code message.
   */
  public String accepted(String appName) {
    return String.format(Locale.ROOT, value("accepted"), appName);
  }

  private String value(String key) {
    return values.getOrDefault(key, key);
  }

  private static String normalizeLanguage(String language) {
    if (language == null || language.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    return language.trim().toLowerCase(Locale.ROOT);
  }

  private static Map<String, String> readLanguage(String language) {
    String path = "lang/" + language + ".json";
    try (InputStream stream = MineVerifyMessages.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        return Map.of();
      }
      return readJson(stream);
    } catch (IOException exception) {
      return Map.of();
    }
  }

  private static Map<String, String> readJson(InputStream stream) throws IOException {
    JsonObject root;
    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      root = JsonParser.parseReader(reader).getAsJsonObject();
    }

    Map<String, String> values = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
      JsonElement value = entry.getValue();
      if (value != null && !value.isJsonNull()) {
        values.put(entry.getKey(), value.getAsString());
      }
    }
    return values;
  }
}
