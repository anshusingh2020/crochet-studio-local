package com.crochet.studio;

import java.util.*;

public final class Inputs {
  private Inputs() {}

  public static String text(Map<String, Object> b, String key, int min, int max) {
    Object v = b.get(key);
    if (!(v instanceof String s) || s.trim().length() < min || s.length() > max)
      throw new ApiError(400, "Check the " + key + " field.");
    return s.trim();
  }

  public static String optional(Map<String, Object> b, String key, int max) {
    return b.get(key) == null ? "" : text(b, key, 0, max);
  }

  public static int number(Map<String, Object> b, String key, int min, int max) {
    Object v = b.get(key);
    if (!(v instanceof Number n)
        || n.doubleValue() != n.intValue()
        || n.intValue() < min
        || n.intValue() > max) throw new ApiError(400, "Check the " + key + " field.");
    return n.intValue();
  }

  public static String email(Map<String, Object> b) {
    String s = text(b, "email", 3, 254).toLowerCase(Locale.ROOT);
    if (!s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new ApiError(400, "Enter a valid email address.");
    return s;
  }

  public static String password(Map<String, Object> b) {
    Object p = b.get("password");
    if (!(p instanceof String s)
        || s.length() < 12
        || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiError(
          400, "Use a password with at least 12 characters and at most 72 UTF-8 bytes.");
    return s;
  }
}
