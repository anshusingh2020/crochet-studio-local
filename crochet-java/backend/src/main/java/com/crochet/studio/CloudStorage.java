package com.crochet.studio;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CloudStorage {
  private final Path root;
  private final String url, key, bucket;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public CloudStorage(
      @Value("${studio.uploads}") String root,
      @Value("${SUPABASE_URL:}") String url,
      @Value("${SUPABASE_SERVICE_ROLE_KEY:}") String key,
      @Value("${SUPABASE_BUCKET:crochet-images}") String bucket,
      @Value("${STORAGE_PROVIDER:local}") String provider)
      throws Exception {
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.url = provider.equals("supabase") ? url.replaceAll("/+$", "") : "";
    this.key = key;
    this.bucket = bucket;
    if (!provider.equals("local") && !provider.equals("supabase"))
      throw new IllegalStateException("Invalid storage provider");
    if (provider.equals("supabase")
        && (!this.url.startsWith("https://") || key.isBlank() || !bucket.matches("[a-z0-9-]+")))
      throw new IllegalStateException("Supabase storage configuration is required");
    if (this.url.isEmpty()) Files.createDirectories(this.root);
  }

  private Path local(String object) {
    Path p = root.resolve(object).normalize();
    if (!p.startsWith(root)) throw new ApiError(400, "Invalid image key.");
    return p;
  }

  private HttpResponse<byte[]> request(String method, String object, String mime, byte[] data)
      throws Exception {
    if (!object.matches("(catalogue|[a-f0-9-]{36})/[a-f0-9-]{36}"))
      throw new ApiError(400, "Invalid image key.");
    var request =
        HttpRequest.newBuilder(URI.create(url + "/storage/v1/object/" + bucket + "/" + object))
            .timeout(Duration.ofSeconds(30))
            .header("apikey", key)
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", mime)
            .method(
                method,
                data == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(data))
            .build();
    var result = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (result.statusCode() == 404 && method.equals("GET"))
      throw new ApiError(404, "Image not found.");
    if (result.statusCode() < 200 || result.statusCode() >= 300)
      throw new ApiError(503, "Image storage is temporarily unavailable.");
    return result;
  }

  public void put(String object, String mime, byte[] bytes) throws Exception {
    if (!url.isEmpty()) {
      request("POST", object, mime, bytes);
      return;
    }
    Path p = local(object);
    Files.createDirectories(p.getParent());
    Files.write(p, bytes, StandardOpenOption.CREATE_NEW);
  }

  public byte[] get(String object) throws Exception {
    if (!url.isEmpty()) return request("GET", object, "application/octet-stream", null).body();
    Path p = local(object);
    if (!Files.isRegularFile(p)) throw new ApiError(404, "Image not found.");
    return Files.readAllBytes(p);
  }

  public void delete(String object) throws Exception {
    if (!url.isEmpty()) {
      request("DELETE", object, "application/octet-stream", null);
      return;
    }
    Files.deleteIfExists(local(object));
  }
}
