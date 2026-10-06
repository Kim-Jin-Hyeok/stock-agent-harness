# KIS 마스터와 주식기본조회 제한 관측

## 목적과 범위

[현재 유형 보완 결과](kis-stock-basic-info-type-resolution.md)를 받아 마스터 다섯 필드와 주식기본조회 두 필드의 문자 관측을 별도로 기록한다. **API 관측은 표준코드·시장 대조 성공 때만 연결하며, 양쪽 값이 달라도 덮어쓰거나 정상 여부 하나로 합치지 않는다.**

보통주 참고 유형이 있어도 SPAC·관리종목·거래정지 등의 원문은 함께 남는다. 이 결과는 후보 제외 정책을 위한 입력 정리이며, 실제 후보 선정·주문 차단·상장 상태·과거 자격·실시간 거래 허가를 승인하지 않는다. `N_OBSERVED`는 해당 필드에서 N을 읽었다는 뜻이지 투자 적격이라는 뜻이 아니다.

## 패키지와 호출 계약

패키지는 `com.stock.strategy.universe.eligibility.restriction.kis.basicinfo`다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoRestrictionObservationPolicy` | `evaluate(KisStockBasicInfoTypeResolutionResult)`로 단건의 원천별 필드를 관측한다. |
| `result.KisStockBasicInfoRestrictionObservationResult` | 유형 보완 입력 전체, 원천별 상태 일곱 개와 관측 버전을 보존한다. |
| 기존 `restriction.kis.result.KisStockTradingFlagStatus` | `fromRawValue(String)`으로 두 경로가 동일한 문자 관측 규칙을 공유한다. |

관측 버전은 `KIS_STOCK_BASIC_INFO_RESTRICTION_OBSERVATION_V1`이다. 마스터 규격 revision, API 규격 참조·SHA-256, 원천 해석·대조·파서 버전, 원문 입력 해시와 수집 메타데이터는 유형 보완 입력 안에 그대로 남는다. 새 관측 버전으로 그 근거를 대체하지 않는다.

정책은 Spring 빈이 아닌 무상태 일반 Java 클래스다. 파일·HTTP·DB 접근, 배치 실행, 새 인터페이스와 설정을 추가하지 않는다. null 입력은 `NullPointerException`으로 거절한다.

## 원천별 필드와 상태

| 원천 | 원문 | 결과 필드 |
| --- | --- | --- |
| 마스터 | 거래정지 | `masterSuspensionStatus` |
| 마스터 | 정리매매 | `masterLiquidationStatus` |
| 마스터 | SPAC | `masterSpacStatus` |
| 마스터 | 관리종목 | `masterManagementStatus` |
| 마스터 | 투자주의환기 | `masterInvestmentCautionStatus` |
| 주식기본조회 | 거래정지 `tr_stop_yn` | `basicInfoSuspensionStatus` |
| 주식기본조회 | 관리종목 `admn_item_yn` | `basicInfoManagementStatus` |

관측한 원문 값은 다음과 같이 변환한다. trim·대소문자 변경·boolean 기본값을 적용하지 않는다.

| 원문 | 상태 |
| --- | --- |
| 정확한 `Y` | `Y_OBSERVED` |
| 정확한 `N` | `N_OBSERVED` |
| 그 밖의 nonnull 문자열 | `VALUE_UNVERIFIED` |
| 실제 미제공 필드의 null | `FIELD_NOT_PROVIDED` |

현재 입력 계약에서 실제 미제공 null은 코스피 마스터 투자주의환기에 해당한다. 코스닥의 제공된 공백은 `VALUE_UNVERIFIED`, 정확한 N은 `N_OBSERVED`다. API 파서는 관측할 두 문자열의 존재와 nonnull을 요구하므로 빈 문자열도 미제공으로 바꾸지 않는다. 누락·null·구조 오류는 파서 단계에서 거절한다.

API 거래정지·관리종목의 공식 설명란이 공백인 점은 그대로 유지한다. 이번 규칙은 Y/N 문자 관측이며 필드의 적용 시점·효력·KRX 및 NXT 거래 가능 여부를 인증하지 않는다. NXT 정지·경쟁매매 허용·상장 및 폐지일·상품번호는 원문으로 보존하지만 이번 관측 필드에는 포함하지 않는다.

## 필드 미제공과 관측 미수행

`FIELD_NOT_PROVIDED`와 결과 필드의 null은 서로 다르다.

- 요청 마스터 행이 없으면 마스터 관측 다섯 개와 API 관측 두 개가 모두 null이다. 대응 원천을 연결하지 못한 것이지 모든 필드가 미제공이라는 뜻은 아니다.
- 요청 마스터 행은 있지만 대조가 실패하면 마스터 다섯 필드는 관측하고 API 두 상태는 null로 둔다. 실패한 응답의 원문은 입력 전체에 남지만 요청 종목의 관측으로 연결하지 않는다.
- 대조가 성공하면 참고 유형이 null이어도 API 두 필드를 관측한다. 유형 미확정·충돌과 식별 대조 실패를 혼동하지 않는다.
- 정상적으로 찾은 코스피 마스터의 투자주의환기는 `FIELD_NOT_PROVIDED`로 남는다. 이를 N이나 관측 미수행 null로 바꾸지 않는다.

마스터가 Y, API가 N인 경우에도 두 관측을 유지한다. 관측 시점과 원천 정의가 다를 수 있으므로 어느 쪽이 틀렸다고 단정하거나 더 최근 값으로 자동 덮어쓰지 않는다. 생략된 관측과 미확인을 정상 상태로 처리하지 않는다.

## 결과 정합성과 기존 경로

결과 생성자는 null 유형 입력과 공백 관측 버전을 거절한다. 존재하는 원천의 각 상태는 해당 원문 문자와 같아야 하며 제거할 수 없다. 요청 행이 없는데 마스터 상태를 넣거나, 대조가 실패했는데 API 상태를 넣으면 거절한다. `FIELD_NOT_PROVIDED`를 넣는 것도 금지된 관측 단계를 생성하는 것이므로 거절한다.

값이 같은 JSON 복원은 허용한다. 생성자 검사는 전달된 값의 정합성이지 실제 정책 실행·출처·해시 진위·시점 가용성의 인증은 아니다. 원천 유형·참고 유형·사유·날짜·제한 원문을 바꾸거나 단일 승인·거부 결과를 만들지 않는다.

기존 [KIS 마스터 제한 관측](../master/kis-stock-trading-restriction.md)은 private 변환을 공통 `fromRawValue` 호출로 바꿨다. `evaluate(KisKrxStockTypeResolutionResult)` 입력, 결과 필드와 상태 이름, V2 버전·근거 revision은 유지했다. 기존 결과 생성자의 검증도 그대로다. 관측 의미와 JSON 계약을 바꾸거나 기존 KIS·KRX 경로를 새 입력으로 교체하지 않았다.

## 관련 테스트

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **409개가 모두 통과**했다. 신규 정책 115개·결과 29개·공통 변환 14개, 기존 제한 관측 198개와 유형 보완 53개를 포함한 11개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 클래스 목록과 XML suite 이름으로 집계했다.

테스트는 양 시장의 마스터 다섯 필드, API 두 필드의 정확한 Y/N·공백·미정의·표기 변형, 동시 다중 Y, 양쪽 관측 차이 보존, 모든 대조 실패 시 API 미연결, 유형 충돌·미확정에서 독립 관측, 코스피 투자주의환기 미제공을 확인한다. 날짜·상품번호·NXT 관련 원문 보존과 반복 호출의 무상태성도 검증한다.

결과 테스트는 일곱 상태의 변조·제거, 금지된 관측 단계 주입, 다른 입력의 상태 사용을 거절하는지 확인한다. 모든 유형 보완 사유와 요청 행이 없는 결과의 전체 JSON 왕복을 포함한다. 기존 KIS·KRX·KRX 제한 관측 테스트도 공통 변환 도입 후 통과했다.

테스트와 `support/KisStockBasicInfoRestrictionObservationFixture.java`는 동일한 패키지 구조의 `src/test/java/` 아래에 있다. 공통 변환 테스트는 기존 `restriction/kis/result/`에 둔다. 기존 파서·대조·유형 보완 Fixture를 재사용하며 합성 수집 메타데이터와 실제 파서로 입력을 만든다. Spring·DB·네트워크·보존 실측 파일이 필요하지 않다.

## 보존 응답 대조

기존 보존 응답 10건과 마스터 4,403행을 다시 읽었다. 전체 마스터 배치와 유형 보완 결과 10건이 이전 보고서와 같았고, 새 결과 전체가 사례별로 고정한 기대 원문 관측과 일치했다. 마스터 50개·API 20개, 총 70개 필드를 대조했다.

| 관측 항목 | Y가 나타난 보존 요청 사례 |
| --- | --- |
| 마스터 거래정지 | `000300`, `067770` |
| 마스터 정리매매 | `067770` |
| 마스터 SPAC | `0004Y0` |
| 마스터 관리종목 | `000040`, `067770` |
| 마스터 투자주의환기 | `007330` |
| API 거래정지 | `000300`, `067770` |
| API 관리종목 | `000040`, `067770` |

마스터 필드 상태는 Y 7개·N 37개·미제공 6개이며, API 필드 상태는 Y 4개·N 16개다. 마스터 미제공 6개는 코스피 투자주의환기다. 여러 제한이 같은 종목에 나타날 수 있으므로 필드의 Y 합계를 서로 다른 종목 수로 사용하지 않는다. 이번 10건의 공통 두 필드는 일치했고, 양쪽이 다른 경우는 합성 테스트로 검증했다.

전체 배치를 포함한 결과 10건의 JSON 왕복과 반복 호출 결과가 같았다. 최초·재현 보고서는 `verifiedAt`만 제외한 전체 JSON 값과 배열 순서가 같았으며 정수·소수의 정밀도를 유지해 숫자 표현을 통일했다. 기존 원문·수집 증적·규격·파서·유형·대조·도구·보고서 75개의 SHA-256도 검사 전후 같았다. 추가 외부 API 요청·토큰 발급은 각각 0회다.

이 결과는 보존 원문의 재현 검증이지 최신·동시점 상태·과거 자격이나 투자 후보 승인 수가 아니다. 원천별 수집 시각과 제한 값의 효력 시각을 동일하게 취급하지 않는다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-restriction-observation-01/`에 `VerifyKisStockBasicInfoRestriction.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 보고서는 공통 마스터 배치를 루트에 한 번 저장하고, 각 행의 `observationResultWithoutSharedMasterBatch.typeResolution.matchingResult`에 배치를 결합해 전체 결과를 복원할 수 있다. 실제 JSON 왕복은 배치를 포함한 전체 결과로 검사했다.

- 최초 보고서 SHA-256: `be38bdcb45053080ed75a99b94f0ccc017d3b734d075b7655cd775ba7c631ef5`
- 재현 보고서 SHA-256: `9bd0f965ea0027b1b3e49a17049deed1f920afa611d4dc00e6fd761f0b87677d`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-restriction-observation-01/observation.init.gradle verifyKisStockBasicInfoRestriction --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-restriction-observation-01/observation.init.gradle verifyKisStockBasicInfoRestriction '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료된 최초·재현 실행의 기록이다. 두 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 기존 원문·보고서·검증 도구를 변경하지 않았고, 이번 공통 변환·기존 정책의 호출 변경·새 정책·결과·도구 해시를 별도로 남긴다. 도구는 운영 빌드에 등록하지 않았으며 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

기존 파서·대조·유형 보완·`StockEligibilityPolicy`·후보 목록·백테스트·주문 연결은 변경하지 않았다. `StockEligibilityInput`, `AS_OF_VERIFIED`, `informationAvailableAt`, 상장 상태와 거래 허가를 생성하지 않는다. `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다.

HTTP Client·Provider·인증·DB·스키마·Spring 빈·라이브러리·스케줄·설정·`.env`·Docker와 서버 상태 변경은 없다. 계좌·주문·OpenAI 호출 및 커밋·Push도 수행하지 않았다.
