package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorRepository extends JpaRepository<Major, String> {

}
