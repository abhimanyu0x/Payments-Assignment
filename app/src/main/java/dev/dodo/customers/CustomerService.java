package dev.dodo.customers;

import dev.dodo.http.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {
  private final JdbcTemplate jdbc;

  public CustomerService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> create(UUID business, String name, String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO customers.customers(id,business_id,name,email) VALUES (?,?,?,?)",
        id,
        business,
        name.strip(),
        email.strip());
    return get(business, id);
  }

  public Map<String, Object> get(UUID business, UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT id,name,email,created_at FROM customers.customers WHERE business_id=? AND id=?",
            business,
            id);
    if (rows.isEmpty()) throw ApiError.missing();
    return Rows.clean(rows.get(0));
  }

  public void require(UUID business, UUID id) {
    get(business, id);
  }
}
