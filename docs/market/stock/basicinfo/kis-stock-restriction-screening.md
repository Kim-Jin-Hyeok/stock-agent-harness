# KIS 시장경보 포함 종목 제한 사전 점검

## 목적과 범위

[기존 제한 점검 V2](kis-stock-basic-info-restriction-screening.md)와 [시장경보 관측](../master/kis-stock-market-warning-observation.md)을 연결하여 한 요청 종목의 종합 점검 결과를 만든다. **같은 시장·같은 마스터 원문인지 먼저 검사하고, 경보·예고·미확인 사유를 독립적으로 보존한다.**

경보 코드 `01`·`02`·`03` 또는 위험 예고 Y를 제외 신호로 보는 것은 초기 모의투자 후보용의 보수적인 내부 정책이다. 공식적인 거래 금지나 실시간 지정 효력 판정이 아니다. 투자주의까지 제외하면 후보와 투자 기회가 줄어들 수 있으므로 이 규칙이 순성과를 개선한다고 가정하지 않는다.

이번 결과는 후보 선정·주문 차단에 연결하지 않는다. 기존 V1·V2의 판정·사유·JSON과 원문 파서·관측 계약은 유지한다. `StockEligibilityPolicy`를 대신하거나 ETF·유형 미확정 종목의 투자를 허용하지 않는다.

## 패키지와 호출 계약

