package com.assay;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

class ImportFailed extends RuntimeException {
  final List<Map<String, Object>> errors;
  ImportFailed(List<Map<String, Object>> e) { super("Import failed"); errors = e; }
}

@Service
class Importer {
  static final List<String> REQ = List.of("Q_ID", "Section", "Dimension", "Question", "Response_Type", "Response_Options", "Mandatory");
  static final Map<String, String> TYPES = Map.of("text", "SHORT", "single choice", "SINGLE", "multi choice", "MULTI", "scale 1-4", "SINGLE", "number", "NUMBER");
  static final Pattern SEC = Pattern.compile("^([A-Za-z0-9]+)\\.\\s*(.*)$"), SC = Pattern.compile("^(\\d+)\\s*=\\s*(.*)$"), RNG = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$");
  static final String SETTINGS = "{\"target\":3.0,\"allowEditAfterSubmit\":true}";
  final JdbcTemplate db;
  Importer(JdbcTemplate db) { this.db = db; }

  record P(String qid, String sec, String dim, String q, String type, List<String> opts, String ev, boolean man, boolean scale) {}

  static Map<String, Object> e(int row, String col, String msg) { return Map.of("row", row, "column", col, "message", msg); }

  static List<List<String>> parse(String t, char d) {
    List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder f = new StringBuilder(); boolean q = false;
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (q) { if (c == '"') { if (i + 1 < t.length() && t.charAt(i + 1) == '"') { f.append('"'); i++; } else q = false; } else f.append(c); }
      else if (c == '"') q = true;
      else if (c == d) { row.add(f.toString()); f.setLength(0); }
      else if (c == '\n' || c == '\r') { if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++; row.add(f.toString()); f.setLength(0); rows.add(row); row = new ArrayList<>(); }
      else f.append(c);
    }
    if (f.length() > 0 || !row.isEmpty()) { row.add(f.toString()); rows.add(row); }
    return rows;
  }

  @Transactional
  public Map<String, Object> run(String title, String text, long owner) {
    text = text.replace("\uFEFF", "").replace('\u2019', '\'');
    String first = text.split("\n", 2)[0];
    char d = first.chars().filter(c -> c == ';').count() > first.chars().filter(c -> c == ',').count() ? ';' : ',';
    List<List<String>> rows = parse(text, d);
    rows.removeIf(r -> r.stream().allMatch(String::isBlank));
    List<Map<String, Object>> errs = new ArrayList<>();
    if (rows.isEmpty()) throw new ImportFailed(List.of(e(1, "", "File is empty")));
    Map<String, Integer> ix = new HashMap<>();
    for (int i = 0; i < rows.get(0).size(); i++) ix.put(rows.get(0).get(i).trim(), i);
    for (String c : REQ) if (!ix.containsKey(c)) errs.add(e(1, c, "Missing column"));
    if (!errs.isEmpty()) throw new ImportFailed(errs);

    List<P> ps = new ArrayList<>(); Set<String> seen = new HashSet<>();
    for (int n = 1; n < rows.size(); n++) {
      List<String> r = rows.get(n); int line = n + 1;
      String qid = g(r, ix, "Q_ID"), sec = g(r, ix, "Section"), dim = g(r, ix, "Dimension"), q = g(r, ix, "Question"), ty = g(r, ix, "Response_Type").toLowerCase(), man = g(r, ix, "Mandatory").toLowerCase();
      if (qid.isEmpty()) errs.add(e(line, "Q_ID", "Q_ID is empty")); else if (!seen.add(qid)) errs.add(e(line, "Q_ID", "Duplicate Q_ID " + qid));
      if (sec.isEmpty()) errs.add(e(line, "Section", "Section is empty"));
      if (dim.isEmpty()) errs.add(e(line, "Dimension", "Dimension is empty"));
      if (q.isEmpty()) errs.add(e(line, "Question", "Question is empty"));
      String type = TYPES.get(ty);
      if (type == null) errs.add(e(line, "Response_Type", "Unknown type '" + g(r, ix, "Response_Type") + "'"));
      if (!List.of("y", "n", "yes", "no", "true", "false").contains(man)) errs.add(e(line, "Mandatory", "Mandatory must be Y or N"));
      List<String> opts = new ArrayList<>();
      for (String o : g(r, ix, "Response_Options").split("\\|")) if (!o.trim().isEmpty()) opts.add(o.trim());
      if (type != null && (type.equals("SINGLE") || type.equals("MULTI")) && opts.size() < 2) errs.add(e(line, "Response_Options", "At least 2 options required"));
      ps.add(new P(qid, sec, dim, q, type, opts, g(r, ix, "Evidence_or_Example"), List.of("y", "yes", "true").contains(man), "scale 1-4".equals(ty)));
    }
    if (!errs.isEmpty()) throw new ImportFailed(errs);

    long fid = Util.insert(db, "INSERT INTO forms(owner_id,title,status) VALUES(?,?,'DRAFT')", owner, title);
    long vid = Util.insert(db, "INSERT INTO form_versions(form_id,version_no,status,settings_json) VALUES(?,1,'DRAFT',?)", fid, SETTINGS);
    Map<String, Long> secs = new LinkedHashMap<>(), dims = new LinkedHashMap<>();
    List<Object[]> optRows = new ArrayList<>(); int qi = 0;
    for (P p : ps) {
      Long sid = secs.get(p.sec());
      if (sid == null) {
        Matcher m = SEC.matcher(p.sec());
        String code = m.matches() ? m.group(1) : null, name = m.matches() ? m.group(2) : p.sec();
        sid = Util.insert(db, "INSERT INTO sections(version_id,code,name,sort_order) VALUES(?,?,?,?)", vid, code, name, secs.size() + 1);
        secs.put(p.sec(), sid);
      }
      String dk = p.sec() + "|" + p.dim();
      Long did = dims.get(dk);
      if (did == null) { did = Util.insert(db, "INSERT INTO dimensions(section_id,name,sort_order) VALUES(?,?,?)", sid, p.dim(), dims.size() + 1); dims.put(dk, did); }
      String val = null, cfg = null;
      if (p.type().equals("NUMBER") && !p.opts().isEmpty()) {
        Matcher m = RNG.matcher(p.opts().get(0));
        if (m.matches()) val = "{\"min\":" + m.group(1) + ",\"max\":" + m.group(2) + "}";
        if (p.opts().stream().anyMatch(o -> o.toLowerCase().contains("know"))) cfg = "{\"allowDontKnow\":true}";
      }
      long qid = Util.insert(db, "INSERT INTO questions(version_id,section_id,dimension_id,external_id,question_text,question_type,help_text,mandatory,sort_order,validation_json,config_json) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
          vid, sid, did, p.qid(), p.q(), p.type(), p.ev().isEmpty() ? null : p.ev(), p.man(), ++qi, val, cfg);
      if (!p.type().equals("NUMBER")) {
        int k = 0;
        for (String o : p.opts()) {
          boolean na = o.toLowerCase().matches("^(don'?t know|do not know|unknown).*"); Integer score = null; String label = o;
          if (p.scale()) { Matcher m = SC.matcher(o); if (m.matches()) { score = Integer.parseInt(m.group(1)); label = m.group(2).trim(); } }
          optRows.add(new Object[]{qid, label, score, na, ++k});
        }
      }
    }
    db.batchUpdate("INSERT INTO question_options(question_id,label,score,is_na,sort_order) VALUES(?,?,?,?,?)", optRows);
    return Map.of("id", fid, "sections", secs.size(), "dimensions", dims.size(), "questions", ps.size());
  }

  static String g(List<String> r, Map<String, Integer> ix, String c) {
    Integer i = ix.get(c);
    return i == null || i >= r.size() ? "" : r.get(i).trim();
  }
}
