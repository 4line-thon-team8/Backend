package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.University;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {

	Optional<University> findByName(String name);

}
