package com.fivegrade.backend.holistic.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.Coverage;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.EvidencePhrase;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.IndicatorSignal;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.InterestTag;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.SectionChars;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * holisticAnalysis 요청 DTO의 형식 검증을 확인한다. DB나 Spring 없이 실행한다.
 */
class HolisticAnalysisRequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void 정상_요청은_통과한다() {
		assertThat(validator.validate(valid())).isEmpty();
	}

	@Test
	void 빈_목록도_통과한다() {
		HolisticAnalysisRequest request = new HolisticAnalysisRequest(
				"0.1.0", coverage(), List.of(), List.of(), List.of());

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void 규칙_버전과_영역_글자_수는_필수이다() {
		HolisticAnalysisRequest noVersion = new HolisticAnalysisRequest(
				" ", coverage(), List.of(), List.of(), List.of());
		HolisticAnalysisRequest noCoverage = new HolisticAnalysisRequest(
				"0.1.0", null, List.of(), List.of(), List.of());
		HolisticAnalysisRequest missingChars = new HolisticAnalysisRequest(
				"0.1.0", new Coverage(new SectionChars(1, 1, 1, null, 1), List.of(1)),
				List.of(), List.of(), List.of());

		assertThat(violatedPaths(noVersion)).contains("rulesVersion");
		assertThat(violatedPaths(noCoverage)).contains("coverage");
		assertThat(violatedPaths(missingChars)).anyMatch(path -> path.contains("sectionChars.subject"));
	}

	@Test
	void 글자_수와_학년_범위를_벗어나면_거부한다() {
		HolisticAnalysisRequest negative = new HolisticAnalysisRequest(
				"0.1.0", new Coverage(new SectionChars(-1, 0, 0, 0, 0), List.of(1)),
				List.of(), List.of(), List.of());
		HolisticAnalysisRequest badYear = new HolisticAnalysisRequest(
				"0.1.0", new Coverage(new SectionChars(1, 1, 1, 1, 1), List.of(4)),
				List.of(), List.of(), List.of());

		assertThat(violatedPaths(negative)).anyMatch(path -> path.contains("autonomy"));
		assertThat(violatedPaths(badYear)).anyMatch(path -> path.contains("years"));
	}

	@Test
	void 지표_신호의_범위를_검사한다() {
		assertThat(violatedPaths(withSignal(new IndicatorSignal("A3", -1, List.of(1), 0.5))))
				.anyMatch(path -> path.contains("count"));
		assertThat(violatedPaths(withSignal(new IndicatorSignal("A3", 1, List.of(1), 1.5))))
				.anyMatch(path -> path.contains("quality"));
		assertThat(violatedPaths(withSignal(new IndicatorSignal("A3", 1, List.of(1), -0.1))))
				.anyMatch(path -> path.contains("quality"));
		assertThat(violatedPaths(withSignal(new IndicatorSignal("A3", 1, List.of(), 0.5))))
				.anyMatch(path -> path.contains("years"));
		assertThat(violatedPaths(withSignal(new IndicatorSignal("", 1, List.of(1), 0.5))))
				.anyMatch(path -> path.contains("id"));
	}

	@Test
	void 지표_신호는_최대_20개이다() {
		List<IndicatorSignal> signals = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			signals.add(new IndicatorSignal("A" + i, 1, List.of(1), 0.5));
		}
		HolisticAnalysisRequest request = new HolisticAnalysisRequest(
				"0.1.0", coverage(), signals, List.of(), List.of());

		assertThat(violatedPaths(request)).contains("indicatorSignals");
	}

	@Test
	void 근거_구절은_30자_이하_최대_150개이다() {
		EvidencePhrase longText = new EvidencePhrase(List.of("METHOD"), "가".repeat(31), 1, "club");
		assertThat(violatedPaths(withEvidence(List.of(longText)))).anyMatch(path -> path.contains("text"));

		List<EvidencePhrase> many = new ArrayList<>();
		for (int i = 0; i < 151; i++) {
			many.add(new EvidencePhrase(List.of("METHOD"), "구절" + i, 1, "club"));
		}
		assertThat(violatedPaths(withEvidence(many))).contains("evidence");
	}

	@Test
	void 근거_구절의_유형과_영역_형식을_검사한다() {
		assertThat(violatedPaths(withEvidence(List.of(new EvidencePhrase(List.of(), "구절", 1, "club")))))
				.anyMatch(path -> path.contains("types"));
		assertThat(violatedPaths(withEvidence(List.of(new EvidencePhrase(List.of("METHOD"), "구절", 1, "home")))))
				.anyMatch(path -> path.contains("section"));
		assertThat(violatedPaths(withEvidence(List.of(new EvidencePhrase(List.of("METHOD"), "구절", 4, "club")))))
				.anyMatch(path -> path.contains("year"));
	}

	@Test
	void 학년을_모르는_구절은_year가_null이어도_통과한다() {
		EvidencePhrase noYear = new EvidencePhrase(List.of("METHOD"), "구절", null, "club");

		assertThat(violatedPaths(withEvidence(List.of(noYear)))).isEmpty();
	}

	@Test
	void 관심_태그의_범위를_검사한다() {
		HolisticAnalysisRequest request = new HolisticAnalysisRequest(
				"0.1.0", coverage(), List.of(), List.of(new InterestTag("", -1, List.of())), List.of());

		assertThat(violatedPaths(request)).anyMatch(path -> path.contains("tag"));
		assertThat(violatedPaths(request)).anyMatch(path -> path.contains("count"));
	}

	@Test
	void toString에는_구절_본문이_나오지_않는다() {
		String secret = "홍길동 학생이 설문 120명을 조사";
		HolisticAnalysisRequest request = withEvidence(List.of(new EvidencePhrase(List.of("METRIC"), secret, 1, "club")));

		assertThat(request.toString()).doesNotContain("홍길동").doesNotContain(secret);
		assertThat(request.evidence().get(0).toString()).doesNotContain("홍길동").contains("METRIC");
	}

	private HolisticAnalysisRequest valid() {
		return new HolisticAnalysisRequest(
				"0.1.0",
				coverage(),
				List.of(new IndicatorSignal("A3", 6, List.of(1, 2), 0.62)),
				List.of(new InterestTag("컴퓨터", 9, List.of(1, 2))),
				List.of(new EvidencePhrase(List.of("METRIC", "METHOD"), "설문 120명 자료를 수집", 2, "subject")));
	}

	private Coverage coverage() {
		return new Coverage(new SectionChars(232, 238, 160, 584, 128), List.of(1, 2));
	}

	private HolisticAnalysisRequest withSignal(IndicatorSignal signal) {
		return new HolisticAnalysisRequest("0.1.0", coverage(), List.of(signal), List.of(), List.of());
	}

	private HolisticAnalysisRequest withEvidence(List<EvidencePhrase> evidence) {
		return new HolisticAnalysisRequest("0.1.0", coverage(), List.of(), List.of(), evidence);
	}

	private Set<String> violatedPaths(HolisticAnalysisRequest request) {
		Set<ConstraintViolation<HolisticAnalysisRequest>> violations = validator.validate(request);
		return violations.stream().map(v -> v.getPropertyPath().toString())
				.collect(java.util.stream.Collectors.toSet());
	}

}
