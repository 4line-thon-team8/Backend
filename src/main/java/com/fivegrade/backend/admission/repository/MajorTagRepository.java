package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.MajorTag;
import com.fivegrade.backend.admission.entity.MajorTagId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MajorTagRepository extends JpaRepository<MajorTag, MajorTagId> {

	List<MajorTag> findByMajorCode(String majorCode);

	List<MajorTag> findByTagId(Long tagId);

	/**
	 * 주어진 학과들과 같은 분류의 태그를 공유하는 다른 학과 코드. 비슷한 학과 확대에 쓴다.
	 * 주어진 학과 자신은 빠진다.
	 */
	@Query("select distinct other.major.code from MajorTag own, MajorTag other"
			+ " where own.major.code in :majorCodes"
			+ " and own.tag.category = :tagCategory"
			+ " and other.tag = own.tag"
			+ " and other.major.code not in :majorCodes")
	List<String> findSimilarMajorCodes(
			@Param("majorCodes") Collection<String> majorCodes,
			@Param("tagCategory") String tagCategory
	);

}
