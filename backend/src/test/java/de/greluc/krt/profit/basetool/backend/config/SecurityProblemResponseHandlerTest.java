/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Unit tests for {@link SecurityProblemResponseHandler}: filter-level 401/403 rejections are handed
 * to the MVC {@code handlerExceptionResolver} (so {@code GlobalExceptionHandler} renders the
 * RFC&nbsp;7807 body), with a {@code sendError} fallback only when the resolver does not handle the
 * exception or the response is already committed.
 */
class SecurityProblemResponseHandlerTest {

  @Test
  void commence_delegatesAuthenticationExceptionToResolver() throws Exception {
    HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(new ModelAndView());
    SecurityProblemResponseHandler handler = new SecurityProblemResponseHandler(resolver);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException ex = new BadCredentialsException("invalid token");

    handler.commence(request, response, ex);

    verify(resolver).resolveException(eq(request), eq(response), isNull(), same(ex));
  }

  @Test
  void handle_delegatesAccessDeniedExceptionToResolver() throws Exception {
    HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(new ModelAndView());
    SecurityProblemResponseHandler handler = new SecurityProblemResponseHandler(resolver);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AccessDeniedException ex = new AccessDeniedException("denied");

    handler.handle(request, response, ex);

    verify(resolver).resolveException(eq(request), eq(response), isNull(), same(ex));
  }

  @Test
  void commence_fallsBackToSendErrorWhenResolverDoesNotHandle() throws Exception {
    HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(null);
    SecurityProblemResponseHandler handler = new SecurityProblemResponseHandler(resolver);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.commence(request, response, new BadCredentialsException("nope"));

    assertEquals(401, response.getStatus(), "fallback sendError uses the 401 status");
  }

  @Test
  void handle_skipsResolverWhenResponseAlreadyCommitted() throws Exception {
    HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    SecurityProblemResponseHandler handler = new SecurityProblemResponseHandler(resolver);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setCommitted(true);

    handler.handle(request, response, new AccessDeniedException("denied"));

    verify(resolver, never()).resolveException(any(), any(), any(), any());
  }
}
