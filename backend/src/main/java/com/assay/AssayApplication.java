package com.assay;

import java.util.Map;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class AssayApplication {
  public static void main(String[] a) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(AssayApplication.class, a);
  }

  @GetMapping("/api/health")
  Map<String, String> health() { return Map.of("status", "ok"); }

  @Bean
  CommandLineRunner seed(JdbcTemplate db, PasswordEncoder pe, @Value("${app.admin-email}") String email, @Value("${app.admin-password}") String pw) {
    return x -> {
      Integer n = db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Integer.class);
      if (n != null && n == 0)
        db.update("INSERT INTO users(name,email,email_normalized,password_hash,role) VALUES('Admin',?,?,?,'ADMIN')", email, email.toLowerCase(), pe.encode(pw));
    };
  }
}
