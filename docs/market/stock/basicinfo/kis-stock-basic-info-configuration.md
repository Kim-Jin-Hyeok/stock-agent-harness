# KIS 주식기본조회 전용 인증 설정과 빈 구성

## 목적과 범위

[주식기본조회 원문 Client](kis-stock-basic-info-client.md)를 기존 모의투자 연결과 분리된 Spring 빈으로 등록한다. **조회 때문에 기존 `broker.kis` 주소나 키를 실전 값으로 바꾸지 않으며, 전용 설정만 활성화해도 계좌·주문·취소 기능은 등록되지 않는다.**

등록된 Client는 종목 하나와 토큰을 전달받아 조회하는 기존 계약을 유지한다. 토큰과 원문 Client를 묶는 Provider, 자동 수집, DB 저장, 캐시, 스케줄이나 후보 선정 연결은 추가하지 않는다. 빈 생성 자체는 인증이나 종목 조회를 실행하지 않는다.

## 패키지와 파일

신규 패키지는 `com.stock.market.stock.basicinfo.provider.kis.config`다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/market/stock/basicinfo/provider/kis/config/KisStockBasicInfoProperties.java` | 활성화 여부, 전용 인증정보와 시간 설정을 바인딩하고 검증한다. |
| `src/main/java/com/stock/market/stock/basicinfo/provider/kis/config/KisStockBasicInfoConfiguration.java` | 전용 HTTP 연결, 인증 객체와 기존 원문 Client를 등록한다. |
| `src/main/java/com/stock/broker/kis/config/KisConfiguration.java` | 기존 인증 의존성을 `@Qualifier`로 지정해 전용 인증 빈과 혼동하지 않게 한다. |
| `src/main/resources/application.yml` | 기본 비활성화 상태와 전용 환경변수 이름을 선언한다. |

테스트는 동일한 `src/test/java/.../provider/kis/config/`의 `KisStockBasicInfoPropertiesTest`, `KisStockBasicInfoConfigurationTest`에 둔다. 기존 `KisConfigurationTest`에는 두 구성의 동시 활성화 검증을 추가한다. 양쪽 구성 테스트에서 HTTP를 가로채는 공통 도구는 `config/support/KisStockBasicInfoMockHttp.java`에 둔다.

## 설정 계약

설정 prefix는 `market.stock.basic-info.kis`다. 기본값은 다음과 같다.

| 설정 | 기본값과 의미 |
| --- | --- |
| `enabled` | `false`. 키가 준비돼 있어도 자동으로 활성화하지 않는다. |
| `app-key` | `${KIS_READONLY_APP_KEY:}`. 기존 모의투자 키로 대체하지 않는다. |
| `app-secret` | `${KIS_READONLY_APP_SECRET:}`. 기존 모의투자 secret으로 대체하지 않는다. |
| `token-refresh-before-expiration` | `1m`. 기존 토큰 Provider의 갱신 여유시간이다. |
| `connect-timeout` | `5s`. 전용 JDK HTTP 클라이언트의 연결 제한이다. |
| `request-timeout` | `15s`. Spring 요청 팩토리의 응답 제한이다. |

실전 주소는 `https://openapi.koreainvestment.com:9443`로 고정한다. 전용 Base URL 설정이나 계좌번호·상품코드를 받지 않는다. 기존 `broker.kis`의 활성화 여부와 무관하게 전용 설정을 판단한다.

활성화하면 키와 secret은 공백·제어문자 없는 visible ASCII 문자열이어야 한다. trim이나 대체값 적용은 하지 않는다. 갱신 여유시간은 nonnull이고 음수가 아니어야 하며 0은 허용한다. 두 timeout은 최소 1ms이고 연결 제한은 응답 제한을 넘을 수 없다. 1ms 미만은 밀리초 타이머에서 즉시 만료될 수 있으므로 거절한다. 검증 오류에는 입력한 인증값을 넣지 않는다.

세 Duration은 `@DefaultValue`도 지정하므로 YAML에 해당 항목이 없어도 Spring 바인딩에서 `1m`, `5s`, `15s`를 사용한다. 비활성화 상태는 인증정보 없이 바인딩할 수 있으며, 실제 애플리케이션의 `@ConfigurationPropertiesScan`과 전용 구성의 설정 등록을 함께 사용해도 같은 설정 빈을 중복 생성하지 않는다.

설정 객체의 `toString()`은 키와 secret을 `<redacted>`로 표시한다. 접근자에는 실제 값이 남으므로 접근자나 직렬화한 설정 전체를 로그로 남기지 않는다. 인증 오류와 요청·응답 DTO 출력은 기존 [인증 민감정보 보호 계약](../../../broker/kis/auth/kis-token-secret-protection.md)을 유지한다.

## 빈과 인증 분리

전용 설정을 활성화하면 다음 singleton 빈을 등록한다. `@Primary`로 어느 환경을 기본값으로 선택하지 않는다.

