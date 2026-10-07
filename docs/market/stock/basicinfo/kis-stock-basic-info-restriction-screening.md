# KIS 현재 종목 제한 사전 점검

## 목적과 범위

[마스터와 주식기본조회 제한 관측](kis-stock-basic-info-restriction-observation.md)을 받아 단건의 제외 신호, 검토 필요 또는 제한 신호 없음을 구분한다. **원천별 관측 전체를 보존하며 식별 대조 실패와 미확인·미제공을 정상으로 바꾸지 않는다.**

이 판정은 후보 검토 전에 사용할 내부의 보수적인 점검 규칙이다. API 필드의 공식 설명란이 공백이라는 기존 원천 한계를 해소한 것이 아니며 Y 관측으로 실제 거래정지 효력이나 주문 불가능을 인증하지 않는다. 아직 후보를 제외하거나 주문을 차단하는 운영 경로에 연결하지 않는다.

`NO_EXCLUSION_SIGNAL_OBSERVED`는 이번 일곱 제한 관측에서 신호가 없다는 뜻이다. 유형·상장 상태·신선도·과거 정보 가용성·NXT 거래 가능성이나 최종 투자 적격을 승인하지 않는다. 현재 참고 유형이 미확정인 입력도 일곱 관측이 N이면 이 상태가 될 수 있다.

## 패키지와 호출 계약

