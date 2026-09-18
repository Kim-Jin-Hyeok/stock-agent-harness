# Broker Current Price Integration Design

## Purpose

이 문서는 고정 가격을 반환하는 현재 구현을 증권사 모의투자 API 기반 현재가 조회로 교체하기 전에 필요한 설계 결정을 정리한다.

이번 단계의 목표는 특정 증권사 API 구현을 미리 완성하는 것이 아니다. 먼저 현재 Harness와 시장 데이터 경계를 유지하면서 어떤 외부 계약을 받아들일지 결정한다.

## Current State

현재 현재가 조회 흐름은 다음과 같다.

```text
HarnessToolExecutor
-> CurrentPriceService
-> CurrentPriceCache
-> CurrentPriceProviderCallGuard
-> CurrentPriceProvider
-> CurrentPriceSnapshot
```

현재 `CurrentPriceProvider` 구현체는 `FixedCurrentPriceProvider` 하나이며, 요청한 종목에 대해 고정 가격을 반환한다.

```text
src/main/java/com/stock/market/price/provider/CurrentPriceProvider.java
src/main/java/com/stock/market/price/provider/FixedCurrentPriceProvider.java
```

`FixedCurrentPriceProvider`는 Harness의 Tool 호출, 캐시, Budget, 결과 검증 및 재시도 흐름을 개발 환경에서 확인하기 위한 임시 구현이다. 실제 시장 가격의 진실 공급원으로 사용하지 않는다.

현재 Harness에는 실제 Provider 연동을 위한 다음 통제 장치가 이미 있다.

- Run별 Provider 호출 Budget
- 현재가 TTL 캐시
- 현재가 관측 시각 기반 신선도 검증
- Provider 일시적 실패와 영구적 실패 분류
- Tool 재시도 횟수 제한
- 재시도 대기와 `WAIT_TOOL_RETRY` Step 기록
- Tool 요청과 실행 결과 저장

## Design Boundary

애플리케이션 내부에서 증권사 현재가 API의 경계는 기존 `CurrentPriceProvider`를 사용한다.

```java
public interface CurrentPriceProvider {
    CurrentPriceSnapshot getCurrentPrice(String symbol);
}
```

증권사 전용 구현은 외부 요청과 응답을 애플리케이션 공통 모델인 `CurrentPriceSnapshot`으로 변환한다.

```text
증권사 요청 symbol
-> 증권사 현재가 API 호출
-> 증권사 전용 응답 DTO
-> CurrentPriceSnapshot(symbol, priceKrw, observedAt)
```

`CurrentPriceService`와 Harness는 증권사별 응답 구조를 알지 않는다. 증권사 전용 필드명, 인증 헤더, 오류 코드는 Provider 구현 내부에 머문다.

현재 단계에서는 `BrokerClient`와 같은 범용 인터페이스를 추가하지 않는다. 실제로 두 개 이상의 증권사 구현을 함께 지원해야 하는 요구가 생기기 전까지 `CurrentPriceProvider`가 필요한 외부 경계 역할을 충분히 수행한다.

## Provider Selection Decision

첫 모의투자 연동 대상은 한국투자증권 Open API로 결정한다.

한국투자증권은 모의투자 환경에서 국내주식 현재가, 주문, 체결 내역 및 잔고 조회를 제공한다. 현재 프로젝트는 먼저 현재가 REST API를 연결하고, 이후 같은 Broker 경계 안에서 모의 주문과 계좌 조회로 확장한다.

증권사를 선택할 때 다음 항목을 확인한다.

