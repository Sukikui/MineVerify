package fr.sukikui.mineverify.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkCodeGeneratorTest {

  @Test
  void generatesReadableCode() {
    LinkCodeGenerator generator = new LinkCodeGenerator();

    String code = generator.generate(candidate -> false);

    assertTrue(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
  }

  @Test
  void normalizesPlayerInput() {
    assertEquals("K7M9-P2Q4", LinkCodeGenerator.normalize(" k7m9-p2q4 "));
  }
}
