package com.crochet.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AccessTests {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void signIn(String role) {
    var u = new Security.User("user-1", "user@test.com", "User", role);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
  }

  @Test
  void onlySuperAdminCanReview() {
    var db = mock(Db.class);
    var controller = new AdminController(db, mock(MailService.class), mock(RateLimits.class));
    assertThrows(ApiError.class, () -> controller.list("zzzz"));
    signIn("CUSTOMER");
    assertThrows(ApiError.class, () -> controller.review(Map.of("id", "a", "action", "approve")));
    signIn("ADMIN");
    assertThrows(ApiError.class, () -> controller.list("zzzz"));
    verifyNoInteractions(db);
  }

  @Test
  void customerCannotWriteCatalogue() {
    var db = mock(Db.class);
    signIn("CUSTOMER");
    assertThrows(
        ApiError.class,
        () ->
            new CatalogueController(db, mock(RateLimits.class))
                .save(Map.of("action", "archive", "id", "x")));
    verifyNoInteractions(db);
  }

  @Test
  void customerCannotReadOthersOrders() {
    var db = mock(Db.class);
    signIn("CUSTOMER");
    when(db.one(anyString(), eq("order-1")))
        .thenReturn(Map.of("id", "order-1", "user_id", "someone-else"));
    assertThrows(
        ApiError.class,
        () -> new OrdersController(db, mock(RateLimits.class)).get("order-1", null));
  }

  @Test
  void pendingAdminCannotLogin() {
    var db = mock(Db.class);
    var encoder = new BCryptPasswordEncoder(4);
    String password = "A-long-test-password";
    when(db.one(anyString(), eq("user@test.com")))
        .thenReturn(
            Map.of(
                "id",
                "user-1",
                "email",
                "user@test.com",
                "role",
                "ADMIN",
                "status",
                "pending",
                "password_hash",
                encoder.encode(password)));
    var controller =
        new AuthController(db, mock(RateLimits.class), encoder, mock(MailService.class), false);
    var req = mock(HttpServletRequest.class);
    when(req.getRemoteAddr()).thenReturn("127.0.0.1");
    when(req.getRequestURI()).thenReturn("/api/admin-auth");
    ApiError e =
        assertThrows(
            ApiError.class,
            () ->
                controller.auth(
                    Map.of("action", "login", "email", "user@test.com", "password", password),
                    req));
    assertEquals(403, e.status);
  }

  @Test
  void validTransitionsOnly() {
    assertTrue(OrdersController.allowed("accepted", "paid"));
    assertFalse(OrdersController.allowed("requested", "paid"));
    assertFalse(OrdersController.allowed("paid", "cancelled"));
    assertFalse(OrdersController.allowed("delivered", "making"));
  }

  @Test
  void passwordsAndTokens() {
    var encoder = new BCryptPasswordEncoder(4);
    String password = "unique test password";
    String hash = encoder.encode(password);
    assertNotEquals(hash, password);
    assertTrue(encoder.matches(password, hash));
    assertFalse(encoder.matches("wrong", hash));
    assertNotEquals(Security.token(), Security.token());
    assertEquals(64, Security.hash("token").length());
  }
}
