# RoadStudy Backend

RoadStudy는 PDF 학습 자료를 AI 설명 대본과 음성으로 변환해 차량에서 복습할 수 있게 하는 현대차 공모전 프로젝트다. 현재 API로 제공하는 기능은 PDF 텍스트 추출이다.

**기술 스택** · Java 21, Spring Boot 3.5, Gradle, PostgreSQL, Spring Data JPA, PDFBox 3, Swagger/OpenAPI, JUnit 5

## 1. 도메인별로 코드를 모으고 포트로 경계를 나눈다

- **컨트롤러는 도메인 단위로 둔다.** 같은 도메인의 HTTP 엔드포인트는 `XxxController`에 모은다. 메서드는 HTTP 입력 처리, 인바운드 포트 호출, 응답 변환을 담당한다.
- **서비스는 도메인별로 `XxxModifyService`와 `XxxQueryService`로 나눈다.** `Modify`는 생성·수정·상태 전이와 그 과정에 필요한 작업을, `Query`는 저장된 상태를 읽는 흐름을 맡는다. 어느 쪽에 둘지는 HTTP 메서드보다 업무 흐름으로 판단한다. PDF 추출을 `StudyModifyService`에 둔 이유도 학습 생성에 필요한 작업이기 때문이다.
- **포트는 행위나 역할에 따라 나누고, 구현은 책임이 비슷하면 한 서비스에 모은다.** 컨트롤러는 `port.in`의 포트를 주입받는다. 포트 이름은 `ExtractStudyText`, `FindStudy`처럼 행위를 드러내고 `UseCase` 접미사는 생략한다. 서비스 하나가 여러 포트를 구현할 수 있다. 서비스 분리는 메서드 수보다 변경 이유, 의존성, 트랜잭션 경계, 실패 처리 방식이 달라지는지를 기준으로 한다.
- **저장과 외부 연동은 `port.out`으로 접근한다.** 서비스는 `StudyRepository`, `DocumentReader` 같은 포트만 사용한다. JPA, PDFBox, OpenAI, TTS 등 구체 기술과 SDK 타입은 각 어댑터에서 처리한다. 웹 타입과 SDK 타입을 애플리케이션 계약이나 도메인 모델에 넘기지 않는다.
- **트랜잭션은 애플리케이션 서비스의 DB 작업 단위에 둔다.** 조회에 필요하면 `@Transactional(readOnly = true)`를 사용한다. 파일 처리와 외부 API 응답을 기다리는 동안 DB 트랜잭션을 열어두지 않는다.

```text
Web Adapter → port.in → Modify / Query Service → port.out ← Outbound Adapter
```

## 2. HTTP 계약은 웹 계층에 둔다

| 역할 | 규칙 |
|---|---|
| HTTP 요청 | `adapter.web.request`의 별도 `XxxRequest` 레코드를 사용한다. 파일 하나만 받는 multipart는 컨트롤러에서 `@RequestPart("file") MultipartFile`로 받아 필요한 값만 포트에 전달한다. |
| 유스케이스 반환 | 실제로 다룬 도메인 객체를 반환한다. 세션 생성·조회라면 `StudySession`을 반환한다. 해당 동작이 엔티티를 다루지 않고 여러 결과값을 반환한다면 `ExtractedStudyText`처럼 애플리케이션 결과 레코드 하나로 묶는다. |
| HTTP 응답 | `adapter.web.response`의 별도 `XxxResponse` 레코드를 사용한다. 컨트롤러는 `XxxResponse.from(...)`을 호출해 `ResponseEntity`로 반환한다. |
| Swagger | `adapter.web.docs`의 별도 인터페이스에 OpenAPI 명세를 작성한다. 컨트롤러가 인터페이스를 구현하고 실제 HTTP 매핑을 선언한다. |

요청·응답·결과 레코드는 필드가 하나여도 별도 파일로 둔다. `XxxDto`보다 `XxxRequest`, `XxxResponse`, `ExtractedStudyText`처럼 용도가 드러나는 이름을 사용한다. 같은 형태의 결과가 인바운드와 아웃바운드 포트에 모두 필요하면 애플리케이션 계층에 있는 결과 타입을 공유한다.

`from(...)`은 필드 매핑과 문자 수처럼 응답에 필요한 파생값을 계산한다. 업무 검증과 상태 변경은 서비스 또는 도메인 모델에서 수행한다. 예를 들어 PDF 추출은 `ExtractedStudyText`를 반환하고, 컨트롤러는 업로드 파일명을 더해 `ExtractTextResponse.from(fileName, result)`를 호출한다.

