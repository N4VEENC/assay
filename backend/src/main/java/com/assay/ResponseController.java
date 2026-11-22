package com.assay;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forms/{fid}")
class ResponseController {
  final JdbcTemplate db;
  ResponseController(JdbcTemplate db) { this.db = db; }

  @GetMapping("/responses")
  Map<String, Object> responses(@PathVariable long fid, Authentication a) {
    Util.own(db, fid, a);
    long current=currentVersion(fid);
    List<Map<String, Object>> list = db.queryForList(
      "SELECT s.id AS submissionId,s.version_id AS versionId,u.id AS userId,u.name,u.email,s.status,s.submitted_at AS submittedAt,s.last_saved_at AS lastSavedAt,v.version_no AS versionNo,"
      + "(SELECT COUNT(*) FROM answers a WHERE a.submission_id=s.id AND " + Util.HAS + ") AS answeredCount,"
      + "(SELECT COUNT(*) FROM questions q WHERE q.version_id=s.version_id) AS totalCount,"
      + "(SELECT ROUND(AVG(o.score),2) FROM answers a JOIN question_options o ON o.id=a.selected_option_id WHERE a.submission_id=s.id AND o.score IS NOT NULL AND o.is_na=0) AS score "
      + "FROM submissions s JOIN users u ON u.id=s.user_id JOIN form_versions v ON v.id=s.version_id WHERE s.form_id=? ORDER BY s.last_saved_at DESC", fid);
    for (Map<String, Object> m : list) {
      long d = ((Number) m.get("answeredCount")).longValue(), t = ((Number) m.get("totalCount")).longValue();
      m.put("progressPct", t == 0 ? 0 : Math.round(d * 100.0 / t));
    }
    Map<String, Object> f = db.queryForMap("SELECT f.id,f.title,COALESCE(pv.version_no,dv.version_no,1) AS versionNo,COALESCE(f.published_version_id,(SELECT MAX(v.id) FROM form_versions v WHERE v.form_id=f.id)) AS versionId FROM forms f LEFT JOIN form_versions pv ON pv.id=f.published_version_id LEFT JOIN form_versions dv ON dv.form_id=f.id AND dv.status='DRAFT' WHERE f.id=?", fid);
    long vid=current;f.remove("versionId");
    int tq=db.queryForObject("SELECT COUNT(*) FROM questions WHERE version_id=?",Integer.class,vid),sq=db.queryForObject("SELECT COUNT(*) FROM questions q WHERE q.version_id=? AND EXISTS(SELECT 1 FROM question_options o WHERE o.question_id=q.id AND o.score IS NOT NULL AND o.is_na=0)",Integer.class,vid);
    Map<String,Object> result=new LinkedHashMap<>();result.put("form",f);result.put("currentVersionId",current);result.put("versions",db.queryForList("SELECT id,version_no AS versionNo,status,published_at AS publishedAt FROM form_versions WHERE form_id=? ORDER BY version_no DESC",fid));result.put("totalQuestions",tq);result.put("scoredQuestions",sq);result.put("respondents",list);return result;
  }

