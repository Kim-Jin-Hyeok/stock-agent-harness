# KIS 주식기본조회 전용 인증 설정과 빈 구성

## 목적과 범위

[주식기본조회 원문 Client](kis-stock-basic-info-client.md)를 기존 모의투자 연결과 분리된 Spring 빈으로 등록한다. **조회 때문에 기존 `broker.kis` 주소나 키를 실전 값으로 바꾸지 않으며, 전용 설정만 활성화해도 계좌·주문·취소 기능은 등록되지 않는다.**

등록된 Client는 종목 하나와 토큰을 전달받아 조회하는 기존 계약을 유지한다. 인증 연계 `KisStockBasicInfoProvider`는 호출자로부터 종목만 받아 전용 토큰 Provider와 원문 Client를 연결한다. 자동 수집, DB 저장, 주식기본정보 캐시, 스케줄이나 후보 선정 연결은 추가하지 않는다. 빈 생성 자체는 인증이나 종목 조회를 실행하지 않는다.

반환 응답의 저장·복원은 별도 [원문 관측 이력 Store](kis-stock-basic-info-observation-storage.md)가 담당한다. 인증 구성과 Provider는 Store를 의존하지 않으며 조회 후 저장 호출을 자동으로 실행하지 않는다.

## 패키지와 파일

