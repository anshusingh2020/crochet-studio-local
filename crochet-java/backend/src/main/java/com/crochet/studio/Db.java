package com.crochet.studio;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Db {
  final JdbcTemplate jdbc;

  public Db(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = rows(sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public static Map<String, Object> api(Map<String, Object> row) {
    if (row == null) return null;
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (k, v) -> {
          StringBuilder key = new StringBuilder();
          boolean upper = false;
          for (char c : k.toCharArray()) {
            if (c == '_') {
              upper = true;
              continue;
            }
            key.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
          }
          result.put(
              key.toString(), v instanceof java.sql.Timestamp t ? t.toInstant().toString() : v);
        });
    return result;
  }

  public static List<Map<String, Object>> api(List<Map<String, Object>> rows) {
    return rows.stream().map(Db::api).toList();
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }
}
