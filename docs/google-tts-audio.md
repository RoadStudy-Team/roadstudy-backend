# Google TTS 학습 음성 API

이슈 #6의 설명 대본 → 한국어 MP3 합성 → 로컬 저장 → 조회 흐름을 제공한다. 생성 요청 본문으로 대본을 직접 받으며, PDF·OpenAI API와 자동 연결하거나 StudySession을 저장하는 흐름은 포함하지 않는다.

## 로컬 실행

Java 21, PostgreSQL과 Google Cloud Text-to-Speech API를 사용할 수 있는 서비스 계정이 필요하다. 기존 `.env`의 DB 설정을 유지하고 다음 설정을 추가한다. 인증 JSON은 저장소 밖이나 Git에서 제외된 `secrets/` 아래에 둔다.

```properties
GOOGLE_APPLICATION_CREDENTIALS=C:/RoadStudy/secrets/google-service-account.json
GOOGLE_TTS_VOICE=ko-KR-Neural2-A
GOOGLE_TTS_SPEAKING_RATE=1.0
GOOGLE_TTS_TIMEOUT_SECONDS=60
AUDIO_STORAGE_PATH=storage/audio
```

`.env`는 Java properties 형식으로 읽는다. Windows 경로에는 위 예시처럼 `/`를 사용한다.

| 설정 | 기본값 | 의미 |
|---|---|---|
| `GOOGLE_APPLICATION_CREDENTIALS` | 빈 값 | 서비스 계정 JSON 경로. 비어 있으면 SDK 기본 인증 탐색 사용 |
| `GOOGLE_TTS_VOICE` | `ko-KR-Neural2-A` | 사용할 음성 이름 |
| `GOOGLE_TTS_SPEAKING_RATE` | `1.0` | 말하기 속도. 0.25~2.0 |
| `GOOGLE_TTS_TIMEOUT_SECONDS` | `60` | 합성 RPC 제한 시간. 양수이며 자동 재시도 없음 |
| `AUDIO_STORAGE_PATH` | `storage/audio` | 실행 디렉터리 기준 저장 경로 |

프로젝트 루트의 PowerShell에서 실행한다.

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

