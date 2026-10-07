# KIS 현재 종목 제한 사전 점검

## 목적과 범위

[마스터와 주식기본조회 제한 관측](kis-stock-basic-info-restriction-observation.md)을 받아 단건의 제외 신호, 검토 필요 또는 제한 신호 없음을 구분한다. **원천별 관측 전체를 보존하며, 시장별 비적용 설명을 원문 미제공 진단과 분리한다.** 식별 대조 실패와 미확인을 정상으로 바꾸지 않는다.

이 판정은 후보 검토 전에 사용할 내부의 보수적인 점검 규칙이다. API 필드의 공식 설명란이 공백이라는 기존 원천 한계를 해소한 것이 아니며 Y 관측으로 실제 거래정지 효력이나 주문 불가능을 인증하지 않는다. 아직 후보를 제외하거나 주문을 차단하는 운영 경로에 연결하지 않는다.

`NO_EXCLUSION_SIGNAL_OBSERVED`는 점검 대상 제한에서 제외 신호나 미해결 차단 사유가 없다는 뜻이다. V2에서는 일곱 관측 모두 N인 경우뿐 아니라, 확인한 코스피 보통주의 투자주의환기만 비적용이고 나머지 여섯 관측이 N인 경우도 포함한다. 유형·상장 상태·신선도·과거 정보 가용성·NXT 거래 가능성이나 최종 투자 적격을 승인하지 않는다. 코스닥 참고 유형이 미확정인 입력도 일곱 관측이 N이면 이 상태가 될 수 있다.

## 패키지와 호출 계약

