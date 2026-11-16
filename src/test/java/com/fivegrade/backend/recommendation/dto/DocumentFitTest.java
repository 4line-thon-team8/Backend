package com.fivegrade.backend.recommendation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fivegrade.backend.recommendation.dto.DocumentFit.Level;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 서류 수준(1~5)을 세 단계 적합도로 줄이는 기준을 확인한다. */
class DocumentFitTest {

	@Test
	void 수준이_높으면_HIGH_낮으면_LOW_그_사이는_MID다() {
		assertEquals(Level.HIGH, DocumentFit.of(5.0, List.of()).level());
		assertEquals(Level.HIGH, DocumentFit.of(4.0, List.of()).level());
		assertEquals(Level.MID, DocumentFit.of(3.99, List.of()).level());
		assertEquals(Level.MID, DocumentFit.of(2.01, List.of()).level());
		assertEquals(Level.LOW, DocumentFit.of(2.0, List.of()).level());
		assertEquals(Level.LOW, DocumentFit.of(1.0, List.of()).level());
	}

	@Test
	void 근거가_없으면_빈_목록이고_바깥에서_바꿀_수_없다() {
		assertEquals(List.of(), new DocumentFit(Level.MID, null).basis());
		DocumentFit fit = DocumentFit.of(4.5, List.of("ACADEMIC"));
		assertThrows(UnsupportedOperationException.class, () -> fit.basis().add("MAJOR_FIT"));
	}

}