  @GetMapping("/submissions/{sid}")
  Map<String, Object> detail(@PathVariable long fid, @PathVariable long sid, Authentication a) {
    Util.own(db, fid, a);
    List<Map<String, Object>> sr = db.queryForList("SELECT s.id,s.version_id AS versionId,u.name,u.email,s.status,s.submitted_at AS submittedAt,s.last_saved_at AS lastSavedAt,v.version_no AS versionNo FROM submissions s JOIN users u ON u.id=s.user_id JOIN form_versions v ON v.id=s.version_id WHERE s.id=? AND s.form_id=?", sid, fid);
    if (sr.isEmpty()) throw Util.err(404, "Not found");
    Map<String, Object> sub = sr.get(0);
    List<Map<String, Object>> rows = db.queryForList(
      "SELECT q.id,q.external_id AS externalId,sec.id AS sectionId,sec.code AS sectionCode,sec.name AS sectionName,d.name AS dimensionName,q.question_text AS text,q.question_type AS type,"
      + "a.id AS answerId,a.value_text AS answerValueText,a.value_number AS answerValueNumber,a.value_date AS answerValueDate,o.label AS optionLabel,o.score AS score,a.remarks,a.state "
      + "FROM questions q JOIN sections sec ON sec.id=q.section_id JOIN dimensions d ON d.id=q.dimension_id "
      + "LEFT JOIN answers a ON a.question_id=q.id AND a.submission_id=? LEFT JOIN question_options o ON o.id=a.selected_option_id "
      + "WHERE q.version_id=? ORDER BY sec.sort_order,q.sort_order", sid, sub.get("versionId"));
    Map<Object, Map<String, Object>> secs = new LinkedHashMap<>();
    int answered = 0; double sum = 0; int scored = 0;
    for (Map<String, Object> r : rows) {
      Map<String, Object> sec = secs.computeIfAbsent(r.get("sectionId"), k -> new LinkedHashMap<>(Map.of("id", r.get("sectionId"), "code", String.valueOf(r.get("sectionCode")), "name", r.get("sectionName"), "questions", new ArrayList<Map<String, Object>>())));
      @SuppressWarnings("unchecked") List<Map<String, Object>> ql = (List<Map<String, Object>>) sec.get("questions");
      ql.add(r);
      String answerText=detailAnswer(r);r.put("answerText",answerText);r.remove("answerId");r.remove("answerValueText");r.remove("answerValueNumber");r.remove("answerValueDate");r.remove("optionLabel");if (answerText != null) answered++;
      if (r.get("score") != null) { sum += ((Number) r.get("score")).doubleValue(); scored++; }
    }
    sub.put("answeredCount", answered); sub.put("totalCount", rows.size()); sub.put("score", scored == 0 ? null : Math.round(sum / scored * 100) / 100.0);
    return Map.of("submission", sub, "sections", new ArrayList<>(secs.values()));
  }

  @GetMapping("/questions/summary") List<Map<String,Object>> summary(@PathVariable long fid,@RequestParam(required=false) String sectionId,@RequestParam(required=false) Long versionId,Authentication a){
    Util.own(db,fid,a);long vid=selectedVersion(fid,versionId);String base="SELECT q.id AS questionId,q.external_id AS externalId,s.id AS sectionId,s.code AS sectionCode,s.name AS sectionName,d.name AS dimensionName,q.question_text AS text,q.question_type AS type FROM questions q JOIN sections s ON s.id=q.section_id JOIN dimensions d ON d.id=q.dimension_id WHERE q.version_id=?";List<Map<String,Object>> qs;
    if(sectionId==null||sectionId.isBlank())qs=db.queryForList(base+" ORDER BY s.sort_order,q.sort_order",vid);else{try{qs=db.queryForList(base+" AND s.id=? ORDER BY s.sort_order,q.sort_order",vid,Long.parseLong(sectionId));}catch(NumberFormatException ex){qs=db.queryForList(base+" AND s.code=? ORDER BY s.sort_order,q.sort_order",vid,sectionId);}}List<Map<String,Object>> out=new ArrayList<>();
    for(Map<String,Object> q:qs){long qid=((Number)q.get("questionId")).longValue();List<Map<String,Object>> as=db.queryForList("SELECT a.id,a.selected_option_id,a.value_text,a.value_number,a.value_date,o.label,o.score,o.is_na FROM answers a JOIN submissions s ON s.id=a.submission_id LEFT JOIN question_options o ON o.id=a.selected_option_id WHERE s.form_id=? AND s.version_id=? AND s.status='SUBMITTED' AND a.question_id=? AND (a.selected_option_id IS NOT NULL OR a.value_text IS NOT NULL OR a.value_number IS NOT NULL OR a.value_date IS NOT NULL OR EXISTS(SELECT 1 FROM answer_options ao WHERE ao.answer_id=a.id))",fid,vid,qid);Map<String,Integer> counts=new LinkedHashMap<>();double sum=0;int scored=0;for(Map<String,Object>x:as){if(x.get("selected_option_id")!=null){String label=Objects.toString(x.get("label"),"");counts.merge(label,1,Integer::sum);if(x.get("score")!=null&&!Boolean.TRUE.equals(x.get("is_na"))){sum+=((Number)x.get("score")).doubleValue();scored++;}}else if(x.get("value_number")!=null)counts.merge(x.get("value_number").toString(),1,Integer::sum);}
      String type=(String)q.get("type");boolean textType=List.of("SHORT","LONG","EMAIL","NUMBER","DATE","TIME").contains(type);List<Map<String,Object>> dist=textType?null:counts.entrySet().stream().map(e->Map.<String,Object>of("label",e.getKey(),"count",e.getValue(),"pct",as.isEmpty()?0:Math.round(e.getValue()*10000.0/as.size())/100.0)).toList();q.put("answeredCount",as.size());q.put("distribution",dist);q.put("avgScore",scored==0?null:Math.round(sum*100.0/scored)/100.0);out.add(q);
    }return out;
  }