패키지는 `com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening`이며 파일은 동일한 `src/main/java/` 경로에 둔다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoRestrictionScreeningPolicy` | `evaluate(observation)`는 현재 V2로 점검한다. `evaluate(observation, screeningVersion)`는 명시한 V1 또는 V2 규칙을 사용한다. |
| `result.KisStockBasicInfoRestrictionScreeningResult` | 원래 관측 전체, 판정, 모든 사유 목록과 점검 버전을 보존한다. |
| `result.KisStockBasicInfoRestrictionScreeningStatus` | 식별 대조와 원천별 사유에 따른 세 상태의 우선순위를 정한다. |
| `result.KisStockBasicInfoRestrictionScreeningReasonCode` | 원천·필드별 Y와 미확인, 투자주의환기 미제공·비적용 설명 및 식별 대조 실패를 구분한다. |

현재 점검 버전은 `KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V2`다. V1은 원래 판정·사유로 평가하고 JSON도 원래 버전대로 복원한다. 두 버전 외의 문자열은 거절하며 옛 결과에 새 사유나 기본값을 채우지 않는다. Spring 빈이 아닌 무상태 일반 Java 클래스이며 외부 의존성, 파일·HTTP·DB 접근과 배치 실행을 추가하지 않는다. 원천의 해석·관측·파서·대조 버전, 근거 revision·참조·SHA-256, 유형과 사유, 원문 및 수집 메타데이터는 `observation` 안에 그대로 남는다.

## 판정 우선순위

| 순서 | 조건 | 판정 |
| --- | --- | --- |
| 1 | 표준코드·시장 대조가 성공하지 않음 | `REVIEW_REQUIRED` |
| 2 | 대조 성공, 일곱 관측 중 하나 이상이 `Y_OBSERVED` | `EXCLUSION_SIGNAL_OBSERVED` |
| 3 | 대조 성공, Y는 없지만 `VALUE_UNVERIFIED` 또는 비적용 근거가 없는 `FIELD_NOT_PROVIDED`가 남음 | `REVIEW_REQUIRED` |
| 4 | 대조 성공, 미해결 차단 사유가 없음 | `NO_EXCLUSION_SIGNAL_OBSERVED` |

현재 관측 입력 계약에서는 대조가 성공하면 일곱 관측 단계가 모두 존재한다. 관측 미수행 null은 대조 실패 때 발생하며 첫 번째 규칙으로 검토 필요에 남는다. null을 N이나 필드 미제공으로 변환하지 않는다.

대조 실패 시 찾은 마스터의 Y와 미확인은 진단 사유로 보존하지만 최종 점검 상태를 제외 신호로 승격하지 않는다. 실패한 API 원문은 기존 입력에 남되 API 관측과 API 사유를 생성하지 않는다. 요청 마스터 행도 없으면 대조 실패 사유만 남고 일곱 관측은 모두 null이다.

대조 성공 뒤 마스터가 Y이고 API가 N이면 원천별 값을 그대로 남기고 마스터 Y를 제외 신호로 기록한다. 반대 경우는 API Y를 기록한다. 두 원천이 Y이면 두 사유를 모두 남긴다. 관측 시점이나 필드의 적용 범위가 같다고 가정하지 않으며 원천 차이만으로 어느 쪽이 잘못됐다고 확정하지 않는다.

Y와 미확인이 함께 있으면 상태는 제외 신호이고 두 종류의 사유를 모두 보존한다. 코스피 투자주의환기 미제공도 제거하지 않는다. V1에서는 다른 여섯 필드가 전부 N인 코스피 입력도 검토 필요다. V2는 아래 근거를 확인한 범위에서만 이 한 항목을 비차단으로 분리한다.

## V2 비적용 범위와 근거

[2026-10-07 시장별 투자주의환기 적용 범위 검증](validation/kis-investment-caution-applicability-validation-01.md)의 KRX 규정 버전·시행일과 KIS 고정 규격에 V2를 연결한다. 결과의 `screeningVersion`과 `MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE`에서 이 해석을 구분하고, 원천 규격 근거는 보존된 관측에서 추적한다. 규정 변경에 자동 대응하거나 모든 과거·미래 시점에 비적용을 인증하는 계약은 아니다.

비적용은 표준코드·시장 대조가 성공한 KOSPI 보통주에 한정한다. 참고 유형과 API 해석 유형은 `COMMON_STOCK`이어야 하며 API 원문은 `STK`·상품유형 `300`·그룹 `ST`·주식종류 `101`이어야 한다. 우선주·ETF·유형 미확인·충돌에는 확대하지 않는다. 코스닥 Y·N·공백 등의 해석은 V1과 같다.

V2가 인정하는 입력 계약도 고정한다. 아래 값 중 하나라도 다르면 비적용 사유를 생성하지 않고 코스피 미제공을 미해결 차단 사유로 유지한다. 다른 제한 Y가 있으면 제외 신호가 우선한다. 원천 해시와 버전 문자열의 일치는 진위나 신선도의 인증이 아니다.

| 입력 근거 | 고정값 |
| --- | --- |
| 제한 관측 | `KIS_STOCK_BASIC_INFO_RESTRICTION_OBSERVATION_V1` |
| 유형 보완 | `KIS_STOCK_BASIC_INFO_CURRENT_TYPE_RESOLUTION_V1` |
| 식별 대조 | `KIS_STOCK_BASIC_INFO_STANDARD_CODE_MARKET_V1` |
| 마스터 해석 | `KIS_STOCK_MASTER_CURRENT_TYPE_V1` |
| 마스터 규격 revision | `277ec0eb7a9b7f63b6807829286c80f36649dad2` |
| 마스터 파서·배치 레이아웃 | `KIS_STOCK_MASTER_RAW_V2` / `OBSERVED_2026_10_05_LF_V1` |
| API 유형 해석·파서 | `KIS_STOCK_BASIC_INFO_CURRENT_TYPE_V1` / `KIS_STOCK_BASIC_INFO_RAW_V1` |
| API 규격 참조 | `build/kis-stock-eligibility-source-validation-01/apiportal-specification.json` |
| API 규격 SHA-256 | `1fbf349c755a3f54469450e0b7ca89ee3aa3a779e4a33286348cc2e198c0567a` |

원문 `null`과 `FIELD_NOT_PROVIDED`, 기존 `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED`는 모두 유지한다. 비적용 설명은 이 진단 바로 뒤에 기록한다. V2 상태 계산에서는 확인된 비적용 설명과 해당 미제공 진단만 비차단이며 다른 Y·미확인은 그대로 남는다. 투자주의·투자경고·투자위험이라는 별도 시장경보의 검사를 구현하거나 면제한 것은 아니다.

후속 [시장경보 원문 추출](../master/kis-stock-market-warning-raw-parsing.md)에서 경보 구분 코드와 위험 예고 문자를 별도로 보존한다. 현재 점검 V1·V2의 입력·판정·사유에는 연결하지 않았고 보존 결과 20건도 동일하게 유지됐다. 이 추출의 존재나 현재 `NO_EXCLUSION_SIGNAL_OBSERVED`를 시장경보 검사 통과로 해석하지 않는다.

별도 [시장경보 관측 상태 해석](../master/kis-stock-market-warning-observation.md)도 추가됐지만, 이 점검의 규칙에는 연결하지 않았다. 경보·예고의 원문과 관측 상태를 보존하는 것과 해당 신호를 후보 제외 규칙으로 사용하는 것은 구분한다. 기존 점검 V1·V2의 입력·판정·사유·JSON은 유지한다.

## 사유 목록과 결과 정합성

사유 이름은 원천·필드·관측을 구분한다. 예를 들어 `MASTER_SUSPENSION_Y_OBSERVED`와 `BASIC_INFO_SUSPENSION_Y_OBSERVED`는 별개다. 미확인은 해당 필드의 `*_VALUE_UNVERIFIED`, 코스피 투자주의환기 미제공은 `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED`로 기록한다.

대조 실패 사유는 `STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED`다. 구체적인 실패 종류는 보존된 `observation.typeResolution.matchingResult.reasonCode`에서 확인한다. 미수행 단계는 기존 null 관측과 실패 입력으로 보존하며 실제 미제공 사유를 만들어 대신 채우지 않는다.

목록 순서는 대조 실패, 마스터 거래정지·정리매매·SPAC·관리종목·투자주의환기, API 거래정지·관리종목 순이다. 해당 필드에서 발생한 사유만 포함하며 정확한 N은 사유를 생성하지 않는다. V2의 비적용 설명만 실제 문자 관측과 별개다. `isExclusionSignal()`은 사유 자체가 Y 관측인지를 나타낼 뿐 대조 실패 때 최종 상태를 결정하는 승인 메서드가 아니다.

결과 생성자는 원래 관측과 상태의 nonnull, V1 또는 V2의 정확한 점검 버전을 요구한다. `reasonCodes`는 `List.copyOf`로 복사하고 null 목록·null 원소를 거절한다. 해당 버전으로 다시 계산한 사유 전체와 정의된 순서가 같아야 하고 판정도 같아야 한다. 사유 삭제·중복·순서 변경·비적용 주입·근거 없는 상태 변경과 판정이 달라지는 결과의 버전명만 교체하는 행위를 거절한다.

같은 값의 전체 JSON 복원은 허용한다. 생성자 검사는 입력과 판정의 정합성이지 실제 정책 실행, 원천·해시·버전의 진위, 신선도 또는 시점 가용성의 인증은 아니다. 유형·상장 상태·날짜나 원천 제한 값은 변경하지 않는다.

## 관련 테스트

2026-10-07 V2 변경에서는 제한 관측·유형 보완·저장된 분석 경로의 **20개 클래스·656개 테스트가 모두 통과**했다. 실패·오류·건너뜀은 0개다. 현재 HTML 클래스 목록과 해당 실행 시각의 XML suite 이름을 함께 대조했다. 전체 프로젝트 테스트는 실행하지 않았다.

V2 전용 테스트 30개는 여섯 필드 각각의 Y·미확인 보존, 코스닥 해석 유지, 우선주·ETF·유형 충돌과 미확인 제외, 11종의 규격·버전·근거 변경 시 보수적 처리와 JSON 비적용 변조 거절을 확인한다. 기존 결과 테스트에는 V1 JSON 원형 복원, 버전명만의 교체와 미지원 버전 거절을 보강했다. 저장된 분석 서비스와 실행기 구성의 H2 테스트도 현재 V2 결과로 통과했다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.*' --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --offline --no-daemon
```

