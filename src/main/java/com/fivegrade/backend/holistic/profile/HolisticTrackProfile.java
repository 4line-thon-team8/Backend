package com.fivegrade.backend.holistic.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 학교·전형별 서류 평가 프로필. 시드로만 채워지는 읽기 전용 엔티티다.
 *
 * 서류 반영 비중과 서류 안의 3축 비중을 담아 서류 보정의 입력으로 쓴다.
 * 3축 비중이 비어 있으면 호출하는 쪽이 균등 비중과 낮은 신뢰도로 처리한다.
 */
@Entity
@Immutable
@Table(name = "holistic_track_profiles")
public class HolisticTrackProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "university_id", nullable = false)
	private Long universityId;

	@Column(name = "track_name", nullable = false, length = 100)
	private String trackName;

	@Column(name = "admission_year", nullable = false)
	private Integer admissionYear;

	@Column(name = "track_kind", nullable = false, length = 20)
	private String trackKind;

	@Column(name = "discrimination_type", nullable = false, length = 20)
	private String discriminationType;

	@Column(name = "document_signal_set", nullable = false, length = 30)
	private String documentSignalSet;

	@Column(name = "document_share", precision = 5, scale = 2)
	private BigDecimal documentShare;

	@Column(name = "academic_ratio", precision = 5, scale = 2)
	private BigDecimal academicRatio;

	@Column(name = "career_ratio", precision = 5, scale = 2)
	private BigDecimal careerRatio;

	@Column(name = "community_ratio", precision = 5, scale = 2)
	private BigDecimal communityRatio;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "extra_elements", columnDefinition = "jsonb")
	private Map<String, Object> extraElements;

	@Column(name = "confidence", nullable = false, length = 10)
	private String confidence;

	@Column(name = "source_tier", nullable = false, length = 5)
	private String sourceTier;

	@Column(name = "source_note")
	private String sourceNote;

	@Column(name = "verified_at")
	private LocalDate verifiedAt;

	@Column(name = "rules_version", length = 20)
	private String rulesVersion;

	protected HolisticTrackProfile() {
	}

	public Long getId() {
		return id;
	}

	public Long getUniversityId() {
		return universityId;
	}

	public String getTrackName() {
		return trackName;
	}

	public Integer getAdmissionYear() {
		return admissionYear;
	}

	public String getTrackKind() {
		return trackKind;
	}

	public String getDiscriminationType() {
		return discriminationType;
	}

	public String getDocumentSignalSet() {
		return documentSignalSet;
	}

	public BigDecimal getDocumentShare() {
		return documentShare;
	}

	public BigDecimal getAcademicRatio() {
		return academicRatio;
	}

	public BigDecimal getCareerRatio() {
		return careerRatio;
	}

	public BigDecimal getCommunityRatio() {
		return communityRatio;
	}

	public Map<String, Object> getExtraElements() {
		return extraElements;
	}

	public String getConfidence() {
		return confidence;
	}

	public String getSourceTier() {
		return sourceTier;
	}

	public String getSourceNote() {
		return sourceNote;
	}

	public LocalDate getVerifiedAt() {
		return verifiedAt;
	}

	public String getRulesVersion() {
		return rulesVersion;
	}

}
