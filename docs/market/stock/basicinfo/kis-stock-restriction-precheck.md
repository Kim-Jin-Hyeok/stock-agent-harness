# KIS 제한·신선도 통합 사전 점검

## 목적과 적용 범위

종합 제한 점검과 관측 신선도를 함께 확인해, 해당 평가시각의 사전 점검을 통과했는지 판정한다. [저장된 종합 분석](kis-stock-restriction-stored-analysis.md)에는 제외 신호가 없어도 입력이 만료됐을 수 있다. 제한 상태만 보고 진행하거나 `FRESH`만 보고 제외 신호를 무시하지 않도록 두 조건을 하나의 정책으로 고정한다.

입력은 기존 `KisStockRestrictionFreshnessResult` 한 개다. 그 안의 전체 종합 분석·보존 응답·마스터·경보·제한 사유와 평가시각·유효기간·신선도 사유를 그대로 보존한다. 새 조회·분석 재실행·시각 추론·사유 번역은 하지 않는다.

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

Policy에 `@Component`·`@Service`를 붙이지 않고 자동 빈 등록을 추가하지 않았다. [수동 분석 실행기](kis-stock-basic-info-manual-analysis.md)는 여전히 기존 종합·신선도 진단까지만 호출한다. Harness·후보 평가·Scheduler·Risk Guard·주문에는 연결하지 않았다. Controller·테이블·외부 API·자동 재수집·재시도·설정 변경도 없다.

## 검증 결과

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

추천 커밋 메시지는 `feat: KIS 제한·신선도 통합 사전 점검 추가`다.