| 빈 이름 | 타입과 역할 |
| --- | --- |
| `kisStockBasicInfoHttpClient` | `HttpClient`. 연결 제한을 설정하고 `Redirect.NEVER`로 리다이렉트를 금지한다. 컨텍스트 종료 시 `shutdownNow()`로 종료한다. |
| `kisStockBasicInfoRestClient` | `RestClient`. 고정 실전 주소와 `JdkClientHttpRequestFactory`를 사용한다. |
| `kisStockBasicInfoTokenClient` | 기존 `KisTokenClient`. 전용 RestClient와 인증정보로 토큰을 요청한다. |
| `kisStockBasicInfoTokenProvider` | 기존 `KisTokenProvider`. 전용 Client와 별도의 인메모리 토큰 캐시를 사용한다. |
| `kisStockBasicInfoClient` | 기존 원문 Client. 전용 RestClient와 동일한 인증정보를 사용한다. |

기존 `kisRestClient`, `kisTokenClient`, `kisTokenProvider`는 그대로 유지한다. 기존 KIS의 현재가, 일봉, 지수, 잔고, 주문, 체결 조회와 취소 구성은 해당 빈 이름을 명시해 사용한다. 특히 기존 지수 Provider의 `tokenProvider` 매개변수도 `@Qualifier("kisTokenProvider")`로 지정한다.

전용 구성 내부도 이름으로 주입한다. 두 인증 빈은 구현 클래스를 공유하지만 인스턴스와 캐시를 공유하지 않는다. 사용자가 두 설정에 같은 키를 넣었는지까지 검사하는 기능은 아니며, 토큰 캐시는 재시작이나 다른 Provider 인스턴스 사이에 복원·공유되지 않는다.

`READONLY` 환경변수 이름은 애플리케이션에서 조회용으로 분리했다는 뜻이다. 증권사가 해당 키에 주문 불가 권한을 부여했다는 뜻은 아니다. 이 구성에 계좌·주문·취소 Client를 연결하지 않는 것이 현재 보호 범위다.

## 호출과 시간 제한

호출자가 전용 토큰 Provider에서 토큰을 얻고 기존 `getStockBasicInfo(symbol, accessToken)`에 전달한다. Client가 토큰 Provider를 직접 의존하도록 변경하지 않았다. 같은 Provider를 재사용하면 기존 만료·갱신 경계에 따라 토큰을 재사용한다.

토큰 발급과 주식기본조회는 같은 전용 요청 팩토리의 응답 제한을 사용한다. 프로젝트의 Spring 6.2.19 구현은 응답 대기 작업을 취소하고 수신한 응답 본문 스트림을 닫는 방식으로 이 제한을 적용한다. JDK `HttpRequest.timeout()` 필드가 설정돼 있다는 계약이 아니다. 연결 제한은 별도로 JDK `HttpClient`에 설정한다.

Client나 토큰 Provider에 새로운 자동 재시도, API Budget, Rate Limit을 추가하지 않는다. 이 빈을 등록한 것만으로 전종목 조회나 무제한 호출을 허용하지 않는다. 네트워크·프록시·전송 구현의 실제 동작과 성능은 Mock 검증 범위를 넘는다.

## 검증 결과

2026-10-06 관련 테스트 14개 클래스의 508개 테스트가 모두 통과했다. 실패, 오류 및 건너뛴 테스트는 각각 0개다. 전체 테스트 모음은 실행하지 않았다.

| 검증 묶음 | 테스트 수 |
| --- | --- |
| 신규 Properties 테스트 | 23 |
| 신규 Configuration 테스트 | 18 |
| 기존 KIS 구성·설정 테스트 | 21 |
| 기존 인증 Client·Provider·DTO 테스트 | 43 |
| 기존 주식기본조회 Client·원문 DTO·파서 테스트 | 400 |
| 기존 OpenAI 구성 테스트 | 3 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.provider.kis.*' --tests 'com.stock.broker.kis.config.*' --tests 'com.stock.broker.kis.auth.*' --tests 'com.stock.agent.provider.ai.openai.config.OpenAiProviderConfigurationTest' --offline --no-daemon
```

기본 비활성화와 키 없는 설정 스캔, 활성화 시 오류 검증과 스택트레이스 비노출, singleton 등록, 주문 빈 미등록 및 두 인증 환경의 동시 활성화를 확인했다. 모든 기존 KIS Client와 Provider의 인증 의존성도 기존 빈을 참조하는지 확인했다.

Mock HTTP에서 모의 주소와 실전 조회 주소에 각각 다른 키로 토큰을 요청하고 반복 호출에서 각 토큰을 재사용했다. 주식기본조회에는 전용 키·secret·토큰이 전달됐으며 원문을 유지했다. 응답 제한 테스트는 실제 JDK 요청 팩토리와 Mock JDK HTTP 클라이언트의 미완료 응답을 사용해 토큰 발급과 주식기본조회 요청이 제한 시간에 취소되는지 확인했다. 실제 네트워크 연결은 하지 않았다.

실제 KIS 요청·토큰 발급, 계좌·주문·취소·OpenAI 요청은 0회다. `.env`, `.env.example`, Docker, DB, 실행 서버와 기존 보존 증적을 변경하지 않았고 커밋·Push도 하지 않았다. `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지하며 과거 자격 검증이나 거래 허가를 생성하지 않는다.