  @GetMapping("/questions/{qid}/answers") List<Map<String,Object>> questionAnswers(@PathVariable long fid,@PathVariable long qid,@RequestParam(required=false) Long versionId,Authentication a){
    Util.own(db,fid,a);long vid=selectedVersion(fid,versionId);List<Map<String,Object>> qs=db.queryForList("SELECT id,question_type FROM questions WHERE id=? AND version_id=?",qid,vid);if(qs.isEmpty())throw Util.err(404,"Question not found");String type=(String)qs.get(0).get("question_type");List<Map<String,Object>> subs=db.queryForList("SELECT s.id AS submissionId,u.name,u.email,s.status FROM submissions s JOIN users u ON u.id=s.user_id WHERE s.form_id=? AND s.version_id=? ORDER BY u.name",fid,vid);List<Map<String,Object>> out=new ArrayList<>();
    for(Map<String,Object> sub:subs){long sid=((Number)sub.get("submissionId")).longValue();List<Map<String,Object>> aa=db.queryForList("SELECT id,selected_option_id,value_text,value_number,value_date,remarks FROM answers WHERE submission_id=? AND question_id=?",sid,qid);Map<String,Object> row=new LinkedHashMap<>(sub);boolean answered=!aa.isEmpty()&&hasValue(aa.get(0));row.put("answerText",answered?answerText(aa.get(0),type):null);row.put("score",answered?score(aa.get(0)):null);row.put("remarks",aa.isEmpty()?null:aa.get(0).get("remarks"));out.add(row);}return out;
  }

