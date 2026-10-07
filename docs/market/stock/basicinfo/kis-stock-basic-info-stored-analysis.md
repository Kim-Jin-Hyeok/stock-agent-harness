# 저장된 KIS 주식기본정보 분석

## 목적과 범위

[원문 관측 저장소](kis-stock-basic-info-observation-storage.md)에 보관된 응답을 ID로 복원한 뒤 기존 파서, 종목 식별 대조, 유형 판단과 거래 제한 점검을 실행한다. **외부 API를 다시 호출하지 않고 같은 관측과 종목 마스터에 대한 분석을 재현한다.**

이 서비스는 원천을 수집하는 서비스와 분리한다. 관측 ID와 이미 파싱된 종목 마스터를 입력받고 분석 결과만 반환한다. 새 관측이나 분석 결과를 DB에 저장하지 않으며 후보 선정, 투자 적격 승인과 주문 경로에 연결하지 않는다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/observation/analysis/`이다.

| 파일 | 책임 |
| --- | --- |
| `KisStockBasicInfoAnalysisService.java` | ID 조회와 기존 분석 정책의 호출 순서를 연결한다. |
| `result/KisStockBasicInfoAnalysisResult.java` | 관측 ID, 복원 응답과 전체 정책 결과를 보존하고 입력 연결을 검사한다. |

서비스는 Spring 빈이 아닌 일반 Java 클래스다. 생성자에는 Store, Parser, MatchingPolicy, TypeResolutionPolicy, RestrictionObservationPolicy와 RestrictionScreeningPolicy를 전달한다. 생성자 실행만으로 DB를 조회하거나 분석하지 않는다. 기존 Store의 읽기 전용 조회 트랜잭션을 사용하며 서비스 전체를 새 트랜잭션으로 감싸지 않는다.

Controller, 자동 실행기, Scheduler, 설정, 새 라이브러리와 테이블은 추가하지 않는다. Provider, Client, 토큰 발급기와 종목 마스터 다운로드 서비스는 의존하지 않는다.

## 분석 계약

`analyze(Long observationId, StockMasterBatchParseResult masterBatch)`는 `KisStockBasicInfoAnalysisResult`를 반환한다.

1. 관측 ID의 nonnull과 양수, 종목 마스터의 nonnull을 확인한다.
2. `Store.findById(observationId)`를 한 번 호출하여 원문 응답을 복원한다.
3. 기존 Parser가 복원한 바이트 전체를 해석한다.
4. 저장된 `response.requestedSymbol()`을 기준으로 마스터의 표준코드와 시장을 대조한다.
5. 기존 유형 판단, 제한 관측과 제한 사전 점검 정책을 차례로 실행한다.
6. 관측 ID, 복원 응답과 전체 점검 결과를 함께 반환한다.

호출자가 별도의 종목 코드를 지정하거나 API의 12자리 `pdno`를 6자리 코드로 잘라 대조하지 않는다. 예를 들어 저장된 요청이 `0004Y0`이면 그 요청으로 마스터를 찾고, API의 `00000A0004Y0`는 원문 필드로 보존한다.

마스터는 호출자가 제공한 `StockMasterBatchParseResult`를 사용한다. 자동 최신 조회나 다른 마스터로의 교체는 없다. 마스터와 API 응답의 수집 시각이 다르더라도 각 원천의 메타데이터를 별도로 보존하며 동시에 관측한 상태로 합치지 않는다.

## 결과와 근거

결과의 필드는 `observationId`, `response`, `screeningResult` 세 개다. 새로운 요약 판정이나 사유 enum을 만들지 않고 기존 정책 결과를 그대로 담는다.

- `response`에는 저장된 요청 종목, 요청 시작·수신 시각, HTTP 상태와 방어적으로 복사되는 원문 바이트가 있다.
- `screeningResult.observation.typeResolution.matchingResult`에는 대조 사유, 요청 종목, API 파싱 결과와 전체 마스터 배치가 있다.
- 같은 경로의 `apiInput.inputSha256`은 바이트 전체의 해시이며, 파서·대조·유형·관측·점검 버전과 원천 근거는 기존 결과 안에 보존된다.

결과 생성자는 관측 ID의 nonnull·양수와 응답·점검 결과의 nonnull을 검사한다. 저장된 요청 종목과 대조 입력의 요청 종목이 같아야 하며, 응답 바이트의 SHA-256과 파서 입력 해시도 같아야 한다. 파싱된 필드가 같아도 공백 등 바이트가 다르면 다른 입력으로 보고 거절한다.

이 검사는 결과 내부의 연결 정합성 검사다. 임의로 생성하거나 JSON에서 복원한 결과의 ID가 실제 DB 행에 속하는지, 실제 정책을 실행했는지, 원천의 진위와 신선도를 증명하지는 않는다. 정상 서비스 호출에서는 Store 조회와 기존 정책 실행으로 결과를 만든다.

DB의 `recorded_at`은 기존 Store 반환 DTO에 포함되지 않으므로 분석 결과에 새 시각을 만들어 대신 넣지 않는다. 복원 응답의 요청·수신 시각은 기존 저장 계약에 따라 마이크로초 정밀도이며, 분석 시각이나 `informationAvailableAt`을 새로 생성하지 않는다.

`toString()`은 ID·요청 종목·입력 해시·점검 상태·사유 목록만 출력한다. 응답 원문, API 메시지와 전체 마스터는 제외한다. 전체 JSON 직렬화에는 원문 바이트와 마스터가 포함되므로 이를 로그나 공개 응답에 그대로 노출하지 않는다.

## 실패 처리

| 조건 | 처리 |
| --- | --- |
| null ID 또는 마스터 | DB 조회 전 `NullPointerException` |
| 0 이하 ID | DB 조회 전 `IllegalArgumentException` |
| 해당 관측 없음 | `NoSuchElementException`, 고정 설명과 관측 ID |
| DB 접근 실패 또는 저장된 원문·메타데이터 손상 | Store의 실패를 그대로 전달하고 중단 |
| 업무 실패 응답, 잘못된 JSON 또는 필수 필드 누락 | 기존 Parser의 `IllegalArgumentException`, 이후 정책 실행 중단 |
| 정책 실행 실패 | 실패를 전달하고 이후 단계를 실행하지 않음 |
| 식별 대조 실패 또는 정책의 미확인 판정 | 예외로 숨기지 않고 기존 정책 결과와 사유를 반환 |

재조회, API 재수집, 자동 재시도, 최신 관측으로 대체, 원문 보정과 재저장은 없다. 동일 종목에 나중의 정상 관측이 있어도 요청한 ID의 실패를 그 관측으로 대체하지 않는다.

## 투자 판단 경계

[기존 제한 사전 점검](kis-stock-basic-info-restriction-screening.md)의 상태와 사유를 유지한다. 식별 대조 실패는 `REVIEW_REQUIRED`, 확인된 Y 관측은 제외 신호, 미확인·미제공 값은 검토 필요로 남긴다. Y와 미확인이 함께 있으면 두 사유를 모두 보존한다. 코스피 투자주의환기 필드 미제공을 N으로 바꾸지 않는다.

`NO_EXCLUSION_SIGNAL_OBSERVED`는 제한 일곱 필드에서 신호가 없다는 뜻이다. 유형 충돌·미확정, 상장 상태, 날짜, NXT 거래 권한과 신선도 문제를 해소하지 않는다. 제한 신호 없음과 참고 유형 확정은 각각의 분석 결과이며 최종 투자 적격이나 주문 허가가 아니다.

현재 관측을 과거 백테스트 자격으로 소급하지 않으며 `AS_OF_VERIFIED`나 거래소 정보의 효력 시각을 생성하지 않는다. 후보·Universe·백테스트·Risk·주문·AI Prompt의 기존 동작은 유지한다.

## 관련 테스트

테스트는 동일한 `src/test/java/.../observation/analysis/`와 `result/` 경로에 둔다. `support/KisStockBasicInfoAnalysisFixture`는 기존 API 파싱·마스터 파싱·유형 판단 Fixture를 재사용한다. 합성 자료만 사용하며 실제 관측 ID 1과 운영 DB를 읽지 않는다.

- 서비스 단위 테스트는 입력 검증, ID 단건 조회, 기존 정책의 순서와 저장된 요청 사용, 단계별 실패 전파, 반복 분석과 미확인·제외 사유 보존을 확인한다.
- 결과 테스트는 요청 코드·해시 연결 검사, 바이트와 사유 목록의 불변성, 전체 JSON 왕복, 바이트 교체 거절과 안전한 요약 출력을 확인한다.
- H2 JPA 통합 테스트는 저장 뒤 영속성 컨텍스트를 비우고 분석 결과가 정책 직접 호출과 같은지 검사한다. 반복 분석 후 행 수·바이트·해시·기록 시각을 유지하고 손상된 관측을 보정하지 않는지 확인한다. Provider·Client·토큰 빈 없이 실행한다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --offline --no-daemon
```

