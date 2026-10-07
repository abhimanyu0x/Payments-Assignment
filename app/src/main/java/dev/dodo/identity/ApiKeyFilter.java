package dev.dodo.identity;

import dev.dodo.http.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyFilter extends OncePerRequestFilter {
  private final JdbcTemplate jdbc;
  private final Json json;

  public ApiKeyFilter(JdbcTemplate jdbc, Json json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    req.setAttribute("requestId", requestId);
    res.setHeader("X-Request-Id", requestId);
    if (!req.getRequestURI().startsWith("/api/")) {
      chain.doFilter(req, res);
      return;
    }
    if (req.getContentLengthLong() > 65536) {
      reject(res, 413, "request_too_large", "Request exceeds 64 KiB.", requestId);
      return;
    }
    String header = req.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      reject(res, 401, "unauthorized", "A valid API key is required.", requestId);
      return;
    }
    String[] key = header.substring(7).split("\\.", 2);
    try {
      var rows =
          key.length == 2 && key[0].length() <= 64
              ? jdbc.queryForList(
                  "SELECT business_id,secret_hash FROM identity.api_keys WHERE key_prefix=? AND"
                      + " revoked_at IS NULL",
                  key[0])
              : List.<Map<String, Object>>of();
      if (rows.isEmpty()
          || !MessageDigest.isEqual(
              rows.get(0).get("secret_hash").toString().getBytes(StandardCharsets.US_ASCII),
              KeyHash.of(key[1]).getBytes(StandardCharsets.US_ASCII))) {
        reject(res, 401, "unauthorized", "A valid API key is required.", requestId);
        return;
      }
      // Also bound chunked requests, not only Content-Length requests.
      byte[] body = req.getInputStream().readNBytes(65537);
      if (body.length > 65536) {
        reject(res, 413, "request_too_large", "Request exceeds 64 KiB.", requestId);
        return;
      }
      req.setAttribute("businessId", rows.get(0).get("business_id"));
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  rows.get(0).get("business_id"), null, List.of()));
      var wrapped =
          new HttpServletRequestWrapper(req) {
            @Override
            public ServletInputStream getInputStream() {
              var in = new ByteArrayInputStream(body);
              return new ServletInputStream() {
                public int read() {
                  return in.read();
                }

                public boolean isFinished() {
                  return in.available() == 0;
                }

                public boolean isReady() {
                  return true;
                }

                public void setReadListener(ReadListener l) {
                  throw new UnsupportedOperationException();
                }
              };
            }
          };
      chain.doFilter(wrapped, res);
    } catch (org.springframework.dao.DataAccessException e) {
      reject(
          res,
          503,
          "temporarily_unavailable",
          "Authentication is temporarily unavailable.",
          requestId);
    } finally {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .info(
              "request_id={} business_id={} method={} path={} status={}",
              requestId,
              req.getAttribute("businessId"),
              req.getMethod(),
              req.getRequestURI(),
              res.getStatus());
      SecurityContextHolder.clearContext();
    }
  }

  private void reject(HttpServletResponse r, int status, String code, String message, String id)
      throws IOException {
    r.setStatus(status);
    r.setContentType("application/json");
    r.getWriter().write(json.write(Errors.body(code, message, id)));
  }
}
