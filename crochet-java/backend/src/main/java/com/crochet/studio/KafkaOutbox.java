package com.crochet.studio;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KafkaOutbox {
  private final Db db;
  private final KafkaTemplate<String, String> kafka;
  private final boolean enabled;
  private final String topic;

  public KafkaOutbox(
      Db db,
      KafkaTemplate<String, String> kafka,
      @Value("${studio.kafka-enabled}") boolean enabled,
      @Value("${studio.kafka-topic}") String topic) {
    this.db = db;
    this.kafka = kafka;
    this.enabled = enabled;
    this.topic = topic;
  }

  public boolean enabled() {
    return enabled;
  }

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void publish() {
    if (!enabled) return;
    var rows =
        db.rows(
            "SELECT e.* FROM order_events e WHERE e.delivery_status='pending' AND"
                + " e.next_attempt<=now() AND NOT EXISTS (SELECT 1 FROM order_events earlier WHERE"
                + " earlier.order_id=e.order_id AND earlier.delivery_status<>'published' AND"
                + " (earlier.created,earlier.id)<(e.created,e.id)) ORDER BY e.created,e.id LIMIT 5"
                + " FOR UPDATE OF e SKIP LOCKED");
    for (var row : rows) {
      try {
        kafka
            .send(topic, (String) row.get("order_id"), (String) row.get("payload"))
            .get(12, TimeUnit.SECONDS);
        db.update(
            "UPDATE order_events SET delivery_status='published',attempts=attempts+1 WHERE id=?",
            row.get("id"));
      } catch (Exception e) {
        int attempt = ((Number) row.get("attempts")).intValue() + 1;
        db.update(
            "UPDATE order_events SET"
                + " attempts=?,delivery_status=?,next_attempt=now()+make_interval(secs=>?) WHERE"
                + " id=?",
            attempt,
            attempt >= 10 ? "dead" : "pending",
            Math.min(3600, 1 << Math.min(attempt, 11)),
            row.get("id"));
      }
    }
  }
}
