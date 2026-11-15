package com.fivegrade.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/** 비밀번호 전용 해시. 토큰 해시와 구분하며 원문은 저장하지 않는다. */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // salt 16B, hash 32B, parallelism 1, memory 19MiB, iterations 2.
        PasswordEncoder argon2 = new Argon2PasswordEncoder(
                16, 32, 1, 19 * 1024, 2
        );
        return new DelegatingPasswordEncoder(
                "argon2id-v1", Map.of("argon2id-v1", argon2)
        );
    }
}