2026-10-07 신규 3개 클래스의 **57개 테스트**와 직접 의존하는 기존 Store·Parser·대조·유형·제한 정책의 517개 회귀 테스트가 통과했다. 합계 10개 클래스·574개이며 실패·오류·건너뜀은 0개다. 최신 HTML 보고서의 클래스 목록과 XML의 suite 이름으로 집계했다. 전체 프로젝트 테스트는 실행하지 않았다.

| 신규 테스트 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoAnalysisServiceTest` | 34 |
| `KisStockBasicInfoAnalysisResultTest` | 14 |
| `KisStockBasicInfoAnalysisServiceIntegrationTest` | 9 |

회귀 검증 명령은 다음과 같다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --tests 'com.stock.market.stock.basicinfo.observation.storage.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParserTest' --tests 'com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicyTest' --tests 'com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.KisStockBasicInfoTypeResolutionPolicyTest' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicyTest' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicyTest' --offline --no-daemon
```

H2 검증은 실제 MySQL 재실행과 신선도 검증을 대신하지 않는다. Gradle의 `--offline`은 의존성 다운로드를 막는 옵션이며 애플리케이션의 모든 네트워크 연결을 차단하는 옵션은 아니다. 이 테스트 경로 자체는 외부 API와 운영 DB를 사용하지 않는다.
