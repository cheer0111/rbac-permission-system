package cheer.common.filter;

import cheer.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 * <p>
 * 每次请求执行：
 * 1. 从 Authorization Header 提取 Bearer token
 * 2. 解析 token → 取出 userId、username、permissions
 * 3. 将 permissions 转为 GrantedAuthority 集合
 * 4. 封装成 UsernamePasswordAuthenticationToken 存入 SecurityContext
 * <p>
 * 无 token 或 token 无效时安静放行（不拒绝），后续 Security 会按路径规则判断是否需要认证
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // 无 token 或格式不对，直接放行
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            // token 无效或过期，安静放行
            filterChain.doFilter(request, response);
            return;
        }

        // 从 token 中提取用户信息
        Long userId = Long.valueOf(claims.getSubject());
        String userName = (String) claims.get("username");
        List<String> permissions = (List<String>) claims.get("permissions");

        // 权限字符串 → SimpleGrantedAuthority，供 @PreAuthorize 使用
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
