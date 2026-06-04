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
    yaml.set("apps.pmc-map.name", "PMC Map");
    yaml.set("apps.pmc-map.base-url", "https://pmc-map.example.com/");
    yaml.set("apps.pmc-map.token", "pmc-token");
    yaml.set("apps.pmc-map.poll-interval-seconds", 5);
    yaml.set("apps.another-app.base-url", "https://another.example.com");
    yaml.set("apps.another-app.token", "another-token");
    yaml.set("linking.code-ttl-seconds", 120);
    yaml.set("linking.cleanup-interval-seconds", 30);
    yaml.set("language", "fr_fr");

    MineVerifyConfig config = MineVerifyConfig.load(yaml);

    assertEquals(2, config.apps().size());
    assertEquals("PMC Map", config.apps().get("pmc-map").name());
    assertEquals("https://pmc-map.example.com", config.apps().get("pmc-map").baseUrl());
    assertEquals("pmc-token", config.apps().get("pmc-map").token());
    assertEquals(Duration.ofSeconds(5), config.apps().get("pmc-map").pollInterval());
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
