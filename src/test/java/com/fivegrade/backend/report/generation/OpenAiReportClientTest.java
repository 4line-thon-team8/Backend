package com.fivegrade.backend.report.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class OpenAiReportClientTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsPinnedModelStoreFalseAndStrictSchema() throws Exception {
        var captured = new AtomicReference<String>();

        start(
                200,
                completed("{\"closing\":\"가상 설명\"}"),
                captured
        );

        var result = client(Duration.ofSeconds(2))
                .generate(Map.of("closing", "안내 원문"));

        assertEquals("가상 설명", result.get("closing"));

        var body = json.readTree(captured.get());

        assertEquals(
                "gpt-4.1-mini-2025-04-14",
                body.path("model").asString()
        );
        assertFalse(body.path("store").asBoolean());
        assertEquals(
                3500,
                body.path("max_output_tokens").asInt()
        );
        assertTrue(
                body.path("text")
                        .path("format")
                        .path("strict")
                        .asBoolean()
        );
        assertFalse(
                body.path("text")
                        .path("format")
                        .path("schema")
                        .path("additionalProperties")
                        .asBoolean()
        );
        assertFalse(body.has("user"));
        assertFalse(body.has("conversation"));
        assertFalse(body.has("previous_response_id"));
    }

    @Test
    void rejectsHttpFailure() throws Exception {
        start(
                429,
                "private provider message",
                new AtomicReference<>()
        );

        assertThrows(
                IllegalStateException.class,
                () -> client(Duration.ofSeconds(2))
                        .generate(Map.of("closing", "원문"))
        );
    }

    @Test
    void rejectsIncompleteResponse() throws Exception {
        start(
                200,
                "{\"status\":\"incomplete\",\"output\":[]}",
                new AtomicReference<>()
        );

        assertThrows(
                IllegalStateException.class,
                () -> client(Duration.ofSeconds(2))
                        .generate(Map.of("closing", "원문"))
        );
    }

    @Test
    void rejectsRefusal() throws Exception {
        String body = json.writeValueAsString(
                Map.of(
                        "status", "completed",
                        "output", List.of(
                                Map.of(
                                        "type", "message",
                                        "content", List.of(
                                                Map.of(
                                                        "type", "refusal",
                                                        "refusal", "거부"
                                                )
                                        )
                                )
                        )
                )
        );

        start(200, body, new AtomicReference<>());

        assertThrows(
                IllegalStateException.class,
                () -> client(Duration.ofSeconds(2))
                        .generate(Map.of("closing", "원문"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "{} {}",
            "{\"closing\":\"a\",\"closing\":\"b\"}",
            "[]"
    })
    void rejectsMalformedStructuredOutput(String output)
            throws Exception {
        start(
                200,
                completed(output),
                new AtomicReference<>()
        );

        assertThrows(
                Exception.class,
                () -> client(Duration.ofSeconds(2))
                        .generate(Map.of("closing", "원문"))
        );
    }

    @Test
    void rejectsOversizedResponseDuringReceipt() throws Exception {
        start(
                200,
                "x".repeat(70000),
                new AtomicReference<>()
        );

        var failure = assertThrows(
                AiReportFailure.class,
                () -> client(Duration.ofSeconds(2))
                        .generate(Map.of("closing", "원문"))
        );

        assertEquals(
                AiReportFailure.Reason.RESPONSE_TOO_LARGE,
                failure.reason()
        );
        assertNull(failure.getCause());
    }

    @Test
    void deadlineIncludesResponseBody() throws Exception {
        var release = new CountDownLatch(1);

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/v1/responses", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        server.start();

        try {
            var failure = assertThrows(
                    AiReportFailure.class,
                    () -> client(Duration.ofMillis(200))
                            .generate(Map.of("closing", "원문"))
            );

            assertEquals(
                    AiReportFailure.Reason.TIMEOUT,
                    failure.reason()
            );
        } finally {
            release.countDown();
        }
    }

    @Test
    void disabledNeedsNoKeyAndEnabledMissingKeyFails() {
        var config = new ReportGenerationConfig();

        assertInstanceOf(
                TemplateReportGenerator.class,
                config.reportGenerator(
                        false,
                        "",
                        "gpt-4.1-mini-2025-04-14",
                        12000,
                        4
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> config.reportGenerator(
                        true,
                        "",
                        "gpt-4.1-mini-2025-04-14",
                        12000,
                        4
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "401,AUTH_ERROR",
            "403,AUTH_ERROR",
            "429,RATE_OR_QUOTA_LIMIT",
            "408,TIMEOUT",
            "504,TIMEOUT",
            "500,PROVIDER_ERROR",
            "503,PROVIDER_ERROR",
            "400,HTTP_ERROR",
            "302,HTTP_ERROR"
    })
    void exposesOnlyAllowlistedFailureReason(
            int status,
            AiReportFailure.Reason expected
    ) throws Exception {
        start(
                status,
                "SECRET_PROVIDER_BODY average=2.34 token=fake-secret",
                new AtomicReference<>()
        );

        var failure = assertThrows(
                AiReportFailure.class,
                () -> client(Duration.ofSeconds(2))
                        .generate(Map.of("closing", "원문"))
        );

        assertEquals(expected, failure.reason());
        assertEquals(expected.name(), failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void configuredConcurrencyRejectsSecondCallWithoutSendingIt()
            throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/v1/responses", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                entered.countDown();
                release.await();

                byte[] bytes = completed(
                        "{\"closing\":\"설명\"}"
                ).getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        server.start();

        var limited = new OpenAiReportClient(
                HttpClient.newHttpClient(),
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()
                                + "/v1/responses"
                ),
                "fake-key",
                "fake-model",
                Duration.ofSeconds(5),
                1
        );

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(
                    () -> limited.generate(
                            Map.of("closing", "원문")
                    )
            );

            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));

                var failure = assertThrows(
                        AiReportFailure.class,
                        () -> limited.generate(
                                Map.of("closing", "원문")
                        )
                );

                assertEquals(
                        AiReportFailure.Reason.CAPACITY_LIMIT,
                        failure.reason()
                );
            } finally {
                release.countDown();
            }

            assertEquals(
                    "설명",
                    first.get(3, TimeUnit.SECONDS).get("closing")
            );
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 33})
    void rejectsInvalidConcurrencyConfiguration(int limit) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAiReportClient(
                        "fake-key",
                        "fake-model",
                        Duration.ofSeconds(1),
                        limit
                )
        );
    }

    private void start(
            int status,
            String body,
            AtomicReference<String> captured
    ) throws Exception {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/v1/responses", exchange -> {
            captured.set(
                    new String(
                            exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.start();
    }

    private String completed(String text) {
        return json.writeValueAsString(
                Map.of(
                        "status", "completed",
                        "output", List.of(
                                Map.of(
                                        "type", "message",
                                        "content", List.of(
                                                Map.of(
                                                        "type", "output_text",
                                                        "text", text
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private OpenAiReportClient client(Duration timeout) {
        return new OpenAiReportClient(
                HttpClient.newHttpClient(),
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()
                                + "/v1/responses"
                ),
                "fake-key",
                "gpt-4.1-mini-2025-04-14",
                timeout
        );
    }
}
