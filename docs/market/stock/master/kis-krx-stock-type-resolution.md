# KIS KRX 현재 종목 유형 보완 결과

## 목적과 범위

KIS와 KRX의 원천별 유형 해석을 그대로 보존하고 **정확한 식별 연결이 확인된 행에만 별도의 현재 참고 유형**을 만든다. 기존 [식별 연결 정책](kis-krx-stock-identity-matching.md), [KIS 유형 정책](stock-master-type-classification.md), [KRX 유형 정책](krx-stock-basic-info-type-classification.md)을 재사용한다.

현재 KIS 정책은 제한적인 ETF 조합만 해석하며, KRX 정책은 주권의 보통주와 일부 우선주를 해석한다. KIS에서 보류한 원문 값을 바꾸지 않고 KRX의 확인된 해석을 별도 결과에 붙인다. 두 원천이 충돌하면 어느 한쪽을 우선하지 않고 참고 유형을 null로 남긴다. 이 결과는 종목 자격·거래 가능 여부·과거 모집단·투자 성과의 검증이 아니다.

## 패키지와 파일

기준 패키지는 `com.stock.strategy.universe.eligibility.classification.kiskrx`다. 원천별 해석 결과를 조합하는 책임이므로 기존 유형 해석 패키지 아래에 두고, 원천 간 식별 연결 패키지와 구분한다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/strategy/universe/eligibility/classification/kiskrx/KisKrxStockTypeResolutionPolicy.java` | 모든 KIS 행을 해석하고 정확히 연결된 KRX 해석으로 참고 유형을 보완한다. |
| 같은 경로의 `result/KisKrxStockTypeResolutionReasonCode.java` | 보완·동의·충돌·미확정 사유 5개를 표현한다. |
| 같은 경로의 `result/KisKrxStockTypeResolutionResult.java` | 양쪽 원천 해석·정확한 식별 연결·참고 유형·사유를 보존한다. |
| 같은 경로의 `result/KisKrxStockTypeResolutionBatchResult.java` | 보완 규칙 버전·전체 식별 연결 입력·모든 KIS 행의 결과를 보존한다. |

정책은 Spring 빈이 아닌 무상태 일반 클래스다. 생성자로 기존 `KisStockMasterTypeClassificationPolicy`와 `KrxStockBasicInfoTypeClassificationPolicy`를 받는다. 새 인터페이스·설정·자동 실행 계층은 추가하지 않는다.

보완 규칙 버전은 `KIS_KRX_CURRENT_TYPE_RESOLUTION_V1`이다. KIS의 `KIS_STOCK_MASTER_CURRENT_TYPE_V1`과 근거 revision, KRX의 `KRX_STOCK_BASIC_INFO_CURRENT_TYPE_V1`과 근거 참조·SHA-256은 각 원천 해석 결과 안에 별도로 유지한다. 보완 버전으로 원천 규칙 버전을 대체하지 않는다.

## 호출과 보존 계약

`resolve(KisKrxStockIdentityMatchingResult)`로 호출한다. 기존 식별 연결 정책에서 얻은 결과를 전달하며, 파일·HTTP·DB 접근 없이 반환값을 만든다. 결과 생성자의 일관성 검사는 정책 실행 여부나 원문 진위를 인증하지 않으므로 실제 입력은 수집 증적·파서·식별 연결 정책을 통해 검증해야 한다.

결과는 모든 KIS 행을 **KOSPI→KOSDAQ 순서로, 각 시장 안에서는 원문 행 순서로** 유지한다. KRX에 대응 행이 없는 KIS 자료도 제외하지 않는다. 원문 값·시장의 정규화, 이름 기반 추정, 단축 코드만으로 연결, 중복 행 중 임의 선택은 하지 않는다.

KRX 정책은 `EXACT_IDENTITY_MATCH`인 대응 행에만 호출한다. 식별 연결이 실패하면 KRX 원문에 보통주처럼 보이는 값이 있어도 참고 유형의 근거로 사용하지 않는다. 기존 입력의 모든 KRX 행·실패 사유·미연결 KIS 행과 양쪽 원문 해시는 배치의 `matchingResult`에 그대로 남는다.

행별 결과 필드는 다음과 같다.

| 필드 | 의미 |
| --- | --- |
| `kisClassification` | 해당 KIS 행의 원문·시장·유형·원천 사유·규칙 메타데이터. 항상 존재한다. |
| `identityMatch` | 이 KIS 행과 정확히 연결된 KRX 행의 식별 결과. 정확한 연결이 없으면 null이다. |
| `krxClassification` | 정확히 연결된 KRX 행의 원문·유형·원천 사유·규칙 메타데이터. 연결이 없으면 null이다. |
| `referenceSecurityType` | 이번 보완 규칙으로 얻은 현재 참고 유형. 원천의 `securityType`을 덮어쓰지 않는다. |
| `reasonCode` | 참고 유형을 선택하거나 보류한 사유. 항상 존재한다. |

`krxClassification == null`은 정확한 식별 연결이 없다는 뜻이다. `krxClassification != null`이면서 그 안의 `securityType == null`이면 연결은 정확하지만 KRX 유형 해석을 보류했다는 뜻이다. 둘을 같은 상태로 축약하지 않는다.

## 참고 유형 판단

| 사유 | KIS 해석 | 정확히 연결된 KRX 해석 | 참고 유형 |
| --- | --- | --- | --- |
| `KIS_TYPE_ONLY` | 유형 있음 | 대응 없음 또는 유형 null | KIS 유형 |
| `KRX_TYPE_SUPPLEMENTED` | 유형 null | 유형 있음 | KRX 유형 |
| `SOURCES_AGREE` | 유형 있음 | 같은 유형 | 공통 유형 |
| `TYPE_CONFLICT` | 유형 있음 | 다른 유형 | null |
| `TYPE_UNVERIFIED` | 유형 null | 대응 없음 또는 유형 null | null |

예를 들어 KIS 원문 ETP 공백으로 유형이 null인 행이 KRX 주권·보통주 행과 정확히 연결되면 참고 유형은 `COMMON_STOCK`이다. KIS의 공백·null 유형·`ETP_VALUE_UNVERIFIED` 사유는 그대로 보존한다. 원문 공백을 `0`으로 바꾸는 규칙이 아니다.

KIS의 확인된 ETF 조합과 KRX 보통주 해석이 충돌하면 두 해석을 모두 보존하고 `TYPE_CONFLICT`로 보류한다. 이 사유는 원천 중 어느 쪽이 틀렸는지 단정하지 않는다.

현재 두 원천 정책의 지원 유형 집합은 겹치지 않는다. `SOURCES_AGREE` 분기는 합성 해석 결과와 모의 정책으로 검증했으며, 실제 정책에 보통주·ETF 해석 규칙을 추가한 것이 아니다. 실제 자료의 충돌 0건도 이후 충돌이 불가능하다는 근거가 아니다.

## 결과 객체 검증

행별 생성자는 다음 관계를 검사한다.

- KRX 해석과 식별 연결은 함께 존재하거나 함께 null이어야 한다.
- 연결은 정확한 일치여야 하며 KIS 시장과 양쪽 원문이 대응 식별 결과와 같아야 한다.
- 원천 유형·참고 유형·보완 사유가 위 판단 표와 일치해야 한다.

배치 생성자는 원래 KIS 행마다 결과가 정확히 하나씩 존재하는지, 시장·원문·행 순서가 같은지, 원래 식별 연결과 같거나 동일하게 null인지 검사한다. 누락·추가·순서 변경·원문 변경·연결 근거 제거 또는 임의 추가를 거절한다. 행 결과 목록은 `List.copyOf`로 방어적으로 복사하며, 입력의 중첩된 Map·List는 기존 식별 연결 결과의 불변 계약을 유지한다.

불변 record의 전체 값으로 비교하므로 값이 같은 JSON 복원을 허용한다. 정책 호출 결과는 원래 원문을 유지하지만 생성자 검사는 객체 인스턴스 동일성에 의존하지 않는다. 생성자만으로 출처·유형 규칙의 실제 실행·해시 진위·정보 가용 시점을 인증하지 않는다.

## 관련 테스트

테스트 패키지는 `src/test/java/com/stock/strategy/universe/eligibility/classification/kiskrx/`이며 `result/` 아래에 결과 객체 테스트를 둔다. `support/KisKrxStockTypeResolutionFixture.java`는 기존 KIS 고정폭·KRX JSON Fixture를 재사용한다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.classification.*' --tests 'com.stock.market.stock.master.matching.kiskrx.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **241개가 모두 통과**했다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다.

| 범위 | 테스트 수 |
| --- | --- |
| 새 보완 정책 | 20 |
| 새 행별 결과 | 12 |
| 새 배치 결과 | 10 |
| 기존 KIS 정책·결과 | 66 |
| 기존 KRX 정책·결과 | 83 |
| 기존 식별 연결 정책·결과 | 50 |
| 합계 | 241 |

새 테스트는 다섯 보완 사유, 기존 정책을 통한 보통주·구형/신형우선주 보완, 실제 정책 조합의 충돌, 식별 연결 실패 시 KRX 정책 미호출, 빈 KRX 입력에서도 전체 KIS 행 유지, null 의존성·잘못된 정책 반환값·변조 결과 거절을 확인한다. 원문 공백·소속부·거래 플래그·시각·해시·규칙 메타데이터, 방어적 복사·반복 호출·값 보존 JSON 왕복도 확인한다. 합성 테스트는 원천 인증을 대신하지 않는다.

## 보존 실응답 대조

KIS 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`와 KRX `20261002` 요청분 두 원문을 재사용했다. 원문을 다시 파싱하고 기존 식별 연결 정책을 실행한 결과가 직전 전체 보고서와 값이 같음을 확인한 뒤 보완 정책을 적용했다. **이번 작업의 추가 외부 데이터 API 요청은 0회**다.

