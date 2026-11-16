package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.dto.TagEntryResponse;
import com.fivegrade.backend.admission.entity.TagKeyword;
import com.fivegrade.backend.admission.entity.TagKeywordId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagKeywordRepository extends JpaRepository<TagKeyword, TagKeywordId> {

	/** 키워드와 태그 이름만 바로 꺼낸다. 정렬은 서비스가 한 곳에서 정한다. */
	@Query("select new com.fivegrade.backend.admission.dto.TagEntryResponse(k.id.keyword, k.tag.name)"
			+ " from TagKeyword k")
	List<TagEntryResponse> findAllEntries();

}
