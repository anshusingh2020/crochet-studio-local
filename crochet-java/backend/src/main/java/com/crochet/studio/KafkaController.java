package com.crochet.studio;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class KafkaController {
  private final Db db;
  private final KafkaOutbox outbox;

  public KafkaController(Db db, KafkaOutbox outbox) {
    this.db = db;
    this.outbox = outbox;
  }

  @GetMapping("/api/integrations/kafka")
  public Map<String, Object> get() {
    Security.admin();
    return Map.of(
        "configured",
        outbox.enabled(),
        "counts",
        db.rows(
            "SELECT delivery_status AS status,count(*) AS count FROM order_events GROUP BY"
                + " delivery_status"),
        "events",
        Db.api(
            db.rows(
                "SELECT id,order_id,kind,created,delivery_status,attempts FROM order_events ORDER"
                    + " BY created DESC LIMIT 30")));
  }

  @PostMapping("/api/integrations/kafka")
  public Map<String, Object> publish() {
    Security.admin();
    if (!outbox.enabled())
      throw new ApiError(503, "Kafka is disabled. Events remain saved in PostgreSQL.");
    outbox.publish();
    return Map.of(
        "ok", true, "message", "Publish batch completed. Refresh status for delivery results.");
  }

  @PostMapping("/api/integrations/kafka/retry")
  public Map<String, Object> retry() {
    Security.admin();
    db.update(
        "UPDATE order_events SET delivery_status='pending',attempts=0,next_attempt=now() WHERE"
            + " delivery_status='dead'");
    return Map.of("ok", true);
  }

  @GetMapping("/api/health")
  public Map<String, Object> health() {
    db.one("SELECT 1 AS healthy");
    return Map.of("status", "UP");
  }
}
