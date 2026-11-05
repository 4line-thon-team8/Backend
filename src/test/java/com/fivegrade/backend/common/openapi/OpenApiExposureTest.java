package com.fivegrade.backend.common.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * OpenAPI 문서가 설정에 따라 열리고 닫히는지 확인한다.
 * 개발자 PC 의 .env(OPENAPI_ENABLED)에 영향받지 않도록 두 경우 모두 값을 테스트에서 직접 지정한다.
 */
class OpenApiExposureTest {

	@Nested
	@Import(TestcontainersConfiguration.class)
	@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
	class 켜져_있을_때 {

		@Autowired
		private WebApplicationContext context;

		@Test
		void 문서에_기본_정보와_ErrorResponse_스키마가_있다() throws Exception {
			MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

			mockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.info.title").value("오등급 API"))
					.andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
					.andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists());
		}

	}

	@Nested
	@Import(TestcontainersConfiguration.class)
	@SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
	class 꺼져_있을_때 {

		@Autowired
		private WebApplicationContext context;

		@Test
		void 문서는_404_NOT_FOUND_이다() throws Exception {
			MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

			mockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}

	}

}
