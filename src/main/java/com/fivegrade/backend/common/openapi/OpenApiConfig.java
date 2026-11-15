package com.fivegrade.backend.common.openapi;

import com.fivegrade.backend.common.error.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서의 기본 정보와 공통 스키마.
 *
 * 문서는 코드에서 자동으로 만들어지는 참고 자료이고, 기획 기준의 API 명세서는 Notion 에 있다.
 * 문서 노출 여부는 application.yaml 의 springdoc.* 설정(OPENAPI_ENABLED)이 정하며 기본값은 꺼짐이다.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI fiveGradeOpenApi() {
		return new OpenAPI().info(new Info()
				.title("오등급 API")
				.version("v1")
				.description("2028 대입 5등급제 환산 시뮬레이터 백엔드 API. "
						+ "오류 응답은 모두 ErrorResponse(code, message) 형식이다."));
	}

	/**
	 * 모든 API 가 공통으로 쓰는 오류 응답을 문서에 등록한다.
	 * 컨트롤러가 이 타입을 직접 반환하지 않아도(예외 핸들러가 만들기 때문에) 스키마가 보이도록 한다.
	 */
	@Bean
	OpenApiCustomizer errorResponseSchemaCustomizer() {
		return openApi -> {
			Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
			ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);
			openApi.setComponents(components);
		};
	}

}
