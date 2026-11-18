package com.fivegrade.backend.holistic.report;

/** 리포트 입력 자료로 글 리포트를 만든다. 구현체는 실패해도 예외를 밖으로 내지 않고 기본 문장으로 대신한다. */
public interface HolisticReportWriter {

	HolisticReportContent generate(HolisticReportInput input);

}
