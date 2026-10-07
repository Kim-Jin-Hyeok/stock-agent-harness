# KIS 시장경보 관측 상태 해석

## 목적과 범위

[시장경보 원문 추출 결과](kis-stock-market-warning-raw-parsing.md)의 경보 코드와 위험 예고를 독립적인 관측 상태로 해석한다. **원문과 근거 전체를 보존하고, 공백·미정의 값을 경보 없음이나 예고 N으로 바꾸지 않는다.**

이는 후보 제외 전에 사용할 관측 계약이다. 실제 지정의 효력·최신성·투자 적격·주문 권한을 승인하지 않는다. 투자주의환기와 시장경보는 별도 필드이며, 코스피 투자주의환기의 비적용 설명이 시장경보 관측을 생략시키지 않는다.

## 패키지와 입력

기준 경로는 `src/main/java/com/stock/strategy/universe/eligibility/restriction/kis/warning/`이고 패키지는 `com.stock.strategy.universe.eligibility.restriction.kis.warning`이다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockMarketWarningObservationPolicy` | `evaluate(KisStockMasterMarketWarningParseResult)`로 한 시장의 모든 원문 행을 해석하고 `KisStockMarketWarningObservationResult`를 반환한다. |
| `result.KisStockMarketWarningStatus` | 정확한 시장경보 코드 네 개와 미확인 값을 구분한다. |
| `result.KisStockMarketWarningObservation` | 한 행의 원문 record와 두 관측 상태를 보존하고 일치 여부를 검사한다. |
| `result.KisStockMarketWarningObservationResult` | 기존 추출 결과 전체, 불변 관측 목록, 해석 버전과 근거 revision을 보존한다. |

원문 구조 검사는 기존 추출 결과의 생성자가 담당한다. 관측 정책은 검증된 결과를 읽고 행마다 상태를 추가하며 파일을 다시 읽거나 외부 API를 호출하지 않는다. Spring 빈·Provider 인터페이스·새 수집기·배치 실행 계층은 추가하지 않는다.

해석 버전은 `KIS_STOCK_MARKET_WARNING_OBSERVATION_V1`이고 근거 revision은 `277ec0eb7a9b7f63b6807829286c80f36649dad2`다. 정책은 입력의 추출 버전 `KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1`, 원문 파서 `KIS_STOCK_MASTER_RAW_V2`, 레이아웃 `OBSERVED_2026_10_05_LF_V1`과 같은 근거 revision을 명시적으로 확인한다. 미래 파서가 새 버전을 받더라도 이번 관측 규칙의 지원 범위를 자동 확대하지 않는다.

## 관측 규칙

보존된 KIS C 헤더의 `mrkt_alrm_cls_code` 주석을 기준으로 정확한 원문만 다음과 같이 해석한다.

| 정확한 원문 | 시장경보 관측 상태 |
| --- | --- |
| `00` | `NO_WARNING_OBSERVED` |
| `01` | `INVESTMENT_CAUTION_OBSERVED` |
| `02` | `INVESTMENT_WARNING_OBSERVED` |
| `03` | `INVESTMENT_RISK_OBSERVED` |
| 그 밖의 값 | `VALUE_UNVERIFIED` |

`NO_WARNING_OBSERVED`는 해당 필드에 문자 `00`을 읽었다는 뜻이다. 다른 제한·예고·유형·상장 상태까지 정상이라는 뜻이 아니다. `INVESTMENT_CAUTION_OBSERVED`도 시장경보의 투자주의 코드 관측이며 기존 `rawInvestmentCaution` 투자주의환기 관측과 합치지 않는다.

`rawMarketWarningRiskPreannouncement`는 기존 `KisStockTradingFlagStatus.fromRawValue`를 재사용한다. 정확한 `Y`는 `Y_OBSERVED`, `N`은 `N_OBSERVED`, 공백·소문자·미정의 값은 `VALUE_UNVERIFIED`다. 새 boolean이나 예고 전용 enum을 추가하지 않는다.

현재 두 시장의 원문 계약은 두 필드 모두 존재하며 null을 거절한다. 재사용한 enum에 `FIELD_NOT_PROVIDED`가 있어도 이번 예고 관측에 적용할 수 없다. 없는 값을 N으로 채우거나 코스피에서 예고 필드를 비적용으로 처리하지 않는다. 시장경보 enum의 변환 메서드도 null을 거절한다.

trim·대소문자 변경·코드 보정·상태 간 우선순위는 없다. 잘못된 바이트 폭·제어문자·인코딩·원문 정합성은 파서가 먼저 거절하며 관측 미확인으로 흡수하지 않는다.

| 조합 예 | 보존할 두 상태 |
| --- | --- |
| 경보 `02`, 예고 `N` | `INVESTMENT_WARNING_OBSERVED` + `N_OBSERVED` |
| 경보 `00`, 예고 `Y` | `NO_WARNING_OBSERVED` + `Y_OBSERVED` |
| 경보 `??`, 예고 `Y` | `VALUE_UNVERIFIED` + `Y_OBSERVED` |
| 경보 `03`, 예고 `y` | `INVESTMENT_RISK_OBSERVED` + `VALUE_UNVERIFIED` |

예고 N으로 경보를 지우거나 경보 `00`으로 예고 Y를 지우지 않는다. 현재 결과에는 정상·제외 같은 단일 종합 판정을 넣지 않는다.

## 결과 정합성과 보존

각 행의 생성자는 `rawRecord`·경보 상태·예고 상태를 필수로 요구하고, 상태가 원문 변환 규칙과 같아야 한다. 전체 결과는 원본 추출 결과를 `source`로 유지하고, 목록을 `List.copyOf`로 보존한다. 행 수와 각 위치의 원문 record가 원본과 같아야 하므로 누락·중복·재정렬·다른 식별자와 값을 거절한다.

직접 생성과 JSON 복원에도 같은 검사를 적용한다. 행의 원문과 상태를 함께 다른 값으로 바꾸어 행 내부의 일치 여부만 통과해도, 전체 결과의 원본 행 대조에서 거절한다. 버전·revision은 현재 지원 값만 받는다. null 의존성은 `NullPointerException`, 원문에 맞지 않는 상태나 목록·버전은 `IllegalArgumentException`으로 중단한다.

이 연결 검사는 내부 정합성 검사다. 원본과 해시를 함께 바꾼 입력의 진위를 인증하지 않으며 보존 파일의 출처 검증·신선도·효력 시각·과거 정보 가용성은 별도다. 해석 시각을 `informationAvailableAt`으로 만들거나 현재 관측을 `AS_OF_VERIFIED`로 승격하지 않는다.

## 기존 동작과 운영 경계

원문 파서·추출 record·RAW_V2 및 원문 추출 V1의 필드·버전·JSON은 유지한다. [기존 제한 관측](../basicinfo/kis-stock-basic-info-restriction-observation.md)과 [사전 점검 V1·V2](../basicinfo/kis-stock-basic-info-restriction-screening.md)에 새 상태를 연결하지 않았으며 기존 점검의 판정·사유·JSON도 유지한다.

이번 관측 결과의 존재만으로 기존 점검이 시장경보까지 검사했다고 간주하지 않는다. 경보·예고·미확인 상태를 어떤 후보 제외 규칙으로 사용할지는 별도 정책이며, 현재 후보 선정·유동성 평가·백테스트·Risk Guard·주문·스케줄·AI Prompt에 연결하지 않는다.

`runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. DB·스키마·설정·`.env`·Docker·라이브러리 변경은 없다. 계좌·주문·OpenAI 호출도 없다.