아래는 최초 V1 구현 당시의 검증 이력이다.

테스트는 동일한 `src/test/java/.../screening/` 아래 정책 테스트와 `result/` 아래 결과 테스트에 둔다. 기존 관측 및 유형 보완 Fixture를 재사용하며 새 외부 의존성이나 실측 파일이 필요하지 않다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **503개가 모두 통과**했다. 신규 정책 75개와 결과 19개, 기존 관련 테스트 409개를 포함한 13개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 클래스 목록과 XML suite 이름으로 집계했다.

일곱 필드의 단일 Y, 공백·소문자·미정의·표기 변형, 128가지 Y 조합, 복수 사유의 안정적인 순서와 Y·미확인의 동시 보존을 확인한다. 마스터 Y·API N과 반대 경우, 코스피 미제공, 모든 대조 실패의 검토 우선순위와 API 사유 미생성도 검증한다.

유형 보완 사유와 참고 유형을 바꾸지 않고, 유형 미확정·NXT Y·알 수 없는 폐지일이 있어도 이번 제한 신호 없음이 다른 검증을 통과시킨 것은 아님을 확인한다. 결과 테스트는 세 상태와 모든 유형 사유의 전체 JSON 왕복, 없는 요청 행의 복원, 판정·사유 변조 거절과 목록의 불변성을 포함한다.

