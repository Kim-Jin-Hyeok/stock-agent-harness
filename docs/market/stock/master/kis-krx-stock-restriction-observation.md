# KIS KRX 종목 제한 관측 통합

## 목적과 범위

같은 종목 유형 입력에서 만든 [KIS 다섯 제한 관측](kis-stock-trading-restriction.md)과 [KRX 소속부 관측](krx-stock-section-restriction.md)을 하나의 불변 결과에 보존한다. **통합은 두 관측을 함께 보관하는 것이며, 정상 여부나 거래 허가로 합치는 것이 아니다.** 원천별 원문·유형·사유·규칙 버전·근거를 유지하고, 서로 다른 입력의 결과를 섞으면 거절한다.

KIS의 다섯 필드가 모두 N이거나 KRX에 대상 표시가 없더라도 후보를 승인하지 않는다. KRX 미연결·소속부 미확인, KIS 미확인·원천 미제공과 유형 충돌도 기존 상태 그대로 남긴다. KRX 원천 데이터를 새로 조회하거나 운영 필수 출처로 확정하지 않는다.

## 패키지와 호출 계약

기준 패키지는 `com.stock.strategy.universe.eligibility.restriction.kiskrx`다. 원천별 해석은 기존 `restriction.kis`와 `restriction.krx`가 담당하고, 새 정책은 두 결과의 조합만 담당한다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/strategy/universe/eligibility/restriction/kiskrx/KisKrxStockRestrictionObservationPolicy.java` | 동일 입력으로 기존 두 정책을 한 번씩 호출하고 요청 입력이 유지됐는지 검사한다. |
| 같은 경로의 `result/KisKrxStockRestrictionObservationResult.java` | 두 결과와 통합 계약 버전을 보존하며 입력 전체의 값이 같은지 검사한다. |

정책 생성자는 기존 `KisStockTradingRestrictionPolicy`와 `KrxStockSectionRestrictionPolicy`를 받는다. 호출은 `evaluate(KisKrxStockTypeResolutionResult)`이고 반환 타입은 `KisKrxStockRestrictionObservationResult`다. Spring 빈 등록·파일·HTTP·DB 접근 없이 동작한다.

KRX 정책 객체에 의존한다는 사실이 KRX 데이터를 필수로 요구한다는 뜻은 아니다. 입력에 정확한 KRX 연결이 없으면 기존 KRX 정책이 `IDENTITY_MATCH_UNVERIFIED` 결과를 만들고, KIS 관측과 함께 반환한다. KRX 결과 자체를 null로 없애거나 연결되지 않은 다른 KRX 행을 대신 사용하지 않는다.

## 결과와 일관성 검사

| 필드 | 의미 |
| --- | --- |
| `kisObservation` | 기존 KIS 결과 전체. 다섯 상태·유형 입력·규칙 버전·규격 revision을 유지한다. |
| `krxObservation` | 기존 KRX 결과 전체. 소속부 사유·유형 입력·규칙 버전·근거 참조·해시를 유지한다. |
| `observationVersion` | 통합 계약 버전 `KIS_KRX_STOCK_RESTRICTION_OBSERVATION_V1`. 원천별 규칙 버전을 대체하지 않는다. |

결과 생성자는 두 결과의 nonnull, nonblank 버전과 `typeResolution` 전체의 값 동등성을 검사한다. 종목코드만 같아도 원문·시장·식별 연결·유형·사유·유형 규칙 메타데이터가 다르면 거절한다. JSON으로 별도 복원한 동일 값은 허용하므로 객체 참조의 동일성으로 검사하지 않는다.

정책은 두 결과끼리 같다는 검사에 더해, 각각의 입력이 실제 요청 입력과 같은지도 확인한다. 두 하위 정책이 모두 다른 종목의 결과를 반환하면서 서로끼리만 일치하는 경우도 거절한다. 하위 정책의 null 반환이나 예외를 정상·미확인 기본 결과로 보정하지 않는다.

실제 기존 정책을 호출한 결과는 두 결과 모두 원래 입력 인스턴스를 유지한다. 생성자 검사는 조합의 일관성을 확인할 뿐, 원천 진위·관측 신선도·적용 시점이나 하위 정책의 실제 실행을 인증하지 않는다. 수집 시각과 KRX 요청 기준일은 기존 배치 증적을 함께 보존해야 한다.

## 원천별 관측 보존

| 입력 사례 | 통합 결과 |
| --- | --- |
| KIS 다섯 필드 Y, KRX 우량기업부 | 다섯 `Y_OBSERVED`와 `NO_TARGET_RESTRICTION_OBSERVED`를 함께 보존한다. 정상 판정이나 원천 오류를 단정하지 않는다. |
| KIS SPAC N·공백·미정의 값, KRX SPAC 표시 | KIS의 N 또는 미확인과 KRX의 `SPAC_OBSERVED`를 각각 유지한다. |
| 정확한 KRX 연결 없음 | KIS 관측과 `IDENTITY_MATCH_UNVERIFIED`를 유지한다. KIS 행을 버리지 않는다. |
| KOSPI 투자주의환기 필드 없음 | `FIELD_NOT_PROVIDED`를 유지한다. N이나 KRX 소속부 값으로 채우지 않는다. |
| 유형 충돌 또는 유형 미확정 | 기존 참고 유형 null과 유형 사유를 유지한다. 제한 관측으로 유형 충돌을 해소하지 않는다. |

KRX 소속부는 KIS의 다섯 독립 필드와 같은 표현 체계가 아니다. 소속부에 어떤 제한 표시가 없다고 모든 제한의 부재를 뜻하지 않는다. 관측 시점도 다르므로 서로 다른 값만으로 어느 출처가 잘못됐다고 확정하지 않는다.

## 관련 테스트

테스트는 동일한 `src/test/java/.../restriction/kiskrx/` 아래 정책 테스트와 `result/` 아래 결과 테스트에 둔다. 기존 바이트·KRX JSON·유형 해석 Fixture와 실제 정책을 재사용하며 헬퍼는 새 테스트 클래스 안에 둔다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.*' --tests 'com.stock.market.stock.master.matching.kiskrx.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **439개가 모두 통과**했다. 새 정책 19개·결과 객체 17개를 포함한 14개 클래스이며 실패·오류·건너뜀은 0개다. 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 보고서의 클래스 목록과 모든 XML suite 이름을 대조해 긴 클래스명의 축약 XML도 집계했다.

동일 입력의 한 번씩 호출과 결과 객체 보존, 원천 간 다른 관측·미확인·미연결·KOSPI 미제공·유형 충돌·반복 호출의 독립성, null 입력·반환값과 요청 입력 변경 거절을 확인했다. 종목 식별자가 같아도 KIS 원문·KRX 원문·연결 여부·유형 규칙 버전이 다른 결과를 거절하고, 별도 복원한 동일 값과 전체 JSON 왕복은 허용한다.

## 보존 원문 대조

KIS 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`의 MST와 KRX 요청 기준일 `20261002`의 두 원문을 다시 읽었다. 원문 파서 V2 배치 전체는 직전 추출 보고서와 같고, 새 정책의 모든 4,403행은 원래 입력·순서를 유지했다.

