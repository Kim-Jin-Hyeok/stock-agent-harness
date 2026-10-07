# KIS 주식기본조회 단일 종목 수집

## 목적과 범위

`KisStockBasicInfoCollectionService`는 종목 하나의 원문 조회와 관측 저장을 연결한다. **`collect(String symbol)`의 반환값은 저장된 관측의 `Long` ID이며, API 업무 성공이나 투자 적격을 뜻하지 않는다.** 저장된 원문은 추가 외부 호출 없이 기존 Store로 복원하고 분석할 수 있다.

기존 [인증 연계 Provider](kis-stock-basic-info-configuration.md)와 [원문 관측 Store](kis-stock-basic-info-observation-storage.md)를 그대로 사용한다. 수집 서비스 자체에는 새 인터페이스·응답 DTO·DB 스키마·설정 prefix를 추가하지 않는다. 자동 수집, 스케줄, 전종목 반복 조회, 응답 캐시와 후보 선정 연결은 포함하지 않는다.

명시적인 단건 실행은 별도 [수동 실행기](kis-stock-basic-info-manual-collection.md)가 담당한다. 전용 프로세스에서 수집 서비스를 한 번 호출하고 종료하며, 기존 서비스의 조회·저장 계약은 유지한다.

## 패키지와 파일

수집 패키지는 `com.stock.market.stock.basicinfo.collection`이다. 인증·HTTP는 `provider.kis`, 저장은 `observation.storage`에 남겨 수집 흐름과 각각의 책임을 분리한다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/market/stock/basicinfo/collection/KisStockBasicInfoCollectionService.java` | 단일 조회, 요청 메타데이터 대조, 저장과 관측 ID 반환을 연결한다. |
| `src/main/java/com/stock/market/stock/basicinfo/provider/kis/config/KisStockBasicInfoConfiguration.java` | 기존 활성화 조건으로 수집 서비스 singleton 빈을 등록한다. |
| `src/test/java/com/stock/market/stock/basicinfo/collection/KisStockBasicInfoCollectionServiceTest.java` | 호출 순서, 실패 전파, 저장·재호출 여부를 검증한다. |
| `src/test/java/com/stock/market/stock/basicinfo/collection/KisStockBasicInfoCollectionServiceIntegrationTest.java` | 실제 Provider·Client·Store 구현을 Mock HTTP와 H2로 연결한다. |

기존 전용 설정과 Broker 구성 테스트에는 Store Mock을 제공한다. 두 인증 경로가 분리되고 수집 빈이 올바른 Provider·Store를 참조하는지 확인하며, 시작 시 Store 호출도 없어야 한다.

## 수집 계약

호출 흐름은 다음과 같다.

1. Provider의 `getStockBasicInfo(symbol)`을 한 번 호출한다.
2. 반환 응답이 nonnull이고 `requestedSymbol`이 입력 종목과 정확히 같은지 확인한다.
3. 같은 응답 객체를 Store의 `save(response)`에 한 번 전달한다.
4. 저장이 완료되면 생성된 관측 ID를 반환한다.

입력 검증은 기존 Provider가 토큰 확보 전에 수행한다. 정확히 여섯 자리 대문자 영숫자만 허용하며 trim, 소문자 변환과 종목 코드 보정은 하지 않는다. 수집 서비스에 같은 검증 규칙을 복제하지 않는다.

응답 대조는 요청 메타데이터만 확인한다. JSON의 상품번호 `pdno`를 읽거나 요청 코드와 비교하지 않는다. 예를 들어 요청 코드 `0004Y0`과 원문의 상품번호 `00000A0004Y0`을 같은 형식으로 보정하지 않는다. 원문 해석과 종목 대조는 기존 파서·매칭 정책의 별도 책임이다.

HTTP 200으로 받은 업무 실패 JSON, 잘못된 JSON과 비 UTF-8 바이트도 기존 원문 DTO 계약을 만족하면 저장한다. 수집 단계는 `rt_cd`를 검사하지 않으므로 파싱에 실패할 원문에도 저장 ID가 생길 수 있다. 원문·요청 시각은 Provider에서 보존하고, Store는 기존 계약대로 세 시각을 마이크로초로 내림하여 기록한다.

명시적으로 `collect`를 두 번 호출하면 조회도 두 번, 저장도 두 번 수행한다. 같은 종목·원문이어도 별도 관측 행을 추가한다. 기본정보 응답을 캐시하거나 기존 행을 덮어쓰지 않으며, 인증 토큰은 기존 singleton 토큰 Provider의 유효기간 규칙에 따라 재사용한다.

## 실패 처리

| 실패 조건 | 처리 |
| --- | --- |
| 입력 오류 | 기존 Provider가 인증·조회 전에 거절하며 저장하지 않는다. |
| 인증 실패 | 기본정보 조회와 저장을 실행하지 않고 기존 예외를 전달한다. |
| HTTP·통신·원문 크기 등 Client 계약 위반 | 저장하지 않고 기존 정제된 예외를 전달한다. |
| null 응답 또는 요청 종목 메타데이터 불일치 | 고정 메시지의 `IllegalStateException`으로 중단하며 저장하지 않는다. |
| 저장 실패 | Store의 예외를 전달하고 ID를 반환하지 않으며 재조회·재저장하지 않는다. |

응답 검증 오류에 원문·종목 입력·인증정보를 넣지 않는다. 새 로그, 예외 래핑이나 자동 재시도도 없다. DB 오류만으로 실제 저장 행이 반드시 0건이라고 단정할 수는 없다. 저장 결과가 불확실할 때 즉시 API를 재호출하여 중복 관측을 늘리는 기능은 제공하지 않는다.

## 트랜잭션과 활성화

수집 서비스에는 `@Transactional`을 붙이지 않는다. **호출자는 DB 트랜잭션 밖에서 수집을 시작하고, 외부 조회가 끝난 뒤 Store의 기존 저장 트랜잭션만 사용한다.** 정상 반환 시에는 이 저장 트랜잭션이 완료되어 ID로 다시 조회할 수 있다.

이미 열린 상위 트랜잭션을 자동으로 중단·분리하는 기능은 없다. 상위 `@Transactional` 메서드에서 호출하면 HTTP도 그 트랜잭션 안에서 실행되고 Store도 해당 트랜잭션에 참여할 수 있으므로, 향후 Runner나 스케줄 연결에서도 트랜잭션 밖 호출 계약을 유지해야 한다.

`market.stock.basic-info.kis.enabled=true`일 때 기존 전용 구성에서 `kisStockBasicInfoCollectionService` 빈을 등록한다. Store는 기존 `@Component` 빈을 사용하므로 활성화된 구성에는 Repository·Clock을 포함한 저장 기반이 필요하다. 비활성화 시 수집 빈은 없으며, 활성화된 빈 생성도 토큰 발급·종목 조회·저장을 실행하지 않는다. 원문만 필요한 기존 Provider 호출은 계속 저장 없이 사용할 수 있다.

수신 시각과 저장 ID는 과거 정보 가용 시점의 인증이 아니다. 현재 관측을 과거 백테스트의 자격 근거로 소급하거나 `AS_OF_VERIFIED`, 거래 허가를 생성하지 않는다. 계좌·주문·취소와 기존 모의투자 인증 경로도 변경하지 않는다.

## 검증 결과

2026-10-07 관련 7개 클래스의 132개 테스트가 모두 통과했다. 실패·오류·건너뛴 테스트는 0개이며 전체 테스트 모음은 실행하지 않았다.

| 검증 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoCollectionServiceTest` | 25 |
| `KisStockBasicInfoCollectionServiceIntegrationTest` | 17 |
| `KisStockBasicInfoConfigurationTest` | 20 |
| `KisConfigurationTest` | 12 |
| `KisStockBasicInfoProviderTest` | 26 |
| `KisStockBasicInfoObservationStoreTest` | 15 |
| `KisStockBasicInfoObservationStoreIntegrationTest` | 17 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.collection.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.config.KisStockBasicInfoConfigurationTest' --tests 'com.stock.broker.kis.config.KisConfigurationTest' --tests 'com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProviderTest' --tests 'com.stock.market.stock.basicinfo.observation.storage.*' --offline --no-daemon
```

단위 테스트는 조회 후 저장 순서, 응답 객체 유지, 요청 메타데이터 불일치, null 응답, 저장 오류 전파와 자동 재호출 없음을 확인한다. 입력 오류는 실제 Provider의 기존 검증을 통과시켜 인증·Client·Store 호출이 모두 0회인지 확인한다.

Mock HTTP와 H2 통합 테스트는 실제 구현을 사용하되 테스트 전체 트랜잭션을 비활성화한다. 인증·조회 시 열린 DB 트랜잭션이 없고, Store가 저장한 행을 별도 조회로 복원할 수 있는지 확인한다. 업무 실패·잘못된 JSON·비 UTF-8 원문도 그대로 저장하고 해시·시각을 확인했다. 원문의 다른 형식 상품번호는 유지되며 저장 전후 파싱 결과도 같다. 반복 수집에서는 토큰 발급 1회·조회 2회·별도 관측 2행을 확인했다.

실제 KIS API·토큰 발급, MySQL·Flyway 적용, Docker·실행 서버·환경변수 변경은 수행하지 않았다. 따라서 이 결과는 실제 네트워크·운영 DB 검증을 대신하지 않는다. 기존 보존 증적은 유지하며 커밋·Push는 수행하지 않는다.
