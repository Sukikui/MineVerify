package fr.sukikui.mineverify.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MineVerifyMessagesTest {

  @Test
  void loadsDefaultEnglishMessages() {
    MineVerifyMessages messages = MineVerifyMessages.load("en_us");

    assertEquals("en_us", messages.language());
    assertEquals("Usage: /mineverify [code]", messages.usage());
    assertEquals("Code accepted for Your App. The app will update shortly.",
        messages.accepted("Your App"));
  }

  @Test
  void loadsFrenchMessages() {
    MineVerifyMessages messages = MineVerifyMessages.load("fr_fr");

    assertEquals("fr_fr", messages.language());
    assertEquals("Utilisation : /mineverify [code]", messages.usage());
  }

  @Test
  void loadsPlayerCoordsApiLanguageSet() {
    List<String> languages =
        List.of("de_de", "en_us", "es_es", "fr_fr", "it_it", "ja_jp", "pt_br", "ru_ru", "zh_cn");

    for (String language : languages) {
      MineVerifyMessages messages = MineVerifyMessages.load(language);

      assertEquals(language, messages.language());
    }
  }
}
