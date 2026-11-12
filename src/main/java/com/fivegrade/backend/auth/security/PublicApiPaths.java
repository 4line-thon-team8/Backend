package com.fivegrade.backend.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class PublicApiPaths {

    private PublicApiPaths() {}

    private static final PathPatternRequestMatcher.Builder PATHS =
            PathPatternRequestMatcher.withDefaults();

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, "/api/auth/signup"),
            PATHS.matcher(HttpMethod.POST, "/api/auth/login"),
            PATHS.matcher(HttpMethod.GET, "/api/majors"),
            PATHS.matcher(HttpMethod.GET, "/api/majors/**"),
            PATHS.matcher(HttpMethod.GET, "/api/subjects"),
            PATHS.matcher(HttpMethod.GET, "/api/subjects/**")
    );
}