  @GetMapping("/scores") Map<String,Object> scores(@PathVariable long fid,@RequestParam(required=false) Long versionId,Authentication a){
    Util.own(db,fid,a);long vid=selectedVersion(fid,versionId);Map<String,Object> ver=db.queryForMap("SELECT settings_json FROM form_versions WHERE id=?",vid);double target=3.0;try{Object val=new com.fasterxml.jackson.databind.ObjectMapper().readValue(String.valueOf(ver.get("settings_json")),Map.class).get("target");if(val instanceof Number n)target=n.doubleValue();}catch(Exception ignored){}
    List<Map<String,Object>> qs=db.queryForList("SELECT q.id AS questionId,q.external_id AS externalId,q.section_id AS sectionId,s.code,s.name AS sectionName,q.dimension_id AS dimensionId,d.name AS dimensionName FROM questions q JOIN sections s ON s.id=q.section_id JOIN dimensions d ON d.id=q.dimension_id WHERE q.version_id=? ORDER BY s.sort_order,d.sort_order,q.sort_order",vid);Map<Long,List<Double>> byQ=new HashMap<>(),byDim=new HashMap<>(),bySec=new HashMap<>();
    for(Map<String,Object>q:qs){long qid=((Number)q.get("questionId")).longValue(),did=((Number)q.get("dimensionId")).longValue(),sid=((Number)q.get("sectionId")).longValue();List<Map<String,Object>> vals=db.queryForList("SELECT o.score FROM answers a JOIN submissions sub ON sub.id=a.submission_id JOIN question_options o ON o.id=a.selected_option_id WHERE sub.form_id=? AND sub.version_id=? AND sub.status='SUBMITTED' AND a.question_id=? AND o.score IS NOT NULL AND o.is_na=0",fid,vid,qid);for(Map<String,Object>x:vals){double n=((Number)x.get("score")).doubleValue();byQ.computeIfAbsent(qid,k->new ArrayList<>()).add(n);byDim.computeIfAbsent(did,k->new ArrayList<>()).add(n);bySec.computeIfAbsent(sid,k->new ArrayList<>()).add(n);}}
    List<Map<String,Object>> sections=new ArrayList<>();Set<Long> seen=new HashSet<>();for(Map<String,Object>q:qs){long sid=((Number)q.get("sectionId")).longValue();if(!seen.add(sid))continue;List<Map<String,Object>> dims=new ArrayList<>();Set<Long> sd=new HashSet<>();for(Map<String,Object>x:qs)if(((Number)x.get("sectionId")).longValue()==sid){long did=((Number)x.get("dimensionId")).longValue();if(sd.add(did)){Map<String,Object> dm=new LinkedHashMap<>();dm.put("id",did);dm.put("name",x.get("dimensionName"));dm.put("score",avg(byDim.get(did)));dm.put("scoredQuestions",(int)qs.stream().filter(t->((Number)t.get("dimensionId")).longValue()==did&&byQ.containsKey(((Number)t.get("questionId")).longValue())).count());dims.add(dm);}}Map<String,Object> sec=new LinkedHashMap<>();sec.put("id",sid);sec.put("code",q.get("code"));sec.put("name",q.get("sectionName"));sec.put("score",avg(bySec.get(sid)));sec.put("dimensions",dims);sections.add(sec);}
    List<Double> all=byQ.values().stream().flatMap(List::stream).toList();List<Map<String,Object>> heat=qs.stream().map(q->{Map<String,Object> h=new LinkedHashMap<>();h.put("questionId",q.get("questionId"));h.put("externalId",q.get("externalId"));h.put("sectionId",q.get("sectionId"));h.put("avgScore",avg(byQ.get(((Number)q.get("questionId")).longValue())));return h;}).toList();Map<String,Object> result=new LinkedHashMap<>();result.put("target",target);result.put("overall",avg(all));result.put("sections",sections);result.put("heat",heat);return result;
  }

