package com.fivegrade.backend.holistic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.Citation;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.Coverage;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.EvidencePhrase;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.IndicatorSignal;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.InterestTag;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.SectionChars;
import com.fivegrade.backend.holistic.rules.HolisticRuleCatalog;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 사전과 대조하는 holisticAnalysis 검증을 확인한다. DB 없이 태그 사전은 고정값으로 바꿔서 실행한다.
 */
class HolisticRequestValidatorTest {

	private static final String SECRET = "비밀구절-홍길동";

	private final HolisticRequestValidator validator =
			new HolisticRequestValidator(new HolisticRuleCatalog(), () -> Set.of("컴퓨터", "화학"));

	@Test
	void 사전에_있는_값은_통과한다() {
		assertThatCode(() -> validator.validate(request(
				List.of(new IndicatorSignal("A3", 6, List.of(1, 2), 0.6), new IndicatorSignal("C1", 3, List.of(1), 0.4)),
				List.of(new InterestTag("컴퓨터", 9, List.of(1))),
				List.of(new EvidencePhrase(List.of("METRIC", "METHOD"), "설문 120명", 2, "subject")),
				sectionChars(100, 100, 100, 100, 100)))).doesNotThrowAnyException();
	}

	@Test
	void 사전에_없는_지표는_거부한다() {
		assertInvalid(request(List.of(new IndicatorSignal("ZZ", 1, List.of(1), 0.5)), List.of(), List.of(),
				sectionChars(1, 1, 1, 1, 1)), "indicatorSignals.id");
	}

	@Test
	void 같은_지표가_겹치면_거부한다() {
		assertInvalid(request(
				List.of(new IndicatorSignal("A3", 1, List.of(1), 0.5), new IndicatorSignal("A3", 2, List.of(2), 0.5)),
				List.of(), List.of(), sectionChars(1, 1, 1, 1, 1)), "indicatorSignals.id");
	}

	@Test
	void 학년이_겹치면_거부한다() {
		assertInvalid(request(List.of(new IndicatorSignal("A3", 1, List.of(1, 1), 0.5)), List.of(), List.of(),
				sectionChars(1, 1, 1, 1, 1)), "indicatorSignals.years");
	}

	@Test
	void 사전에_없는_태그는_거부한다() {
		assertInvalid(request(List.of(), List.of(new InterestTag("없는태그", 1, List.of(1))), List.of(),
				sectionChars(1, 1, 1, 1, 1)), "interestTags.tag");
	}

	@Test
	void 같은_태그가_겹치면_거부한다() {
		assertInvalid(request(List.of(),
				List.of(new InterestTag("컴퓨터", 1, List.of(1)), new InterestTag("컴퓨터", 2, List.of(2))),
				List.of(), sectionChars(1, 1, 1, 1, 1)), "interestTags.tag");
	}

	@Test
	void 사전에_없는_구절_유형은_거부한다() {
		assertInvalid(request(List.of(), List.of(),
				List.of(new EvidencePhrase(List.of("UNKNOWN"), "구절", 1, "club")),
				sectionChars(1, 1, 1, 1, 1)), "evidence.types");
	}

	@Test
	void 구절_안에서_같은_유형이_겹치면_거부한다() {
		assertInvalid(request(List.of(), List.of(),
				List.of(new EvidencePhrase(List.of("METHOD", "METHOD"), "구절", 1, "club")),
				sectionChars(1, 1, 1, 1, 1)), "evidence.types");
	}

	@Test
	void 영역_글자_수_합이_상한을_넘으면_거부한다() {
		assertInvalid(request(List.of(), List.of(), List.of(), sectionChars(100_000, 100_000, 1, 0, 0)),
				"coverage.sectionChars");
	}

	@Test
	void 태그가_없으면_태그_사전을_읽지_않는다() {
		HolisticRequestValidator noLookup = new HolisticRequestValidator(new HolisticRuleCatalog(), () -> {
			throw new AssertionError("태그 사전을 읽으면 안 된다");
		});

		assertThatCode(() -> noLookup.validate(request(List.of(), List.of(), List.of(), sectionChars(1, 1, 1, 1, 1))))
				.doesNotThrowAnyException();
	}