## 최초 V1 보존 응답 재평가

마스터 4,403행과 기존 API 보존 응답 10건을 실제 파서·대조·유형 보완·관측 정책으로 다시 읽었다. 전체 마스터 배치와 관측 10건이 직전 보고서와 같았고, 새 점검 결과 전체가 사례별로 고정한 판정·사유·버전과 일치했다.

| 요청 종목 | 점검 상태 | 주요 근거 |
| --- | --- | --- |
| `005930`, `005935`, `000087`, `069500` | `REVIEW_REQUIRED` | 코스피 투자주의환기 미제공 |
| `000250` | `NO_EXCLUSION_SIGNAL_OBSERVED` | 대조 성공, 일곱 관측 모두 N |
| `0004Y0` | `EXCLUSION_SIGNAL_OBSERVED` | 마스터 SPAC Y |
| `000040` | `EXCLUSION_SIGNAL_OBSERVED` | 마스터·API 관리종목 Y, 코스피 미제공도 보존 |
| `007330` | `EXCLUSION_SIGNAL_OBSERVED` | 마스터 투자주의환기 Y |
| `000300` | `EXCLUSION_SIGNAL_OBSERVED` | 마스터·API 거래정지 Y, 코스피 미제공도 보존 |
| `067770` | `EXCLUSION_SIGNAL_OBSERVED` | 마스터 거래정지·정리매매·관리종목 Y, API 거래정지·관리종목 Y |

종목별 상태는 제외 신호 5건·검토 필요 4건·제한 신호 없음 1건이다. 다중 사유의 수를 서로 다른 종목 수와 혼동하지 않는다. ETF 참고 유형인 `069500`도 이 제한 점검만 수행한 결과이며 ETF 매매를 허용하지 않는다. `000250` 한 건을 투자 후보 승인 수로 해석하지 않는다.

