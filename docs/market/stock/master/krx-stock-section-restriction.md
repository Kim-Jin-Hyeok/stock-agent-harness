# KRX 소속부 기반 현재 종목 제한 사유 해석

## 목적과 범위

정확히 연결된 KRX 종목기본정보의 소속부에서 **SPAC·관리종목·투자주의환기 표시를 별도로 기록**한다. 기존 [KIS KRX 현재 종목 유형 보완 결과](kis-krx-stock-type-resolution.md)를 입력으로 사용하고 유형·원문·원천별 해석 사유는 변경하지 않는다.

보통주 유형만으로 이 표시가 없는 일반 개별주라고 판단할 수 없다. 이번 정책은 제한 검토에 필요한 표시를 해석하는 단계이며 종목을 실제로 제외하거나 거래 가능 여부를 승인하지 않는다. 소속부 표시가 없다는 결과도 거래정지·정리매매·상장 상태·기업행위·시점 근거를 통과했다는 뜻이 아니다.

## 패키지와 파일

기준 패키지는 `com.stock.strategy.universe.eligibility.restriction.krx`다. 증권 유형 해석과 소속부 표시 해석의 책임을 구분한다. 정책은 Spring 빈이 아닌 무상태 일반 클래스이며 새 인터페이스·설정·자동 실행 기능을 추가하지 않는다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/strategy/universe/eligibility/restriction/krx/KrxStockSectionRestrictionPolicy.java` | 정확한 연결과 시장 범위를 확인한 뒤 소속부를 해석한다. |
| 같은 경로의 `result/KrxStockSectionRestrictionReasonCode.java` | 세 표시·대상 표시 미관측·소속부 미확인·식별 연결 미확인 사유를 구분한다. |
| 같은 경로의 `result/KrxStockSectionRestrictionResult.java` | 기존 유형 결과 전체와 소속부 사유·규칙 버전·근거 메타데이터를 보존한다. |

호출 계약은 `evaluate(KisKrxStockTypeResolutionResult)`다. 원천별 유형 정책이나 식별 연결을 다시 구현하지 않는다. 정확한 연결이 없는 행도 확인 불가 결과를 반환하므로, 전체 입력을 순서대로 평가하면 미연결·미확정 KIS 행을 제거할 필요가 없다. 단일 행 정책이며 전체 모집단의 누락·중복·순서를 새로 검증하는 배치 계층은 추가하지 않는다.

## 판단 규칙

현재 해석 근거는 [보존된 KRX 실응답](../../../strategy/swing/validation/stock-eligibility-krx-source-validation-03.md)의 KOSDAQ 소속부 값이다. KOSPI에 같은 문자열이 새로 나타나도 이 규칙을 자동 적용하지 않는다.

| 사유 | 조건 |
| --- | --- |
| `IDENTITY_MATCH_UNVERIFIED` | 입력 유형 결과에 정확한 식별 연결이 없다. 다른 KRX 행의 표시를 가져오지 않는다. |
| `SECTION_UNVERIFIED` | 정확한 연결이 있지만 KOSDAQ이 아니거나 소속부 값이 아래 해석 범위 밖이다. |
| `SPAC_OBSERVED` | KOSDAQ의 정확한 문자열 `SPAC(소속부없음)`이다. |
| `MANAGEMENT_DESIGNATION_OBSERVED` | KOSDAQ의 정확한 문자열 `관리종목(소속부없음)`이다. |
| `INVESTMENT_CAUTION_OBSERVED` | KOSDAQ의 정확한 문자열 `투자주의환기종목(소속부없음)`이다. |
| `NO_TARGET_RESTRICTION_OBSERVED` | KOSDAQ의 `우량기업부`·`중견기업부`·`기술성장기업부`·`벤처기업부` 중 하나다. |

마지막 사유는 **이번 검사 대상인 세 표시가 관측되지 않았다는 뜻**이다. 제한 없음·투자 적격·매수 허가로 사용하지 않는다. `외국기업(소속부없음)`은 관측된 값이지만 이번 해석 범위에 넣지 않아 `SECTION_UNVERIFIED`로 남긴다.

빈 문자열·공백·새 값·표기 변형도 미확인이다. trim·대소문자 변경·부분 문자열·종목 이름 기반 추정을 하지 않는다. KOSPI에서 소속부가 전부 빈 문자열이었다는 사실을 정상 소속부로 채우는 근거로 사용하지 않는다.

유형이 null이어도 정확히 연결된 KRX 소속부의 표시를 기록할 수 있다. 반대로 참고 유형이 ETF로 확인됐어도 KRX 연결이 없으면 이 정책의 결과는 식별 연결 미확인이다. 제한 표시 해석으로 기존 유형 충돌·보류를 해소하거나 덮어쓰지 않는다.

## 결과와 근거 메타데이터

| 필드 | 의미 |
| --- | --- |
| `typeResolution` | 전달받은 기존 불변 유형 결과 전체. KIS·KRX 원문과 정확한 식별 연결·유형·원천 사유·유형 규칙 메타데이터를 유지한다. |
| `reasonCode` | 이번 소속부 규칙의 사유. 유형 사유와 별도로 기록한다. |
| `restrictionVersion` | 소속부 해석 규칙 버전 `KRX_STOCK_SECTION_CURRENT_RESTRICTION_V1`. |
| `sourceReference` | 규칙을 검토한 고정 관측 근거 `build/stock-eligibility-krx-source-validation-03/final-verification.json`. |
| `sourceSha256` | 위 근거의 고정 SHA-256 `df0658234036f57e88f297ef3cdf13eca804ea07ff0b944474ba3151a6448711`. |

정책 호출은 원래 `typeResolution` 인스턴스를 그대로 반환 결과에 넣는다. 모든 값이 불변 record이며 값이 같은 JSON 복원도 허용한다. 결과 생성자는 필수 입력·사유, nonblank 버전·참조와 소문자 64자리 해시, 연결 유무와 사유의 일치, 해석 성공 사유의 KOSDAQ 범위를 검사한다.

**생성자 검사는 실제 정책 실행·원천 진위나 소속부 문자열별 사유의 정확성을 인증하지 않는다.** 호출자는 수집 증적·파싱·배치 식별 연결·유형 보완 정책을 통해 검증한 결과를 전달해야 한다. 개별 행 결과만으로 전체 입력의 중복 부재나 실제 요청 시장을 다시 인증하지 않는다.

근거 메타데이터는 규칙을 검토한 고정 자료이며 전달받은 모든 행의 실제 출처·적용일을 증명하는 값이 아니다. 정책은 이 파일을 읽지 않으므로 운영 환경에 `build/` 증적이 없어도 파일 접근이나 빈 생성 실패는 발생하지 않는다. 실제 배치의 원문 해시·수집 시각·KRX 요청 기준일은 원래 전체 배치와 수집 증적을 별도로 유지해야 한다.

## 관련 테스트

테스트는 `src/test/java/com/stock/strategy/universe/eligibility/restriction/krx/`와 그 아래 `result/`에 둔다. 기존 `KisKrxStockTypeResolutionFixture`와 KIS·KRX 원문 Fixture를 재사용하고 별도 테스트 지원 계층을 만들지 않는다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.krx.*' --tests 'com.stock.strategy.universe.eligibility.classification.*' --tests 'com.stock.market.stock.master.matching.kiskrx.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **292개가 모두 통과**했다. 새 정책 36개·결과 객체 15개와 기존 유형 해석·보완·식별 연결 241개다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다.

세 표시와 일반 소속부 네 값의 정확한 해석, 공백·미검토 값·대소문자·패딩·부분 문자열의 보류, KOSPI로 규칙 확대 금지, 실패·중복 식별 연결의 근거 사용 차단을 확인했다. 미연결 KIS 행과 기존 ETF 유형 유지, 유형 미확정 상태의 소속부 표시 보존, 이름 기반 추정 금지, 원문·규칙 메타데이터 유지·반복 호출·null 입력 거절도 확인했다.

결과 객체 테스트는 필수 필드·근거 형식, 연결 유무·시장 범위의 일관성과 원문·null을 유지하는 JSON 왕복을 확인한다. 합성 테스트의 성공은 실제 원천 인증이나 투자 성과 검증을 대신하지 않는다.

## 보존 실응답 대조

KIS 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`와 KRX 요청 기준일 `20261002`의 두 원문을 다시 파싱했다. 기존 식별 연결·유형 보완 정책을 실행한 전체 결과가 직전 보고서와 값이 같음을 확인한 뒤 새 소속부 정책을 적용했다. 추가 외부 데이터 API 요청은 0회다.

