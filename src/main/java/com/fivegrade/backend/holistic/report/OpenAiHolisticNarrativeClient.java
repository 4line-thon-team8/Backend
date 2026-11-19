package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.ParsedNarrative.EvidenceLine;
import com.fivegrade.backend.holistic.report.ParsedNarrative.MajorLine;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import com.fivegrade.backend.report.generation.AiReportFailure;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responses API로 활동 요약·학과별 조언 초안을 한 번 받는다. 응답·오류 본문을 로그에 남기지 않는다.
 * 요청은 store=false, 엄격한 JSON 스키마(문장 + 근거 값)를 쓴다.
 */
public final class OpenAiHolisticNarrativeClient implements HolisticNarrativeAiClient {

	private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");
	private static final int MAX_OUTPUT_TEXT = 18000;
	private static final int MAX_ITEMS = 8;

	private final JsonMapper json = JsonMapper.builder()
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.build();

	private final HttpClient http;
	private final URI endpoint;
	private final String apiKey;
	private final String model;
	private final Duration timeout;
	private final Semaphore permits;

	public OpenAiHolisticNarrativeClient(String apiKey, String model, Duration timeout, int maxConcurrent) {
		this(HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build(), ENDPOINT, apiKey, model, timeout, maxConcurrent);
	}

	/** 테스트에서 로컬 HTTP 대역을 주입한다. */
	OpenAiHolisticNarrativeClient(HttpClient http, URI endpoint, String apiKey, String model,
			Duration timeout, int maxConcurrent) {
		if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
			throw new IllegalArgumentException("AI credentials and model must be configured");
		}
		if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
				|| timeout.compareTo(Duration.ofSeconds(30)) > 0) {
			throw new IllegalArgumentException("AI timeout must be between 1ms and 30s");
		}
		if (maxConcurrent < 1 || maxConcurrent > 32) {
			throw new IllegalArgumentException("AI concurrency must be between 1 and 32");
		}
		this.http = Objects.requireNonNull(http);
		this.endpoint = Objects.requireNonNull(endpoint);
		this.apiKey = apiKey;
		this.model = model;
		this.timeout = timeout;
		this.permits = new Semaphore(maxConcurrent);
	}

	@Override
	public ParsedNarrative generate(Map<String, Object> input) throws Exception {
		if (!permits.tryAcquire()) {
			throw new AiReportFailure(AiReportFailure.Reason.CAPACITY_LIMIT);
		}
		try {
			Map<String, Object> body = Map.of(
					"model", model,
					"store", false,
					"max_output_tokens", 3000,
					"instructions", HolisticNarrativePrompt.INSTRUCTIONS,
					"input", json.writeValueAsString(input),
					"text", Map.of("format", Map.of(
							"type", "json_schema",
							"name", "holistic_narrative",
							"strict", true,
							"schema", schema())));

			HttpRequest request = HttpRequest.newBuilder(endpoint)
					.timeout(timeout)
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
					.build();

			var pending = http.sendAsync(request, info -> new OpenAiHolisticReportClient.LimitedBody());
			try {
				HttpResponse<String> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
				if (response.statusCode() != 200) {
					throw new AiReportFailure(AiReportFailure.forHttpStatus(response.statusCode()));
				}
				return decode(response.body());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new AiReportFailure(AiReportFailure.Reason.INTERRUPTED);
			} catch (ExecutionException | TimeoutException unavailable) {
				throw new AiReportFailure(AiReportFailure.reasonOf(unavailable));
			} finally {
				if (!pending.isDone()) {
					pending.cancel(true);
				}
			}
		} catch (AiReportFailure failure) {
			throw failure;
		} catch (Exception unavailable) {
			throw new AiReportFailure(AiReportFailure.reasonOf(unavailable));
		} finally {
			permits.release();
		}
	}

	/** 글 하나의 스키마. 학과 조언은 majorCode, 강점 근거는 evidenceIds 가 더 붙는다. */
	private static Map<String, Object> lineSchema(String extraKey, Map<String, Object> extraSchema) {
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();
		if (extraKey != null) {
			properties.put(extraKey, extraSchema);
			required.add(extraKey);
		}
		properties.put("text", Map.of("type", "string"));
		properties.put("refs", Map.of("type", "array", "items", Map.of("type", "string")));
		required.add("text");
		required.add("refs");
		return Map.of(
				"type", "object",
				"properties", properties,
				"required", required,
				"additionalProperties", false);
	}

	private static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("activitySummary", lineSchema(null, null));
		properties.put("overview", lineSchema(null, null));
		properties.put("strengthEvidence", Map.of("type", "array", "items", lineSchema(
				"evidenceIds", Map.of("type", "array", "items", Map.of("type", "string")))));
		properties.put("improvements", Map.of("type", "array", "items", lineSchema(
				"evidenceIds", Map.of("type", "array", "items", Map.of("type", "string")))));
		properties.put("nextSteps", Map.of("type", "array", "items", lineSchema(
				"evidenceIds", Map.of("type", "array", "items", Map.of("type", "string")))));
		properties.put("majorAdvices", Map.of("type", "array", "items", lineSchema(
				"majorCode", Map.of("type", "string"))));
		return Map.of(
				"type", "object",
				"properties", properties,
				"required", List.of("activitySummary", "overview", "strengthEvidence", "improvements", "nextSteps",
						"majorAdvices"),
				"additionalProperties", false);
	}

	private ParsedNarrative decode(String body) {
		try {
			JsonNode root = json.readTree(body);
			if (root == null || !"completed".equals(root.path("status").asString())) {
				throw invalid();
			}
			JsonNode output = root.path("output");
			if (!output.isArray() || output.isEmpty()) {
				throw invalid();
			}
			String text = null;
			for (JsonNode item : output) {
				if (!"message".equals(item.path("type").asString())) {
					throw invalid();
				}
				JsonNode content = item.path("content");
				if (!content.isArray() || content.isEmpty()) {
					throw invalid();
				}
				for (JsonNode part : content) {
					if (!"output_text".equals(part.path("type").asString())
							|| !part.path("text").isString() || text != null) {
						throw invalid();
					}
					text = part.path("text").asString();
				}
			}
			if (text == null || text.isBlank() || text.length() > MAX_OUTPUT_TEXT) {
				throw invalid();
			}
			JsonNode parsed = json.readTree(text);
			if (parsed == null || !parsed.isObject()) {
				throw invalid();
			}
			List<EvidenceLine> strengthEvidence = evidenceLines(parsed.path("strengthEvidence"));
			List<EvidenceLine> improvements = evidenceLines(parsed.path("improvements"));
			List<EvidenceLine> nextSteps = evidenceLines(parsed.path("nextSteps"));
			JsonNode advices = parsed.path("majorAdvices");
			if (!advices.isArray() || advices.size() > MAX_ITEMS) {
				throw invalid();
			}
			List<MajorLine> majorAdvices = new ArrayList<>();
			for (JsonNode item : advices) {
				if (!item.path("majorCode").isString()) {
					throw invalid();
				}
				majorAdvices.add(new MajorLine(item.path("majorCode").asString(), line(item)));
			}
			return new ParsedNarrative(line(parsed.path("activitySummary")), line(parsed.path("overview")),
					strengthEvidence, majorAdvices, improvements, nextSteps);
		} catch (AiReportFailure failure) {
			throw failure;
		} catch (RuntimeException malformed) {
			// 파싱 예외는 응답 일부를 담을 수 있어 원인을 전달하지 않는다.
			throw invalid();
		}
	}

	/** 근거 구절 번호(evidenceIds)가 붙은 문장 배열을 읽는다. 배열이 아니거나 너무 길면 응답 전체를 거부한다. */
	private static List<EvidenceLine> evidenceLines(JsonNode nodes) {
		if (!nodes.isArray() || nodes.size() > MAX_ITEMS) {
			throw invalid();
		}
		List<EvidenceLine> lines = new ArrayList<>();
		for (JsonNode item : nodes) {
			List<String> ids = new ArrayList<>();
			if (!item.path("evidenceIds").isArray()) {
				throw invalid();
			}
			for (JsonNode id : item.path("evidenceIds")) {
				if (!id.isString()) {
					throw invalid();
				}
				ids.add(id.asString());
			}
			lines.add(new EvidenceLine(line(item), ids));
		}
		return lines;
	}

	private static Line line(JsonNode node) {
		if (!node.isObject() || !node.path("text").isString() || !node.path("refs").isArray()) {
			throw invalid();
		}
		List<String> refs = new ArrayList<>();
		for (JsonNode ref : node.path("refs")) {
			if (!ref.isString()) {
				throw invalid();
			}
			refs.add(ref.asString());
		}
		return new Line(node.path("text").asString(), refs);
	}

	private static AiReportFailure invalid() {
		return new AiReportFailure(AiReportFailure.Reason.INVALID_RESPONSE);
	}
}
