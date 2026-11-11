package com.fivegrade.backend.auth.config;

import com.fivegrade.backend.auth.security.ApiCorsProcessor;
import com.fivegrade.backend.auth.security.AuthErrorWriter;
import com.fivegrade.backend.auth.security.BearerTokenFilter;
import com.fivegrade.backend.auth.security.PublicApiPaths;
import com.fivegrade.backend.auth.service.AccessTokenService;
import com.fivegrade.backend.common.error.ErrorCode;

import jakarta.servlet.DispatcherType;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            AccessTokenService tokens,
            AuthErrorWriter errors,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        corsFilter.setCorsProcessor(new ApiCorsProcessor(errors));
        http
                // 쿠키·HTTP Basic 인증을 사용하지 않고 Authorization 헤더만 사용한다.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errors.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                errors.write(response, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PublicApiPaths.MATCHER).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // 문서 노출은 기존 OPENAPI_ENABLED 설정이 제어한다.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(new BearerTokenFilter(tokens, errors),
                        AnonymousAuthenticationFilter.class)
                .addFilterBefore(corsFilter, BearerTokenFilter.class);
        return http.build();
    }

    /** 기본 사용자와 임의 비밀번호 자동 생성을 막는다. 인증은 DB 토큰 필터가 담당한다. */
    @Bean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("Unsupported authentication method");
        };
    }

    @Bean
    WebSecurityCustomizer requestRejectionHandler(AuthErrorWriter errors) {
        return web -> web.requestRejectedHandler((request, response, exception) ->
                errors.write(response, ErrorCode.INVALID_REQUEST));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (origins.stream().anyMatch(value -> value.contains("*"))) {
            throw new IllegalArgumentException("CORS origins must be explicit");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key", "Accept"
        ));
        config.setAllowCredentials(false);
        config.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
