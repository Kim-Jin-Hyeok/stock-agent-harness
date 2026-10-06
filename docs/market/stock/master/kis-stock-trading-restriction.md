# KIS 거래정지 정리매매 관측 상태 해석

## 목적과 범위

KIS 마스터의 거래정지·정리매매 필드에서 **정확한 `Y`·`N` 관측과 미확인 값을 각각 기록**한다. [KIS KRX 현재 종목 유형 보완 결과](kis-krx-stock-type-resolution.md)를 입력으로 사용하며, 원천 유형·참고 유형·원문·식별 연결·기존 사유를 바꾸지 않는다.

두 상태를 단일 사유로 축약하지 않는다. 예를 들어 거래정지 `Y`와 정리매매 `Y`가 함께 나타나면 두 관측을 모두 남기고, 한 필드가 미확인이어도 다른 필드의 확인된 관측을 유지한다. 이 결과는 실제 거래 허가, 종목 자격, 상장 상태나 과거 모집단을 승인하지 않는다.

## 패키지와 호출 계약

기준 패키지는 `com.stock.strategy.universe.eligibility.restriction.kis`다. 기존 `restriction.krx`의 소속부 해석과 원천별 책임을 구분한다. 정책은 Spring 빈이 아닌 무상태 일반 클래스이며 파일·HTTP·DB 접근 없이 동작한다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/strategy/universe/eligibility/restriction/kis/KisStockTradingRestrictionPolicy.java` | KIS 원문 두 필드를 독립적으로 해석한다. |
| 같은 경로의 `result/KisStockTradingFlagStatus.java` | `Y_OBSERVED`·`N_OBSERVED`·`VALUE_UNVERIFIED`를 구분한다. |
| 같은 경로의 `result/KisStockTradingRestrictionResult.java` | 기존 유형 결과 전체·두 관측 상태·규칙 버전·근거 revision을 보존한다. |

호출은 `evaluate(KisKrxStockTypeResolutionResult)`이고 반환 타입은 `KisStockTradingRestrictionResult`다. KIS 원문은 입력에 항상 있으므로 KRX 식별 연결이 없어도 평가한다. KRX 연결이나 유형 충돌·미확정을 해소하려는 정책이 아니다.

단일 행 정책으로서 입력을 제거하거나 모집단을 새로 구성하지 않는다. 전체 결과를 만들 때는 기존 유형 배치의 모든 행을 원래 순서로 전달한다. 전체 입력의 누락·중복·순서 검사는 기존 배치 계약의 책임이며 별도 배치 계층은 추가하지 않았다.

## 관측 규칙과 의미

`rawSuspension()`은 거래정지 필드, `rawLiquidation()`은 정리매매 필드다. 각 필드에 동일한 다음 규칙을 적용한다.

| 정확한 원문 값 | 관측 상태 | 의미 |
| --- | --- | --- |
| `Y` | `Y_OBSERVED` | 해당 필드에서 대문자 `Y`를 읽었다. |
| `N` | `N_OBSERVED` | 해당 필드에서 대문자 `N`을 읽었다. |
| 그 밖의 값 | `VALUE_UNVERIFIED` | 이번 규칙으로 해석하지 않는다. 공백·소문자·새 코드도 포함한다. |

trim·대소문자 변경·boolean 기본값·종목명 추정을 사용하지 않는다. 원문 한 칸 공백을 `N`으로 보정하지 않으며, 두 상태에 우선순위를 주지 않는다. 현재 원문 record는 필드 폭을 한 글자로 제한하고, 바이트 파서는 제어문자 등 구조적으로 잘못된 입력을 먼저 거절한다. 그런 입력을 관측 미확인으로 흡수하는 정책은 아니다.

`N_OBSERVED`는 정상 종목·제한 없음·투자 적격을 뜻하지 않는다. `Y_OBSERVED`도 보존 자료의 문자 관측이므로 실시간 상태나 효력 발생 시각을 인증하지 않는다. 거래정지 관측을 상장폐지·`NOT_LISTED`로 바꾸지 않는다. 유형이 ETF여도 ETF 매매 허용을 뜻하지 않는다.

검토한 KIS 공식 저장소의 고정 revision은 `277ec0eb7a9b7f63b6807829286c80f36649dad2`다. 보존한 `kospi-layout.h`와 `kosdaq-layout.h`는 `trht_yn[1]`을 거래정지 여부, `sltr_yn[1]`을 정리매매 여부로 설명한다. 이 필드명·주석만으로 전체 코드 값이나 현재·과거 거래 가능 상태가 인증되는 것은 아니다. 따라서 이번 상태는 문자 관측으로 한정한다. 규격 증적은 [KIS 유형 해석 문서](stock-master-type-classification.md)와 같은 고정 자료를 사용한다.

현재 보존한 LF 레이아웃의 0부터 시작하는 바이트 위치는 KOSPI 121·122, KOSDAQ 116·117이다. 정책이 바이트 위치를 다시 계산하지 않고 기존 원문 파서의 필드를 사용한다. 관측 레이아웃을 모든 과거·미래 파일의 보편 규격으로 확대하지 않는다.

## 결과와 일관성 검사

| 필드 | 의미 |
| --- | --- |
| `typeResolution` | 전달받은 기존 불변 유형 결과 전체. 양쪽 원문·연결·유형·유형 사유·원천 규칙 메타데이터를 유지한다. |
| `suspensionStatus` | 거래정지 필드의 독립 관측 상태. |
| `liquidationStatus` | 정리매매 필드의 독립 관측 상태. |
| `restrictionVersion` | `KIS_STOCK_TRADING_FLAG_OBSERVATION_V1`. |
| `sourceRevision` | 위에서 검토한 규격의 고정 Git revision. |

정책은 원래 `typeResolution` 인스턴스를 결과에 넣는다. 결과 생성자는 필수 입력·두 상태, nonblank 버전, 소문자 40자리 revision 형식을 확인한다. 각 상태가 자기 원문 필드와 일치하는지도 검사한다. 예를 들어 원문이 `Y`인데 `N_OBSERVED`나 `VALUE_UNVERIFIED`로 생성하면 거절한다. 다른 필드의 상태가 맞아도 이 불일치를 허용하지 않는다.

생성자 일관성 검사는 실제 정책 실행·원천 진위·현재 거래 상태를 인증하지 않는다. 고정 revision도 해당 입력 파일의 출처나 적용일을 대신하지 않는다. 실제 배치의 원문 해시·수집 시각·KRX 요청 기준일은 기존 전체 배치와 수집 증적을 함께 보존해야 한다. 정책은 근거 파일을 읽지 않아 `build/` 증적이 없는 환경에서도 사용할 수 있다.

## 관련 테스트

테스트는 `src/test/java/com/stock/strategy/universe/eligibility/restriction/kis/`의 `KisStockTradingRestrictionPolicyTest`와 그 아래 `result/KisStockTradingRestrictionResultTest`에 둔다. 기존 KIS 바이트 Fixture와 `KisKrxStockTypeResolutionFixture`를 재사용하며 별도 공용 헬퍼를 추가하지 않았다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.*' --tests 'com.stock.market.stock.master.matching.kiskrx.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **346개가 모두 통과**했다. 새 정책 35개·결과 객체 19개, 기존 KRX 소속부 해석 51개·유형 해석과 보완 및 식별 연결 241개다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다.

양 시장의 `Y`·`N`·공백 9조합, 소문자·새 코드 보류, 한 필드 미확인일 때 다른 관측 보존, 두 필드 동시 `Y`, KRX 미연결·유형 미확정·충돌 상태의 평가를 확인한다. 원문·날짜·유형·null·규칙 메타데이터 보존, 반복 호출의 독립성, 필수 필드·근거 형식 검사, 원문과 상태의 불일치 거절, JSON 값 보존 왕복도 확인한다. 합성 테스트의 성공을 원천 인증이나 투자 성과로 해석하지 않는다.

## 보존 실응답 대조

KIS 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`와 KRX 요청 기준일 `20261002`의 원문을 다시 파싱했다. 기존 식별 연결·유형 보완 정책의 전체 결과가 직전 보고서와 값이 같음을 확인한 뒤 새 정책을 실행했다. 별도로 보존 MST 바이트에서 플래그를 직접 읽고 각 행의 두 상태·원문·순서를 대조했다. 추가 외부 데이터 API 요청은 0회다.

