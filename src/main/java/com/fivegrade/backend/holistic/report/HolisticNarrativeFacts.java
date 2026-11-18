package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticStrengthSelector.Strength;
import java.util.List;

/**
 * 활동 요약·학과별 조언을 쓰는 데 쓰는 사실 자료. 서버가 확정한 값만 담는다.
 * 생기부 원문, 이름·학교, 성적, 지원 대학·전형은 담지 않는다. 수준 수치도 구간으로만 남긴다.
 *
 * @param report         요소·지표 구간, 신뢰도, 관찰 학년, 측정하지 않는 지표, 관심 전공 태그
 * @param strengths      서버가 고른 역량 강점(최대 3개)
 * @param majors         희망 학과(입력 순서, 최대 3개)
 * @param evidencePhrases 이름·학교를 가린 근거 구절. 외부 AI 전송 동의 전에는 비어 있다.
 */
public record HolisticNarrativeFacts(
		HolisticReportInput report,
		List<Strength> strengths,
		List<MajorFacts> majors,
		List<String> evidencePhrases
) {

	/**
	 * @param code        희망 학과 코드
	 * @param department  희망 학과 이름
	 * @param tags        학과의 전공 태그. 연관 강도가 높은 순
	 * @param overlapTags 학생의 관심 태그와 겹치는 학과 태그. 없으면 빈 목록
	 */
	public record MajorFacts(String code, String department, List<String> tags, List<String> overlapTags) {
		public MajorFacts {
			tags = List.copyOf(tags);
			overlapTags = List.copyOf(overlapTags);
		}
	}

	public HolisticNarrativeFacts {
		strengths = List.copyOf(strengths);
		majors = List.copyOf(majors);
		evidencePhrases = List.copyOf(evidencePhrases);
	}

	/** 활동 요약을 쓸 근거가 있는가. 강점도 관심 태그도 없으면 요약하지 않는다. */
	public boolean canSummarize() {
		return !strengths.isEmpty() || !report.interestTags().isEmpty();
	}
}
