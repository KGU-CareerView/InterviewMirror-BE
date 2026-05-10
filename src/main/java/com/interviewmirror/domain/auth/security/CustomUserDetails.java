package com.interviewmirror.domain.auth.security;

import com.interviewmirror.domain.user.entity.User;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class CustomUserDetails implements UserDetails {

  private final Long id;
  private final String email;
  private final String name;
  private final String password;

  public CustomUserDetails(User user) {
    this.id = user.getId();
    this.email = user.getEmail();
    this.name = user.getName();
    this.password = user.getPasswordHash();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return password;
  }
}
