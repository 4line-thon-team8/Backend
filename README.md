# Backend

오등급 서비스의 백엔드 저장소입니다.

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.2 |
| 빌드 도구 | Gradle (Groovy) |
| 설정 파일 | YAML (`application.yaml`) |
| DB | PostgreSQL 18 |
| 데이터 접근 | Spring Data JPA |
| DB 마이그레이션 | Flyway |
| 외부 API 호출 | RestClient |
| 테스트 | JUnit 5, Testcontainers (PostgreSQL) |
| 배포 | 미정 (FE·BE 각각 배포) |

API 문서는 코드에서 자동 생성합니다(springdoc). 요청·응답 설명은 코드에 함께 작성합니다.

## API 명세

팀 노션의 "API 명세서"를 기준으로 합니다. 구현이 끝난 API는 자동 생성 문서로도 확인합니다.

## 브랜치

- 기본 브랜치는 `develop`입니다.
- 기능 작업은 `develop`에서 브랜치를 만들어 진행하고 PR로 합칩니다.
- 브랜치 이름 규칙과 커밋 메시지 규칙은 팀 합의 후 이 문서에 적습니다.

## 폴더 구조

기능별 패키지 구조를 사용합니다. 루트 패키지는 `com.fivegrade.backend`입니다.

| 패키지 | 내용 |
| --- | --- |
| `common` | 공통 오류 응답, 공통 설정 |
| `simulation` | 환산 요청 DTO·입력 검증, Controller, 최종 응답 조립 |
| `conversion` | 5등급 환산 엔진 |
| `recommendation` | 추천 원서 조합 |
| `report/generation` | AI 리포트 생성 |
| `report/storage` | 리포트 저장·조회·삭제 |
| `auth` | 회원가입, 로그인·로그아웃, 토큰 관리 |
| `user` | 사용자, 회원 탈퇴 |
| `admission` | 전형 DB, 대학·학과 사전, 태그맵 |

비어 있는 패키지는 `.gitkeep`으로 유지하며, 코드가 들어가면 지웁니다.
DB 마이그레이션 파일은 `src/main/resources/db/migration`에 `V1__설명.sql` 형식으로 추가합니다.

## 실행 방법

### 준비물

- JDK 21
- Docker (Docker Desktop 등). 로컬 DB 실행과 테스트에 사용합니다.

### 1. 환경 변수 파일 만들기

```bash
cp .env.example .env
```

`.env`를 열어 `DB_PASSWORD`를 직접 정한 값으로 바꿉니다.
`.env`는 Git에 올라가지 않습니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | DB 주소 |
| `DB_PORT` | `5432` | DB 포트 |
| `DB_NAME` | `fivegrade` | DB 이름 |
| `DB_USER` | `fivegrade` | DB 계정 |
| `DB_PASSWORD` | 없음 (필수) | DB 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | 빈 값 | 교차 출처 요청을 허용할 FE Origin. 여러 개는 쉼표로 구분 |

### 2. DB 실행

```bash
docker compose up -d
```

### 3. FE 연결과 CORS 설정

FE와 BE의 Origin이 다르면 BE의 `.env` 또는 실행 환경변수에
`CORS_ALLOWED_ORIGINS`를 설정합니다.

로컬 개발 예시:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

여러 Origin을 허용하려면 쉼표로 구분합니다.

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

실제로 사용하는 주소만 등록합니다. Origin에는 스킴(http/https),
호스트와 필요한 포트만 적고, 경로나 마지막 슬래시는 넣지 않습니다.

배포 시에는 확정된 FE Origin을 BE 배포 환경변수에 설정합니다.
`.env.example`에 작성한 값이 배포 환경에 자동으로 적용되지는 않습니다.

값이 비어 있으면 교차 출처 요청을 허용하지 않으며,
와일드카드(`*`)는 사용할 수 없습니다.

### 4. 서버 실행

```bash
./gradlew bootRun
```

서버가 뜰 때 Flyway가 `db/migration`의 마이그레이션을 자동으로 실행합니다.

### 테스트

```bash
./gradlew test
```

테스트는 Testcontainers가 PostgreSQL 컨테이너를 따로 띄워서 실행합니다.
Docker가 켜져 있어야 하고, 로컬 DB(`docker compose`)와는 별개입니다.

비밀 값(`.env`, `application-local.*`, 키 파일)은 저장소에 올리지 않습니다.

## 리포트 생성 설정

기본값은 템플릿 생성이며 외부 AI를 호출하지 않습니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `REPORT_AI_ENABLED` | `false` | 외부 AI 설명 생성 사용 여부 |
| `REPORT_AI_MODEL` | `gpt-4.1-mini-2025-04-14` | 현재 OpenAI 구현에서 사용할 모델 |
| `REPORT_AI_TIMEOUT_MS` | `12000` | AI 응답 본문 수신 완료까지의 제한 시간. 1~30000ms |
| `REPORT_AI_MAX_CONCURRENT` | `4` | 애플리케이션 인스턴스당 동시 AI 호출 제한. 1~32 |
| `OPENAI_API_KEY` | 없음 | AI 사용 시 필요한 서버 시크릿 |

실제 호출 검증 전에는 다음 설정을 사용합니다.

```dotenv
REPORT_AI_ENABLED=false
REPORT_AI_TIMEOUT_MS=12000
REPORT_AI_MAX_CONCURRENT=4
```

`OPENAI_API_KEY`는 서버의 시크릿으로 관리하고 저장소나 FE에 전달하지 않습니다.

AI가 비활성화되어 있으면 API 키 없이 템플릿으로 생성합니다.
활성화 상태에서 키가 누락되면 설정 오류로 서버 기동이 실패합니다.

AI는 확정된 추천 결과의 설명만 보완합니다.
성적 계산, 추천 추가, 순위 변경, 지원 구간 재판정은 수행하지 않습니다.

AI 호출은 분석 요청당 최대 1회이며 자동 재시도하지 않습니다.
호출 실패 시 전체 템플릿으로 대체하고, 섹션 검증 실패 시 해당 섹션만
템플릿으로 대체합니다.

각 설명 섹션의 `generationMethod`는 다음 값을 사용합니다.

- `template`: 템플릿 사용
- `llm`: AI 출력이 검증을 통과해 사용됨

한 분석 응답 안에서 두 값이 섞일 수 있습니다.

AI 활성화 시 참고 평균과 추천 대학·학과·전형 및 설명 문구가
외부 제공사로 전송됩니다.
과목별 원본 성적 목록, 사용자 식별정보, 인증정보는 전달하지 않습니다.

실패 로그에는 고정 사유 코드와 섹션별 템플릿 대체 개수만 기록합니다.
문장, 참고 평균, API 키, 외부 오류 본문과 예외 원문은 기록하지 않습니다.

AI 제한 시간은 분석 API 전체 제한 시간이 아닙니다.
FE·프록시의 제한 시간은 추천 계산과 저장 처리 시간까지 고려해 정합니다.

## 협업 문서

- 이슈 템플릿: `.github/ISSUE_TEMPLATE/`
- PR 템플릿: `.github/pull_request_template.md`