기준 경로는 `src/main/java/com/stock/strategy/universe/eligibility/restriction/kis/screening/`이다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockRestrictionScreeningPolicy` | 기존 V2 점검과 한 시장의 시장경보 관측을 받아 종합 결과를 반환한다. |
| `result.KisStockRestrictionScreeningResult` | 두 입력 전체·종합 상태·새 사유 목록·점검 버전을 보존하고 일치 여부를 검사한다. |
| `result.KisStockRestrictionScreeningStatus` | 연결 실패 우선, 제외 신호 다음, 나머지 검토 필요의 순서로 세 상태를 계산한다. |
| `result.KisStockRestrictionScreeningReasonCode` | 식별·원문 연결 진단, 기존 상태 요약과 시장경보·예고 사유를 생성한다. |

호출은 `evaluate(KisStockBasicInfoRestrictionScreeningResult, KisStockMarketWarningObservationResult)`이고 반환 타입은 `KisStockRestrictionScreeningResult`다. Spring 빈이 아닌 무상태 일반 Java 클래스이며 파일·HTTP·DB 접근과 새 실행 계층은 없다.

새 점검 버전은 `KIS_STOCK_RESTRICTION_SCREENING_V1`이다. 이것은 기존 `KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V1`과 다른 계약이다. 입력은 기존 점검 `KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V2`와 시장경보 관측 `KIS_STOCK_MARKET_WARNING_OBSERVATION_V1`만 받는다. 기존 V1은 보존·복원할 수 있지만 이번 입력으로 받거나 V2로 이름만 바꾸지 않는다.

시장경보 입력은 근거 revision `277ec0eb7a9b7f63b6807829286c80f36649dad2`, 추출 `KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1`, 원문 파서 `KIS_STOCK_MASTER_RAW_V2`, 레이아웃 `OBSERVED_2026_10_05_LF_V1`을 확인한다. 지원하지 않는 계약과 null 의존성은 정상·검토 결과로 보완하지 않고 예외로 거절한다.

## 같은 원문과 요청 종목 연결

요청 종목은 기존 `basicInfoScreening.observation.typeResolution.matchingResult.requestedSymbol`을 사용한다. API의 상품 번호나 별도 호출자 입력으로 바꾸지 않는다. 다음을 순서대로 확인한다.

1. 기존 대조 결과에서 요청한 마스터 행과 시장이 존재해야 한다.
2. 시장경보 관측의 원문 시장이 해당 시장과 같아야 한다.
3. 기존 배치의 해당 시장 `KisStockMasterParseResult` 전체가 시장경보의 원문 결과와 같아야 한다.
4. 기존 행 번호 위치의 시장경보 행이 같은 행 번호·요청 단축 코드·표준 코드를 가져야 한다.

세 번째 검사는 입력 SHA-256·파서 및 레이아웃 버전과 해당 시장의 모든 행·순서·식별자·이름·기존 필드·`rawLine`을 포함한다. 같은 단축 코드나 주장된 해시만 같다는 이유로 합치지 않는다. 기존 객체를 직접 생성하거나 JSON으로 복원하여 관련 없는 다른 행의 값을 바꾼 경우도 검사한다.

시장·원문·요청 행 연결이 실패하면 경보 입력 전체는 결과에 보존하지만, 그 입력의 경보·예고 신호를 요청 종목의 사유로 붙이지 않는다. 구조가 유효한 두 입력의 연결 실패는 진단을 가진 `REVIEW_REQUIRED`로 반환한다.

이 검사는 같은 바이트와 해석 결과의 연결이다. 두 원천의 수집 시각이 같거나 최신 상태라는 인증은 아니다. 시장경보 계약에 없는 수집 시각을 만들어 채우지 않으며, 기존 배치와 API의 보존 메타데이터를 동시에 관측한 시점이나 제한 효력 시점으로 합치지 않는다.

## 판정 우선순위

| 순서 | 조건 | 종합 결과 |
| --- | --- | --- |
| 1 | 기존 표준 코드·시장 대조가 확인되지 않음, 또는 시장경보 입력 연결 실패 | `REVIEW_REQUIRED` |
| 2 | 연결 실패가 없고, 기존 제외 신호 또는 경보 `01`·`02`·`03` 또는 위험 예고 Y가 있음 | `EXCLUSION_SIGNAL_OBSERVED` |
| 3 | 제외 신호는 없지만 기존 검토 필요 또는 경보·예고 미확인이 있음 | `REVIEW_REQUIRED` |
| 4 | 위 문제가 없음 | `NO_EXCLUSION_SIGNAL_OBSERVED` |

시장경보 코드와 위험 예고는 각각 평가한다. 예고 N이 경보를 지우거나 경보 `00`이 예고 Y를 지우지 않는다. 연결된 제외 신호가 있으면 미확인이 함께 있어도 제외 상태가 되지만 두 사유를 모두 보존한다.

기존 API 식별 대조가 실패했더라도 요청한 마스터 행과 시장경보 원문 연결이 확인되면 해당 마스터의 경보 진단은 보존한다. 종합 판정은 첫 번째 규칙에 따라 검토 필요이며, 이 경보가 API 식별 실패를 해소하지 않는다. 요청 행이 없거나 경보 원문 연결도 실패하면 경보 신호를 요청 종목에 붙이지 않는다.

`NO_EXCLUSION_SIGNAL_OBSERVED`는 이번 점검에 제외·미해결 사유가 없다는 뜻이다. 투자 적격·상장 상태·신선도·현재 거래 가능 여부·NXT 권한·과거 정보 가용성·주문 Risk 통과를 뜻하지 않는다.

## 사유와 결과 보존

새 사유의 순서는 기존 식별 진단, 시장경보 연결 진단, 기존 상태 요약, 경보 코드, 위험 예고다. 연결 진단은 요청 행 없음·시장 다름·원문 전체 다름을 구분하며, 원문 연결이 실패하면 해당 단계에서 경보 연결을 중단한다.

기존 상태 요약은 `BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED` 또는 `BASIC_INFO_REVIEW_REQUIRED`다. 기존 일곱 필드의 구체적인 사유와 비적용 설명은 `basicInfoScreening.reasonCodes`와 원래 관측 전체에 그대로 남는다. 새 enum으로 옮기거나 기존 사유를 덮어쓰지 않는다. 기존 V2가 신호 없음이면 새 요약 사유를 추가하지 않으므로 코스피 미제공·비적용 설명만으로 불필요한 검토 상태가 생기지 않는다.

시장경보 사유는 투자주의·투자경고·투자위험·미확인을 구분하고, 예고 Y·예고 미확인도 별도로 기록한다. 연결 실패가 있으면 기존 제외 상태의 요약 사유가 남아 있어도 종합 판정은 검토 필요가 우선한다.

결과는 두 원본 입력 전체를 보존하고 사유 목록은 `List.copyOf`로 만든다. 생성자는 입력에서 사유를 다시 계산하여 누락·중복·재정렬·다른 사유 주입을 거절하고, 상태와 지원 버전도 검사한다. 직접 생성과 JSON 복원에서 같은 검사를 수행한다.

이는 내부 정합성 검사이며 원본·해시·결과를 함께 바꾼 입력의 진위를 인증하는 서명 검증이 아니다. 출처·신선도·효력과 과거 자격은 별도 책임이다.

## 테스트와 보존 사례

같은 `src/test/java/.../restriction/kis/screening/` 아래 정책·상태·결과 세 테스트 클래스와 `support/` Fixture를 추가했다. 기존 바이트·배치·API 파서 Fixture를 재사용하고 실제 파서로 일관된 입력을 만든다. 관련 없는 다른 행에 경보·예고를 넣어 잘못된 행 선택도 검증한다.

종합 점검 정책 구현 당시인 2026-10-07 관련 **19개 클래스·415개 테스트가 모두 통과**했다. 새 테스트는 52개이며 실패·오류·건너뜀은 0개다. 당시 실행 시각의 XML suite 이름으로 긴 클래스명의 축약 파일도 포함해 집계했다. 전체 프로젝트 테스트는 실행하지 않았다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.kis.screening.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.warning.*' --tests 'com.stock.market.stock.master.provider.kis.parsing.warning.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.*' --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --offline --no-daemon
```

