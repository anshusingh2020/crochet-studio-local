package com.crochet.studio;

import java.nio.file.*;
import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FilesController {
  private final Db db;
  private final RateLimits limits;
  private final CloudStorage storage;

  public FilesController(Db db, RateLimits limits, CloudStorage storage) {
    this.db = db;
    this.limits = limits;
    this.storage = storage;
  }

  @PostMapping("/api/files")
  public Map<String, Object> upload(
      @RequestParam MultipartFile file, @RequestParam(defaultValue = "0") int catalogue)
      throws Exception {
    var u = Security.user();
    if (catalogue == 1) Security.admin();
    limits.check("upload", u.id(), 10);
    if (file.getSize() > 5 * 1024 * 1024 || file.getSize() < 12)
      throw new ApiError(400, "Choose a JPG, PNG or WebP image up to 5 MB.");
    byte[] bytes = file.getBytes();
    String mime = file.getContentType();
    boolean valid =
        switch (mime == null ? "" : mime) {
          case "image/jpeg" -> (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
          case "image/png" ->
              (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
          case "image/webp" ->
              new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                  && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                      .equals("WEBP");
          default -> false;
        };
    if (!valid) throw new ApiError(400, "Invalid image.");
    String key = (catalogue == 1 ? "catalogue" : u.id()) + "/" + Db.id();
    storage.put(key, mime, bytes);
    try {
      db.update(
          "INSERT INTO uploads(key,user_id,mime,catalogue) VALUES (?,?,?,?)",
          key,
          u.id(),
          mime,
          catalogue == 1);
    } catch (Exception e) {
      try {
        storage.delete(key);
      } catch (Exception cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
    return Map.of("key", key);
  }

  @GetMapping("/api/files")
  public ResponseEntity<Resource> get(@RequestParam String key) throws Exception {
    if (!key.matches("(catalogue|[a-f0-9-]{36})/[a-f0-9-]{36}"))
      throw new ApiError(404, "Image not found.");
    var meta = db.one("SELECT * FROM uploads WHERE key=?", key);
    if (meta == null) throw new ApiError(404, "Image not found.");
    boolean published =
        db.one("SELECT id FROM products WHERE image=? AND published=1 LIMIT 1", key) != null;
    if (!published) {
      var u = Security.user();
      if (!u.admin() && !u.id().equals(meta.get("user_id")))
        throw new ApiError(404, "Image not found.");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) meta.get("mime")))
        .header("Cache-Control", published ? "public, max-age=300" : "private, no-store")
        .body(new ByteArrayResource(storage.get(key)));
  }
}
