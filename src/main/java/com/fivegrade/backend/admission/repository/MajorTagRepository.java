package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.MajorTag;
import com.fivegrade.backend.admission.entity.MajorTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorTagRepository extends JpaRepository<MajorTag, MajorTagId> {

	List<MajorTag> findByMajorCode(String majorCode);

	List<MajorTag> findByTagId(Long tagId);

}
