package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.holistic.report.HolisticReportParameters.Reliability;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 구간 기준과 지표 신뢰도 표를 확인한다. DB나 Spring 없이 실행한다. */
class HolisticReportParametersTest {

	private final HolisticReportParameters parameters = HolisticReportParameters.DEFAULT;

	@Test
	void 구간은_경계값을_포함해서_나눈다() {
		assertThat(parameters.bandOf(3.5)).isEqualTo(ReportBand.HIGH);
		assertThat(parameters.bandOf(3.49)).isEqualTo(ReportBand.MID);
		assertThat(parameters.bandOf(3.0)).isEqualTo(ReportBand.MID);
		assertThat(parameters.bandOf(2.51)).isEqualTo(ReportBand.MID);
		assertThat(parameters.bandOf(2.5)).isEqualTo(ReportBand.LOW);
		assertThat(parameters.bandOf(1.0)).isEqualTo(ReportBand.LOW);
		assertThat(parameters.bandOf(5.0)).isEqualTo(ReportBand.HIGH);
	}

	@Test
	void 화면용_수준은_반올림하고_1에서_5로_제한한다() {
		assertThat(parameters.displayLevel(4.07)).isEqualTo(4);
		assertThat(parameters.displayLevel(3.5)).isEqualTo(4);
		assertThat(parameters.displayLevel(0.2)).isEqualTo(1);
		assertThat(parameters.displayLevel(5.4)).isEqualTo(5);
	}

	@Test
	void 표에_없는_지표의_신뢰도는_LOW다() {
		assertThat(parameters.reliabilityOf("A3")).isEqualTo(Reliability.MID);
		assertThat(parameters.reliabilityOf("D4")).isEqualTo(Reliability.LOW);
		assertThat(parameters.reliabilityOf("ZZ")).isEqualTo(Reliability.LOW);
	}

	@Test
	void 잘못된_기준은_거부한다() {
		assertThatThrownBy(() -> new HolisticReportParameters(2.5, 3.5, 3000, Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new HolisticReportParameters(3.5, 2.5, -1, Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
