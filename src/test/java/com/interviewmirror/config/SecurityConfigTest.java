package com.interviewmirror.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewmirror.auth.jwt.JwtAuthenticationFilter;
import com.interviewmirror.auth.oauth.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "jwt.secret=change-this-secret-key-change-this-secret-key-123456789",
      "jwt.access-token-expiration-ms=3600000",
      "jwt.refresh-token-expiration-ms=604800000",
      "oauth.success-redirect-url=http://localhost:5174",
      "oauth.code-expiration-ms=180000"
    })
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

  @Test
  @DisplayName("localhost:5174 로그인 preflight 요청을 허용한다")
  void allowLoginPreflightFromLocalhost5174() throws Exception {
    mockMvc
        .perform(
            options("/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, authorization"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH,DELETE,PUT,OPTIONS"))
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type, authorization"));
  }
}
