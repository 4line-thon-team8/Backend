package com.fivegrade.backend.holistic.report;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 학과 코드별 전공 태그를 찾는다. 연관 강도가 높은 순으로 돌려주고, 태그가 없는 학과는 빈 목록이다. */
public interface MajorTagProvider {

	Map<String, List<String>> tagsOf(Collection<String> majorCodes);

}