  @GetMapping(value="/export",produces="text/csv; charset=UTF-8") ResponseEntity<byte[]> export(@PathVariable long fid,@RequestParam String format,@RequestParam(required=false) Long versionId,Authentication a){
    Util.own(db,fid,a);if(!List.of("wide","long").contains(format))throw Util.err(400,"Format must be wide or long");long vid=selectedVersion(fid,versionId);List<Map<String,Object>> qs=db.queryForList("SELECT id,external_id AS externalId,question_text AS text,question_type AS type FROM questions WHERE version_id=? ORDER BY sort_order",vid);List<Map<String,Object>> subs=db.queryForList("SELECT s.id,u.name,u.email,s.status,s.submitted_at FROM submissions s JOIN users u ON u.id=s.user_id WHERE s.form_id=?"+(format.equals("wide")?" AND s.version_id=?":"")+" ORDER BY s.last_saved_at",format.equals("wide")?new Object[]{fid,vid}:new Object[]{fid});StringBuilder csv=new StringBuilder("\uFEFF");
    if(format.equals("wide")){
      List<String> h=new ArrayList<>(List.of("Respondent","Email","Status","Progress %","Score","Submitted at"));for(Map<String,Object>q:qs)h.add(Objects.toString(q.get("externalId"),"Q"+q.get("id")));line(csv,h);
      for(Map<String,Object>s:subs){long sid=((Number)s.get("id")).longValue();Integer done=db.queryForObject("SELECT COUNT(*) FROM answers a WHERE a.submission_id=? AND "+Util.HAS,Integer.class,sid);double progress=qs.isEmpty()?0:Math.round((done==null?0:done)*10000.0/qs.size())/100.0;Object score=db.queryForObject("SELECT AVG(o.score) FROM answers a JOIN question_options o ON o.id=a.selected_option_id WHERE a.submission_id=? AND o.score IS NOT NULL AND o.is_na=0",Object.class,sid);List<String> row=new ArrayList<>(List.of(str(s.get("name")),str(s.get("email")),str(s.get("status")),String.valueOf(progress),score==null?"":String.valueOf(Math.round(((Number)score).doubleValue()*100.0)/100.0),str(s.get("submitted_at"))));
        for(Map<String,Object>q:qs){List<Map<String,Object>> ans=db.queryForList("SELECT id,selected_option_id,value_text,value_number,value_date FROM answers WHERE submission_id=? AND question_id=?",sid,q.get("id"));row.add(ans.isEmpty()?"":answerText(ans.get(0),(String)q.get("type")));}line(csv,row);
      }
    }
    else {line(csv,List.of("Respondent","Email","Status","Q_ID","Section","Dimension","Question","Type","Answer","Score","Comment","Submitted at"));List<Map<String,Object>> allq=db.queryForList("SELECT q.id,q.external_id AS externalId,q.question_text AS text,q.question_type AS type,s.name AS section,d.name AS dimension FROM questions q JOIN form_versions v ON v.id=q.version_id JOIN sections s ON s.id=q.section_id JOIN dimensions d ON d.id=q.dimension_id WHERE v.form_id=? ORDER BY v.version_no,s.sort_order,q.sort_order",fid);for(Map<String,Object>s:subs){long sid=((Number)s.get("id")).longValue();for(Map<String,Object>q:allq){List<Map<String,Object>> ans=db.queryForList("SELECT id,selected_option_id,value_text,value_number,value_date,remarks FROM answers WHERE submission_id=? AND question_id=?",sid,q.get("id"));Map<String,Object>x=ans.isEmpty()?Map.of():ans.get(0);line(csv,List.of(str(s.get("name")),str(s.get("email")),str(s.get("status")),Objects.toString(q.get("externalId"),"Q"+q.get("id")),str(q.get("section")),str(q.get("dimension")),str(q.get("text")),str(q.get("type")),ans.isEmpty()?"":answerText(x,(String)q.get("type")),ans.isEmpty()?"":str(score(x)),ans.isEmpty()?"":str(x.get("remarks")),str(s.get("submitted_at"))));}}}
    String slug="form-"+fid;return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+slug+"-responses-"+format+".csv\"").contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping(value="/submissions/{sid}/export",produces="text/csv; charset=UTF-8") ResponseEntity<byte[]> exportPerson(@PathVariable long fid,@PathVariable long sid,Authentication a){
    Util.own(db,fid,a);List<Map<String,Object>> rows=db.queryForList("SELECT q.id,q.external_id AS externalId,q.question_text AS text,q.question_type AS type,s.code,s.name AS section,d.name AS dimension FROM questions q JOIN sections s ON s.id=q.section_id JOIN dimensions d ON d.id=q.dimension_id JOIN submissions sub ON sub.version_id=q.version_id WHERE sub.id=? AND sub.form_id=? ORDER BY s.sort_order,q.sort_order",sid,fid);if(rows.isEmpty())throw Util.err(404,"Submission not found");StringBuilder csv=new StringBuilder("\uFEFF");line(csv,List.of("Q_ID","Section","Dimension","Question","Type","Answer","Score","Comment"));for(Map<String,Object>q:rows){List<Map<String,Object>>ans=db.queryForList("SELECT id,selected_option_id,value_text,value_number,value_date,remarks FROM answers WHERE submission_id=? AND question_id=?",sid,q.get("id"));Map<String,Object>x=ans.isEmpty()?Map.of():ans.get(0);line(csv,List.of(Objects.toString(q.get("externalId"),"Q"+q.get("id")),str(q.get("code"))+" "+str(q.get("section")),str(q.get("dimension")),str(q.get("text")),str(q.get("type")),ans.isEmpty()?"":answerText(x,(String)q.get("type")),ans.isEmpty()?"":str(score(x)),ans.isEmpty()?"":str(x.get("remarks"))));}return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"respondent-"+sid+"-response.csv\"").contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private long currentVersion(long fid){List<Long>x=db.queryForList("SELECT COALESCE(published_version_id,(SELECT MAX(id) FROM form_versions WHERE form_id=?)) FROM forms WHERE id=?",Long.class,fid,fid);if(x.isEmpty()||x.get(0)==null)throw Util.err(404,"Version not found");return x.get(0);}
  private long selectedVersion(long fid,Long requested){if(requested==null)return currentVersion(fid);if(db.queryForList("SELECT id FROM form_versions WHERE id=? AND form_id=?",requested,fid).isEmpty())throw Util.err(404,"Version not found");return requested;}
  private Object avg(List<Double>x){return x==null||x.isEmpty()?null:Math.round(x.stream().mapToDouble(Double::doubleValue).average().orElse(0)*100.0)/100.0;}
  private String answerText(Map<String,Object>a,String type){if(a.get("selected_option_id")!=null){List<String> labs=db.queryForList("SELECT label FROM question_options WHERE id=?",String.class,a.get("selected_option_id"));return labs.isEmpty()?"":labs.get(0);}long aid=a.get("id")==null?-1:((Number)a.get("id")).longValue();if(type.equals("MULTI")||type.equals("RANKING")){List<Map<String,Object>> os=db.queryForList("SELECT o.label,ao.rank_no FROM answer_options ao JOIN question_options o ON o.id=ao.option_id WHERE ao.answer_id=? ORDER BY ao.rank_no",aid);List<String> v=new ArrayList<>();for(Map<String,Object>o:os)v.add(type.equals("RANKING")?o.get("rank_no")+". "+o.get("label"):str(o.get("label")));return String.join(" | ",v);}Object v=a.get("value_text");if(v==null)v=a.get("value_number");if(v==null)v=a.get("value_date");return "DONT_KNOW".equals(v)?"Don't know":str(v);}
  private String detailAnswer(Map<String,Object> q){if(q.get("answerId")==null)return null;String type=(String)q.get("type");long aid=((Number)q.get("answerId")).longValue();if(type.equals("MULTI")||type.equals("RANKING")){List<Map<String,Object>> opts=db.queryForList("SELECT o.label,ao.rank_no FROM answer_options ao JOIN question_options o ON o.id=ao.option_id WHERE ao.answer_id=? ORDER BY ao.rank_no",aid);List<String> values=new ArrayList<>();for(Map<String,Object>o:opts)values.add(type.equals("RANKING")?o.get("rank_no")+". "+o.get("label"):str(o.get("label")));return values.isEmpty()?null:String.join(" | ",values);}if(q.get("optionLabel")!=null)return str(q.get("optionLabel"));Object value=q.get("answerValueText");if(value==null)value=q.get("answerValueNumber");if(value==null)value=q.get("answerValueDate");return value==null?null:"DONT_KNOW".equals(value)?"Don't know":value.toString();}
  private Object score(Map<String,Object>a){if(a.get("selected_option_id")==null)return null;List<BigDecimal>x=db.queryForList("SELECT score FROM question_options WHERE id=? AND is_na=0",BigDecimal.class,a.get("selected_option_id"));return x.isEmpty()?null:x.get(0);}
  private String str(Object v){return v==null?"":v.toString();}
  private boolean hasValue(Map<String,Object>a){if(a.get("selected_option_id")!=null||a.get("value_number")!=null||a.get("value_date")!=null||a.get("value_text")!=null&&!a.get("value_text").toString().isBlank())return true;long aid=((Number)a.get("id")).longValue();Integer n=db.queryForObject("SELECT COUNT(*) FROM answer_options WHERE answer_id=?",Integer.class,aid);return n!=null&&n>0;}
  private void line(StringBuilder b,List<String> values){b.append(values.stream().map(this::cell).collect(Collectors.joining(","))).append("\r\n");}
  private String cell(String value){String s=value==null?"":value;if(!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
