package com.crochet.studio;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public class ApiError extends RuntimeException {
  final int status;

  public ApiError(int status, String message) {
    super(message);
    this.status = status;
  }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> expected(ApiError e) {
    return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<?> missingResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
    return ResponseEntity.status(404).body(Map.of("error", "Resource not found."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error("Request failed: {}", e.getClass().getSimpleName());
    return ResponseEntity.status(503)
        .body(Map.of("error", "The request could not be completed. Please try again."));
  }
}
