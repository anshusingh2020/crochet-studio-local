package com.crochet.studio;

import java.net.URI;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public class OrdersController {
  private final Db db;
  private final RateLimits limits;
  private final ObjectMapper json = new ObjectMapper();

  public OrdersController(Db db, RateLimits limits) {
    this.db = db;
    this.limits = limits;
  }

  private Map<String, Object> order(String id, Security.User user, boolean lock) {
    var o = db.one("SELECT * FROM orders WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (o == null || (!user.admin() && !user.id().equals(o.get("user_id"))))
      throw new ApiError(404, "Order not found.");
    return o;
  }

  @GetMapping("/api/studio")
  public Map<String, Object> get(
      @RequestParam(required = false) String order, @RequestParam(required = false) String before) {
    var u = Security.user();
    if (order != null) {
      var o = order(order, u, false);
      var messages =
          Db.api(
              db.rows(
                  "SELECT * FROM messages WHERE order_id=? ORDER BY created DESC,id DESC LIMIT 200",
                  order));
      var chronological = new ArrayList<>(messages);
      Collections.reverse(chronological);
      return Map.of(
          "order",
          Db.api(o),
          "messages",
          chronological,
          "events",
          Db.api(
              db.rows(
                  "SELECT id,kind,created FROM order_events WHERE order_id=? ORDER BY created DESC"
                      + " LIMIT 100",
                  order)));
    }
    String cursor = before == null ? "9999-12-31T00:00:00Z" : before;
    try {
      java.time.Instant.parse(cursor);
    } catch (Exception e) {
      throw new ApiError(400, "Invalid pagination cursor.");
    }
    var rows =
        u.admin()
            ? db.rows(
                "SELECT * FROM orders WHERE created<CAST(? AS timestamptz) ORDER BY created DESC"
                    + " LIMIT 51",
                cursor)
            : db.rows(
                "SELECT * FROM orders WHERE user_id=? AND created<CAST(? AS timestamptz) ORDER BY"
                    + " created DESC LIMIT 51",
                u.id(),
                cursor);
    var result = new HashMap<String, Object>();
    result.put("user", Map.of("email", u.email(), "name", u.name()));
    result.put("admin", u.admin());
    result.put("owner", u.owner());
    result.put("orders", Db.api(rows.subList(0, Math.min(rows.size(), 50))));
    result.put("nextCursor", rows.size() > 50 ? Db.api(rows.get(49)).get("created") : null);
    return result;
  }

  @PostMapping("/api/studio")
  @Transactional
  public Map<String, Object> post(@RequestBody Map<String, Object> b) {
    var u = Security.user();
    limits.check("orders", u.id(), 40);
    String action = Inputs.text(b, "action", 1, 20);
    if (action.equals("create")) return create(b, u);
    String id = Inputs.text(b, "id", 36, 36);
    var o = order(id, u, true);
    String status = (String) o.get("status");
    if (action.equals("message")) {
      message(id, u, Inputs.text(b, "body", 1, 2000));
      event(id, "message.created", Map.of("authorRole", u.admin() ? "maker" : "customer"));
      return Map.of("ok", true);
    }
    if (action.equals("quote") && u.admin()) {
      if (!Set.of("requested", "quoted").contains(status))
        throw new ApiError(409, "Order cannot be quoted in this state.");
      int amount = Inputs.number(b, "amount", 100, 10000000);
      db.update("UPDATE orders SET amount=?,status='quoted' WHERE id=?", amount, id);
      message(
          id,
          u,
          "Quote sent: ₹"
              + String.format(java.util.Locale.ROOT, "%.2f", amount / 100.0)
              + ". Confirm the scope, delivery date and shipping charge before accepting.");
      event(id, "order.quoted", Map.of("amount", amount));
      return Map.of("ok", true);
    }
    if (action.equals("accept") && u.id().equals(o.get("user_id"))) {
      int amount = Inputs.number(b, "expectedAmount", 100, 10000000);
      if (!status.equals("quoted") || ((Number) o.get("amount")).intValue() != amount)
        throw new ApiError(409, "The quote changed. Refresh before accepting.");
      db.update("UPDATE orders SET status='accepted' WHERE id=?", id);
      event(id, "order.accepted", Map.of());
      return Map.of("ok", true);
    }
    if (action.equals("cancel")
        && !u.admin()
        && Set.of("requested", "quoted", "accepted").contains(status)) {
      db.update("UPDATE orders SET status='cancelled' WHERE id=?", id);
      event(id, "order.cancelled", Map.of());
      return Map.of("ok", true);
    }
    if (action.equals("status") && u.admin()) {
      String next = Inputs.text(b, "status", 1, 20);
      if (!allowed(status, next)) throw new ApiError(409, "Invalid order status transition.");
      if (next.equals("paid")) {
        String reference = Inputs.text(b, "paymentRef", 4, 120);
        db.update("UPDATE orders SET payment_ref=? WHERE id=?", reference, id);
      }
      if (next.equals("shipped")) {
        String carrier = Inputs.text(b, "carrier", 1, 100),
            tracking = Inputs.text(b, "tracking", 1, 1000);
        try {
          var uri = URI.create(tracking);
          if (!"https".equals(uri.getScheme())
              || uri.getHost() == null
              || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (Exception e) {
          throw new ApiError(400, "Use a valid HTTPS tracking URL.");
        }
        db.update("UPDATE orders SET carrier=?,tracking=? WHERE id=?", carrier, tracking, id);
      }
      db.update("UPDATE orders SET status=? WHERE id=?", next, id);
      message(
          id,
          u,
          "Order update: "
              + next
              + (next.equals("paid") ? " — payment manually verified by the maker." : ""));
      event(id, "order." + next, Map.of());
      return Map.of("ok", true);
    }
    throw new ApiError(403, "Action unavailable.");
  }

  private Map<String, Object> create(Map<String, Object> b, Security.User u) {
    String title = Inputs.text(b, "title", 2, 120),
        details = Inputs.text(b, "details", 5, 4000),
        file = Inputs.optional(b, "file", 150);
    if (!file.isEmpty()
        && db.one(
                "SELECT key FROM uploads WHERE key=? AND user_id=? AND catalogue=false",
                file,
                u.id())
            == null) throw new ApiError(400, "Invalid attachment.");
    if (b.containsKey("items")) {
      if (!(b.get("items") instanceof List<?> items) || items.isEmpty() || items.size() > 30)
        throw new ApiError(400, "Invalid shopping bag.");
      var lines = new ArrayList<String>();
      var seen = new HashSet<String>();
      for (var raw : items) {
        if (!(raw instanceof Map<?, ?> item)
            || !(item.get("id") instanceof String pid)
            || !(item.get("qty") instanceof Number qty)
            || qty.doubleValue() != qty.intValue()
            || qty.intValue() < 1
            || qty.intValue() > 100
            || !seen.add(pid)) throw new ApiError(400, "Invalid item quantity.");
        var p = db.one("SELECT * FROM products WHERE id=? AND published=1", pid);
        if (p == null || ((Number) p.get("stock")).intValue() < qty.intValue())
          throw new ApiError(409, "Product unavailable or insufficient stock.");
        lines.add(
            p.get("name")
                + " × "
                + qty
                + " — ₹"
                + String.format(
                    java.util.Locale.ROOT,
                    "%.2f",
                    ((Number) p.get("price")).longValue() * qty.intValue() / 100.0));
      }
      details =
          String.join("\n", lines)
              + "\nAvailability and shipping charges will be confirmed in your quote.";
    }
    var profile =
        db.one(
            "SELECT name,phone,line1,line2,city,state,pincode FROM profiles WHERE user_id=?",
            u.id());
    String id = Db.id();
    db.update(
        "INSERT INTO orders(id,user_id,email,title,details,file,shipping) VALUES (?,?,?,?,?,?,?)",
        id,
        u.id(),
        u.email(),
        title,
        details,
        file.isEmpty() ? null : file,
        profile == null ? null : json.writeValueAsString(profile));
    event(id, "order.requested", Map.of());
    return Map.of("id", id);
  }

  private void message(String id, Security.User u, String text) {
    db.update(
        "INSERT INTO messages(id,order_id,user_id,role,body) VALUES (?,?,?,?,?)",
        Db.id(),
        id,
        u.id(),
        u.admin() ? "maker" : "customer",
        text);
  }

  private void event(String id, String kind, Map<String, Object> data) {
    String eventId = Db.id();
    db.update(
        "INSERT INTO order_events(id,order_id,kind,payload) VALUES (?,?,?,?)",
        eventId,
        id,
        kind,
        json.writeValueAsString(
            Map.of("eventId", eventId, "orderId", id, "kind", kind, "data", data)));
  }

  public static boolean allowed(String current, String next) {
    return switch (current) {
      case "requested", "quoted" -> next.equals("cancelled");
      case "accepted" -> Set.of("paid", "cancelled").contains(next);
      case "paid" -> next.equals("making");
      case "making" -> next.equals("shipped");
      case "shipped" -> next.equals("delivered");
      default -> false;
    };
  }
}