전체 마스터 배치를 포함한 결과 10건의 JSON 왕복과 반복 호출 결과가 같았다. 최초·재현 보고서는 `verifiedAt`만 제외한 전체 JSON 값과 배열 순서가 같았으며 정수·소수의 정밀도를 유지했다. 기존 원문·수집 증적·규격·코드·도구·보고서 83개의 SHA-256도 실행 전후 같았다. 추가 HTTP 요청·토큰 발급은 각각 0회다.

이 재평가는 보존 자료의 재현성 확인이다. 원천별 수집 시각을 동시에 관측한 상태나 제한 값의 효력 시각으로 합치지 않으며 최신 거래 가능 여부, 과거 자격, 전 시장 모집단과 전략 수익성의 검증으로 사용하지 않는다.

## 최초 V1 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-restriction-screening-observation-01/`에 `VerifyKisStockBasicInfoRestrictionScreening.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 공통 마스터 배치는 보고서 루트에 한 번 저장한다. 각 행의 `screeningResultWithoutSharedMasterBatch.observation.typeResolution.matchingResult`에 배치를 결합해 전체 결과를 복원할 수 있다. 실제 JSON 왕복은 배치를 포함한 전체 결과로 검사했다.

- 최초 보고서 SHA-256: `6188d35b5a4a85e02e9ed466f01a0242b755b51c5e53875e69918be4fc14d858`
- 재현 보고서 SHA-256: `020de8504372525df7da38c0761dcf766a4b5490001027aeaba7bcb9e68290b5`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-restriction-screening-observation-01/observation.init.gradle verifyKisStockBasicInfoRestrictionScreening --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-restriction-screening-observation-01/observation.init.gradle verifyKisStockBasicInfoRestrictionScreening '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 V1 당시 완료된 최초·재현 실행의 기록이다. V1 도구는 당시의 기본 정책 버전을 전제로 하므로 현재 기본 V2 정책의 재평가에는 사용하지 않는다. 기존 보고서와 도구를 수정하지 않고 [별도 V2 대조](validation/kis-stock-basic-info-restriction-screening-observation-02.md)에서 V1을 명시해 복원·평가한다. 기존 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 도구는 운영 빌드에 등록하지 않았고 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## V2 보존 응답 비교

같은 마스터 4,403행과 API 응답 10건으로 V1 전체 결과 복원·재평가와 V2 판정·사유를 대조했다. V1의 제외 5·검토 4·신호 없음 1건은 유지됐다. V2는 제외 5·검토 3·신호 없음 2건이며 상태가 바뀐 것은 보통주 `005930` 한 건뿐이다.

관리종목 `000040`과 거래정지 `000300`에도 미제공·비적용 설명이 함께 남지만 다른 Y 때문에 제외 상태를 유지한다. 우선주 `005935`·`000087`과 ETF `069500`은 검토 필요를 유지한다. 전체 결과 20건의 JSON 왕복, 재실행 결과와 기존 증적 87개의 보존도 확인했다. HTTP·토큰 요청·DB 연결은 0회이며 [상세 보고서](validation/kis-stock-basic-info-restriction-screening-observation-02.md)에 재현 경로와 해시를 기록한다.

## 운영 연결 경계

기존 원천 파서·대조·유형 보완·제한 관측과 `StockEligibilityPolicy`, `StockEligibilityInput`, `StockCandidateEvaluationService`는 변경하지 않았다. 새 점검 결과를 기존 자격 결과로 변환하거나 `AS_OF_VERIFIED`, `informationAvailableAt`, 상장 상태와 거래 허가를 생성하지 않는다. 현재 보존 관측을 과거 백테스트의 자격 근거로 소급하지 않는다.

후보·유동성 평가·운영 Universe·백테스트·주문·Risk·스케줄·AI Prompt 연결은 없으며 `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. DB·스키마·Spring 빈·라이브러리·설정·`.env`·Docker와 서버 상태 변경, 계좌·주문·OpenAI 호출과 커밋·Push도 수행하지 않았다.
