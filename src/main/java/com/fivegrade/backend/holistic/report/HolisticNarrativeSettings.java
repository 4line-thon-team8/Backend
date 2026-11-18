package com.fivegrade.backend.holistic.report;

/**
 * 활동 요약·학과별 조언 설정.
 *
 * @param evidenceEnabled 근거 구절을 외부 AI에 보낼지. 앱에 외부 AI 전송 동의 문구가 들어가기 전에는 false 다.
 */
public record HolisticNarrativeSettings(boolean evidenceEnabled) {
}
