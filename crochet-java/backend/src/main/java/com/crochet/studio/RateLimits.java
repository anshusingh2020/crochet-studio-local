package com.crochet.studio;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RateLimits {
  private final Db db;

  public RateLimits(Db db) {
    this.db = db;
  }

  public void check(String scope, String identity, int max) {
    String key = scope + ":" + Security.hash(identity) + ":" + (System.currentTimeMillis() / 60000);
    var row =
        db.one(
            "INSERT INTO request_limits(key,count,expires) VALUES (?,1,now()+interval '2 minutes')"
                + " ON CONFLICT(key) DO UPDATE SET count=request_limits.count+1 RETURNING count",
            key);
    if (((Number) row.get("count")).intValue() > max)
      throw new ApiError(429, "Too many requests. Please wait a minute.");
  }

  @Scheduled(fixedDelay = 300000)
  public void cleanup() {
    db.update("DELETE FROM request_limits WHERE expires<now()");
    db.update("DELETE FROM sessions WHERE expires<now()");
    db.update("DELETE FROM auth_tokens WHERE expires<now()");
  }
}