패키지는 `com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening`이며 파일은 동일한 `src/main/java/` 경로에 둔다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoRestrictionScreeningPolicy` | `evaluate(KisStockBasicInfoRestrictionObservationResult)`로 단건을 점검한다. |
| `result.KisStockBasicInfoRestrictionScreeningResult` | 원래 관측 전체, 판정, 모든 사유 목록과 점검 버전을 보존한다. |
| `result.KisStockBasicInfoRestrictionScreeningStatus` | 식별 대조와 원천별 사유에 따른 세 상태의 우선순위를 정한다. |
| `result.KisStockBasicInfoRestrictionScreeningReasonCode` | 원천·필드별 Y와 미확인, 코스피 투자주의환기 미제공 및 식별 대조 실패를 구분한다. |

점검 버전은 `KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V1`이다. Spring 빈이 아닌 무상태 일반 Java 클래스이며 외부 의존성, 파일·HTTP·DB 접근과 배치 실행을 추가하지 않는다. 원천의 해석·관측·파서·대조 버전, 근거 revision·참조·SHA-256, 유형과 사유, 원문 및 수집 메타데이터는 `observation` 안에 그대로 남는다.

## 판정 우선순위

| 순서 | 조건 | 판정 |
| --- | --- | --- |
| 1 | 표준코드·시장 대조가 성공하지 않음 | `REVIEW_REQUIRED` |
| 2 | 대조 성공, 일곱 관측 중 하나 이상이 `Y_OBSERVED` | `EXCLUSION_SIGNAL_OBSERVED` |
| 3 | 대조 성공, Y는 없지만 `VALUE_UNVERIFIED` 또는 `FIELD_NOT_PROVIDED`가 남음 | `REVIEW_REQUIRED` |
| 4 | 대조 성공, 일곱 관측 모두 정확한 `N_OBSERVED` | `NO_EXCLUSION_SIGNAL_OBSERVED` |

현재 관측 입력 계약에서는 대조가 성공하면 일곱 관측 단계가 모두 존재한다. 관측 미수행 null은 대조 실패 때 발생하며 첫 번째 규칙으로 검토 필요에 남는다. null을 N이나 필드 미제공으로 변환하지 않는다.

대조 실패 시 찾은 마스터의 Y와 미확인은 진단 사유로 보존하지만 최종 점검 상태를 제외 신호로 승격하지 않는다. 실패한 API 원문은 기존 입력에 남되 API 관측과 API 사유를 생성하지 않는다. 요청 마스터 행도 없으면 대조 실패 사유만 남고 일곱 관측은 모두 null이다.

대조 성공 뒤 마스터가 Y이고 API가 N이면 원천별 값을 그대로 남기고 마스터 Y를 제외 신호로 기록한다. 반대 경우는 API Y를 기록한다. 두 원천이 Y이면 두 사유를 모두 남긴다. 관측 시점이나 필드의 적용 범위가 같다고 가정하지 않으며 원천 차이만으로 어느 쪽이 잘못됐다고 확정하지 않는다.

Y와 미확인이 함께 있으면 상태는 제외 신호이고 두 종류의 사유를 모두 보존한다. 코스피 투자주의환기 미제공도 제거하지 않는다. 다른 여섯 필드가 전부 N인 코스피 입력은 검토 필요다. 이 공백을 보충하지 않고 전 시장 후보 선정이 준비됐다고 판단하지 않는다.

후속 [시장별 투자주의환기 적용 범위 검증](validation/kis-investment-caution-applicability-validation-01.md)은 현행 KRX 규정과 고정 KIS 규격을 대조해 원천 미제공과 제도 비적용을 구분할 근거를 정리한다. 현재 V1 판정과 당시 보고서는 유지한다. 코스피 원문을 N으로 보정하지 않고, 변경된 점검은 별도 버전과 검증을 거쳐야 한다.

## 사유 목록과 결과 정합성

사유 이름은 원천·필드·관측을 구분한다. 예를 들어 `MASTER_SUSPENSION_Y_OBSERVED`와 `BASIC_INFO_SUSPENSION_Y_OBSERVED`는 별개다. 미확인은 해당 필드의 `*_VALUE_UNVERIFIED`, 코스피 투자주의환기 미제공은 `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED`로 기록한다.

대조 실패 사유는 `STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED`다. 구체적인 실패 종류는 보존된 `observation.typeResolution.matchingResult.reasonCode`에서 확인한다. 미수행 단계는 기존 null 관측과 실패 입력으로 보존하며 실제 미제공 사유를 만들어 대신 채우지 않는다.

목록 순서는 대조 실패, 마스터 거래정지·정리매매·SPAC·관리종목·투자주의환기, API 거래정지·관리종목 순이다. 해당 필드에서 발생한 사유만 포함하며 정확한 N은 사유를 생성하지 않는다. `isExclusionSignal()`은 사유 자체가 Y 관측인지를 나타낼 뿐 대조 실패 때 최종 상태를 결정하는 승인 메서드가 아니다.

결과 생성자는 원래 관측과 상태의 nonnull, 점검 버전의 nonblank를 요구한다. `reasonCodes`는 `List.copyOf`로 복사하고 null 목록·null 원소를 거절한다. 원문 관측에 맞는 사유 전체와 정의된 순서가 같아야 하고 판정도 위 우선순위와 같아야 한다. 사유 삭제·중복·순서 변경·다른 필드의 주입과 근거 없는 상태 변경을 거절한다.

같은 값의 전체 JSON 복원은 허용한다. 생성자 검사는 입력과 판정의 정합성이지 실제 정책 실행, 원천·해시·버전의 진위, 신선도 또는 시점 가용성의 인증은 아니다. 유형·상장 상태·날짜나 원천 제한 값은 변경하지 않는다.

## 관련 테스트

테스트는 동일한 `src/test/java/.../screening/` 아래 정책 테스트와 `result/` 아래 결과 테스트에 둔다. 기존 관측 및 유형 보완 Fixture를 재사용하며 새 외부 의존성이나 실측 파일이 필요하지 않다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **503개가 모두 통과**했다. 신규 정책 75개와 결과 19개, 기존 관련 테스트 409개를 포함한 13개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 클래스 목록과 XML suite 이름으로 집계했다.

일곱 필드의 단일 Y, 공백·소문자·미정의·표기 변형, 128가지 Y 조합, 복수 사유의 안정적인 순서와 Y·미확인의 동시 보존을 확인한다. 마스터 Y·API N과 반대 경우, 코스피 미제공, 모든 대조 실패의 검토 우선순위와 API 사유 미생성도 검증한다.

유형 보완 사유와 참고 유형을 바꾸지 않고, 유형 미확정·NXT Y·알 수 없는 폐지일이 있어도 이번 제한 신호 없음이 다른 검증을 통과시킨 것은 아님을 확인한다. 결과 테스트는 세 상태와 모든 유형 사유의 전체 JSON 왕복, 없는 요청 행의 복원, 판정·사유 변조 거절과 목록의 불변성을 포함한다.

## 보존 응답 재평가

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

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-restriction-screening-observation-01/`에 `VerifyKisStockBasicInfoRestrictionScreening.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 공통 마스터 배치는 보고서 루트에 한 번 저장한다. 각 행의 `screeningResultWithoutSharedMasterBatch.observation.typeResolution.matchingResult`에 배치를 결합해 전체 결과를 복원할 수 있다. 실제 JSON 왕복은 배치를 포함한 전체 결과로 검사했다.

- 최초 보고서 SHA-256: `6188d35b5a4a85e02e9ed466f01a0242b755b51c5e53875e69918be4fc14d858`
- 재현 보고서 SHA-256: `020de8504372525df7da38c0761dcf766a4b5490001027aeaba7bcb9e68290b5`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-restriction-screening-observation-01/observation.init.gradle verifyKisStockBasicInfoRestrictionScreening --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-restriction-screening-observation-01/observation.init.gradle verifyKisStockBasicInfoRestrictionScreening '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료된 최초·재현 실행의 기록이다. 기존 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 점검 정책·결과·상태·사유와 새 검증 도구의 해시를 별도로 남겼으며 기존 증적은 변경하지 않았다. 도구는 운영 빌드에 등록하지 않았고 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

기존 원천 파서·대조·유형 보완·제한 관측과 `StockEligibilityPolicy`, `StockEligibilityInput`, `StockCandidateEvaluationService`는 변경하지 않았다. 새 점검 결과를 기존 자격 결과로 변환하거나 `AS_OF_VERIFIED`, `informationAvailableAt`, 상장 상태와 거래 허가를 생성하지 않는다. 현재 보존 관측을 과거 백테스트의 자격 근거로 소급하지 않는다.

후보·유동성 평가·운영 Universe·백테스트·주문·Risk·스케줄·AI Prompt 연결은 없으며 `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. DB·스키마·Spring 빈·라이브러리·설정·`.env`·Docker와 서버 상태 변경, 계좌·주문·OpenAI 호출과 커밋·Push도 수행하지 않았다.
