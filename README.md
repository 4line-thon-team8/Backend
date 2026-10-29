# Backend

오등급 서비스의 백엔드 저장소입니다.

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1 |
| 빌드 도구 | Gradle |
| DB 마이그레이션 | Flyway |
| 외부 API 호출 | RestClient |
| 테스트 | JUnit 5 |
| DB | 미정 |
| 배포 | 미정 (FE·BE 각각 배포) |

API는 `openapi.yaml`을 먼저 작성하고 그에 맞춰 구현합니다.

## API 명세

팀 노션의 "API 명세서"를 기준으로 합니다. 취합이 끝나면 `openapi.yaml`에 반영합니다.

## 브랜치

- 기본 브랜치는 `develop`입니다.
- 기능 작업은 `develop`에서 브랜치를 만들어 진행하고 PR로 합칩니다.
- 브랜치 이름 규칙과 커밋 메시지 규칙은 팀 합의 후 이 문서에 적습니다.

## 폴더 구조

기능별 패키지 구조를 사용합니다. 패키지 이름과 구조는 팀 합의 후 이 문서에 적습니다.

## 실행 방법

프로젝트 뼈대가 만들어지면 로컬 실행 방법과 환경 변수 목록을 이 문서에 적습니다.

비밀 값(`.env`, `application-local.*`, 키 파일)은 저장소에 올리지 않습니다.

## 협업 문서

- 이슈 템플릿: `.github/ISSUE_TEMPLATE/`
- PR 템플릿: `.github/pull_request_template.md`
