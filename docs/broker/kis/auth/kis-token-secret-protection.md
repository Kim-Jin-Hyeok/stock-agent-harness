# KIS 인증 오류와 객체 출력의 민감정보 보호

## 목적과 범위

KIS 토큰 발급 실패를 로그나 실행 이력에 남길 때 앱 키, 앱 secret과 액세스 토큰이 함께 전달되지 않도록 인증 계층의 오류와 객체 문자열 출력을 제한한다. **실제 인증 JSON과 토큰 접근자는 원본값을 유지하며, 토큰 캐시와 갱신 규칙은 변경하지 않는다.**

실전 조회용 인증 구성, 토큰 저장소, 호출 제한이나 재시도 정책을 추가하는 작업은 아니다. 기존 모의투자와 실전 환경 설정 및 주문 권한도 그대로 유지한다.

## 패키지와 변경 파일

기준 패키지는 `com.stock.broker.kis.auth`다. `src/main/java/` 아래 기존 파일을 수정하고 별도의 인증 계층이나 인터페이스는 추가하지 않는다.

| 파일 | 변경 내용 |
| --- | --- |
| `com/stock/broker/kis/auth/KisTokenClient.java` | HTTP 오류 본문을 읽지 않고 상태 코드만 전달한다. 통신 및 응답 변환 실패는 고정 메시지로 바꾼다. |
| `com/stock/broker/kis/auth/KisTokenProvider.java` | 만료시간 파싱 실패에서 입력값이 들어 있는 원인 예외를 제거한다. |
| `com/stock/broker/kis/auth/dto/KisTokenRequest.java` | `toString()`에서 모든 문자열 값을 가린다. |
| `com/stock/broker/kis/auth/dto/KisTokenResponse.java` | `toString()`에서 모든 문자열 값을 가리고 숫자 유효시간만 표시한다. |

새 테스트 파일은 `src/test/java/com/stock/broker/kis/auth/dto/KisTokenRequestTest.java`다. 나머지는 기존 Client, Provider와 응답 DTO 테스트를 보강한다.

## 오류 전달 계약

토큰 발급 경로와 요청은 기존 `POST /oauth2/tokenP` 및 `client_credentials`를 유지한다. HTTP 4xx와 5xx는 응답 본문을 읽기 전에 거절하며 다음처럼 숫자 상태 코드만 전달한다.

```text
KIS token response HTTP status=401.
```

통신, 응답 읽기, JSON 변환, 지원하지 않는 Content-Type 및 응답 닫기 과정의 런타임 실패는 다음 고정 메시지로 전달한다. 닫기 오류가 HTTP 오류를 덮어쓰는 경우에도 원본 오류를 노출하지 않고 이 메시지로 바꾼다.

```text
KIS token request failed.
```

Client는 원본 메시지, 응답 본문, 응답 헤더, 상태 설명 문자열, cause와 suppressed exception을 붙이지 않은 새 예외를 전달한다. 숨긴 예외를 별도로 로깅하지 않는다. 이 선택은 세부 진단 정보보다 인증정보 비노출을 우선한다.

| 실패 | 전달 예외 | 유지하는 정보 |
| --- | --- | --- |
| HTTP 오류 | `RestClientResponseException` | 숫자 HTTP 상태와 안전한 메시지 |
| 통신 오류 | `ResourceAccessException` | 고정 메시지 |
| 응답 변환 등 Spring Client 오류 | `RestClientException` | 고정 메시지 |
| 그 밖의 런타임 오류 | `IllegalStateException` | 고정 메시지 |

HTTP 예외의 응답 헤더와 본문은 비우고 상태 설명 문자열도 비운다. 예외 분류를 모두 `IllegalStateException`으로 바꾸지 않는 이유는 기존 현재가, 일봉과 지수 Provider가 HTTP 429 및 5xx와 통신 오류를 일시적 실패로 구분하기 때문이다. 상위 계층의 기존 실패 분류를 유지하면서 인증정보만 제거한다. Client 안에 재시도를 추가하지 않으며 상위 Harness의 재시도 정책도 변경하지 않는다.

Provider의 잘못된 만료시간은 기존 메시지 `KIS access token expiration is invalid.`를 유지하되 `DateTimeParseException`을 cause로 붙이지 않는다. null, 빈 문자열이나 인증정보가 섞인 문자열이 파싱에 실패해도 입력값을 스택트레이스로 전달하지 않는다.

## 객체 출력과 실제 데이터