## 관련 테스트

테스트는 같은 `src/test/java/.../restriction/kis/warning/` 경로에 정책·enum·행 관측·전체 결과 네 클래스로 둔다. 기존 원문 추출 Fixture를 재사용하며 별도 실제 파일과 외부 서비스는 필요하지 않다.

2026-10-07 관련 범위 **16개 클래스·363개 테스트가 모두 통과**했다. 새 테스트는 57개이며 실패·오류·건너뜀은 0개다. 같은 실행의 XML suite 이름과 시각으로 긴 클래스명의 축약 파일도 포함해 집계했다. 전체 프로젝트 테스트는 실행하지 않았다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.kis.warning.*' --tests 'com.stock.market.stock.master.provider.kis.parsing.warning.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.*' --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --offline --no-daemon
```

두 시장에서 경보 코드 10종과 예고 7종의 조합을 검사하고, 공백·미정의 값 보존·전체 행 식별자·순서·기존 투자주의환기·입력 근거 보존을 확인한다. 원문과 맞지 않는 모든 상태, 예고의 잘못된 `FIELD_NOT_PROVIDED`, 원문·상태·목록·버전 변조와 전체 JSON 왕복·목록 불변성도 검증한다.

로컬 대조에서는 4,403행의 두 상태 8,806개와 전체 JSON 왕복, 기존 점검 결과 20건 및 증적 104개의 보존을 확인했다. 최초·재현 결과는 검증 시각을 제외한 전체 JSON이 같았다. 자료에 없는 `03`·미정의 값은 합성 테스트로 확인하며 실제 관측이 있었던 것처럼 보고하지 않는다. 상세 집계·해시·재현 경로는 [시장경보 관측 대조 기록](validation/kis-stock-market-warning-interpretation-observation-01.md)에 남긴다.
