package com.fivegrade.backend.holistic.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.profile.HolisticTrackProfile;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import com.fivegrade.backend.holistic.scoring.DocumentLevelSpread;
import com.fivegrade.backend.holistic.scoring.DocumentSignal;
import com.fivegrade.backend.recommendation.candidate.CandidateUnit;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.assertj.core.data.Offset;

/**
 * 후보마다 서류 입력을 만드는 규칙을 확인한다. 프로필 조회는 고정 목록으로 바꿔서 DB 없이 실행한다.
 */
class DocumentSignalFactoryTest {

	private static final Offset<Double> DELTA = Offset.offset(1e-9);
	private static final ElementLevels LEVELS = new ElementLevels(4.0, 2.0, 3.0);

	/** 서류 수준은 축 평균을 실제 문서 분포 위의 위치로 편 값이다. 기대값은 평균에 같은 보정을 적용해 만든다. */
	private static final DocumentLevelSpread SPREAD = DocumentLevelSpread.DEFAULT;

	@Test
	void 학종_후보는_전형_프로필의_비중으로_서류_수준과_비중을_만든다() {
		// 학업 100%면 서류 수준은 학업 수준(4.0)과 같다. 서류 비중 70%는 0.7이 된다.
		HolisticTrackProfile profile = profile(1L, "학생부종합(학교장추천)", 2028, "학종", "70", "100", "0", "0");
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(List.of(unit(1L, "학종", "학생부종합(학교장추천)")), LEVELS);

		DocumentSignal signal = resolver.apply(unit(1L, "학종", "학생부종합(학교장추천)"));

		assertThat(signal).isNotNull();
		assertThat(signal.level()).isEqualTo(SPREAD.apply(4.0), DELTA);
		assertThat(signal.share()).isEqualTo(0.7, DELTA);
	}

	@Test
	void 서류_수준에_크게_작용한_요소를_비중과_중립_거리가_큰_순서로_최대_두_개_남긴다() {
		// 학업 60%(4.0 → 거리 1, 기여 0.6), 진로 40%(2.0 → 거리 1, 기여 0.4), 공동체 0%는 제외
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", "70", "60", "40", "0");
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(List.of(unit(1L, "학종", "A전형")), LEVELS);

		assertThat(resolver.apply(unit(1L, "학종", "A전형")).basis()).containsExactly("ACADEMIC", "MAJOR_FIT");
	}

	@Test
	void 수준이_중립인_요소는_근거에_넣지_않는다() {
		// 공동체 수준이 3.0(중립)이라 비중이 커도 근거가 아니다.
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", "70", "10", "10", "80");
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(List.of(unit(1L, "학종", "A전형")), LEVELS);

		assertThat(resolver.apply(unit(1L, "학종", "A전형")).basis()).doesNotContain("COMMUNITY");
	}

	@Test
	void 같은_전형의_여러_연도_중_가장_최근_행을_쓴다() {
		HolisticTrackProfile newer = profile(1L, "A전형", 2028, "학종", "60", "100", "0", "0");
		HolisticTrackProfile older = profile(1L, "A전형", 2027, "학종", "30", "0", "100", "0");
		// 저장소가 최신 연도부터 돌려주는 순서를 따른다.
		Function<CandidateUnit, DocumentSignal> resolver = factory(newer, older).resolverFor(List.of(unit(1L, "학종", "A전형")), LEVELS);

		DocumentSignal signal = resolver.apply(unit(1L, "학종", "A전형"));

		assertThat(signal.level()).isEqualTo(SPREAD.apply(4.0), DELTA);
		assertThat(signal.share()).isEqualTo(0.6, DELTA);
	}

	@Test
	void 교과_후보는_서류_입력이_없다() {
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", "50", "100", "0", "0");
		List<CandidateUnit> units = List.of(unit(1L, "학종", "A전형"), unit(1L, "교과", "A전형"));
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(units, LEVELS);

		assertThat(resolver.apply(unit(1L, "교과", "A전형"))).isNull();
	}

