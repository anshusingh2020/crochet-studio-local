package com.crochet.studio;

import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminController {
  private final Db db;
  private final MailService mail;
  private final RateLimits limits;

  public AdminController(Db db, MailService mail, RateLimits limits) {
    this.db = db;
    this.mail = mail;
    this.limits = limits;
  }

  @GetMapping("/api/admin-requests")
  public Map<String, Object> list(@RequestParam(defaultValue = "zzzz") String before) {
    Security.owner();
    var rows =
        db.rows(
            "SELECT id,email,name,status,created,reviewed,owner_notified FROM users WHERE"
                + " role='ADMIN' AND id<? ORDER BY id DESC LIMIT 51",
            before);
    var result = new HashMap<String, Object>();
    result.put("accounts", rows.subList(0, Math.min(50, rows.size())));
    result.put("next", rows.size() > 50 ? rows.get(49).get("id") : null);
    result.put("emailReady", true);
    return result;
  }

  @PostMapping("/api/admin-requests")
  @Transactional
  public Map<String, Object> review(@RequestBody Map<String, Object> b) {
    var owner = Security.owner();
    limits.check("review", owner.id(), 20);
    String id = Inputs.text(b, "id", 36, 36), action = Inputs.text(b, "action", 1, 12);
    var account = db.one("SELECT * FROM users WHERE id=? AND role='ADMIN' FOR UPDATE", id);
    if (account == null) throw new ApiError(404, "Admin request not found.");
    if (action.equals("notify")) {
      if (!account.get("status").equals("pending"))
        throw new ApiError(409, "Request is not pending.");
      if (!mail.notifyOwner(account)) throw new ApiError(503, "Could not send the owner email.");
      return Map.of("ok", true);
    }
    String expected = action.equals("revoke") ? "approved" : "pending";
    if (!account.get("status").equals(expected))
      throw new ApiError(409, "Request changed. Refresh before reviewing.");
    String status =
        switch (action) {
          case "approve" -> "approved";
          case "reject" -> "rejected";
          case "revoke" -> "revoked";
          default -> throw new ApiError(400, "Invalid action.");
        };
    db.update(
        "UPDATE users SET status=?,reviewed=now(),reviewed_by=? WHERE id=? AND role='ADMIN'",
        status,
        owner.id(),
        id);
    db.update("DELETE FROM sessions WHERE user_id=?", id);
    db.update(
        "INSERT INTO admin_audit(id,account_id,actor,action) VALUES (?,?,?,?)",
        Db.id(),
        id,
        owner.id(),
        action);
    return Map.of("ok", true);
  }
}
