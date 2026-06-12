package com.hub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.common.Result;
import com.hub.exception.ForbiddenException;
import com.hub.exception.RateLimitException;
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
    private final BanCheckService banCheckService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            BanCheckService banCheckService,
            RateLimitService rateLimitService,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.banCheckService = banCheckService;
        this.rateLimitService = rateLimitService;
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
                // JWT 解析成功后立即检查 Redis 封禁标记（DB 兜底）
                banCheckService.checkNotBanned(userId);

                // 搜索接口按 userId 限流
                if ("POST".equalsIgnoreCase(method)) {
                    if ("/api/item/search".equals(path)) {
                        rateLimitService.checkTextSearchRateLimit(userId);
                    } else if ("/api/item/search/image".equals(path)) {
                        rateLimitService.checkImageSearchRateLimit(userId);
                    } else if ("/api/item".equals(path)) {
                        rateLimitService.checkItemCreateRateLimit(userId);
                    } else if ("/api/claim".equals(path)) {
                        rateLimitService.checkClaimCreateRateLimit(userId);
                    }
                }

                AuthContext.setUserId(userId);
            }

            filterChain.doFilter(request, response);
        } catch (RateLimitException e) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Result<Object> body = new Result<>(429, e.getMessage(), null);
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (ForbiddenException e) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Result<Object> body = new Result<>(HttpStatus.FORBIDDEN.value(), e.getMessage(), null);
            response.getWriter().write(objectMapper.writeValueAsString(body));
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
        if ("POST".equalsIgnoreCase(method) && PUBLIC_EXACT.contains(path)) {
            return true;
        }
        return false;
    }
}