	@Test
	void 프로필이_없는_학종_후보는_보정하지_않고_적합도_표시용_입력만_만든다() {
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", "50", "100", "0", "0");
		List<CandidateUnit> units = List.of(unit(1L, "학종", "A전형"), unit(2L, "학종", "A전형"), unit(1L, "학종", "다른전형"));
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(units, LEVELS);

		for (CandidateUnit unit : List.of(unit(2L, "학종", "A전형"), unit(1L, "학종", "다른전형"))) {
			DocumentSignal signal = resolver.apply(unit);

			assertThat(signal).isNotNull();
			assertThat(signal.adjustable()).isFalse();
			assertThat(signal.share()).isNull();
			// 세 축 균등: (4.0 + 2.0 + 3.0) / 3
			assertThat(signal.level()).isEqualTo(SPREAD.apply(3.0), DELTA);
		}
		assertThat(resolver.apply(unit(1L, "학종", "A전형")).adjustable()).isTrue();
	}

	@Test
	void 비중이_없는_프로필은_판단_가능한_축을_균등하게_본다() {
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", null, null, null, null);
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(List.of(unit(1L, "학종", "A전형")), LEVELS);

		DocumentSignal signal = resolver.apply(unit(1L, "학종", "A전형"));

		assertThat(signal.level()).isEqualTo(SPREAD.apply(3.0), DELTA);
		assertThat(signal.share()).isNull();
	}

	@Test
	void 판단_가능한_축이_하나도_없으면_서류_입력이_없다() {
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", "50", "100", "0", "0");
		ElementLevels none = new ElementLevels(null, null, null);
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(List.of(unit(1L, "학종", "A전형")), none);

		assertThat(resolver.apply(unit(1L, "학종", "A전형"))).isNull();
	}

	@Test
	void 판단_가능한_축이_하나뿐이면_그_축_수준으로_서류_입력을_만든다() {
		HolisticTrackProfile profile = profile(1L, "A전형", 2028, "학종", "50", "100", "0", "0");
		ElementLevels onlyOne = new ElementLevels(4.0, null, null);
		Function<CandidateUnit, DocumentSignal> resolver = factory(profile).resolverFor(List.of(unit(1L, "학종", "A전형")), onlyOne);

		DocumentSignal signal = resolver.apply(unit(1L, "학종", "A전형"));
		assertThat(signal).isNotNull();
		assertThat(signal.level()).isEqualTo(SPREAD.apply(4.0), DELTA);
	}

	@Test
	void 학종_후보가_없으면_프로필을_읽지_않는다() {
		DocumentSignalFactory factory = new DocumentSignalFactory((Collection<Long> ids) -> {
			throw new AssertionError("프로필을 읽으면 안 된다");
		});

		Function<CandidateUnit, DocumentSignal> resolver = factory.resolverFor(List.of(unit(1L, "교과", "A전형")), LEVELS);

		assertThat(resolver.apply(unit(1L, "교과", "A전형"))).isNull();
	}

	private static DocumentSignalFactory factory(HolisticTrackProfile... profiles) {
		return new DocumentSignalFactory((Collection<Long> ids) -> List.of(profiles));
	}

	private static CandidateUnit unit(long universityId, String trackType, String trackName) {
		return new CandidateUnit(10L + universityId, universityId, "대학" + universityId, "학과",
				trackType, trackName, CandidateKind.DESIRED_MAJOR, new ArrayList<>());
	}

	/** 읽기 전용 엔티티라 생성자와 setter 가 없어서 필드를 직접 채운다. */
	private static HolisticTrackProfile profile(
			long universityId, String trackName, int year, String trackKind,
			String share, String academic, String career, String community
	) {
		try {
			Constructor<HolisticTrackProfile> constructor = HolisticTrackProfile.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			HolisticTrackProfile profile = constructor.newInstance();
			set(profile, "universityId", universityId);
			set(profile, "trackName", trackName);
			set(profile, "admissionYear", year);
			set(profile, "trackKind", trackKind);
			set(profile, "documentShare", decimal(share));
			set(profile, "academicRatio", decimal(academic));
			set(profile, "careerRatio", decimal(career));
			set(profile, "communityRatio", decimal(community));
			return profile;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}

	private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

}
