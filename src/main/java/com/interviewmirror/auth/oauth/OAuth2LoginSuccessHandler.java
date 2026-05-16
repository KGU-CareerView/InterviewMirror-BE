package com.interviewmirror.auth.oauth;

import com.interviewmirror.auth.service.OAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final OAuthService oauthService;

  @Value("${oauth.success-redirect-url}")
  private String successRedirectUrl;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

    String code = oauthService.issueOAuthCode(oauth2User);
    String redirectUrl = createRedirectUrl(code);

    log.info("OAuth login success. Redirect to {}", successRedirectUrl);
    response.sendRedirect(redirectUrl);
  }

  private String createRedirectUrl(String code) {
    String separator = successRedirectUrl.contains("?") ? "&" : "?";
    String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);

    return successRedirectUrl + separator + "code=" + encodedCode;
  }
}
