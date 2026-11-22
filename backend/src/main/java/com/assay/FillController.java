package com.assay;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FillController {
  final JdbcTemplate db;
  FillController(JdbcTemplate db) { this.db = db; }

  Map<String, Object> formByToken(String token) {
    List<Map<String, Object>> r = db.queryForList("SELECT f.id,f.title,f.description,f.status,f.published_version_id AS vid,v.opens_at,v.expires_at FROM forms f JOIN form_versions v ON v.id=f.published_version_id WHERE f.share_token=? AND f.link_status='ACTIVE' AND f.deleted_at IS NULL", token);
    if (r.isEmpty()) throw Util.err(404, "This link is not valid");
    return r.get(0);
  }

  String status(Map<String, Object> f) {
    String s = Util.display((String) f.get("status"), (Timestamp) f.get("opens_at"), (Timestamp) f.get("expires_at"));
    return s.equals("PUBLISHED") ? "NOT_OPEN" : s;
  }

  @GetMapping("/public/forms/{token}")
  Map<String, Object> info(@PathVariable String token) {
    Map<String, Object> f = formByToken(token);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", f.get("title")); m.put("description", f.get("description")); m.put("displayStatus", status(f));
    m.put("opensAt", f.get("opens_at")); m.put("expiresAt", f.get("expires_at"));
    Integer assigned=db.queryForObject("SELECT COUNT(*) FROM form_assignments WHERE form_id=?",Integer.class,f.get("id"));m.put("requiresInvite",assigned!=null&&assigned>0);
    return m;
  }

  @PostMapping("/fill/{token}/start")
  Map<String, Object> start(@PathVariable String token, Authentication a) {
    Map<String, Object> f = formByToken(token);
    String s = status(f);
    if (s.equals("EXPIRED") || s.equals("CLOSED")) throw Util.err(410, "This form is closed.");
    if (s.equals("NOT_OPEN")) throw Util.err(403, "This form is not open yet.");
    long fid = ((Number) f.get("id")).longValue(), uid = Util.uid(a);
    Integer inv = db.queryForObject("SELECT COUNT(*) FROM form_assignments WHERE form_id=?", Integer.class, fid);
    if (inv != null && inv > 0 && db.queryForList("SELECT id FROM form_assignments WHERE form_id=? AND email=?", fid, Util.claim(a, "email").toLowerCase()).isEmpty())
      throw Util.err(403, "You are not invited to this form.");
    List<Map<String, Object>> ex = db.queryForList("SELECT id,status FROM submissions WHERE form_id=? AND user_id=?", fid, uid);
    if (ex.isEmpty()) {
      try {
        long id = Util.insert(db, "INSERT INTO submissions(form_id,version_id,user_id,status) VALUES(?,?,?,'DRAFT')", fid, f.get("vid"), uid);
        db.update("INSERT INTO submission_events(submission_id,event_type,actor_id) VALUES(?,'CREATED',?)", id, uid);
        return Map.of("submissionId", id, "status", "DRAFT");
      } catch (DuplicateKeyException e) { ex = db.queryForList("SELECT id,status FROM submissions WHERE form_id=? AND user_id=?", fid, uid); }
    }
    return Map.of("submissionId", ex.get(0).get("id"), "status", ex.get(0).get("status"));
  }

  Map<String, Object> mine(long id, Authentication a) {
    List<Map<String, Object>> r = db.queryForList("SELECT s.id,s.form_id,s.version_id,s.status,s.row_version,f.title,f.status AS fstatus,v.version_no,v.expires_at,v.opens_at,v.settings_json FROM submissions s JOIN forms f ON f.id=s.form_id JOIN form_versions v ON v.id=s.version_id WHERE s.id=? AND s.user_id=?", id, Util.uid(a));
    if (r.isEmpty()) throw Util.err(404, "Not found");
    return r.get(0);
  }

  boolean closed(Map<String, Object> s) {
    Timestamp e = (Timestamp) s.get("expires_at");
    if("CLOSED".equals(s.get("fstatus")) || (e != null && e.toInstant().isBefore(Instant.now())))return true;
    if("SUBMITTED".equals(s.get("status"))){Map<String,Object> settings=Util.jsonMap(s.get("settings_json"));return settings!=null&&Boolean.FALSE.equals(settings.get("allowEditAfterSubmit"));}
    return false;
  }

  @GetMapping("/submissions/{id}")
  Map<String, Object> get(@PathVariable long id, Authentication a) {
    Map<String, Object> s = mine(id, a);
    long vid = ((Number) s.get("version_id")).longValue();
    List<Map<String, Object>> secs = db.queryForList("SELECT id,code,name FROM sections WHERE version_id=? ORDER BY sort_order", vid);
    List<Map<String, Object>> qs = db.queryForList("SELECT q.id,q.external_id AS externalId,q.section_id AS sectionId,q.dimension_id AS dimensionId,d.name AS dimensionName,q.question_text AS text,q.question_type AS type,q.help_text AS helpText,q.mandatory,q.validation_json AS validation,q.config_json AS config FROM questions q JOIN dimensions d ON d.id=q.dimension_id WHERE q.version_id=? ORDER BY q.sort_order", vid);
    List<Map<String, Object>> os = db.queryForList("SELECT o.id,o.question_id AS qid,o.label,o.is_na AS isNa FROM question_options o JOIN questions q ON q.id=o.question_id WHERE q.version_id=? ORDER BY o.sort_order", vid);
    List<Map<String, Object>> ans = db.queryForList("SELECT * FROM answers WHERE submission_id=?", id);
    List<Map<String, Object>> ao = db.queryForList("SELECT ao.answer_id,ao.option_id FROM answer_options ao JOIN answers a ON a.id=ao.answer_id WHERE a.submission_id=? ORDER BY ao.rank_no", id);
    Map<Object, Map<String, Object>> byQ = new HashMap<>();
    for (Map<String, Object> a1 : ans) {
      List<Object> ids = new ArrayList<>();
      if (a1.get("selected_option_id") != null) ids.add(a1.get("selected_option_id"));
      for (Map<String, Object> x : ao) if (x.get("answer_id").equals(a1.get("id"))) ids.add(x.get("option_id"));
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("questionId", a1.get("question_id")); m.put("state", "DRAFT".equals(a1.get("state")) ? "ANSWERED" : a1.get("state")); m.put("optionIds", ids);
      m.put("text", a1.get("value_text")); m.put("number", a1.get("value_number")); m.put("date", a1.get("value_date") == null ? null : a1.get("value_date").toString()); m.put("remarks", a1.get("remarks"));
      byQ.put(a1.get("question_id"), m);
    }
    for (Map<String, Object> q : qs) {
      q.put("validation",Util.jsonMap(q.get("validation")));q.put("config",Util.jsonMap(q.get("config")));
      q.put("options", os.stream().filter(o -> o.get("qid").equals(q.get("id"))).toList());
      q.put("answer", byQ.get(q.get("id")));
    }
    for (Map<String, Object> sec : secs) sec.put("questions", qs.stream().filter(q -> q.get("sectionId").equals(sec.get("id"))).toList());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id); m.put("formTitle", s.get("title")); m.put("status", s.get("status")); m.put("rowVersion", s.get("row_version"));
    Map<String,Object> settings=Util.jsonMap(s.get("settings_json"));
    m.put("formId",s.get("form_id"));m.put("versionNo", s.get("version_no")); m.put("expiresAt", s.get("expires_at")); m.put("readOnly", closed(s));m.put("allowEditAfterSubmit",settings==null||!Boolean.FALSE.equals(settings.get("allowEditAfterSubmit"))); m.put("sections", secs);
    return m;
  }

  @PutMapping("/submissions/{id}/answers")
  @Transactional
  public Map<String, Object> save(@PathVariable long id, @RequestBody Map<String, List<Map<String, Object>>> body, @RequestHeader(value = "If-Match", required = false) String im, Authentication a) {
    Map<String, Object> s = mine(id, a);
    if (closed(s)) throw Util.err(410, "This form is closed.");
    if (im != null && !im.replace("\"", "").equals(String.valueOf(s.get("row_version")))) throw Util.err(409, "Updated elsewhere");
    long vid = ((Number) s.get("version_id")).longValue();
    for (Map<String, Object> m : body.getOrDefault("answers", List.of())) {
      long qid = ((Number) m.get("questionId")).longValue();
      List<Map<String, Object>> q = db.queryForList("SELECT question_type,validation_json,config_json FROM questions WHERE id=? AND version_id=?", qid, vid);
      if (q.isEmpty()) throw Util.err(400, "Unknown question " + qid);
      String type = (String) q.get(0).get("question_type");Map<String,Object> validation=Util.jsonMap(q.get(0).get("validation_json")),config=Util.jsonMap(q.get(0).get("config_json"));
      List<?> ids = m.get("optionIds") instanceof List<?> l ? l : List.of();
      String text = m.get("text") == null || m.get("text").toString().isBlank() ? null : m.get("text").toString();
      BigDecimal num;Date date;try{num=m.get("number")==null||m.get("number").toString().isBlank()?null:new BigDecimal(m.get("number").toString());date=m.get("date")==null||m.get("date").toString().isBlank()?null:Date.valueOf(m.get("date").toString());}catch(Exception e){throw Util.err(400,"Invalid number or date value");}
      String remarks = m.get("remarks") == null || m.get("remarks").toString().isBlank() ? null : m.get("remarks").toString();
      String state = m.get("state") == null ? "ANSWERED" : m.get("state").toString();
      boolean multi = type.equals("MULTI") || type.equals("RANKING");
      if(!Set.of("ANSWERED","REVIEW").contains(state))throw Util.err(400,"Invalid answer state");
      if(!multi&&ids.size()>1)throw Util.err(400,"Only one option may be selected");
      if(!List.of("SINGLE","MULTI","DROPDOWN","YES_NO","LIKERT","RANKING").contains(type)&&!ids.isEmpty())throw Util.err(400,"This question does not accept options");
      Set<Long> unique=new HashSet<>();for(Object raw:ids){if(!(raw instanceof Number n)||!unique.add(n.longValue()))throw Util.err(400,"Invalid or duplicate option");}
      if(!ids.isEmpty()){List<Long> valid=db.queryForList("SELECT id FROM question_options WHERE question_id=?",Long.class,qid);Set<Long> allowed=new HashSet<>(valid);for(Object raw:ids)if(!allowed.contains(((Number)raw).longValue()))throw Util.err(400,"Option does not belong to question");}
      validateAnswer(type,validation,config,text,num,date,remarks,(!ids.isEmpty()||text!=null||num!=null||date!=null));
      db.update("DELETE FROM answers WHERE submission_id=? AND question_id=?", id, qid);
      boolean value = !ids.isEmpty() || text != null || num != null || date != null;
      if (!value && remarks == null && state.equals("ANSWERED")) continue;
      Object sel = !multi && !ids.isEmpty() ? ((Number) ids.get(0)).longValue() : null;
      long aid = Util.insert(db, "INSERT INTO answers(submission_id,question_id,value_text,value_number,value_date,selected_option_id,remarks,state) VALUES(?,?,?,?,?,?,?,?)", id, qid, text, num, date, sel, remarks, state);
      if (multi) for (int i = 0; i < ids.size(); i++) db.update("INSERT INTO answer_options(answer_id,option_id,rank_no) VALUES(?,?,?)", aid, ((Number) ids.get(i)).longValue(), i + 1);
    }
    int updated=db.update("UPDATE submissions SET row_version=row_version+1,last_saved_at=? WHERE id=? AND row_version=?", Timestamp.from(Instant.now()), id, s.get("row_version"));if(updated!=1)throw Util.err(409,"Updated elsewhere");
    Integer done = db.queryForObject("SELECT COUNT(*) FROM answers a WHERE a.submission_id=? AND " + Util.HAS, Integer.class, id);
    Integer total = db.queryForObject("SELECT COUNT(*) FROM questions WHERE version_id=?", Integer.class, vid);
    return Map.of("rowVersion", db.queryForObject("SELECT row_version FROM submissions WHERE id=?", Integer.class, id), "answeredCount", done, "totalCount", total);
  }

  private void validateAnswer(String type,Map<String,Object> v,Map<String,Object> c,String text,BigDecimal n,Date d,String remarks,boolean hasValue){
    if(hasValue&&v!=null&&Boolean.TRUE.equals(v.get("requireComment"))&&(remarks==null||remarks.isBlank()))throw Util.err(400,"A comment is required");
    if(text!=null&&!List.of("SHORT","LONG","EMAIL","TIME","NUMBER").contains(type))throw Util.err(400,"This question does not accept text");
    if(text!=null&&List.of("SHORT","LONG","EMAIL").contains(type)){int len=text.length();if(v!=null&&v.get("minLength") instanceof Number min&&len<min.intValue())throw Util.err(400,"Answer is shorter than allowed");if(v!=null&&v.get("maxLength") instanceof Number max&&len>max.intValue())throw Util.err(400,"Answer is longer than allowed");}
    if(text!=null&&type.equals("EMAIL")&&!text.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw Util.err(400,"Enter a valid email address");
    if(text!=null&&type.equals("TIME")&&!text.matches("^(?:[01]\\d|2[0-3]):[0-5]\\d$"))throw Util.err(400,"Enter a valid time");
    if(text!=null&&type.equals("NUMBER")&&text.equals("DONT_KNOW")&&(c==null||!Boolean.TRUE.equals(c.get("allowDontKnow"))))throw Util.err(400,"Don't know is not allowed");
    if(n!=null){if(Set.of("SCALE","RATING","NPS","NUMBER").contains(type)){double min=type.equals("NPS")?0:type.equals("SCALE")&&c!=null&&c.get("min") instanceof Number x?x.doubleValue():1;double max=type.equals("NPS")?10:type.equals("SCALE")&&c!=null&&c.get("max") instanceof Number x?x.doubleValue():type.equals("NUMBER")?Double.POSITIVE_INFINITY:5;if(type.equals("NUMBER")&&v!=null&&v.get("min") instanceof Number x)min=x.doubleValue();if(type.equals("NUMBER")&&v!=null&&v.get("max") instanceof Number x)max=x.doubleValue();if(n.doubleValue()<min||n.doubleValue()>max)throw Util.err(400,"Number is outside the allowed range");if(type.equals("NUMBER")&&v!=null&&Boolean.TRUE.equals(v.get("integerOnly"))&&n.stripTrailingZeros().scale()>0)throw Util.err(400,"Enter a whole number");}}
    if(d!=null&&type.equals("DATE")&&v!=null){if(v.get("minDate")!=null&&d.toString().compareTo(v.get("minDate").toString())<0)throw Util.err(400,"Date is earlier than allowed");if(v.get("maxDate")!=null&&d.toString().compareTo(v.get("maxDate").toString())>0)throw Util.err(400,"Date is later than allowed");}
    if(n!=null&&!Set.of("NUMBER","SCALE","RATING","NPS").contains(type))throw Util.err(400,"This question does not accept a number");
    if(d!=null&&!type.equals("DATE"))throw Util.err(400,"This question does not accept a date");
  }

  @PostMapping("/submissions/{id}/submit")
  ResponseEntity<Map<String, Object>> submit(@PathVariable long id, Authentication a) {
    Map<String, Object> s = mine(id, a);
    if (closed(s)) throw Util.err(410, "This form is closed.");
    List<Map<String, Object>> missing = db.queryForList("SELECT q.id AS questionId,q.external_id AS externalId,'REQUIRED' AS reason FROM questions q WHERE q.version_id=? AND q.mandatory=1 AND NOT EXISTS(SELECT 1 FROM answers a WHERE a.submission_id=? AND a.question_id=q.id AND " + Util.HAS + ") ORDER BY q.sort_order", s.get("version_id"), id);
    if (!missing.isEmpty()) return ResponseEntity.status(422).body(Map.of("status", 422, "message", "Required answers missing", "fieldErrors", List.of(), "missing", missing));
    Timestamp now = Timestamp.from(Instant.now());
    db.update("UPDATE submissions SET status='SUBMITTED',submitted_at=COALESCE(submitted_at,?) WHERE id=?", now, id);
    db.update("INSERT INTO submission_events(submission_id,event_type,actor_id) VALUES(?,'SUBMITTED',?)", id, Util.uid(a));
    return ResponseEntity.ok(Map.of("status", "SUBMITTED", "submittedAt", now));
  }
}
