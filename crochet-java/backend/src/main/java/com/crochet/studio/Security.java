package com.crochet.studio;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class Security {
  public record User(String id, String email, String name, String role) {
    public boolean admin() {
      return role.equals("ADMIN") || owner();
    }

    public boolean owner() {
      return role.equals("SUPER_ADMIN");
    }
  }

  public static User optional() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.getPrincipal() instanceof User u ? u : null;
  }

  public static User user() {
    var u = optional();
    if (u == null) throw new ApiError(401, "Sign in required.");
    return u;
  }

  public static User admin() {
    var u = user();
    if (!u.admin()) throw new ApiError(403, "Admin access required.");
    return u;
  }

  public static User owner() {
    var u = user();
    if (!u.owner()) throw new ApiError(403, "Only the super admin can perform this action.");
    return u;
  }

  public static String token() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return HexFormat.of().formatHex(b);
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String session(HttpServletRequest req) {
    var cookies = req.getCookies();
    if (cookies != null)
      for (var c : cookies) if (c.getName().equals("STUDIO_SESSION")) return c.getValue();
    return "";
  }

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, Db db, @Value("${studio.origin}") String origin)
      throws Exception {
    // JSON/multipart mutations require the exact configured Origin. No credentialed CORS is
    // enabled.
    var filter =
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            res.setHeader("Cache-Control", "no-store");
            res.setHeader("X-Content-Type-Options", "nosniff");
            if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
                && !origin.equals(req.getHeader("Origin"))) {
              deny(res, 403, "Invalid origin");
              return;
            }
            if (req.getContentType() != null
                && req.getContentType().startsWith("application/json")
                && (req.getContentLengthLong() < 0 || req.getContentLengthLong() > 16000)) {
              deny(res, 413, "Request body is too large or has no length");
              return;
            }
            String token = session(req);
            if (token.matches("[a-f0-9]{64}")) {
              var row =
                  db.one(
                      "SELECT u.id,u.email,u.name,u.role FROM sessions s JOIN users u ON"
                          + " u.id=s.user_id WHERE s.token_hash=? AND s.expires>now() AND"
                          + " u.status='approved'",
                      hash(token));
              if (row != null) {
                var u =
                    new User(
                        (String) row.get("id"),
                        (String) row.get("email"),
                        (String) row.get("name"),
                        (String) row.get("role"));
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                            u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role()))));
              }
            }
            chain.doFilter(req, res);
          }
        };
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static void deny(HttpServletResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
