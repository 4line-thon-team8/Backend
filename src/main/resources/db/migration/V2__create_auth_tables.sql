-- 사용자 및 DB 저장형 인증 토큰의 영속성 기반을 구성한다.
-- 전형 DB 스키마인 V1 이후 적용한다.
--
-- 공통 정책:
-- 1. 기본키는 BIGINT GENERATED ALWAYS AS IDENTITY를 사용한다.
-- 2. 비밀번호 원문과 인증 토큰 원문은 저장하지 않는다.
-- 3. 사용자를 삭제하면 해당 사용자의 인증 토큰도 삭제한다.
-- 4. 리포트 테이블 및 리포트 삭제 처리는 별도 작업에서 구현한다.
--
-- 이 마이그레이션은 저장 구조만 정의한다.
-- 비밀번호 인코딩, 토큰 생성·해시 계산, 만료 검증은 인증 계층에서 수행한다.


-- 계정 정보를 저장한다.
-- password_hash에는 비밀번호 전용 인코더가 생성한 결과만 저장한다.
-- email의 UNIQUE는 저장된 문자열 기준이며,
-- 이메일 정규화 정책은 후속 가입·로그인 구현에서 일관되게 적용한다.
CREATE TABLE users (
                       id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       email         VARCHAR(254) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT uk_users_email UNIQUE (email),
                       CONSTRAINT ck_users_email_not_blank
                           CHECK (btrim(email) <> ''),
                       CONSTRAINT ck_users_password_hash_not_blank
                           CHECK (btrim(password_hash) <> '')
);

-- 사용자 한 명이 여러 인증 토큰을 보유할 수 있다.
-- token_hash는 랜덤 토큰의 SHA-256 결과를 소문자 16진수 64자리로 저장한다.
-- UNIQUE 제약으로 동일한 해시의 중복 저장을 방지한다.
-- expires_at은 만료 시각이며, 실제 만료 여부는 요청 인증 시 검사한다.
CREATE TABLE auth_tokens (
                             id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             user_id    BIGINT NOT NULL,
                             token_hash VARCHAR(64) NOT NULL,
                             expires_at TIMESTAMPTZ NOT NULL,

    -- 계정 삭제 시 해당 계정의 토큰을 DB에서 함께 제거한다.
    -- 토큰을 삭제하는 동작이 사용자 삭제로 이어지는 것은 아니다.
                             CONSTRAINT fk_auth_tokens_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users (id)
                                     ON DELETE CASCADE,

                             CONSTRAINT uk_auth_tokens_token_hash
                                 UNIQUE (token_hash),

                             CONSTRAINT ck_auth_tokens_token_hash_format
                                 CHECK (token_hash ~ '^[0-9a-f]{64}$')
    );

-- 사용자별 토큰 조회와 사용자 삭제 시 FK 관련 처리를 지원한다.
CREATE INDEX idx_auth_tokens_user_id
    ON auth_tokens (user_id);