요청 객체의 `grantType`, `appKey`, `appSecret`과 응답 객체의 `accessToken`, `tokenType`, `expiresAt`은 `toString()`에서 `<redacted>`로 표시한다. 정상적인 타입이나 시각도 가리는 이유는 잘못된 응답이나 임의로 생성한 DTO에서 해당 문자열에 비밀값이 들어갈 수 있기 때문이다. 숫자인 `expiresInSeconds`는 출력한다.

record 필드, 접근자, `@JsonProperty`, 동등성은 변경하지 않는다. Jackson 직렬화와 역직렬화에는 실제 키, secret과 토큰이 그대로 사용된다. 인증 요청에서 비밀값을 가리면 정상 인증이 불가능하므로 문자열 출력과 전송 JSON을 구분한다.

**이 보호는 모든 로깅 경로의 비밀정보 필터가 아니다.** DTO 접근자를 직접 기록하거나 DTO를 JSON으로 변환해 기록하면 비밀값이 노출된다. 주입된 HTTP 구현과 인터셉터의 헤더 및 본문 디버그 로깅도 별도로 통제해야 한다. 원본 인증 JSON은 운영 로그나 공개 응답으로 사용하지 않는다.

## 유지되는 토큰 재사용 규칙

`getAccessToken()`의 synchronized 처리와 Provider 인스턴스별 인메모리 캐시를 유지한다. 현재 시각이 `expiresAt - refreshBeforeExpiration`보다 이르면 기존 토큰을 재사용하고, 정확히 그 시각에 도달하면 새 토큰을 요청한다. 만료시간은 기존 서울 시간대 해석을 유지한다.

유효하지 않은 응답은 캐시에 저장하지 않고 한 번의 호출 안에서 자동으로 재시도하지 않는다. 이후 호출자가 다시 `getAccessToken()`을 호출하면 발급 요청을 다시 할 수 있다는 기존 동작은 유지한다. 실패 대기나 재발급 제한 기능을 새로 추가한 것은 아니다.

캐시는 프로세스 재시작 후 복구되지 않으며 다른 Provider 인스턴스와 공유하지 않는다. 이번 수정이 토큰의 영속 저장이나 재시작 시 재발급 방지를 제공하는 것은 아니다.

## 검증 결과

2026-10-06에 다음 관련 테스트만 실행했고 93개 모두 통과했다. 실패, 오류 및 건너뛴 테스트는 각각 0개다.

| 테스트 클래스 | 검증 내용 | 테스트 수 |
| --- | --- | --- |
| `KisTokenClientTest` | 정상 요청과 응답, HTTP 오류 본문 미읽기, 통신 및 변환 오류, 응답 데이터 및 cause와 suppressed 제거, 현재가 Provider까지의 실패 분류와 스택트레이스 비노출, 자동 재시도 없음 | 24 |
| `KisTokenProviderTest` | 토큰 재사용, 갱신 직전과 경계, 만료시간 오류 비노출, 실패 응답 미캐싱, 자동 재시도 없음 | 12 |
| `KisTokenRequestTest` | 객체 출력 가림, 원본 접근자와 JSON 필드 및 값 보존, null 처리 | 3 |
| `KisTokenResponseTest` | 객체 출력 가림, 원본 JSON 직렬화와 역직렬화, null 처리 | 4 |
| `KisConfigurationTest` | 기존 KIS 빈 구성 회귀 검증 | 9 |
| `KisCurrentPriceProviderTest` | 기존 현재가 변환과 실패 분류 회귀 검증 | 7 |
| `KisDailyPriceHistoryProviderTest` | 기존 일봉 조회와 실패 분류 회귀 검증 | 18 |
| `KisMarketIndexDailyHistoryProviderTest` | 기존 지수 조회와 실패 분류 회귀 검증 | 16 |

```powershell
.\gradlew.bat test --tests 'com.stock.broker.kis.auth.*' --tests 'com.stock.broker.kis.config.KisConfigurationTest' --tests 'com.stock.market.price.provider.kis.KisCurrentPriceProviderTest' --tests 'com.stock.market.price.history.provider.kis.KisDailyPriceHistoryProviderTest' --tests 'com.stock.market.index.history.provider.kis.KisMarketIndexDailyHistoryProviderTest' --offline --no-daemon
```

HTTP 테스트는 `MockRestServiceServer`와 가짜 인증정보를 사용한다. 실제 KIS 요청이나 토큰 발급을 하지 않았으며 `.env`, Docker와 실행 서버를 변경하지 않았다. 이 결과는 인증 오류 전달과 객체 출력 계약에 대한 검증이며 실전 조회 연결이나 투자 성과 검증은 아니다.