| 필드와 시장 | `Y_OBSERVED` | `N_OBSERVED` | `VALUE_UNVERIFIED` | 전체 |
| --- | --- | --- | --- | --- |
| 거래정지 KOSPI | 41 | 2,537 | 0 | 2,578 |
| 거래정지 KOSDAQ | 85 | 1,740 | 0 | 1,825 |
| 거래정지 합계 | 126 | 4,277 | 0 | 4,403 |
| 정리매매 KOSPI | 3 | 2,575 | 0 | 2,578 |
| 정리매매 KOSDAQ | 7 | 1,818 | 0 | 1,825 |
| 정리매매 합계 | 10 | 4,393 | 0 | 4,403 |

| 거래정지 / 정리매매 원문 | KOSPI | KOSDAQ | 합계 |
| --- | --- | --- | --- |
| `Y/Y` | 1 | 2 | 3 |
| `Y/N` | 40 | 83 | 123 |
| `N/Y` | 2 | 5 | 7 |
| `N/N` | 2,535 | 1,735 | 4,270 |
| 전체 | 2,578 | 1,825 | 4,403 |

두 필드의 `Y` 건수는 3행이 겹치므로 126+10을 서로 다른 종목 수로 사용하지 않는다. 이번 자료에서 미확인이 0행이라는 사실은 이후 파일에도 새 코드가 없을 것이라는 근거가 아니다. `N/N` 4,270행도 다른 제한이나 데이터·시점 검증을 통과한 후보 수가 아니다.

