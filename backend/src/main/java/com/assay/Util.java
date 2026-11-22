package com.assay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

final class Util {
  static final String HAS = "(a.selected_option_id IS NOT NULL OR a.value_text IS NOT NULL OR a.value_number IS NOT NULL OR a.value_date IS NOT NULL OR EXISTS(SELECT 1 FROM answer_options ao WHERE ao.answer_id=a.id))";

  static long insert(JdbcTemplate db, String sql, Object... a) {
    KeyHolder kh = new GeneratedKeyHolder();
    db.update(c -> {
      PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
      return p;
    }, kh);
    return kh.getKey().longValue();
  }
  static long uid(Authentication a) { return Long.parseLong(((Jwt) a.getPrincipal()).getSubject()); }
  static String claim(Authentication a, String k) { return String.valueOf(((Jwt) a.getPrincipal()).getClaims().get(k)); }
  static Map<String,Object> jsonMap(Object v){if(v==null)return null;if(v instanceof Map<?,?>m){Map<String,Object> out=new java.util.LinkedHashMap<>();m.forEach((k,x)->out.put(String.valueOf(k),x));return out;}try{return new com.fasterxml.jackson.databind.ObjectMapper().readValue(v.toString(),Map.class);}catch(Exception e){return null;}}
  static ResponseStatusException err(int s, String m) { return new ResponseStatusException(HttpStatus.valueOf(s), m); }
  static Timestamp ts(String s) { return s == null || s.isBlank() ? null : Timestamp.from(Instant.parse(s)); }
  static String random(int n) { byte[] b = new byte[n]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  static String sha(String s) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  static void own(JdbcTemplate db, long formId, Authentication a) {
    if (db.queryForList("SELECT id FROM forms WHERE id=? AND owner_id=? AND deleted_at IS NULL", formId, uid(a)).isEmpty()) throw err(404, "Form not found");
  }
  static String display(String stored, Timestamp opens, Timestamp exp) {
    if ("CLOSED".equals(stored)) return "CLOSED";
    if ("PUBLISHED".equals(stored)) {
      Instant n = Instant.now();
      if (opens != null && n.isBefore(opens.toInstant())) return "PUBLISHED";
      if (exp != null && n.isAfter(exp.toInstant())) return "EXPIRED";
      return "OPEN";
    }
    return stored;
  }
}
