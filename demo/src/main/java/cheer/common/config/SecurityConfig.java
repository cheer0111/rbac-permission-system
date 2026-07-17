package cheer.common.config;

import cheer.common.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类
 * <p>
 * - 无状态 Session（STATELESS）
 * - 自定义 JwtFilter 在 UsernamePasswordAuthenticationFilter 之前执行
 * - @EnableMethodSecurity 开启 @PreAuthorize 方法级权限控制
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Autowired
    private JwtAuthenticationFilter filter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login").permitAll()     // 登录接口放行
                        .requestMatchers("/druid/**").permitAll()       // Druid 监控页放行
                        .requestMatchers("/doc.html").permitAll()       // Knife4j 文档页放行
                        .requestMatchers("/swagger-ui/**").permitAll()   // Swagger UI 放行
                        .requestMatchers("/v3/api-docs/**").permitAll()  // OpenAPI 文档放行
                        .requestMatchers("/webjars/**").permitAll()      // 静态资源放行
                        .anyRequest().authenticated()                  // 其余请求需认证
                )
        ;
        return http.build();
    }

    /**
     * 密码编码器（BCrypt），用于登录校验和注册加密
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
