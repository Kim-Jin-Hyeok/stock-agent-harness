# KIS 제한·신선도 통합 사전 점검

## 목적과 적용 범위

종합 제한 점검과 관측 신선도를 함께 확인해, 해당 평가시각의 사전 점검을 통과했는지 판정한다. [저장된 종합 분석](kis-stock-restriction-stored-analysis.md)에는 제외 신호가 없어도 입력이 만료됐을 수 있다. 제한 상태만 보고 진행하거나 `FRESH`만 보고 제외 신호를 무시하지 않도록 두 조건을 하나의 정책으로 고정한다.

순수 정책의 입력은 기존 `KisStockRestrictionFreshnessResult` 한 개다. 그 안의 전체 종합 분석·보존 응답·마스터·경보·제한 사유와 평가시각·유효기간·신선도 사유를 그대로 보존한다. 정책 자체는 새 조회·분석 재실행·시각 추론·사유 번역을 하지 않는다. 저장 관측에서 이 결과를 준비하는 연결 서비스는 아래 별도 책임으로 구분한다.

**`CLEAR`는 제한·신선도의 사전 점검 통과이지 투자 적격·종목 유형 허용·상장 상태·실시간 거래 가능 여부·유동성·전략 성과·Risk 통과나 주문 승인이 아니다.** 이 정책은 수익 개선을 입증하는 전략이 아니라 오래된 입력과 제한 신호를 함께 차단하기 위한 준비다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/strategy/universe/eligibility/restriction/kis/precheck/`다.

| 파일 | 책임 |
| --- | --- |
| `KisStockRestrictionPrecheckPolicy.java` | 전체 신선도 결과를 받아 사전 점검 결과를 반환하는 순수 Java 정책 |
| `result/KisStockRestrictionPrecheckStatus.java` | `CLEAR`·`BLOCKED`와 두 입력 상태의 단일 판정 규칙 |
| `result/KisStockRestrictionPrecheckResult.java` | 전체 입력·판정·정책 버전 보존과 결과 정합성 검사 |

정책 버전은 `KIS_STOCK_RESTRICTION_PRECHECK_V1`이다. 테스트는 같은 `src/test/java/` 패키지에 두고 `support/KisStockRestrictionPrecheckFixture.java`에서 기존 분석·신선도 합성 헬퍼를 재사용한다. 실제 정책으로 입력 결과를 만들며 상태만 임의로 지정한 mock 결과로 판정 행렬을 검증하지 않는다.

## 판정 규칙

| 종합 제한 상태 | `FRESH` | `EXPIRED` | `TIME_UNVERIFIED` |
| --- | --- | --- | --- |
| `NO_EXCLUSION_SIGNAL_OBSERVED` | `CLEAR` | `BLOCKED` | `BLOCKED` |
| `REVIEW_REQUIRED` | `BLOCKED` | `BLOCKED` | `BLOCKED` |
| `EXCLUSION_SIGNAL_OBSERVED` | `BLOCKED` | `BLOCKED` | `BLOCKED` |

검토 필요는 제외 사실을 확정한 것과 다르지만 사전 점검은 통과하지 못한다. 만료와 시각 미확인도 서로 다른 진단이며 `BLOCKED`라는 공통 진행 차단 상태 안에서 원래 사유로 구분한다. null 입력·상태는 기본 통과나 빈 결과로 대체하지 않고 예외로 거절한다.

상태 규칙은 `KisStockRestrictionPrecheckStatus.fromInputs` 한 곳에 있다. Policy가 이를 사용하고 Result 생성자도 같은 규칙으로 입력과 판정의 일치를 확인한다. `CLEAR`여야 하는 입력에 `BLOCKED`를 붙이는 경우도 거절하므로, 별도 운영 중단이나 Risk 거절을 이 결과의 상태 변경으로 표현하지 않는다.

## 결과와 입력 보존

결과 record의 필드는 다음과 같다.

| 필드 | 의미 |
| --- | --- |
| `freshnessResult` | 요청 조건과 전체 종합 분석을 포함한 기존 신선도 결과 |
| `status` | 두 입력 상태에서 계산한 사전 점검 판정 |
| `precheckVersion` | 지원하는 사전 점검 정책 버전 |

예를 들어 시장경보가 확인되고 마스터·기본정보도 모두 만료됐다면 결과는 `BLOCKED`다. 동시에 종합 제한 사유 `MARKET_WARNING_INVESTMENT_WARNING_OBSERVED`와 신선도 사유 `MASTER_OBSERVATION_EXPIRED`·`BASIC_INFO_OBSERVATION_EXPIRED`가 모두 원래 입력 안에 남는다. 첫 실패만 남기거나 새로운 공통 reason enum으로 중복 변환하지 않는다.

Result 생성자는 필수 입력·상태, 지원 버전과 입력에서 계산한 판정의 일치를 검사한다. JSON 복원도 같은 생성자를 거치고 중첩된 기존 결과의 검증을 유지한다. 내부적으로 일관된 원문·입력·판정을 함께 바꾼 자료의 진위를 인증하거나 실제 DB 관측과의 연결을 증명하는 서명·원천 검증 기능은 아니다.

기존 입력은 불변 결과 계약이며 그대로 참조한다. 사유 목록은 변경할 수 없고 보존 응답 바이트는 기존 방어적 복사 규칙을 유지한다. `toString()`에는 관측 ID·요청 종목·평가시각·두 상태와 사유·판정·정책 버전만 남기고 응답 본문·API 메시지·종목 이름·전체 마스터를 출력하지 않는다. JSON은 전체 증거를 포함하므로 요약 로그처럼 공개 출력하지 않는다.

## 시점과 운영 경계

사전 점검은 입력의 `evaluatedAt`에 대해서만 유효하다. 현재 시각을 내부에서 읽거나 `recordedAt`으로 관측 나이를 줄이지 않는다. 이전 `CLEAR`를 나중의 실행에 최신 판정처럼 재사용하면 안 되며, 후속 운영 연결에서 새로운 평가 조건으로 신선도를 먼저 계산해야 한다.

[실환경 검증 01](validation/kis-stock-restriction-freshness-observation-01.md)의 보존 입력은 제외 신호 없음·신선도 만료이므로 이 규칙상 `BLOCKED`다. 이번 작업에서는 해당 MySQL 입력을 재조회하거나 실환경 점검을 다시 실행하지 않았다. 코드 검증에 사용한 입력은 합성 자료다.

기존 `StockEligibilityPolicy`의 과거 기준일·정보 가용성 계약은 변경하지 않는다. 현재 관측을 `AS_OF_VERIFIED`로 승격하거나 과거 후보 선정에 소급하지 않는다. `BLOCKED`는 이번 입력으로의 사전 점검 차단이지 종목을 영구 제외하거나 기존 보유 종목을 강제 매도한다는 뜻도 아니다. 보유 종목의 매도·주문 취소·정합성 조회 허용 여부를 이 상태 하나로 결정하지 않는다.

Policy에 `@Component`·`@Service`를 붙이지 않고 정상 서버의 자동 빈 등록을 추가하지 않았다. 정책 구현 당시 [수동 분석 실행기](kis-stock-basic-info-manual-analysis.md)는 기존 종합·신선도 진단까지만 호출했다. 이후 수동 연결은 아래 별도 범위로 구분한다. Harness·후보 평가·Scheduler·Risk Guard·주문에는 연결하지 않았다. Controller·테이블·외부 API·자동 재수집·재시도도 없다.

## 저장 관측 연결 서비스

`KisStockRestrictionPrecheckService`는 저장 관측의 종합 분석 → 신선도 계산 → 사전 점검을 한 호출로 연결한다. 기존 정책을 바꾸거나 판정 규칙을 서비스에 복제하지 않는다. 새 클래스 경로는 다음과 같다.

```text
src/main/java/com/stock/market/stock/basicinfo/observation/analysis/restriction/precheck/KisStockRestrictionPrecheckService.java
```

생성자는 기존 `KisStockRestrictionAnalysisService`, `KisStockRestrictionFreshnessPolicy`, `KisStockRestrictionPrecheckPolicy`를 받는다. 생성만으로 조회하거나 판단하지 않으며 자동 빈 등록 어노테이션도 없다. 기존 분석 서비스의 생성자·호출·반환 계약은 변경하지 않았다.

`precheck(observationId, masterBatch, marketWarningObservation, freshnessRequest)`의 입력과 반환은 다음과 같다.

| 항목 | 계약 |
| --- | --- |
| `observationId` | 필수인 양의 `Long`. 지정한 저장 관측만 읽으며 최신 관측이나 같은 종목의 다른 성공 응답으로 대체하지 않는다. |
| `masterBatch` | 준비된 전체 `StockMasterBatchParseResult`. 서비스는 파일을 다운로드·파싱하거나 배치를 자동 선택하지 않는다. |
| `marketWarningObservation` | 준비된 전체 경보 관측. 지정한 경보를 보존하며 다른 시장의 관측으로 자동 교체하지 않는다. |
| `freshnessRequest` | 필수인 기존 `KisStockRestrictionFreshnessRequest`. 평가시각·두 유효기간은 호출자가 명시한다. |
| 반환 | 기존 `KisStockRestrictionPrecheckResult`. 새 DTO·상태·사유 enum은 추가하지 않는다. |

서비스의 실행 순서는 다음과 같다.

1. 필수 입력과 관측 ID를 검증한다. 잘못된 입력이면 의존성을 호출하지 않는다.
2. 기존 종합 분석 서비스를 한 번 호출한다. 내부의 기존 기본 분석 서비스·Store를 통해 관측을 한 번 조회한다.
3. 반환 관측 ID·전체 마스터·전체 경보가 요청과 같은지 확인한다.
4. 명시한 평가 조건과 전체 분석을 신선도 정책에 한 번 전달하고 반환 요청·전체 분석의 일치를 확인한다.
5. 전체 신선도 결과를 사전 점검 정책에 한 번 전달하고 반환 입력의 일치를 확인한 뒤 결과를 반환한다.

각 단계의 null 반환이나 다른 입력의 결과는 다음 단계 전에 예외로 거절한다. 관측 없음·DB 오류·파싱 오류·무결성 오류·정책 예외는 원래 실패를 전파하고 자동 재시도·기본 통과·빈 결과로 대체하지 않는다. 분석·신선도·사전 점검의 상태와 사유는 기존 계약을 그대로 사용한다.

`BLOCKED`는 실행 실패와 다르다. 사용자가 유효한 다른 시장의 경보를 명시한 경우에는 종합 점검의 `REVIEW_REQUIRED`·`MARKET_WARNING_MARKET_NOT_MATCHED`, 신선도의 `TIME_UNVERIFIED`·`MASTER_OBSERVATION_SOURCE_UNVERIFIED`를 보존한 정상 `BLOCKED` 결과를 반환한다. 반대로 의존성이 요청과 다른 경보를 반환하면 연결 오류로 거절한다.

서비스는 분석 결과를 재사용해 신선도와 사전 점검을 수행한다. 두 번째 관측 조회·별도 종목 조회·현재 시각 읽기·DB 저장·캐시·전체 트랜잭션 추가는 없다. 재호출하면 명시한 ID를 다시 조회해 전달된 평가 조건으로 판단하며 이전 `CLEAR`를 재사용하지 않는다. 읽기 전용 Store의 기존 트랜잭션 계약을 유지한다.

서비스 구현 당시에는 수동 Runner·정상 서버·Harness·후보 선정에 연결하지 않았다. 이후 수동 Runner 연결은 아래 별도 범위로 구분한다. MySQL·Docker·`.env`·스키마 변경은 없다. 과거 자격을 생성하거나 `AS_OF_VERIFIED`로 승격하지 않으며 매도·주문 취소·계좌 정합성 허용 여부를 결정하는 전역 통제로 사용하지 않는다.

## 정책 구현 검증 결과

2026-10-07 관련 **8개 클래스·172개 테스트**가 실패·오류·건너뜀 없이 통과했다. 신규 Policy 15개·Result 24개, 기존 신선도 81개·종합 제한 52개를 합한 수다. 각 상태 조합과 JSON 왕복 검증은 두 시장 모두 실행하며, 테스트 수와 내부 조합 검증 횟수를 혼동하지 않는다.

- 제한 상태 3개 × 신선도 상태 3개에서 유일한 `CLEAR` 조합과 나머지 차단을 확인한다.
- 제외 신호와 두 만료 사유의 동시 보존, 신선한 식별 불일치의 차단을 확인한다.
- 전체 분석·평가시각·유효기간·원본 바이트·버전의 보존과 반복 호출의 상태 분리를 확인한다.
- JSON 왕복 후 같은 정책 재평가의 일치, 상태·사유·시각·버전 조작 거절을 확인한다.
- null·모순된 결과·지원하지 않는 버전, 중첩 입력의 변경 불가와 요약 출력 범위를 확인한다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.kis.precheck.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.freshness.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.screening.*' --offline --no-daemon
```

