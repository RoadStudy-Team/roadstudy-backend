# RoadStudy Backend

자차 출퇴근 직장인이 올린 공부 자료를 AI 기반 오디오 학습으로 변환하고, 운전이 쉬운 순간에 학습할 수 있도록 지원하는 자기계발 학습 코치 앱 **로드스터디(RoadStudy)**의 백엔드 서버입니다.

RoadStudy 백엔드는 PDF 학습 자료 처리, AI 학습 설명 생성, 음성 합성 및 학습 콘텐츠 제공을 담당합니다.

## 🚀 Tech Stack

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.16, Spring MVC |
| Database | PostgreSQL 17.9 |
| ORM | Spring Data JPA |
| PDF Processing | Apache PDFBox 3.0.8 |
| AI | OpenAI Responses API |
| Text-to-Speech | Google Cloud Text-to-Speech |
| API Documentation | Springdoc OpenAPI 2.10.0, Swagger UI |
| Testing | JUnit 5, Spring Boot Test, H2 |
| Build | Gradle Wrapper |
| Environment | Docker Compose (로컬 PostgreSQL) |

## 📦 주요 기능

| 기능 | 설명 |
| --- | --- |
| PDF 텍스트 추출 | 업로드한 PDF 학습 자료에서 텍스트 추출 |
| AI 학습 설명 생성 | OpenAI API를 활용해 학습용 설명 대본 생성 |
| 음성 합성 | Google Cloud TTS로 설명 대본을 한국어 MP3로 합성 |
| 비동기 학습 생성 | PDF 텍스트 추출·검증 후 대본과 음성 생성을 백그라운드에서 처리 |
| 학습 데이터 관리 | PostgreSQL에 학습 세션, 대본, 음성 파일 경로 저장·조회 |
| 음성 스트리밍 | 로컬에 저장한 MP3 제공 및 HTTP Range 요청 지원 |

### 학습 콘텐츠 생성 흐름

```text
PDF 학습 자료 업로드
        ↓
PDFBox 텍스트 추출·검증
        ↓
학습 세션 저장 및 요청 접수 응답 (202 Accepted)
        ↓
OpenAI 학습 설명 대본 생성 (백그라운드)
        ↓
Google Cloud TTS 음성 합성 (백그라운드)
        ↓
MP3 로컬 저장 및 학습 세션 갱신
        ↓
학습 상태 조회 / 저장된 MP3 재생
        ↓
RoadStudy 차량용 앱
```

## 🛠️ 프로젝트 구조

백엔드는 Ports & Adapters 아키텍처를 기반으로 구성합니다.

```text
roadstudy-backend/
├── src/
│   ├── main/
│   │   ├── java/com/roadstudy/
│   │   │   ├── adapter/       HTTP, PDFBox, OpenAI, TTS, 파일 저장, JPA 연동
│   │   │   ├── application/   학습 생성·조회 유스케이스와 포트
│   │   │   ├── domain/        학습 세션과 상태 전이
│   │   │   ├── config/        애플리케이션 구성
│   │   │   └── global/        공통 예외 처리
│   │   └── resources/        애플리케이션 설정
│   └── test/                 테스트 코드
├── compose.yaml              PostgreSQL 개발 환경
├── build.gradle              Gradle 빌드 설정
├── gradlew                   Gradle Wrapper
├── gradlew.bat               Windows용 Gradle Wrapper
└── README.md
```

- **Web Adapter** — HTTP 요청·응답 및 Swagger 명세
- **Application** — 학습 생성·조회 유스케이스 처리
- **Domain** — 학습 세션과 상태 전이 규칙
- **Outbound Adapter** — PostgreSQL, PDFBox, OpenAI, Google Cloud TTS 및 로컬 파일 저장 연동

## ▶️ 실행 방법

### 1. 프로젝트 다운로드

```bash
git clone https://github.com/RoadStudy-Team/roadstudy-backend.git
cd roadstudy-backend
```

### 2. 환경변수 설정

프로젝트 루트에 `.env`를 만들고 예시 값을 실제 환경에 맞게 수정합니다. 기본 프로필은 `local`입니다.

