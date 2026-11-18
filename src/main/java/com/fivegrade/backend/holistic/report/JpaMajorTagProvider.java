package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.admission.entity.MajorTag;
import com.fivegrade.backend.admission.repository.MajorTagRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 적재된 학과-태그 연결(major_tags)에서 학과별 태그를 읽는다. */
@Component
public class JpaMajorTagProvider implements MajorTagProvider {

	private final MajorTagRepository repository;

	public JpaMajorTagProvider(MajorTagRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, List<String>> tagsOf(Collection<String> majorCodes) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String code : majorCodes) {
			List<String> tags = repository.findByMajorCode(code).stream()
					.sorted(Comparator.comparing(JpaMajorTagProvider::weightOf).reversed()
							.thenComparing(link -> link.getTag().getName()))
					.map(link -> link.getTag().getName())
					.distinct()
					.toList();
			result.put(code, tags);
		}
		return result;
	}

	private static BigDecimal weightOf(MajorTag link) {
		return link.getWeight() == null ? BigDecimal.ZERO : link.getWeight();
	}
}
