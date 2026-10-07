# 저장된 KIS 관측의 시장경보 종합 분석

## 목적과 범위

저장된 주식기본조회 응답의 관측 ID, 보존된 종목 마스터 배치와 준비된 시장경보 관측을 받아 종합 제한 점검을 재현한다. **기존 분석 서비스를 한 번 호출하고, 그 V2 결과를 시장경보 포함 점검에 연결한다.** 제외·검토 사유를 원문과 함께 추적하는 분석 경로이며 투자 적격이나 주문 권한을 승인하지 않는다.

[기존 분석 서비스](kis-stock-basic-info-stored-analysis.md)의 생성자·호출·반환 계약과 [수동 실행기](kis-stock-basic-info-manual-analysis.md)는 변경하지 않는다. 새 서비스는 별도 일반 Java 클래스이며 Spring 빈·Controller·Scheduler·설정·테이블을 추가하지 않는다. 생성만으로 분석하거나 DB에 접근하지 않는다.

## 패키지와 구성

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/observation/analysis/restriction/`이다.

| 파일 | 책임 |
| --- | --- |
| `KisStockRestrictionAnalysisService.java` | 입력 검증, 기존 분석 호출과 반환 입력 확인, 종합 점검 연결을 담당한다. |
| `result/KisStockRestrictionAnalysisResult.java` | 기존 분석 전체와 종합 점검 전체를 보존하고 두 결과의 기본 점검이 같은지 검사한다. |

서비스의 생성자는 `KisStockBasicInfoAnalysisService`와 `KisStockRestrictionScreeningPolicy`를 받는다. 기존 분석 서비스의 Store·Parser·MatchingPolicy·유형 및 제한 정책을 복제하지 않는다. Provider·Client·토큰 발급기·마스터 다운로드 서비스와 파일 저장소에 직접 의존하지 않는다.

## 호출과 실행 순서

`analyze(Long observationId, StockMasterBatchParseResult masterBatch, KisStockMarketWarningObservationResult marketWarningObservation)`은 `KisStockRestrictionAnalysisResult`를 반환한다.

1. 관측 ID가 nonnull·양수이며 마스터와 시장경보 관측이 nonnull인지 확인한다.
2. 기존 분석 서비스의 `analyze(observationId, masterBatch)`를 한 번 호출한다. 정상 기존 서비스에서는 Store의 단건 조회가 한 번 발생하고, 저장된 요청 종목으로 V2까지 분석한다.
3. 반환된 관측 ID와 분석 안의 전체 마스터 배치가 호출 입력과 같은지 확인한다.
4. 반환된 V2 결과와 전달받은 시장경보 관측으로 [종합 점검 정책](kis-stock-restriction-screening.md)을 실행한다.
5. 종합 점검이 전달된 시장경보 전체를 보존하는지 확인하고, 기존 분석과 종합 결과를 함께 반환한다.

호출자가 같은 보존 마스터에서 시장별 경보를 추출·관측한 뒤 전달한다. 여러 종목의 분석에 준비된 관측을 재사용할 수 있으며, 서비스 안에서 시장경보를 다시 추출하거나 다른 시장을 자동 선택하지 않는다. 서비스는 이전 관측 ID·결과를 필드에 저장하지 않는다.

별도 요청 종목 파라미터는 없다. `response.requestedSymbol()`을 유지하며 API의 상품 번호 `pdno`를 요청 코드로 대체하지 않는다. 새 다운로드·외부 API·토큰 발급·재수집·자동 재시도는 없다. 서비스 전체에 새 트랜잭션을 붙이지 않고 기존 Store의 읽기 전용 조회 트랜잭션을 사용한다.

## 결과와 입력 연결

결과는 `basicInfoAnalysis`와 `restrictionScreeningResult` 두 필드를 가진다. 별도 요약 판정·사유 enum·분석 버전을 추가하지 않고 기존 계약의 판정·사유·버전을 보존한다.

`basicInfoAnalysis`에는 관측 ID, 복원 응답 전체와 기존 V2 점검 전체가 있다. `restrictionScreeningResult`에는 그 V2 결과와 시장경보 관측 전체, 종합 상태·사유·점검 버전이 있다. 기존 세부 사유를 새 사유로 덮어쓰거나 원문 바이트·해시·행·수집 메타데이터를 생략하지 않는다.

결과 생성자는 두 입력의 nonnull과 `basicInfoAnalysis.screeningResult().equals(restrictionScreeningResult.basicInfoScreening())`을 확인한다. 같은 종목·상태·관측 ID라는 이유만으로 다른 기본 점검을 결합하지 않는다. 공백만 다른 응답 바이트의 해시, 다른 마스터와 점검 버전도 전체 비교에 포함된다. 직접 생성과 JSON 복원 모두 같은 검사를 수행하며, 독립 객체로 복원된 동등한 결과는 허용한다.

서비스는 호출 ID·전체 마스터와 반환 분석의 연결, 전달 경보와 정책 반환 경보의 연결도 검사한다. 지원 계약과 종합 사유·상태 정합성은 기존 종합 점검 생성자가 검사한다. 구조적으로 유효하지만 서로 연결되지 않는 경보 입력은 그 정책에 따라 진단이 남는 `REVIEW_REQUIRED`이며, 서비스가 자동 보정하지 않는다.

이는 내부 정합성 검사다. 결과를 직접 생성하거나 JSON으로 복원하는 것만으로 관측 ID가 실제 DB 행에 속하는지, 원천의 진위·현재 신선도·효력·과거 정보 가용 시점이 맞는지 증명하지 않는다. 실제 서비스 호출에서는 지정된 ID를 기존 Store로 읽는다.

원래 API 요청·수신 시각과 마스터 수집 시각은 각각 보존하며 하나의 동시 관측 시각으로 합치지 않는다. Store가 반환하지 않는 DB `recorded_at`, 새 분석 시각이나 `informationAvailableAt`을 만들어 채우지 않는다.

`toString()`에는 관측 ID·요청 코드·API 입력 해시·종합 상태·사유만 출력한다. 원문 응답·API 메시지·전체 마스터·시장경보 행은 출력하지 않는다. 전체 JSON에는 이 자료들이 포함되므로 일반 로그나 공개 API 응답에 그대로 노출하지 않는다.

## 실패 처리

| 조건 | 처리 |
| --- | --- |
| null ID·마스터·시장경보 | 의존 서비스 호출 전 `NullPointerException` |
| 0 이하 ID | 의존 서비스 호출 전 `IllegalArgumentException` |
| 관측 없음·DB 접근 실패·저장 원문 또는 메타데이터 손상·파싱 실패 | 기존 실패를 그대로 전달하고 종합 점검 전 중단 |
| 기존 분석이 null 반환 | `NullPointerException`으로 중단 |
| 반환 분석의 관측 ID·전체 마스터가 호출 입력과 다름 | `IllegalStateException`, 종합 정책 호출 전 중단 |
| 기존 V1 등 종합 정책이 지원하지 않는 계약 | 정책의 `IllegalArgumentException`, 버전 보정 없음 |
| 종합 정책 실패 또는 null 반환 | 기존 실패 전달 또는 `NullPointerException`, 재분석 없음 |
| 종합 정책 반환의 경보가 전달 경보와 다름 | `IllegalStateException` |
| 종합 결과의 기본 점검이 기존 분석의 기본 점검과 다름 | 결과 생성자의 `IllegalArgumentException` |
| 유효한 입력의 다른 시장·마스터·요청 행 연결 실패 | 전체 입력과 진단을 보존한 `REVIEW_REQUIRED` |

같은 종목의 나중 정상 관측으로 실패한 ID를 교체하거나 저장 원문을 보정하지 않는다. 분석 결과를 DB에 저장하는 기능도 없다.

## 검증 결과

2026-10-07 관련 12개 클래스·234개 테스트가 실패·오류·건너뜀 없이 통과했다. 새 테스트는 3개 클래스·64개이며, 동일한 `src/test/java/.../analysis/restriction/`와 `result/`에 있다. `support/KisStockRestrictionAnalysisFixture`는 기존 API·마스터 파서와 분석·경보 Fixture를 재사용한다.

| 새 테스트 클래스 | 테스트 수 | 주요 검증 |
| --- | --- | --- |
| `KisStockRestrictionAnalysisServiceTest` | 37 | 입력 검증, 기존 분석 및 단건 조회 한 번, 순서, 요청 코드 유지, 사유 보존, 연결 실패, 단계별 실패 전파, 잘못된 반환 입력과 이전 요청 상태 혼입 방지 |
| `result.KisStockRestrictionAnalysisResultTest` | 14 | 전체 입력·메타데이터 보존, 다른 기본 점검 거절, 독립 동등 객체 허용, 두 시장·세 상태 및 연결 실패의 JSON 왕복, 불변성과 요약 출력 |
| `KisStockRestrictionAnalysisServiceIntegrationTest` | 13 | H2 저장·복원 후 직접 정책 결과 일치, 반복 분석 후 원문·해시·길이·요청 및 수신 시각·기록 시각·행 수 보존, 관측 누락·업무 실패·손상에 대한 무보정 중단 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.screening.*' --offline --no-daemon
```