	@Test
	void 규칙_버전_불일치는_예외가_아니라_판정으로_알린다() {
		assertThat(validator.isAcceptedVersion("0.1.0")).isTrue();
		assertThat(validator.isAcceptedVersion("local-0.1")).isFalse();
	}

	@Test
	void 오류_메시지에는_사용자_값이_들어가지_않는다() {
		HolisticAnalysisRequest request = request(List.of(), List.of(),
				List.of(new EvidencePhrase(List.of("UNKNOWN"), SECRET, 1, "club")), sectionChars(1, 1, 1, 1, 1));

		assertThatThrownBy(() -> validator.validate(request))
				.isInstanceOf(BusinessException.class)
				.hasMessageNotContaining(SECRET)
				.hasMessageNotContaining("UNKNOWN");
	}

	@Test
	void 사전에_있는_지표를_가진_인용_구절은_통과한다() {
		assertThatCode(() -> validator.validate(withCitations(
				citation("c1", "실험 결과를 표로 정리하고 오차의 원인을 분석하여 발표함", "A3"),
				citation("c2", "맡은 역할을 끝까지 책임지고 성실하게 수행함", "C3", "A2")))).doesNotThrowAnyException();
	}

	@Test
	void 인용_구절_번호가_겹치면_거부한다() {
		assertInvalid(withCitations(citation("c1", "문장 하나", "A3"), citation("c1", "문장 둘", "C3")), "citations.id");
	}

	@Test
	void 인용_구절의_지표가_사전에_없거나_겹치면_거부한다() {
		assertInvalid(withCitations(citation("c1", "문장 하나", "ZZ")), "citations.indicators");
		assertInvalid(withCitations(citation("c1", "문장 하나", "A3", "A3")), "citations.indicators");
	}

	@Test
	void 인용_구절에_제어_문자가_있으면_거부한다() {
		assertInvalid(withCitations(citation("c1", "문장\n하나", "A3")), "citations.text");
	}

	@Test
	void 인용_구절이_없는_요청과_null_요청은_통과한다() {
		assertThatCode(() -> validator.validate(withCitations())).doesNotThrowAnyException();
		assertThatCode(() -> validator.validate(request(List.of(), List.of(), List.of(), sectionChars(1, 1, 1, 1, 1))))
				.doesNotThrowAnyException();
	}

	@Test
	void 인용_구절_오류_메시지와_toString에는_문장이_들어가지_않는다() {
		Citation secret = citation("c1", SECRET, "ZZ");

		assertThatThrownBy(() -> validator.validate(withCitations(secret)))
				.isInstanceOf(BusinessException.class)
				.hasMessageNotContaining(SECRET);
		assertThat(secret.toString()).doesNotContain(SECRET);
		assertThat(withCitations(secret).toString()).doesNotContain(SECRET);
	}

	private void assertInvalid(HolisticAnalysisRequest request, String field) {
		assertThatThrownBy(() -> validator.validate(request))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
					assertThat(e.getMessage()).endsWith("holisticAnalysis." + field);
				});
	}

	private HolisticAnalysisRequest request(
			List<IndicatorSignal> signals,
			List<InterestTag> tags,
			List<EvidencePhrase> evidence,
			SectionChars chars
	) {
		return new HolisticAnalysisRequest("0.1.0", new Coverage(chars, List.of(1)), signals, tags, evidence);
	}

	private static Citation citation(String id, String text, String... indicators) {
		return new Citation(id, text, List.of(indicators), 2, "subject", 0.95);
	}

	private HolisticAnalysisRequest withCitations(Citation... citations) {
		return new HolisticAnalysisRequest("0.1.0", new Coverage(sectionChars(1, 1, 1, 1, 1), List.of(1)), List.of(),
				List.of(), List.of(), List.of(citations));
	}

	private SectionChars sectionChars(int autonomy, int club, int career, int subject, int behavior) {
		return new SectionChars(autonomy, club, career, subject, behavior);
	}

}
