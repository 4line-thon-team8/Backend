-- 이메일은 앱에서 앞뒤 공백 제거와 소문자 변환 후 저장한다.
-- 정규화가 누락된 직접 INSERT/UPDATE는 DB에서도 거부한다.
-- 기존 uk_users_email UNIQUE 제약은 유지한다.
-- 이미 공유된 V2는 수정하지 않는다.

ALTER TABLE users
    ADD CONSTRAINT ck_users_email_normalized
        CHECK (email = lower(btrim(email)));