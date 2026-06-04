package com.hub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.common.Result;
import com.hub.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 分离校验用户 JWT 与管理员 JWT；公开接口放行。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/api/user/register",
            "/api/user/login",
            "/api/admin/login"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            String method = request.getMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (isSwaggerUiPath(path, method) || isPublic(path, method)) {
                filterChain.doFilter(request, response);
                return;
            }

            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (path.startsWith("/api/admin/")) {
                long adminId = jwtTokenProvider.parseAdminId(auth);
                AuthContext.setAdminId(adminId);
            } else {
                long userId = jwtTokenProvider.parseUserId(auth);
                AuthContext.setUserId(userId);
            }

            filterChain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Result<Object> body = new Result<>(HttpStatus.UNAUTHORIZED.value(), e.getMessage(), null);
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } finally {
            AuthContext.clear();
        }
    }

    /**
     * Swagger UI 与静态资源（springdoc 将 UI 入口配置在 /api/doc.html 时，静态资源可能位于 /api/swagger-ui/**）
     */
    private boolean isSwaggerUiPath(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/api/doc.html".equals(path)) {
            return true;
        }
        return path.startsWith("/api/swagger-ui/");
    }

    private boolean isPublic(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            if ("/api/item/list".equals(path)) {
                return true;
            }
            if (path.matches("^/api/item/\\d+$")) {
                return true;
            }
        }
        if ("POST".equalsIgnoreCase(method) && "/api/item/search".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/item/search/image".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && PUBLIC_EXACT.contains(path)) {
            return true;
        }
        return false;
    }
}
