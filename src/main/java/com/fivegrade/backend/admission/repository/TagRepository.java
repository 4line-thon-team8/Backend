package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.Tag;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

	Optional<Tag> findByName(String name);

}
