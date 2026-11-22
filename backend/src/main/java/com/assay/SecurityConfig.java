package com.assay;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
class SecurityConfig {
  @Bean SecretKey jwtKey(@Value("${app.jwt-secret}") String s) { return new SecretKeySpec(s.getBytes(StandardCharsets.UTF_8), "HmacSHA256"); }
  @Bean JwtEncoder jwtEncoder(SecretKey k) { return new NimbusJwtEncoder(new ImmutableSecret<>(k)); }
  @Bean JwtDecoder jwtDecoder(SecretKey k) { return NimbusJwtDecoder.withSecretKey(k).macAlgorithm(MacAlgorithm.HS256).build(); }
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean
  SecurityFilterChain chain(HttpSecurity h) throws Exception {
    ObjectMapper json=new ObjectMapper();
    AuthenticationEntryPoint entry=(req,res,e)->{res.setStatus(401);res.setContentType("application/json");json.writeValue(res.getOutputStream(),Map.of("status",401,"message","Not logged in","fieldErrors",List.of()));};
    AccessDeniedHandler denied=(req,res,e)->{res.setStatus(403);res.setContentType("application/json");json.writeValue(res.getOutputStream(),Map.of("status",403,"message","Not allowed","fieldErrors",List.of()));};
    var conv = new JwtGrantedAuthoritiesConverter();
    conv.setAuthoritiesClaimName("role");
    conv.setAuthorityPrefix("ROLE_");
    var jc = new JwtAuthenticationConverter();
    jc.setJwtGrantedAuthoritiesConverter(conv);
    h.csrf(c -> c.disable())
     .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
     .authorizeHttpRequests(a -> a
        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/public/**", "/api/health", "/error").permitAll()
        .requestMatchers("/api/forms/**").hasRole("ADMIN")
        .requestMatchers("/api/sections/**", "/api/dimensions/**", "/api/questions/**").hasRole("ADMIN")
        .requestMatchers("/api/fill/**", "/api/submissions/**").hasRole("RESPONDENT")
        .anyRequest().authenticated())
     .exceptionHandling(e->e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
     .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jc)));
    return h.build();
  }
}
