-- V11: 학교·전형별 서류 평가 프로필 (학종 서술 분석 서류 보정용)
-- 의존: V1(universities)
-- 근거: holistic-decisions-v0.2 D1(변별 유형은 새 테이블), D2(교과 서류 분리), 와이드 + JSONB 방식 확정(2026-11-16)
--
-- 이 테이블은 "그 전형이 서류의 어떤 면을 얼마나 보는가"만 담는다.
--  - 서류 반영 비중(document_share)은 2028 전형 구조 값이다. eval_weights는 2026 입결 행(admission_unit_years)에
--    붙어 있어 2028 개편 비율을 담을 수 없으므로 이 테이블에 둔다.
--  - 입결과의 연결은 (university_id, track_name) 으로 하고, 서비스가 admission_year 가 가장 최근인 행을 쓴다.
-- 데이터는 V12 이후 별도 시드로 채운다. 확인되지 않은 값은 NULL 또는 confidence=LOW 로 둔다.
CREATE TABLE holistic_track_profiles (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    university_id       BIGINT       NOT NULL REFERENCES universities (id),
    track_name          VARCHAR(100) NOT NULL,
    admission_year      INTEGER      NOT NULL,
    -- 학종 | 교과. 교과 전형도 서류 비중이 있으면 행을 둔다(예: 한양 교과 60 + 학종평가 40).
    track_kind          VARCHAR(20)  NOT NULL,
    -- 변별 유형: 성적 중심 | 진로·진학의지 중심 | 혼합 | 미확인
    discrimination_type VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    -- 서류 신호 가중 세트. 교과 전형은 SUBJECT_DOCUMENT(계열 이수·선택과목·교과 세특 중심)
    document_signal_set VARCHAR(30)  NOT NULL,
    -- 전형 전체에서 서류가 차지하는 비중(%). 1단계 서류 100 → 100, 교과 60 + 서류 40 → 40. 모르면 NULL.
    document_share      NUMERIC(5,2),
    -- 서류 안에서의 3축 비중(%). 모두 채웠을 때만 합이 100 근처여야 한다.
    academic_ratio      NUMERIC(5,2),
    career_ratio        NUMERIC(5,2),
    community_ratio     NUMERIC(5,2),
    -- 학교가 따로 명시한 요소(발전가능성 등)나 고유 규칙. 요강으로 확인된 값만 넣는다.
    extra_elements      JSONB,
    confidence          VARCHAR(10)  NOT NULL DEFAULT 'LOW',
    -- 근거 등급: S1 대학 공식·전 사정관, S2 컨설턴트 등
    source_tier         VARCHAR(5)   NOT NULL,
    source_note         TEXT,
    verified_at         DATE,
    rules_version       VARCHAR(20),
    CONSTRAINT uk_holistic_track_profiles UNIQUE (university_id, track_name, admission_year),
    CONSTRAINT ck_htp_track_kind CHECK (track_kind IN ('학종', '교과')),
    CONSTRAINT ck_htp_discrimination CHECK (discrimination_type IN ('ACADEMIC', 'CAREER_INTENT', 'MIXED', 'UNKNOWN')),
    CONSTRAINT ck_htp_signal_set CHECK (document_signal_set IN ('HOLISTIC_FULL', 'SUBJECT_DOCUMENT')),
    CONSTRAINT ck_htp_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_htp_share_range CHECK (document_share IS NULL OR document_share BETWEEN 0 AND 100),
    CONSTRAINT ck_htp_ratio_range CHECK (
        (academic_ratio  IS NULL OR academic_ratio  BETWEEN 0 AND 100) AND
        (career_ratio    IS NULL OR career_ratio    BETWEEN 0 AND 100) AND
        (community_ratio IS NULL OR community_ratio BETWEEN 0 AND 100)
    ),
    -- 3축을 모두 채웠다면 합이 100(±1)이어야 한다.
    CONSTRAINT ck_htp_ratio_sum CHECK (
        academic_ratio IS NULL OR career_ratio IS NULL OR community_ratio IS NULL
        OR (academic_ratio + career_ratio + community_ratio) BETWEEN 99 AND 101
    )
);

CREATE INDEX idx_htp_university_track ON holistic_track_profiles (university_id, track_name);
