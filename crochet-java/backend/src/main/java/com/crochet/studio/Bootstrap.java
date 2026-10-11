package com.crochet.studio;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements CommandLineRunner {
  private final Db db;
  private final PasswordEncoder encoder;
  private final String email, password;

  public Bootstrap(
      Db db,
      PasswordEncoder encoder,
      @Value("${studio.super-email}") String email,
      @Value("${studio.super-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.email = email.trim().toLowerCase(Locale.ROOT);
    this.password = password;
  }

  public void run(String... args) {
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || password.length() < 16
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "Set SUPER_ADMIN_EMAIL and a unique 16–72 byte SUPER_ADMIN_PASSWORD before starting.");
    db.update(
        "INSERT INTO users(id,email,name,password_hash,role,status) VALUES"
            + " (?,?,?,?,'SUPER_ADMIN','approved') ON CONFLICT DO NOTHING",
        Db.id(),
        email,
        "Studio owner",
        encoder.encode(password));
    var owner = db.one("SELECT email FROM users WHERE role='SUPER_ADMIN'");
    if (owner == null || !email.equals(owner.get("email")))
      throw new IllegalStateException(
          "The configured super admin does not match the existing database owner. Resolve"
              + " explicitly; no role was reassigned.");
  }
}