두 시장에서 경보 10종과 예고 7종의 조합, 기존 제외·미확인 보존, 모든 API 식별 실패, 다른 시장·원문·버전·해시·행 순서와 관련 없는 행의 변조를 확인했다. 지원하지 않는 기존 V1, 목록 불변성, 판정·사유·입력·버전 변조와 연결 실패 결과를 포함한 전체 JSON 왕복도 검증했다. JSON 테스트는 배치 수집 시각 `Instant`를 보존하기 위해 기존 방식대로 `JavaTimeModule`을 등록한다.

보존된 4,403행의 시장경보 관측과 기존 V1·V2 결과 20건을 다시 대조했다. 실제 사례 10건은 모두 경보 `00`·예고 `N`이므로 새 종합 결과도 기존 V2와 같은 제외 5·검토 3·신호 없음 2건이다. 원본을 포함한 종합 결과 10건의 전체 JSON 왕복·반복 호출과 최초·재현 결과 비교도 성공했다. 증적 112개의 해시는 유지됐다. [상세 대조 기록](validation/kis-stock-restriction-screening-observation-01.md)에 보고서 경로와 해시를 남긴다.

## 운영과 성과 검증 경계

정책 구현 당시에는 `KisStockBasicInfoAnalysisService`·수동 실행기·후보 선정·유동성 평가·백테스트·Risk Guard·주문·스케줄·AI Prompt에 연결하지 않았다. 그 단계에서 DB·스키마·Spring 빈·라이브러리·설정·`.env`·Docker 변경과 추가 API·토큰·계좌·주문·OpenAI 호출은 없었다.

후속 [저장 관측 종합 분석 서비스](kis-stock-restriction-stored-analysis.md)는 기존 분석 서비스의 반환 V2 결과를 이 정책에 연결하는 별도 호출 경로다. [수동 실행기의 선택적 종합 모드](kis-stock-basic-info-manual-analysis.md#시장경보-포함-실행)는 경보를 준비하여 그 서비스를 호출한다. 기존 분석 서비스·실행기 기본 모드와 정책·원문 추출·관측 버전·보존 결과는 유지한다. 종합 모드의 명시적 빈 등록 외에 자동 실행·후보 선정·주문에는 연결하지 않는다.

`runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. 현재 보존 자료를 `AS_OF_VERIFIED`로 승격하거나 과거 백테스트의 자격 근거로 소급하지 않는다. 향후 제외 수준을 완화하려면 같은 데이터·후보 범위·기간·거래비용 조건에서 후보 감소와 비용 차감 후 성과를 비교해야 한다.
