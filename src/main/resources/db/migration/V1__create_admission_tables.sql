-- 전형 DB 테이블 (ERD v0.4 중 재원 담당 범위: 대학·학과 사전·태그·모집단위·연도별 입결·평가요소·매핑)
-- 리포트 테이블(reports, report_recommendations)은 users 테이블이 먼저 필요하므로 이 파일에 포함하지 않는다.
-- 테이블 이름은 snake_case 복수형, 기본키는 identity(자동 증가)를 쓴다.

-- 대학
CREATE TABLE universities (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_universities_name UNIQUE (name)
);

-- 희망 학과 사전. 코드는 전형 DB 기준.
CREATE TABLE majors (
    code VARCHAR(30)  PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- 학과에 붙는 태그 (비슷한 학과 확대에 사용)
CREATE TABLE tags (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(50) NOT NULL,
    category VARCHAR(30),
    CONSTRAINT uk_tags_name UNIQUE (name)
);

-- 학과-태그 연결. weight는 연관 강도(선택).
CREATE TABLE major_tags (
    major_code VARCHAR(30)   NOT NULL REFERENCES majors (code),
    tag_id     BIGINT        NOT NULL REFERENCES tags (id),
    weight     NUMERIC(4, 2),
    PRIMARY KEY (major_code, tag_id)
);
CREATE INDEX idx_major_tags_tag_id ON major_tags (tag_id);

-- 모집단위(대학·학과·전형). 연도와 무관한 정체성.
CREATE TABLE admission_units (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    university_id BIGINT       NOT NULL REFERENCES universities (id),
    dept_name     VARCHAR(100) NOT NULL,
    track_type    VARCHAR(20)  NOT NULL,
    track_name    VARCHAR(100) NOT NULL,
    CONSTRAINT uk_admission_units UNIQUE (university_id, dept_name, track_name)
);

-- 모집단위의 연도별 모집 정보와 입결.
-- 입결 통계는 학교가 공개한 것만 채우므로 모두 NULL을 허용한다. 산출 기준은 note에 적는다.
-- grade_min은 '최저' = 가장 낮은 성적(등급 숫자가 큰 값)이다.
-- fill_rate는 충원율(%) 숫자 그대로(100 = 100%), fill_rank는 충원합격 예비번호이다.
CREATE TABLE admission_unit_years (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_id          BIGINT        NOT NULL REFERENCES admission_units (id),
    year             INTEGER       NOT NULL,
    quota            INTEGER,
    applicants       INTEGER,
    competition_rate NUMERIC(8, 2),
    csat_min         TEXT,
    is_new_or_merged BOOLEAN,
    grade_avg        NUMERIC(4, 2),
    grade_min        NUMERIC(4, 2),
    cut_grade_50     NUMERIC(4, 2),
    cut_grade_70     NUMERIC(4, 2),
    fill_rate        NUMERIC(8, 1),
    fill_rank        INTEGER,
    note             TEXT,
    source           VARCHAR(300),
    collected_at     DATE,
    CONSTRAINT uk_admission_unit_years UNIQUE (unit_id, year),
    CONSTRAINT ck_aug_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_aug_quota CHECK (quota IS NULL OR quota >= 0),
    CONSTRAINT ck_aug_applicants CHECK (applicants IS NULL OR applicants >= 0),
    CONSTRAINT ck_aug_competition_rate CHECK (competition_rate IS NULL OR competition_rate >= 0),
    CONSTRAINT ck_aug_fill_rate CHECK (fill_rate IS NULL OR fill_rate >= 0),
    CONSTRAINT ck_aug_fill_rank CHECK (fill_rank IS NULL OR fill_rank >= 0),
    CONSTRAINT ck_aug_grade_avg CHECK (grade_avg IS NULL OR grade_avg BETWEEN 1 AND 9),
    CONSTRAINT ck_aug_grade_min CHECK (grade_min IS NULL OR grade_min BETWEEN 1 AND 9),
    CONSTRAINT ck_aug_cut_50 CHECK (cut_grade_50 IS NULL OR cut_grade_50 BETWEEN 1 AND 9),
    CONSTRAINT ck_aug_cut_70 CHECK (cut_grade_70 IS NULL OR cut_grade_70 BETWEEN 1 AND 9)
);

-- 연도별 평가요소 반영 비율 (교과/서류/면접 등)
CREATE TABLE eval_weights (
    unit_year_id BIGINT        NOT NULL REFERENCES admission_unit_years (id) ON DELETE CASCADE,
    element      VARCHAR(30)   NOT NULL,
    ratio        NUMERIC(5, 2) NOT NULL,
    PRIMARY KEY (unit_year_id, element),
    CONSTRAINT ck_eval_weights_ratio CHECK (ratio BETWEEN 0 AND 100)
);

-- 학과 사전과 모집단위의 N:M 매핑
CREATE TABLE unit_major_map (
    unit_id    BIGINT      NOT NULL REFERENCES admission_units (id),
    major_code VARCHAR(30) NOT NULL REFERENCES majors (code),
    PRIMARY KEY (unit_id, major_code)
);
CREATE INDEX idx_unit_major_map_major_code ON unit_major_map (major_code);
