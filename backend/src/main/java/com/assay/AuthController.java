package com.assay;

import jakarta.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
  record Req(String name, String email, String password) {}
  final JdbcTemplate db; final PasswordEncoder pe; final JwtEncoder enc;
  final Map<String,Deque<Instant>> failures=new ConcurrentHashMap<>();
  @Value("${app.cookie-secure}") boolean secure;

  AuthController(JdbcTemplate db, PasswordEncoder pe, JwtEncoder enc) { this.db = db; this.pe = pe; this.enc = enc; }

  @PostMapping("/register")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> register(@RequestBody Req r, HttpServletResponse res) {
    if (r.name() == null || r.name().isBlank() || r.email() == null || r.email().isBlank() || r.password() == null || r.password().length() < 8)
      throw Util.err(400, "Name, email and a password of at least 8 characters are required");
    String e = r.email().trim().toLowerCase();
    if (!db.queryForList("SELECT id FROM users WHERE email_normalized=?", e).isEmpty()) throw Util.err(409, "Email already registered");
    long id = Util.insert(db, "INSERT INTO users(name,email,email_normalized,password_hash,role) VALUES(?,?,?,?,'RESPONDENT')", r.name().trim(), r.email().trim(), e, pe.encode(r.password()));
    return session(id, res);
  }

  @PostMapping("/login")
  Map<String, Object> login(@RequestBody Req r, HttpServletResponse res, HttpServletRequest request) {
    String key=(r.email()==null?"":r.email().trim().toLowerCase())+"|"+request.getRemoteAddr();
    Deque<Instant> attempts=failures.computeIfAbsent(key,k->new ConcurrentLinkedDeque<>());Instant cutoff=Instant.now().minus(Duration.ofMinutes(15));while(!attempts.isEmpty()&&attempts.peekFirst().isBefore(cutoff))attempts.pollFirst();
    if(attempts.size()>=5)throw Util.err(429,"Too many login attempts. Try again later.");
    List<Map<String, Object>> rows = db.queryForList("SELECT id,password_hash,status FROM users WHERE email_normalized=?", r.email() == null ? "" : r.email().trim().toLowerCase());
    if (rows.isEmpty() || r.password() == null || !pe.matches(r.password(), (String) rows.get(0).get("password_hash")) || !"ACTIVE".equals(rows.get(0).get("status"))) { attempts.addLast(Instant.now());
      throw Util.err(401, "Invalid email or password");
    }
    attempts.clear();failures.remove(key);
    long id = ((Number) rows.get(0).get("id")).longValue();
    db.update("UPDATE users SET last_login_at=? WHERE id=?", Timestamp.from(Instant.now()), id);
    return session(id, res);
  }

  @PostMapping("/refresh")
  Map<String, Object> refresh(@CookieValue(name = "assay_rt", required = false) String raw, @RequestHeader(value="X-Requested-With",required=false) String requested, HttpServletResponse res) {
    if(!"assay".equals(requested))throw Util.err(401,"Invalid refresh request");
    if (raw == null) throw Util.err(401, "No session");
    List<Map<String, Object>> rows = db.queryForList("SELECT id,user_id FROM refresh_tokens WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?", Util.sha(raw), Timestamp.from(Instant.now()));
    if (rows.isEmpty()) throw Util.err(401, "Session expired");
    if(db.update("UPDATE refresh_tokens SET revoked_at=? WHERE id=? AND revoked_at IS NULL", Timestamp.from(Instant.now()), rows.get(0).get("id"))!=1)throw Util.err(401,"Session expired");
    return session(((Number) rows.get(0).get("user_id")).longValue(), res);
  }

  @PostMapping("/logout")
  void logout(@CookieValue(name = "assay_rt", required = false) String raw, HttpServletResponse res) {
    if (raw != null) db.update("UPDATE refresh_tokens SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL", Timestamp.from(Instant.now()), Util.sha(raw));
    res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("assay_rt", "").httpOnly(true).secure(secure).sameSite("Lax").path("/api/auth").maxAge(0).build().toString());
    res.setStatus(204);
  }

  @GetMapping("/me")
  Map<String, Object> me(Authentication a) { return db.queryForMap("SELECT id,name,email,role FROM users WHERE id=?", Util.uid(a)); }

  private Map<String, Object> session(long uid, HttpServletResponse res) {
    Map<String, Object> u = db.queryForMap("SELECT id,name,email,role FROM users WHERE id=?", uid);
    String raw = Util.random(32);
    db.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES(?,?,?)", uid, Util.sha(raw), Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
    res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("assay_rt", raw).httpOnly(true).secure(secure).sameSite("Lax").path("/api/auth").maxAge(Duration.ofDays(7)).build().toString());
    Instant now = Instant.now();
    JwtClaimsSet c = JwtClaimsSet.builder().subject(String.valueOf(uid)).claim("role", u.get("role")).claim("email", u.get("email")).issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(15))).build();
    String token = enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), c)).getTokenValue();
    return Map.of("accessToken", token, "user", u);
  }
}