전체 프로젝트 테스트·MySQL·Docker·KIS·계좌·주문·OpenAI 실행은 하지 않았다. 기존 실환경 증적을 덮어쓰거나 `gradlew clean`을 실행하지 않았다. 자동 후보 선정과 운영상 차단 효과는 후속 연결·실환경 검증 대상이다.

정책 구현의 커밋 메시지는 `feat: KIS 제한·신선도 통합 사전 점검 추가`다.

## 서비스 연결 검증 결과

2026-10-07 연결 서비스 추가 후 관련 **10개 클래스·253개 테스트**가 실패·오류·건너뜀 없이 통과했다. 신규 서비스 테스트 69개, 기존 종합 분석 64개·신선도 81개·사전 점검 39개를 합한 수다. 앞의 172개는 순수 정책 구현 단계의 기록으로 유지한다. 이번 실행의 XML suite 이름과 시각으로 집계했고 전체 프로젝트 테스트는 실행하지 않았다.

| 신규 테스트 클래스 | 테스트 수 | 주요 검증 |
| --- | --- | --- |
| `KisStockRestrictionPrecheckServiceTest` | 46 | 입력·의존성 검증, 두 시장의 9가지 상태 조합, 단계별 단일 호출 순서, 실제 분석 경로의 Store 조회 한 번, null·예외·다른 반환 입력 거절, 명시적 평가 조건과 반복 실행 |
| `KisStockRestrictionPrecheckServiceIntegrationTest` | 23 | H2의 실제 저장 관측 조회와 직접 정책 대조, 원본·시각·해시·행 수 보존, 다른 시장의 경보 진단, 관측 없음·업무 실패·손상 입력 처리 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.restriction.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.freshness.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.precheck.*' --offline --no-daemon
```

단위 테스트에서 기존 기본 분석 서비스·Store·종합 정책·신선도 정책·사전 점검의 순서와 각각 한 번의 호출을 확인했다. 명시한 ID·전체 마스터·전체 경보·평가시각·두 유효기간·전체 분석·신선도 결과를 바꾼 반환은 다음 단계 전에 거절했다. 실패를 재시도하거나 다른 성공 관측으로 대체하지 않았다.

H2에서는 두 시장의 9가지 상태 조합마다 실제 저장한 응답을 복원해 같은 기존 정책의 직접 계산과 결과를 대조했다. **단일 서비스 호출 구간의 관측 엔티티 로드는 1건**이고 그 구간의 엔티티 INSERT·UPDATE·DELETE는 0건이었다. 테스트 준비·전후 메타데이터 확인을 포함한 테스트 전체가 조회 한 번이라는 뜻은 아니다.

원문 바이트·길이·해시·요청 및 응답 시각·기록 시각·관측 행 수를 유지했고 서비스 결과의 바이트 배열을 변경해도 보존 원문은 바뀌지 않았다. JDBC 저장 시각은 기존 Store의 마이크로초 정규화 규칙으로 직접 계산과 대조했고 마스터 시각·평가 요청을 DB 기록 시각으로 대체하지 않았다.

관측 ID가 없을 때는 기존 성공 관측이 있어도 대체하지 않았다. 지정한 응답의 업무 실패·잘못된 JSON·손상 해시는 다음 정책 호출 없이 실패했고 원본을 보정하지 않았다. 이후 명시적으로 다른 성공 ID를 호출하면 별도 결과를 반환한다. 다른 시장 경보를 명시한 정상 진단과 의존성이 요청과 다른 경보를 반환한 연결 오류도 구분했다.

테스트는 일시적인 H2와 합성 마스터를 사용했다. 새 서비스·사전 점검 정책·KIS Provider·Client·토큰 Provider의 빈이 해당 JPA 테스트 Context에 없음을 확인했다. 이는 정상 서버의 전체 빈 구성이나 운영 DB·MySQL 호출 지연·동시 갱신·실환경 최신 거래 가능성 검증은 아니다.

이번 구현에서는 수동 실행기·정상 서버·Harness·후보 선정·외부 API·계좌·주문·OpenAI를 실행하거나 연결하지 않았다. 실제 MySQL·Docker·`.env`·설정·스키마를 변경하지 않았고 기존 실환경 증적을 덮어쓰거나 `gradlew clean`을 실행하지 않았다.

서비스 연결 작업의 추천 커밋 메시지는 `feat: 저장된 KIS 관측의 통합 사전 점검 서비스 추가`다.

## 수동 실행기 연결

기존 [수동 분석 실행기](kis-stock-basic-info-manual-analysis.md)의 `run-precheck=true` 옵션으로 저장 관측 연결 서비스를 호출할 수 있다. 이 옵션의 기본값은 `false`이며 활성화 시 경보 포함·신선도 점검과 명시적 평가 조건을 모두 요구한다. 기존 기본·경보·신선도 모드는 그대로 유지한다.

Runner는 지정한 마스터·경보를 한 번 준비한 뒤 서비스를 한 번 호출한다. 통합 모드에서 종합 분석·신선도 정책을 먼저 별도로 실행하지 않고, 반환된 전체 분석·신선도 결과를 기존 로그에 재사용한다. 최종 로그에 관측 식별자·평가 조건·제한 및 신선도 상태와 원래 사유·`CLEAR/BLOCKED`·사전 점검 버전을 출력한다. 원문·종목 이름·API 메시지·전체 마스터를 출력하지 않는다.

반환 입력이 요청과 다른 경우나 실행 오류는 완료로 출력하지 않는다. 정상 `BLOCKED` 진단은 프로세스 실패와 구분한다. `CLEAR`를 주문 승인으로 사용하거나 현재 관측을 과거 자격으로 승격하지 않는다.

전용 Configuration에서 전체 수동 실행과 옵션이 활성화된 경우에만 기존 Policy와 Service를 등록한다. 정상 서버의 컴포넌트 스캔·Harness·후보 선정·Scheduler·Risk·주문에는 연결하지 않았다. 새 실행기·Gradle Task·테이블·외부 API 호출·재수집·재시도는 없고 `application.yml`·`.env`·Docker·기존 정책과 서비스의 판정 규칙도 변경하지 않았다.

수동 실행 연결 작업의 추천 커밋 메시지는 `feat: KIS 통합 사전 점검 수동 실행 연결`이다.

2026-10-07 수동 연결 검증에서는 관련 **10개 클래스·412개 테스트**가 통과했다. 앞의 253개는 서비스 구현 단계의 기록이다. 수동 실행기 223개·연결 서비스 69개·신선도 81개·사전 점검 39개를 이번 실행의 XML 이름과 시각으로 집계했다. H2 실제 전용 Context에서 직접 정책 대조, 관측 엔티티 로드 1건·삽입·수정·삭제 0건, 원본 보존과 풀 종료를 확인했다. 상세 명령과 범위는 [수동 실행기 검증 결과](kis-stock-basic-info-manual-analysis.md#통합-사전-점검-연결-검증-결과)에 있다. 실제 MySQL·Docker·외부 API·주문이나 전체 프로젝트 테스트를 실행한 결과는 아니다.
