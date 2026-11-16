package com.fivegrade.backend.admission.dto;

/**
 * 대학별 전형 목록을 만들 때 저장소에서 꺼내는 한 줄. API 응답이 아니라 서비스 안에서만 쓴다.
 *
 * @param universityName 대학 이름
 * @param trackType      전형 유형 (학종, 교과)
 * @param trackName      전형 이름
 */
public record UniversityTrackRow(String universityName, String trackType, String trackName) {
}
