package com.assay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/forms")
@Transactional
class FormController {
  final JdbcTemplate db; final Importer imp;
  @Value("${app.frontend-url}") String front;
  FormController(JdbcTemplate db, Importer imp) { this.db = db; this.imp = imp; }

  List<Map<String, Object>> rows(long owner, Long id) {
    String sql = "SELECT f.id,f.title,f.description,f.status AS storedStatus,f.share_token AS shareToken,f.created_at AS createdAt,"
      + "COALESCE(pv.version_no,dv.version_no,1) AS versionNo,pv.expires_at AS expiresAt,pv.opens_at AS opensAt,"
      + "(SELECT COUNT(*) FROM submissions s WHERE s.form_id=f.id) AS responded,"
      + "(SELECT COUNT(*) FROM submissions s WHERE s.form_id=f.id AND s.status='SUBMITTED') AS submitted,"
      + "(SELECT COUNT(*) FROM form_assignments a WHERE a.form_id=f.id) AS assignedCount "
      + "FROM forms f LEFT JOIN form_versions pv ON pv.id=f.published_version_id LEFT JOIN form_versions dv ON dv.form_id=f.id AND dv.status='DRAFT' "
      + "WHERE f.owner_id=? AND f.deleted_at IS NULL" + (id == null ? "" : " AND f.id=?") + " ORDER BY f.created_at DESC";
    List<Map<String, Object>> list = id == null ? db.queryForList(sql, owner) : db.queryForList(sql, owner, id);
    for (Map<String, Object> m : list) {
      m.put("displayStatus", Util.display((String) m.get("storedStatus"), (Timestamp) m.get("opensAt"), (Timestamp) m.get("expiresAt")));
      long ac = ((Number) m.remove("assignedCount")).longValue();
      m.put("assigned", ac == 0 ? null : ac);
    }
    return list;
  }

  @GetMapping("/overview")
  List<Map<String, Object>> overview(Authentication a) { return rows(Util.uid(a), null); }

  @PostMapping
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> create(@RequestBody Map<String, String> b, Authentication a) {
    String t = b.getOrDefault("title", "").trim();
    if (t.isEmpty()) throw Util.err(400, "Title is required");
    long f = Util.insert(db, "INSERT INTO forms(owner_id,title,description,status) VALUES(?,?,?,'DRAFT')", Util.uid(a), t, b.get("description"));
    db.update("INSERT INTO form_versions(form_id,version_no,status,settings_json) VALUES(?,1,'DRAFT',?)", f, Importer.SETTINGS);
    return Map.of("id", f);
  }