설정 패키지는 `com.stock.market.stock.basicinfo.provider.kis.config`다. 인증 연계 Provider는 기존 Client와 같은 `com.stock.market.stock.basicinfo.provider.kis`에 둔다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/market/stock/basicinfo/provider/kis/config/KisStockBasicInfoProperties.java` | 활성화 여부, 전용 인증정보와 시간 설정을 바인딩하고 검증한다. |
| `src/main/java/com/stock/market/stock/basicinfo/provider/kis/config/KisStockBasicInfoConfiguration.java` | 전용 HTTP 연결, 인증 객체, 기존 원문 Client와 인증 연계 Provider를 등록한다. |
| `src/main/java/com/stock/market/stock/basicinfo/provider/kis/KisStockBasicInfoProvider.java` | 토큰 확보 전 종목을 검증하고 전용 인증으로 단일 종목의 원문을 조회한다. |
| `src/main/java/com/stock/broker/kis/config/KisConfiguration.java` | 기존 인증 의존성을 `@Qualifier`로 지정해 전용 인증 빈과 혼동하지 않게 한다. |
| `src/main/resources/application.yml` | 기본 비활성화 상태와 전용 환경변수 이름을 선언한다. |

테스트는 동일한 `src/test/java/.../provider/kis/config/`의 `KisStockBasicInfoPropertiesTest`, `KisStockBasicInfoConfigurationTest`에 둔다. 기존 `KisConfigurationTest`에는 두 구성의 동시 활성화 검증을 추가한다. 양쪽 구성 테스트에서 HTTP를 가로채는 공통 도구는 `config/support/KisStockBasicInfoMockHttp.java`에 둔다.

인증 연계 테스트는 `src/test/java/com/stock/market/stock/basicinfo/provider/kis/KisStockBasicInfoProviderTest.java`에 둔다. 별도 공통 인터페이스나 수집 서비스 계층은 추가하지 않는다.

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
| `kisStockBasicInfoProvider` | 단일 종목 조회 진입점. 기존 원문 Client와 `kisStockBasicInfoTokenProvider`를 연결한다. |

기존 `kisRestClient`, `kisTokenClient`, `kisTokenProvider`는 그대로 유지한다. 기존 KIS의 현재가, 일봉, 지수, 잔고, 주문, 체결 조회와 취소 구성은 해당 빈 이름을 명시해 사용한다. 특히 기존 지수 Provider의 `tokenProvider` 매개변수도 `@Qualifier("kisTokenProvider")`로 지정한다.

전용 구성 내부도 이름으로 주입한다. 두 인증 빈은 구현 클래스를 공유하지만 인스턴스와 캐시를 공유하지 않는다. 사용자가 두 설정에 같은 키를 넣었는지까지 검사하는 기능은 아니며, 토큰 캐시는 재시작이나 다른 Provider 인스턴스 사이에 복원·공유되지 않는다.

`READONLY` 환경변수 이름은 애플리케이션에서 조회용으로 분리했다는 뜻이다. 증권사가 해당 키에 주문 불가 권한을 부여했다는 뜻은 아니다. 이 구성에 계좌·주문·취소 Client를 연결하지 않는 것이 현재 보호 범위다.

## 호출과 시간 제한

호출자는 인증 연계 Provider의 `getStockBasicInfo(symbol)`을 사용한다. Provider는 Client의 package-private 종목 검증을 재사용하여 정확히 여섯 자리 대문자 영숫자인지 먼저 확인한다. trim이나 코드 보정은 하지 않으며 잘못된 입력은 토큰 확보와 조회를 모두 실행하기 전에 거절한다.

검증을 통과하면 `@Qualifier("kisStockBasicInfoTokenProvider")`로 지정된 기존 토큰 Provider에서 토큰을 얻고 원문 Client의 `getStockBasicInfo(symbol, accessToken)`에 전달한다. Client 자체가 토큰 Provider를 의존하도록 변경하지 않았다. 같은 토큰 Provider를 재사용하여 기존 만료·갱신 경계에 따라 토큰을 재사용하며, 새 토큰 Provider를 호출마다 생성하지 않는다.

반환 타입은 기존 `KisStockBasicInfoRawResponse`다. Client가 반환한 객체와 요청 코드·시작 시각·수신 시각·HTTP 상태·원문 바이트를 그대로 유지한다. null 응답은 고정 메시지로 거절한다. 인증 실패 시 조회를 실행하지 않고, 기존 인증 및 Client가 정제한 예외를 추가 래핑이나 자동 재시도 없이 전달한다. 조회 실패로 토큰 캐시를 초기화하지 않는다.

**반환은 원문 수신 완료이지 업무 응답 성공이나 종목 일치·거래 가능 판정이 아니다.** `rt_cd=1`이나 잘못된 JSON도 기존 Client 계약대로 원문으로 전달한다. 파싱·종목 매칭·제한 관측·자격 판정에는 연결하지 않으며, 수신 시각을 거래소 정보의 적용 시각이나 과거 자격 기준 시각으로 사용하지 않는다. 토큰 캐시 외에 주식기본정보 응답 캐시는 없으므로 반복 조회도 각각 Client를 호출한다.

토큰 발급과 주식기본조회는 같은 전용 요청 팩토리의 응답 제한을 사용한다. 프로젝트의 Spring 6.2.19 구현은 응답 대기 작업을 취소하고 수신한 응답 본문 스트림을 닫는 방식으로 이 제한을 적용한다. JDK `HttpRequest.timeout()` 필드가 설정돼 있다는 계약이 아니다. 연결 제한은 별도로 JDK `HttpClient`에 설정한다.

Client나 토큰 Provider에 새로운 자동 재시도, API Budget, Rate Limit을 추가하지 않는다. 이 빈을 등록한 것만으로 전종목 조회나 무제한 호출을 허용하지 않는다. 네트워크·프록시·전송 구현의 실제 동작과 성능은 Mock 검증 범위를 넘는다.

## 검증 결과

### 전용 빈 구성 도입 시 검증

2026-10-06 전용 설정과 빈 구성을 처음 추가할 때 관련 테스트 14개 클래스의 508개 테스트가 모두 통과했다. 아래 수와 명령은 당시 검증 기록이다. 실패, 오류 및 건너뛴 테스트는 각각 0개다. 전체 테스트 모음은 실행하지 않았다.

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

### 인증 연계 Provider 추가 검증

2026-10-06 인증 연계 Provider 추가 후 관련 5개 클래스의 124개 테스트가 모두 통과했다. 실패·오류·건너뛴 테스트는 0개이며 전체 테스트 모음은 실행하지 않았다.

| 검증 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoProviderTest` | 26 |
| `KisStockBasicInfoClientTest` | 54 |
| `KisStockBasicInfoConfigurationTest` | 20 |
| `KisConfigurationTest` | 12 |
| `KisTokenProviderTest` | 12 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProviderTest' --tests 'com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClientTest' --tests 'com.stock.market.stock.basicinfo.provider.kis.config.KisStockBasicInfoConfigurationTest' --tests 'com.stock.broker.kis.config.KisConfigurationTest' --tests 'com.stock.broker.kis.auth.KisTokenProviderTest' --offline --no-daemon
```

단위 테스트의 Mock으로 입력 오류 시 호출 0회, 호출 순서, 원문·시각 유지, 재시도 없음과 업무 실패·잘못된 JSON의 미해석을 확인했다.

실제 구현의 토큰 Provider와 Mock 토큰 Client를 조합하여 여러 종목과 같은 종목의 반복 조회에서 발급은 1회, 기본정보 조회는 매번 실행되는지 확인했다. 조회 실패 후 명시적인 재호출에서도 기존 토큰을 재사용한다.

Spring 구성에서는 비활성화 시 미등록, singleton, 시작 시 요청 0회, HTTP 429 인증 실패 시 GET 0회와 민감정보 비노출을 확인했다. Mock HTTP의 명시적인 호출에서 토큰 발급 1회로 두 종목을 조회했고, 두 인증 환경을 함께 활성화해도 전용 Provider가 기존 모의투자 토큰이 아닌 전용 토큰을 사용하는지 확인했다.

실제 KIS 요청·토큰 발급, 계좌·주문·취소·OpenAI 요청은 0회다. `.env`, `.env.example`, Docker, DB, 실행 서버와 기존 보존 증적을 변경하지 않았고 커밋·Push도 하지 않았다. `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지하며 과거 자격 검증이나 거래 허가를 생성하지 않는다.
