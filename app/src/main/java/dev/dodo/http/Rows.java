package dev.dodo.http;

import java.util.*;

public final class Rows {
  private Rows() {}

  public static Map<String, Object> clean(Map<String, Object> row) {
    var out = new LinkedHashMap<String, Object>();
    row.forEach(
        (k, v) ->
            out.put(
                k,
                v instanceof java.sql.Timestamp t
                    ? t.toInstant().toString()
                    : v instanceof java.sql.Date d ? d.toLocalDate().toString() : v));
    if (row.get("total_amount_cents") instanceof Number n)
      out.put(
          "amount_display", "$" + java.math.BigDecimal.valueOf(n.longValue(), 2).toPlainString());
    if (row.get("amount_cents") instanceof Number n)
      out.put(
          "amount_display", "$" + java.math.BigDecimal.valueOf(n.longValue(), 2).toPlainString());
    if (row.get("unit_amount_cents") instanceof Number n)
      out.put(
          "unit_amount_display",
          "$" + java.math.BigDecimal.valueOf(n.longValue(), 2).toPlainString());
    return out;
  }
}