KIS 수집 시각은 `2026-10-05T09:32:35.795200200Z`~`2026-10-05T09:32:36.202168500Z`이며 KRX 요청 기준일은 `20261002`다. 식별자가 같아도 같은 시점 상태나 그 당시 정보 가용성이 입증되는 것은 아니다. 두 날짜를 같게 만들거나 `informationAvailableAt`·`AS_OF_VERIFIED`로 변환하지 않는다.

| 현재 참고 유형 | KIS 행 수 |
| --- | --- |
| `COMMON_STOCK` | 2,604 |
| `PREFERRED_STOCK` | 101 |
| `ETF` | 1,159 |
| 유형 null | 539 |
| 전체 | 4,403 |

| 보완 사유 | KIS 행 수 |
| --- | --- |
| `KIS_TYPE_ONLY` | 1,159 |
| `KRX_TYPE_SUPPLEMENTED` | 2,705 |
| `SOURCES_AGREE` | 0 |
| `TYPE_CONFLICT` | 0 |
| `TYPE_UNVERIFIED` | 539 |
| 전체 | 4,403 |

모든 KIS 행이 남았으며 기존 KIS ETF 1,159행·null 유형 3,244행과 원천별 첫 사유 집계는 변하지 않았다. 정확히 연결된 KRX 2,766행의 해석 전체는 기존 KRX 행별 보고서의 원문·유형·사유·규칙 메타데이터와 일치했다. 참고 유형은 KIS 원문 조합과 기존 KRX 증적에서 독립적으로 산출한 기대값에 맞았다. 이 수치는 후보 선정·거래 승인·순성과 개선 건수가 아니다.

