package com.hub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.common.Result;
import com.hub.common.constant.RedisKeyConstants;
import com.hub.exception.ForbiddenException;
import com.hub.exception.RateLimitException;
import com.hub.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * JWT 鉴权过滤器。
 * <p>
 * 用户请求按以下顺序校验：
 * <ol>
 *   <li>解析 JWT → userId / sessionId / tokenVersion</li>
 *   <li>封禁检查：Redis user:ban:{userId} 命中 → 403；Redis 故障 → DB 兜底</li>
 *   <li>Token 版本检查：JWT.ver != Redis.tokenVersion → 401 "令牌已失效"</li>
 *   <li>会话检查（单设备）：JWT.sid != Redis.sessionId → 401 "账号已在其他设备登录"</li>
 *   <li>限流检查</li>
 * </ol>
 */
@Slf4j
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
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            BanCheckService banCheckService,
            RateLimitService rateLimitService,
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.banCheckService = banCheckService;
        this.rateLimitService = rateLimitService;
        this.stringRedisTemplate = stringRedisTemplate;
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
                // ── Step 0: 解析 JWT ──────────────────────────────
                JwtTokenProvider.TokenInfo token = jwtTokenProvider.parseUserToken(auth);
                long userId = token.userId();

                // ── Step 1: 封禁检查 ────────────────────────────────
                // Redis 命中 → 403；Redis 故障 → 回源 DB 兜底
                banCheckService.checkNotBanned(userId);

                // ── Step 2: Token 版本校验 ──────────────────────────
                // Redis 故障 → fail-open（不阻断正常业务）
                try {
                    String versionKey = RedisKeyConstants.userTokenVersion(userId);
                    String redisVersion = stringRedisTemplate.opsForValue().get(versionKey);
                    if (redisVersion != null) {
                        long serverVer = Long.parseLong(redisVersion);
                        if (token.tokenVersion() != serverVer) {
                            throw new UnauthorizedException("令牌已失效");
                        }
                    }
                } catch (UnauthorizedException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("Redis 不可用，跳过 Token 版本校验，userId={}", userId, e);
                }

                // ── Step 3: 会话校验（单设备登录互踢） ──────────────
                // Redis 故障 → fail-open
                try {
                    String sessionKey = RedisKeyConstants.userSession(userId);
                    String activeSession = stringRedisTemplate.opsForValue().get(sessionKey);
                    if (activeSession != null && !activeSession.equals(token.sessionId())) {
                        throw new UnauthorizedException("账号已在其他设备登录");
                    }
                } catch (UnauthorizedException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("Redis 不可用，跳过会话校验，userId={}", userId, e);
                }

                // ── Step 4: 限流检查 ────────────────────────────────
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

    private boolean isSwaggerUiPath(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        if ("/api/doc.html".equals(path)) return true;
        return path.startsWith("/api/swagger-ui/");
    }

    private boolean isPublic(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            if ("/api/item/list".equals(path)) return true;
            if (path.matches("^/api/item/\\d+$")) return true;
        }
        return "POST".equalsIgnoreCase(method) && PUBLIC_EXACT.contains(path);
    }
}
