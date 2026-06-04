package fr.sukikui.mineverify.link;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Generates readable one-time MineVerify codes.
 */
public final class LinkCodeGenerator {

  private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private static final int GROUP_SIZE = 4;
  private static final int GROUP_COUNT = 2;
  private static final int MAX_ATTEMPTS = 128;

  private final SecureRandom random;

  /**
   * Creates a generator backed by {@link SecureRandom}.
   */
  public LinkCodeGenerator() {
    this(new SecureRandom());
  }

  /**
   * Creates a generator with an explicit random source.
   */
  public LinkCodeGenerator(SecureRandom random) {
    this.random = Objects.requireNonNull(random, "random");
  }

  /**
   * Generates a globally unique active code.
   */
  public String generate(Predicate<String> isActiveCode) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String code = nextCode();
      if (!isActiveCode.test(code)) {
        return code;
      }
    }
    throw new IllegalStateException("Unable to generate a unique MineVerify code");
  }

  /**
   * Normalizes player-entered codes.
   */
  public static String normalize(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private String nextCode() {
    StringBuilder builder = new StringBuilder((GROUP_SIZE * GROUP_COUNT) + GROUP_COUNT - 1);
    for (int group = 0; group < GROUP_COUNT; group++) {
      if (group > 0) {
        builder.append('-');
      }
      appendGroup(builder);
    }
    return builder.toString();
  }

  private void appendGroup(StringBuilder builder) {
    for (int index = 0; index < GROUP_SIZE; index++) {
      builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    }
  }
}
