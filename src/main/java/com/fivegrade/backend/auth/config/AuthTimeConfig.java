package com.fivegrade.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 인증 토큰의 발급·만료 판정에 사용할 서버 시각.
 *
 * Clock을 주입하면 테스트에서 시간을 고정해
 * 만료 직전·정각·이후를 기다림 없이 검증할 수 있다.
 */
@Configuration(proxyBeanMethods = false)
public class AuthTimeConfig {

    @Bean("authTokenClock")
    public Clock authTokenClock() {
        return Clock.systemUTC();
    }
}