| 확인 항목 | 결정 기준 | 현재 상태 |
| --- | --- | --- |
| 모의투자 지원 | 실제 자금 없이 현재가 조회와 주문 흐름을 검증할 수 있는가 | 지원 |
| 현재가 API | 국내 주식 종목 코드를 기준으로 현재가를 조회할 수 있는가 | 지원 |
| 인증 방식 | 애플리케이션에서 토큰 발급과 재사용을 자동화할 수 있는가 | OAuth 접근 토큰 사용, 세부 갱신 정책 확인 필요 |
| 호출 제한 | 초당, 분당 또는 일별 제한을 공식적으로 확인할 수 있는가 | 미결정 |
| 관측 시각 | 응답에서 가격 기준 시각을 확인할 수 있는가 | 미결정 |
| 오류 계약 | HTTP 상태 외에 Broker 오류 코드와 메시지를 제공하는가 | 미결정 |
| 운영 전환 | 모의투자와 실제투자를 설정으로 명확히 분리할 수 있는가 | Base URL과 App Key를 환경별로 분리 가능 |

공식 예제에서 확인한 현재 계약은 다음과 같다.

```text
Broker 이름: 한국투자증권
모의투자 API Base URL: https://openapivts.koreainvestment.com:29443
실제투자 API Base URL: https://openapi.koreainvestment.com:9443
토큰 Endpoint: /oauth2/tokenP
토큰 발급 방식: client_credentials
현재가 Endpoint: /uapi/domestic-stock/v1/quotations/inquire-price
TR ID: FHKST01010100
시장 구분 요청 필드: FID_COND_MRKT_DIV_CODE
종목 코드 요청 필드: FID_INPUT_ISCD
현재가 응답 필드: stck_prpr
```

다음 값은 한국투자증권 공식 API 문서 또는 실제 모의투자 응답을 확인한 후 확정한다.

```text
토큰 유효 시간과 재발급 제한
가격 기준 일자 및 체결 시각 응답 필드
HTTP 상태 및 Broker 오류 코드별 실패 분류
초당 호출 제한
분당 호출 제한
```

현재 Scheduler는 약 5분 간격으로 Agent Run을 시작하므로 첫 현재가 연동은 REST API를 사용한다. 지속적인 실시간 시세 구독이 필요한 단계가 오기 전까지 WebSocket 연동은 범위에 포함하지 않는다.

## Current Price Contract

증권사 구현체가 반환하는 `CurrentPriceSnapshot`은 다음 계약을 만족해야 한다.

```text
symbol
-> 요청한 종목 코드와 같아야 한다.

priceKrw
-> 원화 기준 현재가다.
-> 0보다 커야 한다.

observedAt
-> 애플리케이션이 응답을 받은 시각이 아니라 가격이 관측된 시각이다.
-> null일 수 없다.
```

한국투자증권 `inquire-price` 응답에서 가격 기준 일자와 체결 시각으로 사용할 수 있는 필드를 확인한 후 `observedAt` 변환 규칙을 결정한다. 현재 단계에서는 필드명을 추측하여 계약으로 확정하지 않는다.

응답에 관측 시각이 명시적으로 없다면 임의로 `Instant.now()`를 넣기 전에 다음 중 하나를 선택해야 한다.

1. 공식 문서에서 응답 가격의 기준 시각을 계산할 수 있는 필드를 찾는다.
2. 수신 시각을 관측 시각으로 사용한다는 제한을 문서화한다.
3. 정확한 관측 시각을 보장할 수 없는 응답은 Provider 계약 실패로 처리한다.

기본 추천은 공식 응답에서 확인된 영업일자와 체결 시각을 조합해 `observedAt`을 만드는 것이다. 사용할 수 있는 시각 필드가 없는 경우에만 수신 시각 사용을 별도 설계 결정으로 검토한다.

## Failure Classification

증권사 Adapter는 외부 실패를 `CurrentPriceProviderException`으로 변환한다.

```text
src/main/java/com/stock/market/price/provider/error/CurrentPriceProviderException.java
src/main/java/com/stock/market/price/provider/error/CurrentPriceProviderFailureType.java
```

### Temporary Failure

같은 요청이 잠시 후 성공할 가능성이 있는 실패다.

예상 대상:

- 연결 실패
- 연결 또는 응답 타임아웃
- HTTP 429
- HTTP 5xx
- Broker가 명시한 일시적 시스템 장애