```dotenv
DB_PASSWORD=your_local_password
DB_PORT=5433
SPRING_JPA_HIBERNATE_DDL_AUTO=update
OPENAI_API_KEY=your_openai_api_key
GOOGLE_APPLICATION_CREDENTIALS=C:/secrets/roadstudy-tts.json
```

- 기존 PostgreSQL 볼륨을 재사용할 경우 초기화에 사용한 비밀번호를 입력합니다.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`는 로컬 개발용 스키마 설정입니다.
- Google 인증 파일 경로는 실행 환경에 맞게 지정합니다. Windows에서는 예시처럼 슬래시(`/`)를 사용합니다.
- API 키가 포함된 `.env`와 Google 인증 파일은 GitHub에 올리지 않습니다.

### 3. PostgreSQL 실행

Docker를 실행한 뒤 프로젝트 루트에서 다음 명령어를 실행합니다.

```bash
docker compose up -d postgres
docker compose ps
```

### 4. Spring Boot 실행

macOS / Linux

```bash
./gradlew bootRun
```

Windows

```powershell
.\gradlew.bat bootRun
```

생성된 MP3는 기본적으로 실행 디렉터리 기준 `storage/audio`에 저장됩니다. `AUDIO_STORAGE_PATH`로 저장 위치를 변경할 수 있습니다.

## ⚙️ 테스트 환경 및 필요한 설정

| 항목 | 환경 |
| --- | --- |
| Java | JDK 21 |
| Spring Boot | 3.5.16 |
| PostgreSQL | 17.9 |
| Database | `roadstudy` |
| Database User | `roadstudy` |
| Database Port | `5433` (기본값) |
| Build Tool | Gradle Wrapper |
| API Documentation | Springdoc OpenAPI |
| External API | OpenAI API, Google Cloud TTS |

### 테스트 실행

macOS / Linux

```bash
./gradlew test
```

Windows

```powershell
.\gradlew.bat test
```

자동 테스트는 H2와 모의 외부 클라이언트를 사용해 HTTP 응답, 학습 상태 전이, 음성 저장·조회 등을 검증합니다.

### Swagger API 문서

[Swagger UI](http://localhost:8080/swagger-ui/index.html)에서 `POST /api/studies`로 PDF를 올립니다. 응답의 `Location` 주소에서 상태를 조회하고, `COMPLETED`가 되면 응답의 `audioUrl`로 MP3를 재생할 수 있습니다.

## 📱 프론트엔드 및 APK 안내

RoadStudy는 차량용 앱과 모바일 앱을 별도 Android 모듈로 개발하며, 공용 코드는 `shared` 모듈에 둡니다. APK는 프론트엔드 저장소에서 관리합니다. [RoadStudy Frontend](https://github.com/RoadStudy-Team/roadstudy-frontend)

| 모듈 | 실행 환경 |
| --- | --- |
| `automotive` | Pleos Connect Emulator v2.1.0 (API 34) |
| `mobile` | Android 휴대폰 에뮬레이터 (API 28 이상) |
| `shared` | 차량용·모바일 앱 공용 라이브러리 |

### Pleos Connect 테스트 환경

- 에뮬레이터 버전 2.1.0, API 34
- 화면 규격 2560 × 1440 px, 화면 밀도 160 dpi
- Virtual Console 사용

### APK 파일 위치

- **차량용 앱** — `apk/Car공족_RoadStudy_automotive_release.apk`
- **모바일 앱** — `apk/Car공족_RoadStudy_mobile_release.apk`

APK 설치 및 에뮬레이터 실행 방법은 프론트엔드 문서를 참고해 주세요. [RoadStudy Frontend README](https://github.com/RoadStudy-Team/roadstudy-frontend/blob/dev/README.md)

## 🔗 GitHub Repository

- [Frontend](https://github.com/RoadStudy-Team/roadstudy-frontend)
- [Backend](https://github.com/RoadStudy-Team/roadstudy-backend)

---

**현대자동차그룹 차량용 앱 개발 공모전 | Car공족 · RoadStudy**
