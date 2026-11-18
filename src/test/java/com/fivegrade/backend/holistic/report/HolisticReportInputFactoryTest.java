package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NarrativeAmount;
import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.IndicatorDef;
import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.TagCount;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 평가 결과를 리포트 입력 자료로 바꾸는 규칙을 확인한다. DB나 Spring 없이 실행한다. */
class HolisticReportInputFactoryTest {

	private static final List<IndicatorDef> DEFS = List.of(
			new IndicatorDef("A2", "학업태도", "ACADEMIC"),
			new IndicatorDef("A3", "탐구력", "ACADEMIC"),
			new IndicatorDef("B3", "진로 탐색 활동과 경험", "MAJOR_FIT"),
			new IndicatorDef("B4", "전공 연계 일관성", "MAJOR_FIT"),
			new IndicatorDef("C1", "협업과 소통능력", "COMMUNITY"),
			new IndicatorDef("D1", "자기주도성", "POTENTIAL"),
			new IndicatorDef("D2", "경험의 다양성", "POTENTIAL"));

	/** B4, D2 는 규칙이 없어 관찰할 수 없다. */
	private static final Set<String> OBSERVABLE = Set.of("A2", "A3", "B3", "C1", "D1");

	private final HolisticReportInputFactory factory = new HolisticReportInputFactory();

	private HolisticReportInput create(long chars, List<TagCount> tags) {
		return factory.create(
				DEFS, OBSERVABLE,
				Map.of("A3", 4.2, "B3", 2.0, "C1", 3.0),
				Map.of("ACADEMIC", 4.2, "MAJOR_FIT", 2.0, "COMMUNITY", 3.0),
				3.1,
				Map.of("A3", List.of(3, 1, 3), "B3", List.of(2), "C1", List.of(1, 2)),
				tags,
				chars);
	}

	private ElementView element(HolisticReportInput input, String id) {
		return input.elements().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void 요소는_항상_네_개이고_정해진_순서다() {
		HolisticReportInput input = create(5000, List.of());

		assertThat(input.elements()).extracting(ElementView::id)
				.containsExactly("ACADEMIC", "MAJOR_FIT", "COMMUNITY", "POTENTIAL");
		assertThat(input.elements()).extracting(ElementView::label)
				.containsExactly("학업역량", "전공적합성", "공동체역량", "발전가능성");
	}

	@Test
	void 관찰된_지표는_구간_정수수준_학년_신뢰도를_가진다() {
		ElementView academic = element(create(5000, List.of()), "ACADEMIC");

		assertThat(academic.band()).isEqualTo(ReportBand.HIGH);
		assertThat(academic.level()).isEqualTo(4);
		assertThat(academic.indicators()).hasSize(1);
		var a3 = academic.indicators().get(0);
		assertThat(a3.id()).isEqualTo("A3");
		assertThat(a3.band()).isEqualTo(ReportBand.HIGH);
		assertThat(a3.observedYears()).containsExactly(1, 3);
	}

	@Test
	void 규칙은_있는데_신호가_없는_지표는_관찰되지_않음으로_분류한다() {
		ElementView academic = element(create(5000, List.of()), "ACADEMIC");
		ElementView potential = element(create(5000, List.of()), "POTENTIAL");

		assertThat(academic.notObserved()).extracting(n -> n.id()).containsExactly("A2");
		assertThat(potential.notObserved()).extracting(n -> n.id()).containsExactly("D1");
	}

	@Test
	void 규칙이_없는_지표는_측정하지_않음으로_분류한다() {
		HolisticReportInput input = create(5000, List.of());

		assertThat(input.notMeasured()).extracting(n -> n.id()).containsExactly("B4", "D2");
		// 측정하지 않은 지표는 관찰되지 않음 목록에 들어가지 않는다.
		assertThat(element(input, "MAJOR_FIT").notObserved()).isEmpty();
	}

	@Test
	void 판단하지_못한_요소는_구간과_수준이_null이다() {
		ElementView potential = element(create(5000, List.of()), "POTENTIAL");

		assertThat(potential.band()).isNull();
		assertThat(potential.level()).isNull();
		assertThat(potential.indicators()).isEmpty();
	}

	@Test
	void 서술_분량이_기준_미만이면_LIMITED다() {
		assertThat(create(2999, List.of()).narrativeAmount()).isEqualTo(NarrativeAmount.LIMITED);
		assertThat(create(3000, List.of()).narrativeAmount()).isEqualTo(NarrativeAmount.SUFFICIENT);
	}

	@Test
	void 태그는_빈도_높은_순_같으면_이름순_최대_5개이고_빈도_0은_뺀다() {
		HolisticReportInput input = create(5000, List.of(
				new TagCount("간호", 3), new TagCount("생명과학", 9), new TagCount("통계", 3),
				new TagCount("의료", 1), new TagCount("화학", 2), new TagCount("물리", 1), new TagCount("수학", 0)));

		assertThat(input.interestTags()).containsExactly("생명과학", "간호", "통계", "화학", "물리");
	}

	@Test
	void 전체_구간은_전체_수준으로_정한다() {
		assertThat(create(5000, List.of()).overallBand()).isEqualTo(ReportBand.MID);
	}
}
