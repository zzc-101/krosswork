package com.kross.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class CsrfCookieFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional.ofNullable(request.getAttribute(CsrfToken.class.getName()))
        .filter(CsrfToken.class::isInstance)
        .map(CsrfToken.class::cast)
        .ifPresent(CsrfToken::getToken);
    filterChain.doFilter(request, response);
  }
}