각 행의 KIS 결과는 직전 관측 V2 보고서와 전체 JSON 값이 같았다. KRX 결과는 기존 V1 보고서 이후 추가된 KIS 원문 세 필드만 비교값에서 제외한 뒤 전체 JSON 값이 같았다. 소속부 사유·원문·유형·식별 연결·규칙 버전·근거 해시를 변경하지 않았으며 이전 보고서를 직접 수정하지 않았다.

| KRX 관측 사유 | 행 수 |
| --- | --- |
| `SPAC_OBSERVED` | 67 |
| `MANAGEMENT_DESIGNATION_OBSERVED` | 129 |
| `INVESTMENT_CAUTION_OBSERVED` | 40 |
| `NO_TARGET_RESTRICTION_OBSERVED` | 1,573 |
| `SECTION_UNVERIFIED` | 957 |
| `IDENTITY_MATCH_UNVERIFIED` | 1,637 |
| 전체 | 4,403 |

위 집계는 기존 KRX 보고서와 같고 KIS 다섯 상태의 시장별 집계도 [직전 V2 검증](kis-stock-trading-restriction.md)과 같다. 두 원천의 제한 건수를 합쳐 서로 다른 종목 수나 후보 승인 건수로 사용하지 않는다.

실행 전후 원문·규격·기존 보고서와 연결 증적 71개의 SHA-256이 같았다. 새 통합 결과 4,403행의 JSON 왕복과 최초·재현 검증 시각을 제외한 전체 JSON 값·배열 순서도 일치했다. 외부 API 요청·토큰 발급은 각각 0회다.

KIS 수집 시각은 `2026-10-05T09:32:35.795200200Z`~`2026-10-05T09:32:36.202168500Z`이며 KRX 요청 기준일은 `20261002`다. 이를 같은 관측 시점으로 합치거나 과거 정보 가용성을 생성하지 않는다. 보존 자료의 재현 검사를 최신 종목 상태 확인으로 해석하지 않는다.

## 수동 재현 증적

Git 제외 경로 `build/kis-krx-stock-restriction-observation-01/`에 `VerifyKisKrxRestrictionObservation.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 보고서에는 두 관측을 포함한 전체 4,403행, 원천별 수집 시각 또는 요청 기준일과 보존 증적 71개의 해시를 남긴다.

- 최초 보고서 SHA-256: `56f856710055501d4f1da04e7f28b439213fe58bae6a836217d3a53f641daf6e`
- 재현 보고서 SHA-256: `abf1066d044b14c02622476f6676ed801192e7ebaf9252f8ca61967576e147c4`

```powershell
.\gradlew.bat -I build/kis-krx-stock-restriction-observation-01/observation.init.gradle verifyKisKrxRestrictionObservation --offline --no-daemon
.\gradlew.bat -I build/kis-krx-stock-restriction-observation-01/observation.init.gradle verifyKisKrxRestrictionObservation '-PobservationReport=verification-replay.json' --offline --no-daemon
```

최초·재현 검증은 모두 성공했다. 이미 존재하는 두 보고서는 덮어쓰기를 거절하므로 위 명령은 완료된 실행의 기록이며 그대로 반복하면 보호 오류가 난다. PowerShell에서는 `-P` 인자 전체를 따옴표로 묶는다. 검증 도구는 운영 빌드에 연결하지 않았으며 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

기존 KIS·KRX 정책과 원문 파서·식별 연결·유형 해석·`StockEligibilityPolicy`·`StockEligibilityInput`은 변경하지 않았다. 후보·백테스트·주문·스케줄·Risk에 연결하지 않으며 `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. `AS_OF_VERIFIED`·`informationAvailableAt`·상장 상태·거래 허가를 생성하지 않는다.

DB·스키마·새 Spring 빈·라이브러리·설정·`.env`·Docker 변경과 계좌·주문·OpenAI 호출은 없다. 커밋·Push도 실행하지 않았다. 실제 후보 제외에 사용하려면 관측 신선도·시점·제외 기준과 주문 전 통제를 별도로 검증해야 한다.
