package com.fivegrade.backend.admission.dto;

import java.util.List;

/**
 * GET /api/majors 응답. 배열을 바로 내리지 않고 한 번 감싸는 이유는
 * 나중에 계열이나 안내 문구 같은 필드가 생겨도 응답 형태를 깨지 않기 위해서다.
 *
 * @param majors 학과 사전 목록. 비어 있으면 빈 배열
 */
public record MajorListResponse(List<MajorResponse> majors) {

}
