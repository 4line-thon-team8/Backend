package com.fivegrade.backend.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fivegrade.backend.admission.dto.TrackResponse;
import com.fivegrade.backend.admission.dto.UniversityListResponse;
import com.fivegrade.backend.admission.dto.UniversityResponse;
import com.fivegrade.backend.admission.dto.UniversityTrackRow;
import com.fivegrade.backend.admission.repository.AdmissionUnitRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 대학별 전형 목록의 묶기, 정렬, 자격 제한 전형 제외 규칙을 확인한다. DB 없이 저장소를 고정값으로 바꿔서 실행한다.
 */
class UniversityServiceTest {

	@Test
	void 대학_이름순으로_묶고_전형은_유형_이름_순으로_정렬한다() {
		UniversityListResponse response = serviceWith(List.of(
				new UniversityTrackRow("동국대학교", "학종", "Do Dream"),
				new UniversityTrackRow("건국대학교", "학종", "학생부종합(KU자기추천전형)"),
				new UniversityTrackRow("건국대학교", "교과", "학생부교과(KU지역균형전형)"),
				new UniversityTrackRow("건국대학교", "교과", "학생부교과(KU교과우수자)")))
				.findAll();

		assertThat(response.universities()).extracting(UniversityResponse::name)
				.containsExactly("건국대학교", "동국대학교");
		// 교과가 학종보다 앞이고, 같은 유형 안에서는 전형 이름 순이다.
		assertThat(response.universities().get(0).tracks()).containsExactly(
				new TrackResponse("교과", "학생부교과(KU교과우수자)"),
				new TrackResponse("교과", "학생부교과(KU지역균형전형)"),
				new TrackResponse("학종", "학생부종합(KU자기추천전형)"));
	}

	@Test
	void 대상자만_지원할_수_있는_전형은_뺀다() {
		UniversityListResponse response = serviceWith(List.of(
				new UniversityTrackRow("동국대학교", "학종", "Do Dream"),
				new UniversityTrackRow("동국대학교", "학종", "학생부종합(농어촌학생전형)"),
				new UniversityTrackRow("동국대학교", "학종", "학생부종합(기회균형전형)"),
				new UniversityTrackRow("동국대학교", "학종", "불교추천인재")))
				.findAll();

		assertThat(response.universities()).hasSize(1);
		assertThat(response.universities().get(0).tracks())
				.containsExactly(new TrackResponse("학종", "Do Dream"));
	}

	@Test
	void 일반_전형이_하나도_없는_대학은_목록에서_뺀다() {
		UniversityListResponse response = serviceWith(List.of(
				new UniversityTrackRow("가나대학교", "학종", "학생부종합(농어촌학생전형)"),
				new UniversityTrackRow("동국대학교", "학종", "Do Dream")))
				.findAll();

		assertThat(response.universities()).extracting(UniversityResponse::name)
				.containsExactly("동국대학교");
	}

	@Test
	void 같은_전형이_여러_번_와도_한_번만_담는다() {
		UniversityListResponse response = serviceWith(List.of(
				new UniversityTrackRow("동국대학교", "학종", "Do Dream"),
				new UniversityTrackRow("동국대학교", "학종", "Do Dream")))
				.findAll();

		assertThat(response.universities().get(0).tracks()).hasSize(1);
	}

	@Test
	void 적재된_전형이_없으면_빈_목록이다() {
		assertThat(serviceWith(List.of()).findAll().universities()).isEmpty();
	}

	private UniversityService serviceWith(List<UniversityTrackRow> rows) {
		AdmissionUnitRepository repository = mock(AdmissionUnitRepository.class);
		when(repository.findDistinctTracks()).thenReturn(rows);
		return new UniversityService(repository);
	}

}
