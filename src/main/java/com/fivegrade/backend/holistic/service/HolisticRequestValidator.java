package com.fivegrade.backend.holistic.service;

import com.fivegrade.backend.admission.dto.TagEntryResponse;
import com.fivegrade.backend.admission.repository.TagKeywordRepository;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.rules.HolisticRuleCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@code holisticAnalysis} 요청 DTO의 형식 검증만으로 확인할 수 없는 검증을 맡는다.
 * 지표 ID와 신호 유형이 규칙 사전에 있는지, 같은 지표·태그가 겹치지 않는지, 태그가 전공 키워드 사전에 있는지,
 * 영역 글자 수 합이 상한 안인지 확인한다.
 *
 * 규칙 버전이 맞는지는 여기서 거부하지 않는다. 버전 불일치는 요청 오류가 아니라 서술 분석을 반영하지 않는 경우
 * ({@link #isAcceptedVersion})라서, 호출하는 쪽이 성적 기준 결과를 그대로 돌려주도록 판단한다.
 *
 * 메시지에는 사용자가 보낸 값을 넣지 않고 필드 이름만 쓴다.
 */
@Component
public class HolisticRequestValidator {

	private static final String INVALID_PREFIX = "입력값이 올바르지 않습니다: holisticAnalysis.";

	private final HolisticRuleCatalog catalog;
	private final Supplier<Set<String>> knownTags;

	@Autowired
	public HolisticRequestValidator(HolisticRuleCatalog catalog, TagKeywordRepository tagKeywordRepository) {
		this(catalog, lazyTagNames(tagKeywordRepository));
	}

	/** 테스트에서 태그 사전 조회를 대신하기 위한 진입점이다. */
	HolisticRequestValidator(HolisticRuleCatalog catalog, Supplier<Set<String>> knownTags) {
		this.catalog = catalog;
		this.knownTags = knownTags;
	}

	/** 서버가 받아 주는 규칙 버전인지. 아니면 서술 분석을 반영하지 않는다(요청 오류가 아니다). */
	public boolean isAcceptedVersion(String rulesVersion) {
		return catalog.isAcceptedVersion(rulesVersion);
	}

	/** 사전과 대조한 검증. 문제가 있으면 필드 이름만 담은 400 예외를 던진다. */
	public void validate(HolisticAnalysisRequest request) {
		validateIndicatorSignals(request);
		validateInterestTags(request);
		validateEvidence(request);
		validateCitations(request);
		if (request.coverage().sectionChars().total() > catalog.limits().totalSectionCharsMax()) {
			throw invalid("coverage.sectionChars");
		}
	}

	private void validateIndicatorSignals(HolisticAnalysisRequest request) {
		Set<String> seen = new HashSet<>();
		for (HolisticAnalysisRequest.IndicatorSignal signal : request.indicatorSignals()) {
			if (!catalog.hasIndicator(signal.id()) || !seen.add(signal.id())) {
				throw invalid("indicatorSignals.id");
			}
			if (hasDuplicate(signal.years())) {
				throw invalid("indicatorSignals.years");
			}
		}
		if (request.indicatorSignals().size() > catalog.limits().indicatorSignalsMax()) {
			throw invalid("indicatorSignals");
		}
	}

	private void validateInterestTags(HolisticAnalysisRequest request) {
		if (request.interestTags().size() > catalog.limits().interestTagsMax()) {
			throw invalid("interestTags");
		}
		Set<String> seen = new HashSet<>();
		Set<String> known = request.interestTags().isEmpty() ? Set.of() : knownTags.get();
		for (HolisticAnalysisRequest.InterestTag tag : request.interestTags()) {
			if (!known.contains(tag.tag()) || !seen.add(tag.tag())) {
				throw invalid("interestTags.tag");
			}
			if (hasDuplicate(tag.years())) {
				throw invalid("interestTags.years");
			}
		}
	}

	private void validateEvidence(HolisticAnalysisRequest request) {
		if (request.evidence().size() > catalog.limits().evidenceMax()) {
			throw invalid("evidence");
		}
		for (HolisticAnalysisRequest.EvidencePhrase phrase : request.evidence()) {
			if (phrase.text().length() > catalog.limits().evidenceTextMaxChars()) {
				throw invalid("evidence.text");
			}
			Set<String> types = new HashSet<>();
			for (String type : phrase.types()) {
				if (!catalog.hasType(type) || !types.add(type)) {
					throw invalid("evidence.types");
				}
			}
		}
	}

	/** 인용 구절: 번호가 겹치지 않고, 지표가 규칙 사전에 있고, 제어 문자가 없어야 한다. 개수와 길이는 DTO 검증이 이미 확인했다. */
	private void validateCitations(HolisticAnalysisRequest request) {
		Set<String> ids = new HashSet<>();
		for (HolisticAnalysisRequest.Citation citation : request.citationsOrEmpty()) {
			if (!ids.add(citation.id())) {
				throw invalid("citations.id");
			}
			Set<String> indicators = new HashSet<>();
			for (String indicator : citation.indicators()) {
				if (!catalog.hasIndicator(indicator) || !indicators.add(indicator)) {
					throw invalid("citations.indicators");
				}
			}
			if (citation.text().chars().anyMatch(Character::isISOControl)) {
				throw invalid("citations.text");
			}
		}
	}

	private static boolean hasDuplicate(List<Integer> years) {
		return new HashSet<>(years).size() != years.size();
	}

	private static BusinessException invalid(String field) {
		return new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_PREFIX + field);
	}

	/** 태그 사전은 마이그레이션으로만 바뀌는 읽기 전용 데이터라, 처음 필요할 때 한 번만 읽어 둔다. */
	private static Supplier<Set<String>> lazyTagNames(TagKeywordRepository repository) {
		return new Supplier<>() {

			private volatile Set<String> cached;

			@Override
			public Set<String> get() {
				Set<String> names = cached;
				if (names == null) {
					Set<String> loaded = new HashSet<>();
					for (TagEntryResponse entry : repository.findAllEntries()) {
						loaded.add(entry.tag());
					}
					names = Set.copyOf(loaded);
					cached = names;
				}
				return names;
			}
		};
	}

}
