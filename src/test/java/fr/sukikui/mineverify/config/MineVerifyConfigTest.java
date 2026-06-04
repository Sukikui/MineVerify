package fr.sukikui.mineverify.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MineVerifyConfigTest {

  @Test
  void loadsConfiguredApps() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("apps.my-app.name", "My App");
    yaml.set("apps.my-app.base-url", "https://my-app.example.com/");
    yaml.set("apps.my-app.token", "my-app-token");
    yaml.set("apps.my-app.poll-interval-seconds", 5);
    yaml.set("apps.another-app.base-url", "https://another.example.com");
    yaml.set("apps.another-app.token", "another-token");
    yaml.set("linking.code-ttl-seconds", 120);
    yaml.set("linking.cleanup-interval-seconds", 30);
    yaml.set("language", "fr_fr");

    MineVerifyConfig config = MineVerifyConfig.load(yaml);

    assertEquals(2, config.apps().size());
    assertEquals("My App", config.apps().get("my-app").name());
    assertEquals("https://my-app.example.com", config.apps().get("my-app").baseUrl());
    assertEquals("my-app-token", config.apps().get("my-app").token());
    assertEquals(Duration.ofSeconds(5), config.apps().get("my-app").pollInterval());
    assertEquals(Duration.ofSeconds(120), config.codeTtl());
    assertEquals(Duration.ofSeconds(30), config.cleanupInterval());
    assertEquals("fr_fr", config.messages().language());
  }

  @Test
  void ignoresIncompleteApps() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("apps.incomplete.base-url", "https://example.com");

    MineVerifyConfig config = MineVerifyConfig.load(yaml);

    assertTrue(config.apps().isEmpty());
  }

  @Test
  void usesAppIdAsDefaultName() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("apps.my-app.base-url", "https://example.com");
    yaml.set("apps.my-app.token", "token");

    MineVerifyConfig config = MineVerifyConfig.load(yaml);

    assertEquals("my-app", config.apps().get("my-app").name());
  }
}
