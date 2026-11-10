package com.fivegrade.backend.admission.service;

import com.fivegrade.backend.admission.dto.MajorResponse;
import com.fivegrade.backend.admission.entity.Major;
import com.fivegrade.backend.admission.repository.MajorRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학과 사전 조회. 학과 사전은 마이그레이션으로만 바뀌는 읽기 전용 데이터다.
 */
@Service
public class MajorService {

	/** 한글 이름순. 화면에서 따로 정렬하지 않아도 같은 순서로 보이게 서버가 정한다. */
	private static final Comparator<Major> BY_NAME_THEN_CODE =
			Comparator.comparing(Major::getName).thenComparing(Major::getCode);

	private final MajorRepository majorRepository;

	public MajorService(MajorRepository majorRepository) {
		this.majorRepository = majorRepository;
	}

	@Transactional(readOnly = true)
	public List<MajorResponse> findAll() {
		return majorRepository.findAll().stream()
				.sorted(BY_NAME_THEN_CODE)
				.map(MajorResponse::from)
				.toList();
	}

}
