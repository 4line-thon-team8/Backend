package com.fivegrade.backend.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fivegrade.backend.admission.dto.TagEntryResponse;
import com.fivegrade.backend.admission.dto.TagMapResponse;
import com.fivegrade.backend.admission.repository.TagKeywordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 키워드 사전의 정렬과 버전 규칙을 확인한다. DB 없이 저장소를 고정값으로 바꿔서 실행한다.
 */
class TagMapServiceTest {

	@Test
	void 키워드_태그_이름_순으로_정렬해서_돌려준다() {
		TagMapResponse response = serviceWith(List.of(
				new TagEntryResponse("인공지능", "데이터·AI"),
				new TagEntryResponse("반도체", "전자·반도체"),
				new TagEntryResponse("인공지능", "IT·소프트웨어")))
				.getTagMap();

		assertThat(response.entries()).extracting(TagEntryResponse::keyword)
				.containsExactly("반도체", "인공지능", "인공지능");
		// 같은 키워드 안에서는 태그 이름 순이다. 'I' 가 한글보다 앞선다.
		assertThat(response.entries().get(1).tag()).isEqualTo("IT·소프트웨어");
		assertThat(response.entries().get(2).tag()).isEqualTo("데이터·AI");
	}

	@Test
	void 저장소가_돌려주는_순서와_상관없이_버전이_같다() {
		TagMapResponse first = serviceWith(List.of(
				new TagEntryResponse("반도체", "전자·반도체"),
				new TagEntryResponse("회계", "경영·회계"))).getTagMap();
		TagMapResponse reversed = serviceWith(List.of(
				new TagEntryResponse("회계", "경영·회계"),
				new TagEntryResponse("반도체", "전자·반도체"))).getTagMap();

		assertThat(first.version()).isEqualTo(reversed.version());
	}

	@Test
	void 사전_내용이_바뀌면_버전이_바뀐다() {
		String before = serviceWith(List.of(new TagEntryResponse("반도체", "전자·반도체"))).getTagMap().version();
		String keywordChanged = serviceWith(List.of(new TagEntryResponse("반도체공학", "전자·반도체"))).getTagMap().version();
		String tagChanged = serviceWith(List.of(new TagEntryResponse("반도체", "화학·소재"))).getTagMap().version();
		String added = serviceWith(List.of(
				new TagEntryResponse("반도체", "전자·반도체"),
				new TagEntryResponse("회계", "경영·회계"))).getTagMap().version();

		assertThat(before).isNotEqualTo(keywordChanged).isNotEqualTo(tagChanged).isNotEqualTo(added);
	}

	@Test
	void 버전은_소문자_16진수_12자리다() {
		String version = serviceWith(List.of(new TagEntryResponse("반도체", "전자·반도체"))).getTagMap().version();

		assertThat(version).matches("[0-9a-f]{12}");
	}

	@Test
	void 키워드_경계가_섞이지_않는다() {
		// ("ab", "c") 와 ("a", "bc") 처럼 이어 붙이면 같아지는 조합도 다른 버전이어야 한다.
		String first = TagMapService.versionOf(List.of(new TagEntryResponse("ab", "c")));
		String second = TagMapService.versionOf(List.of(new TagEntryResponse("a", "bc")));

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void 사전이_비어_있어도_버전과_빈_목록을_돌려준다() {
		TagMapResponse response = serviceWith(List.of()).getTagMap();

		assertThat(response.entries()).isEmpty();
		assertThat(response.version()).matches("[0-9a-f]{12}");
	}

	private TagMapService serviceWith(List<TagEntryResponse> stored) {
		TagKeywordRepository repository = mock(TagKeywordRepository.class);
		when(repository.findAllEntries()).thenReturn(stored);
		return new TagMapService(repository);
	}

}
