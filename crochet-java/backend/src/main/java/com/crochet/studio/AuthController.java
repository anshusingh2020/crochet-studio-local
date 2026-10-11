package com.crochet.studio;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  private final Db db;
  private final RateLimits limits;
  private final PasswordEncoder encoder;
  private final MailService mail;
  private final boolean secure;
  private final String dummy;

  public AuthController(
      Db db,
      RateLimits limits,
      PasswordEncoder encoder,
      MailService mail,
      @Value("${studio.secure-cookie}") boolean secure) {
    this.db = db;
    this.limits = limits;
    this.encoder = encoder;
    this.mail = mail;
    this.secure = secure;
    this.dummy = encoder.encode(Security.token());
  }

  @GetMapping("/api/auth/me")
  public Map<String, Object> me() {
    var u = Security.optional();
    return u == null
        ? Map.of("signedIn", false)
        : Map.of("signedIn", true, "user", u, "admin", u.admin(), "owner", u.owner());
  }

  @GetMapping("/api/admin-auth")
  public Map<String, Object> config() {
    return Map.of("registrationEnabled", true);
  }

  @PostMapping({"/api/auth", "/api/admin-auth"})
  public ResponseEntity<?> auth(@RequestBody Map<String, Object> b, HttpServletRequest req) {
    limits.check("auth-ip", req.getRemoteAddr(), 15);
    String action = Inputs.text(b, "action", 1, 20);
    if (action.equals("logout")) {
      db.update("DELETE FROM sessions WHERE token_hash=?", Security.hash(Security.session(req)));
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, cookie("", 0))
          .body(Map.of("ok", true));
    }
    if (action.equals("verify") || action.equals("reset"))
      return ResponseEntity.ok(consume(b, action));
    String email = Inputs.email(b);
    limits.check("auth-email", email, 5);
    var account = db.one("SELECT * FROM users WHERE email=?", email);
    if (action.equals("login")) {
      String password = Inputs.password(b);
      boolean matches =
          encoder.matches(
              password, account == null ? dummy : (String) account.get("password_hash"));
      if (account == null || !matches) throw new ApiError(401, "Email or password is incorrect.");
      if (!account.get("status").equals("approved"))
        throw new ApiError(
            403,
            "Account status: "
                + account.get("status")
                + ". Verify your email and, for admins, wait for owner approval.");
      if (req.getRequestURI().equals("/api/admin-auth") && account.get("role").equals("CUSTOMER"))
        throw new ApiError(403, "Use customer sign in.");
      String token = Security.token();
      db.update(
          "INSERT INTO sessions(token_hash,user_id,expires) SELECT ?,id,now()+interval '8 hours'"
              + " FROM users WHERE id=? AND status='approved'",
          Security.hash(token),
          account.get("id"));
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, cookie(token, 28800))
          .body(Map.of("ok", true));
    }
    if (!Set.of("register", "resend", "forgot").contains(action))
      throw new ApiError(400, "Invalid action.");
    limits.check("mail", email, 1);
    limits.check("mail-global", "all", 30);
    if (action.equals("register")) {
      String password = Inputs.password(b), name = Inputs.text(b, "name", 2, 100);
      String role = req.getRequestURI().equals("/api/admin-auth") ? "ADMIN" : "CUSTOMER";
      if (account == null) {
        db.update(
            "INSERT INTO users(id,email,name,password_hash,role,status) VALUES"
                + " (?,?,?,?,?,'unverified') ON CONFLICT(email) DO NOTHING",
            Db.id(),
            email,
            name,
            encoder.encode(password),
            role);
        account = db.one("SELECT * FROM users WHERE email=?", email);
      }
    }
    String purpose = action.equals("forgot") ? "reset" : "verify";
    boolean eligible =
        account != null
            && (purpose.equals("verify")
                ? account.get("status").equals("unverified")
                : Set.of("pending", "approved").contains(account.get("status")));
    if (eligible) {
      String token = Security.token();
      db.update(
          "DELETE FROM auth_tokens WHERE user_id=? AND purpose=?", account.get("id"), purpose);
      db.update(
          "INSERT INTO auth_tokens(token_hash,user_id,purpose,expires) VALUES (?,?,?,now()+interval"
              + " '1 hour')",
          Security.hash(token),
          account.get("id"),
          purpose);
      try {
        mail.link(email, purpose, token);
      } catch (Exception e) {
        throw new ApiError(
            503, "Could not send the email. Please request another verification email later.");
      }
    }
    return ResponseEntity.ok(
        Map.of(
            "ok",
            true,
            "message",
            "If this email is eligible, an email has been sent. Check your inbox."));
  }

  private String cookie(String value, long age) {
    return ResponseCookie.from("STUDIO_SESSION", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(age)
        .build()
        .toString();
  }

  // Row lock and token deletion are handled by a TransactionTemplate to apply even on internal
  // calls.
  private Map<String, Object> consume(Map<String, Object> b, String purpose) {
    return transactions.execute(
        db,
        () -> {
          String value = Inputs.text(b, "token", 64, 64), password = Inputs.password(b);
          var row =
              db.one(
                  "SELECT u.* FROM auth_tokens t JOIN users u ON u.id=t.user_id WHERE"
                      + " t.token_hash=? AND t.purpose=? AND t.expires>now() FOR UPDATE OF u,t",
                  Security.hash(value),
                  purpose);
          if (row == null
              || !(purpose.equals("verify")
                  ? row.get("status").equals("unverified")
                  : Set.of("pending", "approved").contains(row.get("status"))))
            throw new ApiError(400, "This link has expired or was already used.");
          String status =
              purpose.equals("verify")
                  ? (row.get("role").equals("ADMIN") ? "pending" : "approved")
                  : (String) row.get("status");
          db.update(
              "UPDATE users SET password_hash=?,status=? WHERE id=?",
              encoder.encode(password),
              status,
              row.get("id"));
          db.update("DELETE FROM auth_tokens WHERE user_id=?", row.get("id"));
          db.update("DELETE FROM sessions WHERE user_id=?", row.get("id"));
          boolean notified =
              !status.equals("pending") || !purpose.equals("verify") || mail.notifyOwner(row);
          return Map.of(
              "ok",
              true,
              "message",
              status.equals("pending")
                  ? "Email verified. Your admin application is awaiting super admin approval."
                      + (notified ? "" : " The request is saved, but the owner email failed.")
                  : "Password confirmed. You can now sign in.");
        });
  }

  private final Tx transactions = new Tx();

  static class Tx {
    <T> T execute(Db db, java.util.function.Supplier<T> work) {
      return new org.springframework.transaction.support.TransactionTemplate(
              new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                  Objects.requireNonNull(db.jdbc.getDataSource())))
          .execute(s -> work.get());
    }
  }
}