이 실패는 `PROVIDER_TEMPORARY_FAILURE`로 변환되며 Harness 재시도 정책의 대상이 된다.

### Permanent Failure

같은 요청을 그대로 반복해도 성공하기 어려운 실패다.

예상 대상:

- 잘못된 종목 코드
- 지원하지 않는 시장 구분
- 요청 형식 오류
- 유효하지 않은 App Key 또는 App Secret
- 권한이 없는 계좌나 API
- Broker가 명시한 영구적 요청 오류

이 실패는 `PROVIDER_PERMANENT_FAILURE`로 변환되며 같은 Run에서 재시도하지 않는다.

인증 토큰 만료는 단순히 영구적 실패로 고정하지 않는다. 토큰을 한 번 갱신한 뒤 요청을 다시 보낼 수 있는지 증권사 인증 계약을 확인한 후 분류한다. 토큰 갱신 자체의 재시도와 Harness의 Tool 재시도도 중복되지 않도록 책임을 구분해야 한다.

구체적인 HTTP 상태 및 Broker 오류 코드 매핑은 증권사 선정 후 공식 명세를 기준으로 표를 추가한다.

## Sensitive Information

다음 값은 소스 코드와 Git에 저장하지 않는다.

- App Key
- App Secret
- Access Token
- Refresh Token
- 계좌번호 전체
- 실제 Broker 요청 및 응답에 포함된 개인 식별 정보

민감한 설정은 환경 변수 또는 운영 환경의 Secret 저장소에서 주입한다. `application.yml`에는 실제 값 대신 환경 변수 참조만 둘 수 있다.

예상 설정 모양은 다음과 같으며, 정확한 이름은 증권사가 결정된 뒤 확정한다.

```yaml
broker:
  provider: ${BROKER_PROVIDER}
  base-url: ${BROKER_BASE_URL}
  app-key: ${BROKER_APP_KEY}
  app-secret: ${BROKER_APP_SECRET}
```

실패 결과와 Harness 이력에는 Broker 원본 응답 전체를 저장하지 않는다. 인증정보나 계좌정보가 포함되지 않은 정제된 실패 메시지와 내부 reasonCode만 저장한다.

## Rate Limit Responsibility

현재 `HarnessProviderCallBudget`은 한 Run에서 실제 Provider 호출을 몇 번 허용할지 제한한다. 이는 여러 Run에 걸친 초당 또는 분당 Broker 호출 제한과 다르다.

```text
Run Provider Call Budget
-> 한 Agent Run 내부의 최대 호출 횟수

Broker Rate Limit
-> 애플리케이션 전체 또는 API Key 전체의 시간 단위 최대 호출 횟수
```

시간 단위 Rate Limit은 증권사의 실제 제한을 확인한 후 설계한다. 제한값을 모르는 상태에서 임의의 범용 Rate Limiter를 먼저 구현하지 않는다.

단일 애플리케이션 인스턴스에서 시작한다면 인메모리 제한을 사용할 수 있다. 여러 서버가 같은 API Key를 공유하게 되면 Redis와 같은 공유 저장소가 필요할 수 있다. 현재 단계에서는 Redis를 도입하지 않는다.

## Expected Package Structure

한국투자증권 전용 구현은 `kis` 패키지 아래에 둔다.

```text
src/main/java/com/stock/market/price/provider/kis/
├── KisCurrentPriceProvider.java
├── KisCurrentPriceClient.java
├── KisCurrentPriceProperties.java
└── dto/
    ├── KisCurrentPriceResponse.java
    └── KisCurrentPriceOutput.java
```

인증 구현이 필요해지면 현재가 패키지 안에 억지로 넣지 않고 Broker 공통 인증 영역으로 분리한다.

```text
src/main/java/com/stock/broker/kis/auth/
├── KisTokenClient.java
├── KisTokenProvider.java
├── KisTokenProperties.java
└── dto/
    └── KisTokenResponse.java
```

