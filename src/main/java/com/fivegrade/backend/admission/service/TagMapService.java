package com.fivegrade.backend.admission.service;

import com.fivegrade.backend.admission.dto.TagEntryResponse;
import com.fivegrade.backend.admission.dto.TagMapResponse;
import com.fivegrade.backend.admission.repository.TagKeywordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전공 키워드 사전 조회. 사전은 마이그레이션으로만 바뀌는 읽기 전용 데이터다.
 */
@Service
public class TagMapService {

	/** 키워드, 태그 이름 순. 데이터베이스 정렬 규칙과 무관하게 항상 같은 순서가 되도록 서버가 정한다. */
	private static final Comparator<TagEntryResponse> BY_KEYWORD_THEN_TAG =
			Comparator.comparing(TagEntryResponse::keyword).thenComparing(TagEntryResponse::tag);

	/** 버전으로 쓸 해시 길이(16진수 글자 수). 충돌 걱정이 없으면서 FE 가 저장하기에 짧은 길이다. */
	private static final int VERSION_LENGTH = 12;

	private final TagKeywordRepository tagKeywordRepository;

	public TagMapService(TagKeywordRepository tagKeywordRepository) {
		this.tagKeywordRepository = tagKeywordRepository;
	}

	@Transactional(readOnly = true)
	public TagMapResponse getTagMap() {
		List<TagEntryResponse> entries = tagKeywordRepository.findAllEntries().stream()
				.sorted(BY_KEYWORD_THEN_TAG)
				.toList();
		return new TagMapResponse(versionOf(entries), entries);
	}

	/**
	 * 사전 내용으로 버전을 만든다. 별도로 버전을 관리하지 않아도 키워드가 바뀌면 값이 자동으로 바뀐다.
	 * 입력은 정렬된 목록이라고 가정하며, 같은 내용이면 언제 계산해도 같은 값이다.
	 */
	static String versionOf(List<TagEntryResponse> sortedEntries) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (TagEntryResponse entry : sortedEntries) {
				digest.update((entry.keyword() + '\t' + entry.tag() + '\n').getBytes(StandardCharsets.UTF_8));
			}
			return HexFormat.of().formatHex(digest.digest()).substring(0, VERSION_LENGTH);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 은 모든 자바 구현이 지원해야 하는 알고리즘이라 여기에 오지 않는다.
			throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
		}
	}

}
