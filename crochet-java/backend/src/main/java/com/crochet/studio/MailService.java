package com.crochet.studio;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
  private final JavaMailSender sender;
  private final Db db;
  private final String from, origin, owner;

  @Value("${MAIL_PROVIDER:smtp}")
  private String provider;

  @Value("${RESEND_API_KEY:}")
  private String apiKey;

  public MailService(
      JavaMailSender sender,
      Db db,
      @Value("${studio.mail-from}") String from,
      @Value("${studio.origin}") String origin,
      @Value("${studio.super-email}") String owner) {
    this.sender = sender;
    this.db = db;
    this.from = from;
    this.origin = origin;
    this.owner = owner;
  }

  public void send(String to, String subject, String text) {
    if ("resend".equals(provider)) {
      if (apiKey.isBlank()) throw new ApiError(503, "Email delivery has not been configured.");
      var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(10000);
      factory.setReadTimeout(20000);
      org.springframework.web.client.RestClient.builder()
          .requestFactory(factory)
          .build()
          .post()
          .uri("https://api.resend.com/emails")
          .header("Authorization", "Bearer " + apiKey)
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body(Map.of("from", from, "to", java.util.List.of(to), "subject", subject, "text", text))
          .retrieve()
          .toBodilessEntity();
      return;
    }
    if (!"smtp".equals(provider))
      throw new ApiError(503, "Email delivery has not been configured.");
    var m = new SimpleMailMessage();
    m.setFrom(from);
    m.setTo(to);
    m.setSubject(subject);
    m.setText(text);
    sender.send(m);
  }

  public boolean notifyOwner(Map<String, Object> account) {
    try {
      send(
          owner,
          "Crochet Studio: admin access request",
          account.get("name")
              + " ("
              + account.get("email")
              + ") verified their email and requested administrator access.\nReview: "
              + origin
              + "/admin/requests\n"
              + "Sign in as super admin to approve or reject. Opening this link does not approve"
              + " access.");
      db.update("UPDATE users SET owner_notified=true WHERE id=?", account.get("id"));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public void link(String email, String purpose, String value) {
    send(
        email,
        "Crochet Studio: "
            + (purpose.equals("verify") ? "verify your email" : "reset your password"),
        "Open the link and confirm the password you want to use:\n"
            + origin
            + "/login#"
            + purpose
            + "="
            + value
            + "\nThis link expires in one hour. Ignore it if you did not request it.");
  }
}