아직 인증 방식과 토큰 재사용 요구가 확인되지 않았으므로 위 인증 클래스는 지금 생성하지 않는다.

## Incremental Implementation Plan

증권사 선정 후에도 한 번에 인증, 현재가, 주문을 모두 구현하지 않는다.

### Step 1. Response Mapping

증권사 현재가 응답 DTO를 만들고 `CurrentPriceSnapshot` 변환 규칙을 테스트한다. 이 단계에서는 실제 HTTP 요청을 보내지 않는다.

완료 조건:

- 정상 응답에서 종목 코드와 가격이 변환된다.
- Broker 시각이 `Instant`로 변환된다.
- 필수 값이 없거나 가격이 올바르지 않은 응답을 거부한다.

### Step 2. HTTP Client Boundary

현재가 Endpoint를 호출하는 증권사 전용 Client를 구현하고 HTTP 요청과 응답 역직렬화를 테스트한다.

완료 조건:

- 요청 URL, Header, Query Parameter가 공식 계약과 일치한다.
- 원격 서버 없이 HTTP 경계 테스트를 실행할 수 있다.
- 원본 인증정보가 로그와 예외 메시지에 노출되지 않는다.

### Step 3. Authentication

토큰 발급과 재사용 범위를 구현한다.

완료 조건:

- 유효한 토큰을 불필요하게 다시 발급하지 않는다.
- 만료된 토큰을 갱신할 수 있다.
- 인증 실패가 정제된 Provider 실패로 변환된다.

### Step 4. Provider Replacement

증권사 Provider를 `CurrentPriceProvider` 구현으로 연결하고 고정 Provider와 실행 환경을 분리한다.

완료 조건:

- 개발 또는 테스트에서는 고정 Provider를 선택할 수 있다.
- 모의투자 환경에서는 증권사 Provider를 선택할 수 있다.
- 동시에 두 구현이 Bean으로 등록되어 애플리케이션 시작이 실패하지 않는다.
- 기존 `CurrentPriceService`와 Harness 계약은 변경하지 않는다.

### Step 5. Harness Integration Verification

실제 Provider 결과가 기존 Harness 통제 흐름을 지나는지 검증한다.

완료 조건:

- 캐시 적중 시 외부 API를 다시 호출하지 않는다.
- 캐시 미스 시 Provider Budget을 소비한다.
- 일시적 실패만 Harness가 재시도한다.
- 영구적 실패는 즉시 Run 실패로 이어진다.
- 현재가와 조회 출처가 Run 이력에 저장된다.

## Out of Scope

현재가 첫 연동 단계에서는 다음 작업을 함께 진행하지 않는다.

- 실제투자 주문 실행
- BUY 또는 SELL Broker API 연동
- 여러 증권사 동시 지원
- Redis 캐시 또는 분산 Rate Limit
- 전체 종목 Universe 수집
- 차트, 재무정보, 뉴스 API 연동
- Broker 응답 전체 저장
- WebSocket 실시간 시세 구독

## References

- 한국투자증권 공식 Open API 예제 저장소: https://github.com/koreainvestment/open-trading-api
- 국내주식 현재가 REST 예제: https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/domestic_stock/inquire_price/inquire_price.py
- 모의투자 및 실전 환경설정 예제: https://github.com/koreainvestment/open-trading-api/blob/main/kis_devlp.yaml
- OAuth 인증 예제: https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/kis_auth.py

## Next Decision

다음 구현을 시작하기 전에 확인해야 할 핵심 질문은 다음과 같다.

> 한국투자증권 현재가 응답에서 `observedAt`을 어떤 공식 필드로 구성할 것인가?

공식 응답 필드가 확인되면 첫 코드 작업은 `Step 1. Response Mapping`으로 제한한다. 실제 HTTP 호출이나 인증 구현보다 먼저 `KisCurrentPriceOutput`을 `CurrentPriceSnapshot`으로 변환하는 계약을 테스트한다.
