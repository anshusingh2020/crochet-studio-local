package com.crochet.studio;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogueController {
  private final Db db;
  private final RateLimits limits;

  public CatalogueController(Db db, RateLimits limits) {
    this.db = db;
    this.limits = limits;
  }

  @GetMapping("/api/catalogue")
  public Map<String, Object> get(@RequestParam(defaultValue = "0") int all) {
    if (all == 1) Security.admin();
    return Map.of(
        "products",
        Db.api(
            db.rows(
                "SELECT * FROM products "
                    + (all == 1 ? "" : "WHERE published=1 ")
                    + "ORDER BY created DESC LIMIT 200")));
  }

  @PostMapping("/api/catalogue")
  public Map<String, Object> save(@RequestBody Map<String, Object> b) {
    var u = Security.admin();
    limits.check("catalogue", u.id(), 40);
    if ("archive".equals(b.get("action"))) {
      db.update("UPDATE products SET published=0 WHERE id=?", Inputs.text(b, "id", 36, 36));
      return Map.of("ok", true);
    }
    String name = Inputs.text(b, "name", 2, 120),
        description = Inputs.text(b, "description", 0, 3000),
        category = Inputs.text(b, "category", 1, 30),
        colour = Inputs.text(b, "colour", 0, 120),
        image = Inputs.optional(b, "image", 150);
    if (!Set.of("Bags", "Accessories", "Home", "Gifts", "Clothing").contains(category))
      throw new ApiError(400, "Invalid category.");
    if (!image.isEmpty()
        && (!image.startsWith("catalogue/")
            || db.one("SELECT key FROM uploads WHERE key=? AND catalogue=true", image) == null))
      throw new ApiError(400, "Invalid product image.");
    int price = Inputs.number(b, "price", 100, 10000000),
        stock = Inputs.number(b, "stock", 0, 10000),
        published = Inputs.number(b, "published", 0, 1);
    if (b.get("id") != null) {
      int changed =
          db.update(
              "UPDATE products SET"
                  + " name=?,description=?,category=?,price=?,stock=?,image=?,colour=?,published=?"
                  + " WHERE id=?",
              name,
              description,
              category,
              price,
              stock,
              image.isEmpty() ? null : image,
              colour,
              published,
              Inputs.text(b, "id", 36, 36));
      if (changed == 0) throw new ApiError(404, "Product not found.");
    } else
      db.update(
          "INSERT INTO products(id,name,description,category,price,stock,image,colour,published)"
              + " VALUES (?,?,?,?,?,?,?,?,?)",
          Db.id(),
          name,
          description,
          category,
          price,
          stock,
          image.isEmpty() ? null : image,
          colour,
          published);
    return Map.of("ok", true);
  }
}
