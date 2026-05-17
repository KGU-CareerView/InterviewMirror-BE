package com.interviewmirror.auth.service;

import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.user.entity.User;
import com.interviewmirror.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuthService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final OAuthCodeService oauthCodeService;

  @Transactional
  public String issueOAuthCode(OAuth2User oauth2User) {
    String email = oauth2User.getAttribute("email");
    String name = oauth2User.getAttribute("name");

    if (email == null || email.isBlank()) {
      throw new BusinessException(ErrorCode.OAUTH_EMAIL_NOT_FOUND);
    }

    User user = userRepository.findByEmail(email).orElseGet(() -> createOAuthUser(email, name));

    return oauthCodeService.saveCode(user.getId());
  }

  private User createOAuthUser(String email, String name) {
    String displayName = name == null || name.isBlank() ? email : name;

    User user =
        User.builder()
            .email(email)
            .name(displayName)
            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
            .build();

    return userRepository.save(user);
  }
}