실행 전후 기존 보고서 8개의 고정 SHA-256과 그에 연결된 KIS 배치 증적 7개·KRX 관측 증적 15개·KIS 규격 증적 4개의 크기·해시를 대조했다. 기존 KIS·KRX 공개 자료 보존 검사도 수행했으며 원문·이전 관측 보고서는 덮어쓰지 않았다.

## 수동 재현 증적

수동 도구와 전체 결과는 Git 제외 경로 `build/kis-krx-stock-type-resolution-observation-01/`에 보존한다. `VerifyKisKrxTypeResolution.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`이며 운영 빌드에 연결하지 않는다. 정책은 이 경로의 파일을 읽지 않아 증적이 없어도 사용할 수 있다. `gradlew clean`은 이 증적을 삭제할 수 있다.

- 최초 보고서 SHA-256: `00b6b9fb9a6cfdd42ac0594a8898f13045464866b6da730cab350f1bb1fbb229`
- 재현 보고서 SHA-256: `a196578771eeb90a83fab37dfd94f33e152016faa427e8c7af03e093d523d73f`

같은 입력으로 두 번 실행했고 검증 시각 `verifiedAt`만 제외한 전체 JSON 구조가 일치했다. 양쪽을 저장되는 JSON 형식으로 읽어 값·배열 순서를 비교하며 객체 필드 순서는 비교 기준으로 삼지 않는다.

```powershell
.\gradlew.bat -I build/kis-krx-stock-type-resolution-observation-01/observation.init.gradle verifyKisKrxTypeResolution '-PobservationReport=verification-replay.json' --offline --no-daemon
```

보고서가 이미 있으면 덮어쓰기를 거절한다. PowerShell에서는 `-P` 인자 전체를 따옴표로 묶는다. 이번 최초·재현 검증은 모두 성공했다.

## 자격 판단과 운영 연결 제한

KRX 소속부의 SPAC·관리종목·투자주의환기종목 표시와 KIS 거래정지·정리매매 등 원문은 별도로 남는다. 보통주 유형이 확인됐다고 이 조건을 통과하는 것이 아니다. ETF 유형도 ETF 매매 허용을 뜻하지 않는다. 상장 상태·기업행위·과거 자격·시점 가용성과 현재 거래 가능 여부는 이 결과에서 승인하지 않는다.

`StockEligibilityInput`, 운영 후보·백테스트·주문·스케줄에 연결하지 않았다. `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지하고, 이전 관측의 당시 승인 상태도 소급 변경하지 않는다.

기존 원천 정책·파서·식별 연결 코드, DB·스키마·Spring 빈·설정·`.env`·Docker·라이브러리는 변경하지 않았다. Broker 계좌·주문·OpenAI 호출은 없으며 커밋과 Push도 실행하지 않았다.

## 후속 소속부 표시 해석

[KRX 소속부 기반 현재 종목 제한 사유 해석](krx-stock-section-restriction.md)은 이 결과를 그대로 받아 정확히 연결된 KOSDAQ 자료의 SPAC·관리종목·투자주의환기 표시를 별도로 기록한다. 원천 유형·참고 유형·보류 사유는 변경하지 않고, 소속부 미확인과 대상 표시 미관측도 구분한다. 실제 후보 제외·종목 자격·거래 가능 승인으로 연결하지 않는다.

[KIS 거래정지 정리매매 관측 상태 해석](kis-stock-trading-restriction.md)은 같은 입력의 KIS 원문 두 필드에서 `Y`·`N`·미확인을 독립적으로 기록한다. KRX 미연결 행도 보존하며 유형·소속부 해석을 변경하지 않는다. 원문 관측을 현재 거래 허가나 과거 자격으로 바꾸지 않는다.