  @PutMapping("/{id}") Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,String>b,Authentication a){
    Util.own(db,id,a);String title=required(b,"title");db.update("UPDATE forms SET title=?,description=? WHERE id=?",title,b.get("description"),id);return Map.of("id",id,"title",title,"description",Objects.toString(b.get("description"),""));
  }

  @PostMapping(value = "/import", consumes = "multipart/form-data")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> importCsv(@RequestParam("title") String title, @RequestParam("file") MultipartFile file, Authentication a) throws IOException {
    if (title.isBlank()) throw Util.err(400, "Title is required");
    return imp.run(title.trim(), new String(file.getBytes(), StandardCharsets.UTF_8), Util.uid(a));
  }

  @ExceptionHandler(ImportFailed.class)
  ResponseEntity<Map<String, Object>> importFailed(ImportFailed e) {
    return ResponseEntity.status(422).body(Map.of("status", 422, "message", "Import failed", "fieldErrors", List.of(), "importErrors", e.errors));
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable long id, Authentication a) {
    Util.own(db, id, a);
    db.update("UPDATE forms SET deleted_at=? WHERE id=?", Timestamp.from(Instant.now()), id);
  }

  @GetMapping("/{id}/builder")
  @Transactional
  Map<String, Object> builder(@PathVariable long id, Authentication a) {
    Util.own(db, id, a);
    List<Map<String, Object>> drafts = db.queryForList("SELECT id,version_no FROM form_versions WHERE form_id=? AND status='DRAFT'", id);
    if (drafts.isEmpty()) clonePublished(id);
    Map<String, Object> f = rows(Util.uid(a), id).get(0);
    f.put("link", f.get("shareToken") == null ? null : front + "/f/" + f.get("shareToken"));
    Map<String, Object> v = db.queryForList("SELECT id,version_no AS versionNo,status,opens_at AS opensAt,expires_at AS expiresAt,settings_json AS settings FROM form_versions WHERE form_id=? AND status='DRAFT' ORDER BY version_no DESC LIMIT 1", id).get(0);
    long vid = ((Number) v.get("id")).longValue();
    List<Map<String, Object>> secs = db.queryForList("SELECT id,code,name,sort_order AS sortOrder FROM sections WHERE version_id=? ORDER BY sort_order", vid);
    List<Map<String, Object>> dims = db.queryForList("SELECT id,section_id AS sectionId,name,sort_order AS sortOrder FROM dimensions WHERE section_id IN (SELECT id FROM sections WHERE version_id=?) ORDER BY sort_order", vid);
    List<Map<String, Object>> qs = db.queryForList("SELECT q.id,q.section_id AS sectionId,q.dimension_id AS dimensionId,q.external_id AS externalId,q.question_text AS text,q.question_type AS type,q.mandatory,q.help_text AS helpText,q.sort_order AS sortOrder,q.validation_json AS validation,q.config_json AS config FROM questions q WHERE q.version_id=? ORDER BY q.sort_order", vid);
    List<Map<String, Object>> opts = db.queryForList("SELECT o.id,o.question_id AS questionId,o.label,o.score,o.is_na AS isNa,o.sort_order AS sortOrder FROM question_options o JOIN questions q ON q.id=o.question_id WHERE q.version_id=? ORDER BY o.sort_order", vid);
    for (Map<String, Object> q : qs) {
      q.put("options", opts.stream().filter(o -> o.get("questionId").equals(q.get("id"))).toList());
      q.put("validation",jsonMap(q.get("validation")));q.put("config",jsonMap(q.get("config")));
    }
    for (Map<String, Object> s : secs) {
      s.put("dimensions", dims.stream().filter(d -> d.get("sectionId").equals(s.get("id"))).toList());
      s.put("questions", qs.stream().filter(q -> q.get("sectionId").equals(s.get("id"))).toList());
    }
    try { v.put("settings", new com.fasterxml.jackson.databind.ObjectMapper().readValue(String.valueOf(v.get("settings")), Map.class)); }
    catch (Exception ignored) { v.put("settings", Map.of("target", 3.0, "allowEditAfterSubmit", true)); }
    return Map.of("form", f, "version", v, "sections", secs);
  }

  private long clonePublished(long formId) {
    Map<String,Object> old = db.queryForList("SELECT id,version_no,settings_json,opens_at,expires_at FROM form_versions WHERE form_id=? AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1", formId).stream().findFirst().orElse(null);
    int next = db.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM form_versions WHERE form_id=?", Integer.class, formId);
    long vid = Util.insert(db,"INSERT INTO form_versions(form_id,version_no,status,settings_json) VALUES(?,?,'DRAFT',?)",formId,next, old == null ? Importer.SETTINGS : old.get("settings_json"));
    if (old == null) return vid;
    long oldId=((Number)old.get("id")).longValue(); Map<Long,Long> secIds=new HashMap<>(), dimIds=new HashMap<>(), qIds=new HashMap<>();
    for (Map<String,Object> s:db.queryForList("SELECT * FROM sections WHERE version_id=? ORDER BY sort_order",oldId)) secIds.put(((Number)s.get("id")).longValue(),Util.insert(db,"INSERT INTO sections(version_id,code,name,sort_order) VALUES(?,?,?,?)",vid,s.get("code"),s.get("name"),s.get("sort_order")));
    for (Map<String,Object> d:db.queryForList("SELECT d.* FROM dimensions d JOIN sections s ON s.id=d.section_id WHERE s.version_id=? ORDER BY d.sort_order",oldId)) dimIds.put(((Number)d.get("id")).longValue(),Util.insert(db,"INSERT INTO dimensions(section_id,name,sort_order) VALUES(?,?,?)",secIds.get(((Number)d.get("section_id")).longValue()),d.get("name"),d.get("sort_order")));
    for (Map<String,Object> q:db.queryForList("SELECT * FROM questions WHERE version_id=? ORDER BY sort_order",oldId)) qIds.put(((Number)q.get("id")).longValue(),Util.insert(db,"INSERT INTO questions(version_id,section_id,dimension_id,external_id,question_text,question_type,help_text,mandatory,sort_order,validation_json,config_json) VALUES(?,?,?,?,?,?,?,?,?,?,?)",vid,secIds.get(((Number)q.get("section_id")).longValue()),dimIds.get(((Number)q.get("dimension_id")).longValue()),q.get("external_id"),q.get("question_text"),q.get("question_type"),q.get("help_text"),q.get("mandatory"),q.get("sort_order"),q.get("validation_json"),q.get("config_json")));
    for (Map<String,Object> o:db.queryForList("SELECT o.* FROM question_options o JOIN questions q ON q.id=o.question_id WHERE q.version_id=? ORDER BY o.sort_order",oldId)) Util.insert(db,"INSERT INTO question_options(question_id,label,score,is_na,sort_order) VALUES(?,?,?,?,?)",qIds.get(((Number)o.get("question_id")).longValue()),o.get("label"),o.get("score"),o.get("is_na"),o.get("sort_order"));
    return vid;
  }

  @PostMapping("/{formId}/sections") @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED) Map<String,Object> addSection(@PathVariable long formId,@RequestBody Map<String,String> b,Authentication a) {
    Util.own(db,formId,a); long v=draftId(formId); int n=db.queryForObject("SELECT COUNT(*) FROM sections WHERE version_id=?",Integer.class,v);
    String code=Objects.toString(b.get("code"),"").trim();if(code.isEmpty())code=String.valueOf((char)('A'+(n%26)));
    long id=Util.insert(db,"INSERT INTO sections(version_id,code,name,sort_order) VALUES(?,?,?,?)",v,code,required(b,"name"),n+1);
    return Map.of("id",id,"code",code,"name",b.get("name"),"sortOrder",n+1,"dimensions",List.of(),"questions",List.of());
  }
  @PutMapping("/sections/{id}") Map<String,Object> editSection(@PathVariable long id,@RequestBody Map<String,String>b,Authentication a){ownSection(id,a);db.update("UPDATE sections SET name=?,code=? WHERE id=?",required(b,"name"),b.get("code"),id);return Map.of("id",id,"name",b.get("name"),"code",Objects.toString(b.get("code"),""));}
  @DeleteMapping("/sections/{id}") void delSection(@PathVariable long id,Authentication a){ownSection(id,a);db.update("DELETE FROM sections WHERE id=?",id);}
  @PostMapping("/sections/{sectionId}/dimensions") Map<String,Object> addDim(@PathVariable long sectionId,@RequestBody Map<String,String>b,Authentication a){ownSection(sectionId,a);int n=db.queryForObject("SELECT COUNT(*) FROM dimensions WHERE section_id=?",Integer.class,sectionId);long id=Util.insert(db,"INSERT INTO dimensions(section_id,name,sort_order) VALUES(?,?,?)",sectionId,required(b,"name"),n+1);return Map.of("id",id,"name",b.get("name"),"sortOrder",n+1);}
  @PutMapping("/dimensions/{id}") Map<String,Object> editDim(@PathVariable long id,@RequestBody Map<String,String>b,Authentication a){ownDim(id,a);db.update("UPDATE dimensions SET name=? WHERE id=?",required(b,"name"),id);return Map.of("id",id,"name",b.get("name"));}
  @DeleteMapping("/dimensions/{id}") void delDim(@PathVariable long id,Authentication a){ownDim(id,a);db.update("DELETE FROM dimensions WHERE id=?",id);}
  @PostMapping("/sections/{sectionId}/questions") @Transactional Map<String,Object> addQuestion(@PathVariable long sectionId,@RequestBody Map<String,Object>b,Authentication a){ownSection(sectionId,a);long did=dimensionFor(sectionId,b.get("dimensionId"));int n=db.queryForObject("SELECT COUNT(*) FROM questions WHERE section_id=?",Integer.class,sectionId);long qid=saveQuestion(null,sectionId,did,b,n+1);return question(qid);}
  @PutMapping("/questions/{id}") Map<String,Object> editQuestion(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){Map<String,Object> q=ownQuestion(id,a);long did=dimensionFor(((Number)q.get("section_id")).longValue(),b.get("dimensionId"));saveQuestion(id,((Number)q.get("section_id")).longValue(),did,b,((Number)q.get("sort_order")).intValue());return question(id);}
  @PostMapping("/questions/{id}/clone") Map<String,Object> cloneQuestion(@PathVariable long id,Authentication a){Map<String,Object> q=ownQuestion(id,a);long sid=((Number)q.get("section_id")).longValue();int order=((Number)q.get("sort_order")).intValue();db.update("UPDATE questions SET sort_order=sort_order+1 WHERE section_id=? AND sort_order>?",sid,order);long copy=Util.insert(db,"INSERT INTO questions(version_id,section_id,dimension_id,question_text,question_type,help_text,mandatory,sort_order,validation_json,config_json) SELECT version_id,section_id,dimension_id,question_text,question_type,help_text,mandatory,?,validation_json,config_json FROM questions WHERE id=?",order+1,id);db.update("INSERT INTO question_options(question_id,label,score,is_na,sort_order) SELECT ?,label,score,is_na,sort_order FROM question_options WHERE question_id=?",copy,id);return question(copy);}
  @DeleteMapping("/questions/{id}") void delQuestion(@PathVariable long id,Authentication a){ownQuestion(id,a);db.update("DELETE FROM questions WHERE id=?",id);}
  @PutMapping("/{formId}/order") @Transactional void order(@PathVariable long formId,@RequestBody Map<String,Object>b,Authentication a){Util.own(db,formId,a);String type=String.valueOf(b.get("type"));if(!Set.of("SECTIONS","DIMENSIONS","QUESTIONS").contains(type))throw Util.err(400,"Invalid order type");List<?> ids=(List<?>)b.get("ids");if(ids==null)throw Util.err(400,"ids is required");Object parent=b.get("parentId");String table=type.equals("SECTIONS")?"sections":type.equals("DIMENSIONS")?"dimensions":"questions";String sql=type.equals("SECTIONS")?"SELECT id FROM sections WHERE version_id=?":type.equals("DIMENSIONS")?"SELECT id FROM dimensions WHERE section_id=?":"SELECT id FROM questions WHERE section_id=?";long p=parent==null?draftId(formId):((Number)parent).longValue();if(!type.equals("SECTIONS"))ownSection(p,a);List<Long> existing=db.queryForList(sql,Long.class,p);Set<Long> got=new HashSet<>();for(Object i:ids)got.add(((Number)i).longValue());if(!got.equals(new HashSet<>(existing))||got.size()!=ids.size())throw Util.err(400,"Order ids must match the current items");for(int i=0;i<ids.size();i++)db.update("UPDATE "+table+" SET sort_order=? WHERE id=?",i+1,ids.get(i));}
  @PostMapping("/{id}/save-draft") Map<String,String> saveDraft(@PathVariable long id,Authentication a){Util.own(db,id,a);draftId(id);db.update("UPDATE forms SET status=CASE WHEN status='PUBLISHED' THEN 'PUBLISHED' ELSE 'SAVED' END WHERE id=?",id);return Map.of("storedStatus",db.queryForObject("SELECT status FROM forms WHERE id=?",String.class,id));}
  private String required(Map<String,String>b,String k){String v=b.get(k);if(v==null||v.isBlank())throw Util.err(400,k+" is required");return v.trim();}
  private long draftId(long id){List<Long>x=db.queryForList("SELECT id FROM form_versions WHERE form_id=? AND status='DRAFT' ORDER BY version_no DESC LIMIT 1",Long.class,id);return x.isEmpty()?clonePublished(id):x.get(0);}
  private long dimensionFor(long sid,Object raw){if(raw instanceof Number n){if(db.queryForList("SELECT id FROM dimensions WHERE id=? AND section_id=?",n.longValue(),sid).isEmpty())throw Util.err(400,"Dimension does not belong to section");return n.longValue();}List<Long>x=db.queryForList("SELECT id FROM dimensions WHERE section_id=? ORDER BY sort_order LIMIT 1",Long.class,sid);return x.isEmpty()?Util.insert(db,"INSERT INTO dimensions(section_id,name,sort_order) VALUES(?,'General',1)",sid):x.get(0);}
  private void ownSection(long id,Authentication a){if(db.queryForList("SELECT s.id FROM sections s JOIN form_versions v ON v.id=s.version_id JOIN forms f ON f.id=v.form_id WHERE s.id=? AND f.owner_id=? AND v.status='DRAFT'",id,Util.uid(a)).isEmpty())throw Util.err(404,"Section not found");}
  private void ownDim(long id,Authentication a){if(db.queryForList("SELECT d.id FROM dimensions d JOIN sections s ON s.id=d.section_id JOIN form_versions v ON v.id=s.version_id JOIN forms f ON f.id=v.form_id WHERE d.id=? AND v.status='DRAFT'"+(a==null?"":" AND f.owner_id=?"),a==null?new Object[]{id}:new Object[]{id,Util.uid(a)}).isEmpty())throw Util.err(404,"Dimension not found");}
  private Map<String,Object> ownQuestion(long id,Authentication a){List<Map<String,Object>>x=db.queryForList("SELECT q.* FROM questions q JOIN form_versions v ON v.id=q.version_id JOIN forms f ON f.id=v.form_id WHERE q.id=? AND f.owner_id=? AND v.status='DRAFT'",id,Util.uid(a));if(x.isEmpty())throw Util.err(404,"Question not found");return x.get(0);}
  private long saveQuestion(Long id,long sid,long did,Map<String,Object>b,int order){String type=String.valueOf(b.getOrDefault("type","SHORT"));if(!Set.of("SHORT","LONG","SINGLE","MULTI","DROPDOWN","YES_NO","SCALE","RATING","LIKERT","NPS","RANKING","DATE","TIME","NUMBER","EMAIL").contains(type))throw Util.err(400,"Unknown question type");String text=Objects.toString(b.get("text"),"").trim();if(text.isEmpty())throw Util.err(400,"Question text is required");String validation=jsonString(b.get("validation")),config=jsonString(b.get("config"));List<Map<String,Object>>options=(List<Map<String,Object>>)b.getOrDefault("options",List.of());if(type.equals("YES_NO"))options=new ArrayList<>(List.of(Map.of("label","Yes","isNa",false),Map.of("label","No","isNa",false)));if(type.equals("LIKERT"))options=new ArrayList<>(List.of("Strongly disagree","Disagree","Neutral","Agree","Strongly agree").stream().map(label->Map.<String,Object>of("label",label,"isNa",false)).toList());long qid=id==null?Util.insert(db,"INSERT INTO questions(version_id,section_id,dimension_id,question_text,question_type,help_text,mandatory,sort_order,validation_json,config_json) SELECT version_id,?,?,?,?,?,?,?,?,? FROM sections WHERE id=?",sid,did,text,type,b.get("helpText"),b.getOrDefault("mandatory",false),order,validation,config,sid):id;if(id!=null)db.update("UPDATE questions SET dimension_id=?,question_text=?,question_type=?,help_text=?,mandatory=?,validation_json=?,config_json=? WHERE id=?",did,text,type,b.get("helpText"),b.getOrDefault("mandatory",false),validation,config,id);if(id!=null)db.update("DELETE FROM question_options WHERE question_id=?",qid);int i=0;for(Map<String,Object>o:options){String label=Objects.toString(o.get("label"),"").trim();if(!label.isEmpty())db.update("INSERT INTO question_options(question_id,label,score,is_na,sort_order) VALUES(?,?,?,?,?)",qid,label,o.get("score"),o.getOrDefault("isNa",false),++i);}return qid;}
  private Map<String,Object> question(long id){Map<String,Object>q=db.queryForMap("SELECT id,section_id AS sectionId,dimension_id AS dimensionId,external_id AS externalId,question_text AS text,question_type AS type,help_text AS helpText,mandatory,sort_order AS sortOrder,validation_json AS validation,config_json AS config FROM questions WHERE id=?",id);q.put("validation",jsonMap(q.get("validation")));q.put("config",jsonMap(q.get("config")));q.put("options",db.queryForList("SELECT id,label,score,is_na AS isNa,sort_order AS sortOrder FROM question_options WHERE question_id=? ORDER BY sort_order",id));return q;}
  private Object jsonMap(Object v){if(v==null)return null;if(v instanceof Map<?,?>)return v;try{return new com.fasterxml.jackson.databind.ObjectMapper().readValue(v.toString(),Map.class);}catch(Exception e){return null;}}
  private String jsonString(Object v){if(v==null)return null;if(v instanceof String s)return s;try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v);}catch(Exception e){throw Util.err(400,"Invalid JSON field");}}

  @PostMapping("/{id}/publish")
  ResponseEntity<Map<String,Object>> publish(@PathVariable long id, @RequestBody Map<String, String> b, Authentication a) {
    Util.own(db, id, a);
    List<Map<String, Object>> dv = db.queryForList("SELECT id,version_no FROM form_versions WHERE form_id=? AND status='DRAFT'", id);
    if (dv.isEmpty()) throw Util.err(422, "Nothing to publish");
    long vid = ((Number) dv.get(0).get("id")).longValue();
    Integer qn = db.queryForObject("SELECT COUNT(*) FROM questions WHERE version_id=?", Integer.class, vid);
    List<Map<String,Object>> problems=new ArrayList<>();
    if (qn == null || qn == 0) problems.add(Map.of("questionId",0,"message","The form needs at least one question"));
    problems.addAll(db.queryForList("SELECT q.id AS questionId,'At least 2 options required' AS message FROM questions q WHERE q.version_id=? AND q.question_type IN ('SINGLE','MULTI','DROPDOWN','RANKING') AND (SELECT COUNT(*) FROM question_options o WHERE o.question_id=q.id)<2",vid));
    if(!problems.isEmpty())return ResponseEntity.unprocessableEntity().body(Map.of("status",422,"message","Cannot publish","fieldErrors",List.of(),"problems",problems));
    Timestamp exp = Util.ts(b.get("expiresAt")), opens = Util.ts(b.get("opensAt"));
    if (exp != null && exp.toInstant().isBefore(Instant.now())) return ResponseEntity.unprocessableEntity().body(Map.of("status",422,"message","Cannot publish","fieldErrors",List.of(),"problems",List.of(Map.of("questionId",0,"message","Expiry must be in the future"))));
    if(opens!=null&&exp!=null&&!opens.toInstant().isBefore(exp.toInstant()))return ResponseEntity.unprocessableEntity().body(Map.of("status",422,"message","Cannot publish","fieldErrors",List.of(),"problems",List.of(Map.of("questionId",0,"message","Opening time must be before expiry"))));
    db.update("UPDATE form_versions SET status='ARCHIVED' WHERE form_id=? AND status='PUBLISHED'", id);
    db.update("UPDATE form_versions SET status='PUBLISHED',published_at=?,expires_at=?,opens_at=? WHERE id=?", Timestamp.from(Instant.now()), exp, opens, vid);
    List<Map<String, Object>> t = db.queryForList("SELECT share_token FROM forms WHERE id=?", id);
    String token = t.get(0).get("share_token") != null ? (String) t.get(0).get("share_token") : Util.random(24);
    db.update("UPDATE forms SET status='PUBLISHED',share_token=?,link_status='ACTIVE',published_version_id=? WHERE id=?", token, vid, id);
    Map<String,Object> result=new LinkedHashMap<>();result.put("link",front+"/f/"+token);result.put("shareToken",token);result.put("versionNo",dv.get(0).get("version_no"));result.put("opensAt",opens);result.put("expiresAt",exp);result.put("displayStatus",Util.display("PUBLISHED",opens,exp));return ResponseEntity.ok(result);
  }

  @PostMapping("/{id}/link/regenerate")
  Map<String, Object> regenerate(@PathVariable long id, Authentication a) {
    Util.own(db, id, a);
    String token = Util.random(24);
    db.update("UPDATE forms SET share_token=?,link_status='ACTIVE' WHERE id=?", token, id);
    return Map.of("link", front + "/f/" + token, "shareToken", token);
  }

  @PostMapping("/{id}/unpublish")
  void unpublish(@PathVariable long id, Authentication a) {
    Util.own(db, id, a);
    db.update("UPDATE forms SET status='CLOSED' WHERE id=?", id);
  }

  @GetMapping("/{id}/assignments") List<Map<String,Object>> assignments(@PathVariable long id,Authentication a){
    Util.own(db,id,a);return db.queryForList("SELECT fa.email,(u.id IS NOT NULL) AS registered,s.status AS submissionStatus FROM form_assignments fa LEFT JOIN users u ON u.email_normalized=fa.email LEFT JOIN submissions s ON s.form_id=fa.form_id AND s.user_id=u.id WHERE fa.form_id=? ORDER BY fa.email",id);
  }
  @GetMapping("/{id}/versions") List<Map<String,Object>> versions(@PathVariable long id,Authentication a){Util.own(db,id,a);return db.queryForList("SELECT v.version_no AS versionNo,v.status,v.published_at AS publishedAt,v.expires_at AS expiresAt,(SELECT COUNT(*) FROM submissions s WHERE s.version_id=v.id) AS responseCount FROM form_versions v WHERE v.form_id=? ORDER BY v.version_no DESC",id);}
  @PutMapping("/{id}/assignments") Map<String,Integer> setAssignments(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
    Util.own(db,id,a);Object raw=b.get("emails");if(!(raw instanceof List<?> emails))throw Util.err(400,"emails must be a list");Set<String> clean=new LinkedHashSet<>();for(Object item:emails){String email=Objects.toString(item,"").trim().toLowerCase();if(email.isEmpty())continue;if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw Util.err(400,"Invalid email: "+email);clean.add(email);}db.update("DELETE FROM form_assignments WHERE form_id=?",id);for(String email:clean)db.update("INSERT INTO form_assignments(form_id,email) VALUES(?,?)",id,email);return Map.of("count",clean.size());
  }
}
