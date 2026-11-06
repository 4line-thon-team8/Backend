package com.fivegrade.backend.common.openapi;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.TestcontainersConfiguration;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * OpenAPI 문서(JSON)와 Swagger UI 가 하나의 스위치(OPENAPI_ENABLED)로 열리고 닫히는지 확인한다.
 * 개발자 PC 의 .env 에 영향받지 않도록 컨텍스트 테스트는 두 경우 모두 OPENAPI_ENABLED 값을 직접 지정한다.
 * (테스트에서 지정한 값은 .env 와 OS 환경변수보다 우선한다.)
 */
class OpenApiExposureTest {

	@Nested
	@Import(TestcontainersConfiguration.class)
	@SpringBootTest(properties = "OPENAPI_ENABLED=true")
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

		@Test
		void Swagger_UI_화면은_열린다() throws Exception {
			MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

			// springdoc 이 /swagger-ui.html 을 UI 화면으로 보낸다(리다이렉트). 404 가 아니면 열려 있는 것이다.
			mockMvc.perform(get("/swagger-ui.html"))
					.andExpect(status().is(not(404)));
		}

	}

	@Nested
	@Import(TestcontainersConfiguration.class)
	@SpringBootTest(properties = "OPENAPI_ENABLED=false")
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

		@Test
		void Swagger_UI_화면도_404_NOT_FOUND_이다() throws Exception {
			MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

			mockMvc.perform(get("/swagger-ui.html"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));

			mockMvc.perform(get("/swagger-ui/index.html"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}

	}

	/**
	 * application.yaml 만 읽어서 스위치 연결을 확인한다. 스프링 컨텍스트와 DB 가 필요 없고,
	 * 개발자 PC 의 .env·OS 환경변수가 섞이지 않는다.
	 */
	@Nested
	class 설정_파일의_스위치 {

		@Test
		void 스위치를_지정하지_않으면_JSON과_UI_모두_꺼진다() {
			MockEnvironment environment = applicationYamlEnvironment();

			assertEquals(false, environment.getProperty("springdoc.api-docs.enabled", Boolean.class));
			assertEquals(false, environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class));
		}

		@Test
		void OPENAPI_ENABLED_하나로_JSON과_UI가_함께_켜진다() {
			MockEnvironment environment = applicationYamlEnvironment().withProperty("OPENAPI_ENABLED", "true");

			assertEquals(true, environment.getProperty("springdoc.api-docs.enabled", Boolean.class));
			assertEquals(true, environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class));
		}

		@Test
		void OPENAPI_ENABLED_false면_JSON과_UI가_함께_꺼진다() {
			MockEnvironment environment = applicationYamlEnvironment().withProperty("OPENAPI_ENABLED", "false");

			assertEquals(false, environment.getProperty("springdoc.api-docs.enabled", Boolean.class));
			assertEquals(false, environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class));
		}

		private MockEnvironment applicationYamlEnvironment() {
			YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
			yaml.setResources(new ClassPathResource("application.yaml"));
			Properties properties = yaml.getObject();

			MockEnvironment environment = new MockEnvironment();
			environment.getPropertySources().addLast(new PropertiesPropertySource("application.yaml", properties));
			return environment;
		}

	}

}
