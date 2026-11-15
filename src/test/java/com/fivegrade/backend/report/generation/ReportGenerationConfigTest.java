package com.fivegrade.backend.report.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class ReportGenerationConfigTest {

    @Test
    void startsWithSingleGeneratorAndAssemblerWithoutOverrideOrApiKey() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setAllowBeanDefinitionOverriding(false);

            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "test",
                                    Map.of(
                                            "REPORT_AI_ENABLED", "false",
                                            "OPENAI_API_KEY", ""
                                    )
                            )
                    );

            context.register(
                    ReportGenerationConfig.class,
                    AssemblyConfig.class
            );

            context.refresh();

            assertEquals(
                    1,
                    context.getBeansOfType(
                            ReportGenerator.class
                    ).size()
            );
            assertEquals(
                    1,
                    context.getBeansOfType(
                            SimulationReportAssembler.class
                    ).size()
            );
            assertInstanceOf(
                    TemplateReportGenerator.class,
                    context.getBean(ReportGenerator.class)
            );
            assertSame(
                    context.getBean(ReportGenerator.class),
                    context.getBean("reportGenerator")
            );
        }
    }

    /** #66에서 설명 생성기 Bean을 제거한 뒤의 조립기 등록 계약을 재현한다. */
    @Configuration(proxyBeanMethods = false)
    static class AssemblyConfig {

        @Bean
        SimulationReportAssembler simulationReportAssembler(
                ReportGenerator generator
        ) {
            return new SimulationReportAssembler(generator);
        }
    }
}
