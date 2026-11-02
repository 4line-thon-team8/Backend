-- V3: 저장 리포트 테이블 (B-12)
-- 의존: V1(admission_units), V2(users). V2가 머지되기 전에는 적용할 수 없다.
-- 근거: ERD v0.4 결정 #3(스냅샷 저장), #4(UUID), 제약/인덱스 초안(5절), 기획 확인(8절)

-- 저장된 리포트. 성적·환산 결과·원본 입력은 저장하지 않는다(명세서 3.3, P-04).
-- user_id는 ON DELETE 규칙을 두지 않는다. 탈퇴 시 서비스가 리포트를 먼저 지운다(세빈 님과 합의).
-- 보관 기간은 정하지 않았으므로 expires_at 컬럼을 두지 않는다.
CREATE TABLE reports (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    idempotency_key  VARCHAR(100),
    greeting_summary TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 같은 사용자가 같은 키로 다시 저장하면 중복으로 막는다. 키가 NULL이면 제약을 적용하지 않는다.
    CONSTRAINT uk_reports_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- 마이페이지 목록(최신순)
CREATE INDEX idx_reports_user_created ON reports (user_id, created_at DESC);

-- 리포트의 추천 원서. 정확히 6장은 DB가 아니라 서비스 코드에서 보장한다(B-10).
-- 대학/학과/전형 이름은 저장 시점의 값을 복사한 스냅샷이다. 전형 데이터가 바뀌어도 리포트는 그대로 남는다.
-- unit_id는 참조용 선택값이며, 전형 데이터가 지워지면 NULL이 된다.
CREATE TABLE report_recommendations (
    report_id       UUID          NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    rank            INTEGER       NOT NULL,
    university_name VARCHAR(100)  NOT NULL,
    dept_name       VARCHAR(100)  NOT NULL,
    track_name      VARCHAR(100)  NOT NULL,
    label           VARCHAR(30),
    reason          TEXT          NOT NULL,
    unit_id         BIGINT        REFERENCES admission_units (id) ON DELETE SET NULL,
    PRIMARY KEY (report_id, rank),
    CONSTRAINT ck_report_recommendations_rank CHECK (rank BETWEEN 1 AND 6),
    -- 한 리포트 안에서 같은 대학·학과·전형 중복 금지
    CONSTRAINT uk_report_recommendations_target UNIQUE (report_id, university_name, dept_name, track_name)
);

CREATE INDEX idx_report_recommendations_unit_id ON report_recommendations (unit_id);