모든 KIS 행을 KOSPI→KOSDAQ, 시장 안의 원문 순서로 유지했다. 기존 보통주 2,604개·우선주 101개·ETF 1,159개·미확정 539개와 미연결 KIS 1,637행을 변경하지 않았다. [KRX 소속부 관측 결과](krx-stock-section-restriction.md)도 수정하거나 두 상태로 덮어쓰지 않았다.

실행 전후 유형 보완 보고서 2개·소속부 보고서 2개와 유형 보고서에 연결된 이전 보고서 8개, KIS 배치 증적 7개·KRX 관측 증적 15개·KIS 규격 증적 4개의 고정 해시 또는 크기를 확인했다. 기존 공개 자료의 보존 검사도 수행했다. 이전 원문·보고서는 덮어쓰지 않았다.

KIS 수집 시각은 `2026-10-05T09:32:35.795200200Z`~`2026-10-05T09:32:36.202168500Z`, KRX 요청 기준일은 `20261002`다. 정확한 식별 연결과 이번 검증 시각으로 같은 시점 상태나 당시 정보 가용성을 만들지 않는다. `AS_OF_VERIFIED`·`informationAvailableAt`을 채우지 않는다.

## 수동 재현 증적

Git 제외 경로 `build/kis-stock-trading-restriction-observation-01/`에 `VerifyKisTradingRestriction.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 두 보고서는 4,403행의 결과와 각 행의 원래 유형 입력을 보존한다. 전체 유형 배치와 소속부 보고서는 고정 경로·SHA-256으로 연결하므로 연결된 원문·보고서도 함께 보존해야 한다.

- 최초 보고서 SHA-256: `c77af75b100471fb5e9f2dad5637ca78e4e168b7a4cb2db8ec4fdcbff5763eee`
- 재현 보고서 SHA-256: `85212d4c8958d3a4d9f2e1cf8adb61c0b2cd22601234be6ead386cd404d3d43f`

같은 자료로 두 번 실행했고 `verifiedAt`만 제외한 전체 JSON 구조가 같았다. 저장되는 JSON 형식으로 양쪽을 읽어 값·배열 순서를 비교하며 객체 필드 순서는 비교 기준으로 삼지 않는다. 최초·재현 검증은 모두 성공했다.

```powershell
.\gradlew.bat -I build/kis-stock-trading-restriction-observation-01/observation.init.gradle verifyKisTradingRestriction '-PobservationReport=verification-replay.json' --offline --no-daemon
```

이미 있는 보고서는 덮어쓰기를 거절한다. PowerShell에서는 `-P` 인자 전체를 따옴표로 묶는다. 이 도구는 운영 빌드에 연결하지 않았으며 일반 Git 커밋 대상이 아니다. `gradlew clean`으로 증적이 삭제될 수 있다.

## 운영 연결 제한

기존 파서·식별 연결·유형 정책·KRX 소속부 정책, `StockEligibilityPolicy`·`StockEligibilityInput`은 변경하지 않았다. 후보·백테스트·주문·스케줄·Risk에 연결하지 않으며 `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. 이전 관측의 당시 승인 상태도 소급 변경하지 않는다.

DB·스키마·새 Spring 빈·인터페이스·라이브러리·설정·`.env`·Docker 변경, Broker 계좌·주문·OpenAI 호출은 없다. 커밋과 Push도 실행하지 않았다. 실제 주문에 사용하려면 관측 신선도·시점·다른 제한과 실행 통제를 별도로 검증해야 한다.

## KIS 단독 원천 확인

후속 [KIS 종목 자격 대체 원천 검증](validation/kis-stock-eligibility-source-validation-01.md)은 KRX를 운영 필수 출처로 확정하기 전에 KIS 주식기본조회 코드 정의와 마스터의 SPAC·관리종목·투자주의환기 필드를 확인한다. 주식기본조회는 공식 규격상 모의투자 미지원이라 실제 응답 확인은 별도 실전 조회 자격 증명 준비가 필요하다. 기존 관측 입력과 정책을 변경하거나 두 출처의 상태를 합치지 않으며 운영 후보·과거 자격 승인은 보류한다.

별도 [실전 조회 실측](validation/kis-stock-basic-info-observation-01.md)에서는 고정 사례 10건의 거래정지·관리종목을 원문과 대조했다. SPAC이 보통주 코드로 반환되는 사례와 정리매매 종목의 주식종류 빈 값을 확인했으므로 주식기본조회만으로 제한 정보를 전부 대체하지 않는다. 이 정책이나 기존 모의 설정을 변경한 결과는 아니다.