| 소속부 해석 사유 | 전체 KIS 행 수 |
| --- | --- |
| `SPAC_OBSERVED` | 67 |
| `MANAGEMENT_DESIGNATION_OBSERVED` | 129 |
| `INVESTMENT_CAUTION_OBSERVED` | 40 |
| `NO_TARGET_RESTRICTION_OBSERVED` | 1,573 |
| `SECTION_UNVERIFIED` | 957 |
| `IDENTITY_MATCH_UNVERIFIED` | 1,637 |
| 전체 | 4,403 |

소속부 미확인 957행은 정확히 연결된 KOSPI 942행과 KOSDAQ 외국기업 소속부 15행이다. 식별 연결 미확인 1,637행은 기존 미연결 KIS 자료이며 정상·상장폐지·거래 불가로 자동 변환하지 않았다. 모든 KIS 행과 원래 시장·원문 순서를 유지했다.

세 표시가 있는 행은 236개이며, 그중 기존 보완 결과의 `COMMON_STOCK`은 SPAC 67개·관리종목 127개·투자주의환기종목 40개, 합계 **234개**다. 이 표시를 기록해도 기존 보통주 2,604개·우선주 101개·ETF 1,159개·미확정 539개의 유형 결과는 변경하지 않는다. 실제 후보 제외·승인이나 순성과 개선 건수가 아니다.

