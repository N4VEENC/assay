package com.assay;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImporterTest {
  @Test void parsesQuotedCommasAndEscapedQuotes() {
    assertEquals(List.of(List.of("name","question"),List.of("A","What, exactly?"),List.of("B","She said \"yes\"")),
      Importer.parse("name,question\r\nA,\"What, exactly?\"\r\nB,\"She said \"\"yes\"\"\"", ','));
  }

  @Test void parsesSemicolonsAndUtf8BomInputAfterRemoval() {
    String input="Q_ID;Question\nA1;\"A question; with a semicolon\"";
    assertEquals("A question; with a semicolon",Importer.parse(input,';').get(1).get(1));
  }

  @Test void reportsAllCsvRowProblemsBeforeWriting() {
    String csv="Q_ID,Section,Dimension,Question,Response_Type,Response_Options,Mandatory\n"
      +"A1,A,Core,First,text,,Yes\n"
      +"A1,A,Core,,mystery,,No\n";
    ImportFailed failure=assertThrows(ImportFailed.class,()->new Importer(null).run("Bad",csv,1));
    assertTrue(failure.errors.stream().anyMatch(e->"Q_ID".equals(e.get("column"))&&e.get("message").toString().contains("Duplicate")));
    assertTrue(failure.errors.stream().anyMatch(e->"Question".equals(e.get("column"))));
    assertTrue(failure.errors.stream().anyMatch(e->"Response_Type".equals(e.get("column"))));
  }

  @Test void readsTheSuppliedQuestionnaireShape() throws Exception {
    Path sample=Path.of("..","sample","QE_Assessment_PreRead_Questionnaire_App_Leads.csv");
    String text=Files.readString(sample).replace("\uFEFF","");
    List<List<String>> rows=Importer.parse(text,',');
    assertEquals(73,rows.size());
    int section=rows.get(0).indexOf("Section"),dimension=rows.get(0).indexOf("Dimension"),mandatory=rows.get(0).indexOf("Mandatory"),type=rows.get(0).indexOf("Response_Type"),options=rows.get(0).indexOf("Response_Options");
    assertEquals(11,rows.subList(1,rows.size()).stream().map(r->r.get(section)).distinct().count());
    assertEquals(23,rows.subList(1,rows.size()).stream().map(r->r.get(section)+"|"+r.get(dimension)).distinct().count());
    assertEquals(53,rows.subList(1,rows.size()).stream().filter(r->r.get(mandatory).equalsIgnoreCase("Y")).count());
    assertEquals(18,rows.subList(1,rows.size()).stream().filter(r->r.get(type).equalsIgnoreCase("Scale 1-4")).count());
    assertTrue(rows.subList(1,rows.size()).stream().anyMatch(r->Arrays.stream(r.get(options).split("\\|")).anyMatch(x->x.trim().equalsIgnoreCase("Don't know"))));
  }
}
