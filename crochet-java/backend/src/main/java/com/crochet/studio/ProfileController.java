package com.crochet.studio;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {
  private final Db db;
  private final RateLimits limits;

  public ProfileController(Db db, RateLimits limits) {
    this.db = db;
    this.limits = limits;
  }

  @GetMapping("/api/profile")
  public Map<String, Object> get() {
    var u = Security.user();
    var result = new HashMap<String, Object>();
    result.put("profile", Db.api(db.one("SELECT * FROM profiles WHERE user_id=?", u.id())));
    return result;
  }

  @PostMapping("/api/profile")
  public Map<String, Object> save(@RequestBody Map<String, Object> b) {
    var u = Security.user();
    limits.check("profile", u.id(), 15);
    String name = Inputs.text(b, "name", 2, 100),
        phone = Inputs.text(b, "phone", 10, 10),
        line1 = Inputs.text(b, "line1", 5, 200),
        line2 = Inputs.optional(b, "line2", 200),
        city = Inputs.text(b, "city", 1, 100),
        state = Inputs.text(b, "state", 1, 100),
        pin = Inputs.text(b, "pincode", 6, 6);
    if (!phone.matches("[0-9]{10}") || !pin.matches("[0-9]{6}"))
      throw new ApiError(400, "Use a 10-digit phone and 6-digit PIN code.");
    db.update(
        "INSERT INTO profiles(user_id,name,phone,line1,line2,city,state,pincode) VALUES"
            + " (?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET"
            + " name=EXCLUDED.name,phone=EXCLUDED.phone,line1=EXCLUDED.line1,line2=EXCLUDED.line2,city=EXCLUDED.city,state=EXCLUDED.state,pincode=EXCLUDED.pincode,updated=now()",
        u.id(),
        name,
        phone,
        line1,
        line2,
        city,
        state,
        pin);
    return Map.of("ok", true);
  }
}