## 3. 도메인 모델에 상태와 규칙을 둔다

- 현재 MVP에서 `StudySession`은 도메인 모델과 JPA 엔티티를 겸하는 일반 클래스다. 저장 방식은 `StudyRepository` 포트 뒤에 둔다.
- 도메인 객체는 `create(...)` 정적 팩토리로 생성하고 JPA 기본 생성자는 `protected`로 유지한다. 생성 입력을 묶을 때는 `StudySessionCreateRequest`처럼 도메인 옆에 별도 레코드를 둔다. HTTP 요청과 생성 입력의 검증 책임을 구분한다.
- 초기 상태와 생성 시각은 모델이 정한다. 외부 처리 결과는 준비된 시점에 기존 객체에 반영한다. 상태 변경은 `fail()`처럼 의미 있는 메서드로 드러내고, 상태 변경에 필요한 불변식도 모델에서 확인한다.
- 자바 타입의 기본 매핑으로 충분하면 그 매핑을 쓴다. `summary`와 `explanationScript`처럼 PostgreSQL `TEXT`가 필요한 필드는 명시한다. PDF 원본과 MP3 바이너리는 DB에 저장하지 않고, 음성 저장 시에는 로컬 MP3 경로만 기록한다.
- 반복 코드는 롬복으로 줄인다. getter, 필수 인자 생성자, JPA 기본 생성자, 로거에 `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor`, `@Slf4j`를 사용한다. 생성 검증과 상태 전이는 코드에 명시한다.

## 4. 예외와 테스트 기준

- 예상 가능한 업무 오류에는 `ApplicationException`을 상속한 예외를 사용한다. 같은 기능에서 발생하고 호출자가 타입별로 처리할 필요가 없는 오류는 기능별 예외 하나에 원인별 정적 팩토리로 묶을 수 있다. 도메인 불변식 위반은 해당 도메인의 예외로 표현한다.
- **오류 코드와 필드 식별자는 영어, 직접 작성하는 예외 메시지는 한국어**로 쓴다. 예외는 오류 코드와 `FailureType`을 전달하고, `GlobalExceptionHandler`가 HTTP 상태와 `ProblemDetail`로 변환한다. 내부 예외 원문과 인증 정보는 응답에 노출하지 않는다.
- 테스트 클래스와 메서드에 한국어 `@DisplayName`을 붙인다. 기능마다 정상 경로, 정의된 실패 조건, 경계값을 검증한다. API 실패 테스트는 HTTP 상태뿐 아니라 `ProblemDetail` 형식과 오류 코드도 확인한다.
- 도메인 규칙은 단위 테스트로, HTTP 요청·응답과 Swagger 계약은 웹 테스트로, 저장·조회는 JPA 테스트로 확인한다. 일반 자동 테스트는 실제 PostgreSQL이나 유료 API 호출에 의존하지 않는다.

## 5. 브랜치·커밋·PR

- 작업 브랜치는 `<유형>/#<이슈번호>-<영문-주제>` 형식으로 만든다. 예시는 `feature/#1-pdf-text-extraction`이다.
- 유형은 `feature`, `fix`, `refactor`, `docs`를 사용한다. `init`은 초기 프로젝트 설정 커밋에만 사용한다.
- 커밋 메시지는 `<유형>: <변경 내용>`으로 작성한다. 예시는 `feature: PDF 업로드 및 텍스트 추출 기능 구현`이다.
- 한 기능의 구현과 테스트는 함께 커밋한다. 기능 변경과 로컬 설정 변경처럼 각각 리뷰할 수 있는 작업은 따로 스테이징한다.
- 작업 브랜치를 푸시하고 PR을 통해 `main`에 병합한다. PR에는 작업 내용, 검증 결과, 관련 이슈를 짧게 적는다.

## 6. 팀원과 에이전트의 작업 순서

1. 구현 전에 기존 코드와 Git 상태를 읽고, 해당 행위가 들어갈 도메인·서비스·포트를 확인한다.
2. 같은 책임을 가진 코드가 있으면 그 구조에 붙인다. 새 클래스나 포트는 기존 책임으로 표현할 수 없거나 경계를 분리할 이유가 있을 때 추가한다.
3. 동작과 API 계약을 테스트하고 변경 파일, 검증 결과, 확인하지 못한 범위를 보고한다. 요청된 작업 단위로 스테이징하고, 커밋과 푸시는 사용자 지시에 따른다.
