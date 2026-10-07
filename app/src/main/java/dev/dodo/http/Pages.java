package dev.dodo.http;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Pages {
  private final JdbcTemplate jdbc;

  public Pages(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // SQL identifiers/predicates are supplied only by application code, never from request strings.
  public Map<String, Object> list(
      String table,
      String columns,
      UUID business,
      String scope,
      String extra,
      List<Object> extraArgs,
      int limit,
      String cursor) {
    if (limit < 1 || limit > 100) throw ApiError.invalid("limit must be between 1 and 100.");
    var args = new ArrayList<Object>();
    args.add(business);
    args.addAll(extraArgs);
    String where = " WHERE business_id=? " + extra;
    if (cursor != null) {
      try {
        var parts =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (parts.length != 4 || !parts[0].equals(business.toString()) || !parts[1].equals(scope))
          throw new IllegalArgumentException();
        where += " AND (created_at,id)<(?,?)";
        args.add(java.sql.Timestamp.from(Instant.parse(parts[2])));
        args.add(UUID.fromString(parts[3]));
      } catch (Exception e) {
        throw new ApiError(400, "invalid_cursor", "Cursor does not match this list.");
      }
    }
    args.add(limit + 1);
    var rows =
        jdbc.queryForList(
            "SELECT "
                + columns
                + " FROM "
                + table
                + where
                + " ORDER BY created_at DESC,id DESC LIMIT ?",
            args.toArray());
    boolean more = rows.size() > limit;
    if (more) rows.remove(rows.size() - 1);
    String next = null;
    if (more) {
      var last = rows.get(rows.size() - 1);
      String raw =
          business
              + "|"
              + scope
              + "|"
              + ((java.sql.Timestamp) last.get("created_at")).toInstant()
              + "|"
              + last.get("id");
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("data", rows.stream().map(Rows::clean).toList());
    result.put("next_cursor", next);
    return result;
  }
}