Swagger UI는 [로컬 Swagger](http://localhost:8080/swagger-ui/index.html)에서 확인할 수 있다. local 프로필의 시작 점검은 클라이언트 빈 생성을 검사하며 실제 음성 합성을 수행하지 않는다.

## 음성 생성

`POST /api/studies/generate-audio`, `Content-Type: application/json`

```json
{
  "explanationScript": "오늘은 자동차의 첨단 운전자 보조 시스템에 대해 알아보겠습니다."
}
```

대본의 앞뒤 공백을 제거한 뒤 검증한다. UTF-8 기준 5,000바이트까지 허용한다. 한글 음절 1,667개는 5,001바이트이므로 거부된다.

성공 응답은 `200 OK`다.

```json
{
  "audioId": "550e8400-e29b-41d4-a716-446655440000",
  "audioUrl": "/api/studies/audio/550e8400-e29b-41d4-a716-446655440000"
}
```

PowerShell 호출 예시:

```powershell
$body = @{ explanationScript = '오늘은 자동차의 안전 기능에 대해 알아보겠습니다.' } | ConvertTo-Json
$audio = Invoke-RestMethod -Method Post `
    -Uri 'http://localhost:8080/api/studies/generate-audio' `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
$audio
```

## 저장된 MP3 조회와 구간 재생

`GET /api/studies/audio/{audioId}`

- 전체 요청: `200 OK`, `Content-Type: audio/mpeg`, 본문은 MP3 바이트.
- 정상 `Range` 요청: `206 Partial Content`, 요청 구간의 바이트와 `Content-Range` 반환.
- 잘못되거나 충족할 수 없는 `Range` 요청: `416`, `ProblemDetail`과 `Content-Range: bytes */전체크기` 반환.
- 존재하지 않거나 표준 UUID 형식이 아닌 ID: `404`. 표준 UUID의 영문 대소문자는 모두 허용한다.

브라우저에서 `http://localhost:8080` 뒤에 생성 응답의 `audioUrl`을 붙여 열면 재생할 수 있다. 기본 저장 위치는 `storage/audio/{audioId}.mp3`다. 서버의 실제 파일 경로는 생성 응답에 포함하지 않는다.

## 오류 계약

오류 본문은 `application/problem+json` 형식이며 `code` 필드로 원인을 구분한다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | `EMPTY_SCRIPT` | 대본 누락·null·빈 문자열·공백뿐인 문자열 |
| 400 | `HTTP_400` | 깨진 JSON 또는 누락된 요청 본문 |
| 404 | `AUDIO_NOT_FOUND` | ID 형식 오류 또는 저장된 파일 없음 |
| 415 | `HTTP_415` | JSON이 아닌 생성 요청 |
| 416 | `HTTP_416` | 잘못된 음성 구간 요청 |
| 422 | `TTS_INPUT_TOO_LONG` | UTF-8 5,000바이트 초과 |
| 500 | `AUDIO_STORAGE_FAILED` | 디렉터리 생성·파일 쓰기·최종 이동 실패 |
| 502 | `TTS_GENERATION_FAILED` | Google 호출 실패·클라이언트 초기화 실패·빈 음성 응답 |
| 504 | `TTS_TIMEOUT` | Google 합성 제한 시간 초과 |

합성·저장 오류의 원인은 내부 예외 체인에 보존한다. HTTP 응답은 정의된 메시지만 반환하며 인증 정보나 원본 예외 메시지를 포함하지 않는다.

## 코드 구성

```text
StudyController → GenerateStudyAudio → StudyModifyService
                                      ├─ SpeechSynthesizer ← GoogleSpeechSynthesizer
                                      └─ AudioStorage     ← LocalAudioStorage

StudyController → FindStudyAudio → StudyQueryService → AudioStorage
```

Google SDK 타입은 TTS 어댑터와 설정에서 처리한다. 파일 저장은 같은 디렉터리의 임시 파일에 쓰고 원자적 이동으로 최종 `.mp3`를 만든다. 저장 실패 시 임시 파일 정리를 시도한다. 이 흐름에는 DB 작업이 없어 DB 트랜잭션을 열지 않는다.

저장된 파일은 호출마다 새 UUID로 생성되며 자동 삭제하지 않는다. 현재는 대본 분할·병합과 동일 대본의 합성 캐시를 제공하지 않는다.

## 자동 테스트와 수동 완료 확인

```powershell
$env:SPRING_PROFILES_ACTIVE = 'test'
.\gradlew.bat --offline test --no-daemon
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

Gradle 의존성을 처음 받는 환경에서는 `--offline`을 빼고 실행한다. 테스트는 H2와 모의 Google 클라이언트를 사용한다. 설정 검사는 모의 인증 객체로 제한 시간과 단일 시도를 확인하고, 저장 검사는 JUnit 임시 디렉터리에 실제로 파일을 쓴다.

자동 테스트의 음성 바이트는 저장·전송 검사용 데이터다. 실제 MP3 디코딩과 Google 음성 품질은 아래 수동 확인으로 검증한다.

이관 시 각 구현 커밋 상태에서 전체 자동 테스트를 실행한 결과다.

| 구현 단위 | 전체 | 통과 | 건너뜀 | 실패 |
|---|---|---|---|---|
| TTS 어댑터·설정·오류 계약 | 52 | 51 | 1 | 0 |
| 로컬 저장·조회 어댑터 추가 | 58 | 57 | 1 | 0 |
| 음성 생성·조회 API 연결 | 95 | 94 | 1 | 0 |

건너뛴 항목은 기존의 환경변수 조건부 Google TTS 클라이언트 빈 생성 테스트다. 설정·합성 어댑터 검사는 모의 클라이언트와 인증 객체로 수행했으며 실제 Google 합성을 호출하지 않았다.

- [ ] 실제 서비스 계정으로 짧은 한국어 대본의 MP3 생성 성공
- [ ] 저장 디렉터리에 MP3가 생성되고 응답 URL에서 음성이 재생됨
- [ ] 브라우저에서 재생 위치 이동 확인
- [ ] 빈 대본 400, 길이 초과 422, 없는 ID 404 확인

실제 Google 합성과 브라우저 재생은 코드 이관 단계에서 실행하지 않았다.
