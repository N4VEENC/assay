package com.assay;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssayApplication.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {
  @Autowired MockMvc mvc;
  @MockBean JdbcTemplate db;
  @MockBean JwtDecoder decoder;

  @Test void healthIsPublic() throws Exception { mvc.perform(get("/api/health")).andExpect(status().isOk()); }
  @Test void adminRouteRejectsMissingToken() throws Exception { mvc.perform(get("/api/forms/overview")).andExpect(status().isUnauthorized()); }
  @Test void adminRouteRejectsRespondentRole() throws Exception {
    mvc.perform(get("/api/forms/overview").with(jwt().jwt(j -> j.subject("2").claim("role","RESPONDENT"))))
      .andExpect(status().isForbidden());
  }
  @Test void builderResourcePathsRejectRespondentRole() throws Exception {
    for(String path:new String[]{"/api/sections/1","/api/dimensions/1","/api/questions/1"})
      mvc.perform(get(path).with(jwt().jwt(j->j.subject("2").claim("role","RESPONDENT")))).andExpect(status().isForbidden());
  }
  @Test void respondentPathsRejectAdminRole() throws Exception {
    mvc.perform(get("/api/submissions/1").with(jwt().jwt(j->j.subject("1").claim("role","ADMIN"))))
      .andExpect(status().isForbidden());
  }
}