기존 분석 서비스·결과·수동 실행기·설정과 종합 점검 정책의 테스트를 함께 실행했다. 현재 실행의 XML suite 이름으로 긴 클래스명의 축약 파일까지 집계했다. 전체 프로젝트 테스트는 실행하지 않았다.

H2에는 합성 관측만 저장했으며 실제 MySQL이나 운영 관측 ID를 읽지 않았다. 테스트 Context에 Provider·Client·토큰 빈이 없고, 새 서비스도 자동 등록되지 않음을 확인했다. 외부 API·계좌·주문·OpenAI를 호출하지 않았다.

## 운영과 성과 검증 경계

기존 분석 서비스·수동 실행기·후보 선정·유동성 평가·Universe·백테스트·Risk Guard·주문·스케줄·AI Prompt의 동작은 유지한다. Spring 빈·설정·라이브러리·DB 스키마·`.env`·Docker와 서버 상태는 변경하지 않았다. 새 분석 경로는 호출자가 명시적으로 구성하여 호출해야 한다.

현재 관측을 과거 자격으로 소급하지 않으며 `AS_OF_VERIFIED`로 승격하지 않는다. `NO_EXCLUSION_SIGNAL_OBSERVED`는 투자 적격·실시간 거래 가능 여부·주문 허가·양의 기대수익을 뜻하지 않는다. 후보 제외 정책의 운영 적용이나 성과 개선은 별도 검증 대상이다.