독립적으로 보존 KRX 원문의 소속부 문자열과 시장에서 기대 사유·건수를 산출하고 Java 정책의 각 행과 대조했다. 결과의 원래 유형 객체·근거 메타데이터도 확인했다. 실행 전후 직전 유형 보완 보고서 두 개와 그에 연결된 이전 보고서 8개, KIS 배치 증적 7개·KRX 관측 증적 15개·KIS 규격 증적 4개의 크기 또는 고정 해시를 확인했다. 별도로 기존 KIS·KRX 공개 자료의 보존 검사도 수행했다. 이전 보고서·원문을 덮어쓰지 않았다.

KIS 수집 시각 `2026-10-05T09:32:35.795200200Z`~`2026-10-05T09:32:36.202168500Z`와 KRX 요청 기준일 `20261002`는 구분한다. 정확한 식별 연결이나 이번 재해석 시각으로 같은 시점 상태·과거 정보 가용성을 만들지 않는다. `AS_OF_VERIFIED`나 `informationAvailableAt`을 채우지 않는다.

## 수동 재현 증적

Git 제외 경로 `build/krx-stock-section-restriction-observation-01/`에 `VerifyKrxSectionRestriction.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 두 보고서는 모든 KIS 행의 결과와 원래 유형 행을 보존하고 전체 입력 배치는 직전 유형 보고서의 경로·고정 해시로 연결한다. 원문 해시·수집 시각과 실패·미연결 진단 전체를 재현하려면 연결된 원래 보고서와 원문도 함께 보존해야 한다.

- 최초 보고서 SHA-256: `773206e3286042ef780350d1239035445de84879a3721164b106ad0b80d092da`
- 재현 보고서 SHA-256: `0f1601a153b600e3d1c4c997089e89f5483e0cdc955de1da244171927216fc16`

같은 자료로 두 번 실행했고 `verifiedAt`만 제외한 전체 JSON 구조가 같았다. 양쪽을 저장되는 JSON 형식으로 읽어 값과 배열 순서를 비교하며 객체 필드 순서는 비교 기준으로 삼지 않는다. 최초·재현 실행은 모두 성공했다.

```powershell
.\gradlew.bat -I build/krx-stock-section-restriction-observation-01/observation.init.gradle verifyKrxSectionRestriction '-PobservationReport=verification-replay.json' --offline --no-daemon
```

이미 있는 보고서는 덮어쓰지 않는다. PowerShell의 `-P` 인자 전체를 따옴표로 묶는다. 수동 도구는 운영 빌드 설정에 연결하지 않았으며 일반 Git 커밋 대상이 아니다. `gradlew clean`으로 증적이 삭제될 수 있다.

## 변경하지 않은 영역

기존 유형·식별 연결 정책·원문 파서와 `StockEligibilityPolicy`·`StockEligibilityInput`은 변경하지 않았다. KIS 거래정지·정리매매 해석, 상장 상태·기업행위·과거 모집단·실제 거래 가능 여부의 검증은 추가하지 않았다.

운영 후보·백테스트·주문·스케줄·Risk에 연결하지 않는다. `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`와 이전 관측의 당시 승인 상태를 유지한다. DB·스키마·Spring 빈·라이브러리·설정·`.env`·Docker 변경, Broker·계좌·주문·OpenAI 호출은 없으며 커밋과 Push도 실행하지 않았다.
