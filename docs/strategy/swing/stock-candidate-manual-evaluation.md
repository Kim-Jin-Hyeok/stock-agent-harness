# 통합 후보 수동 평가와 DB 기록

## 목적과 경계

`StockCandidateEvaluationRunner`는 명시한 종목 자격 입력과 DB 일봉을 기존 통합 QueryService로 평가하고, 전체 결과 스냅샷을 한 번 저장하는 수동 진입점이다. 자격 판정·유동성 계산·선정 정책을 새로 만들지 않고 기존 QueryService·Snapshot·Store를 연결한다. 운영 후보·백테스트·주문에 결과를 적용하지 않는다.

Runner는 `com.stock.strategy.universe.candidate.evaluation.runner`, 설정은 그 아래 `config.StockCandidateEvaluationProperties`다. `ApplicationRunner`이므로 명시적으로 활성화한 별도 프로세스의 시작 시 한 번 실행한다. 기본 `enabled=false`이며 활성화 설정을 계속 남기면 재시작마다 새 평가·새 DB 행이 생긴다. 일반 서버 설정이나 `.env`에 켜두지 않고 수동 실행 인자에서만 활성화한다. Runner가 애플리케이션을 강제로 종료하지는 않는다.

이 문서는 실행 안내이지 실제 MySQL 실행 결과가 아니다. 이번 구현은 단위·설정·컨텍스트·합성 H2 통합 테스트로 검증했으며 실제 DB 마이그레이션 적용·평가 기록·Docker 조작·KIS·OpenAI 호출은 하지 않았다. `COMPLETE`는 명시한 대상과 근거의 분류 완료이며 원천 검증·과거 모집단 확인·수익성 증명 또는 주문 승인이 아니다. 전체 계약의 `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지한다.

## 평가 설정

접두어는 `strategy.universe.candidate.evaluation.manual`이다.

| 항목 | 계약 |
| --- | --- |
| `enabled` | 기본 `false`. 별도 수동 실행에서만 `true`로 지정한다. |
| `target-symbols` | 비어 있지 않은 원래 대상 목록. 앞자리 0을 보존하고 중복·빈 코드를 거절한다. 운영 Universe나 DB 종목 목록으로 대체하지 않는다. |
| `selection-as-of-date` | 자격·유동성 평가가 공유하는 기준일. DB 일봉 조회 종료일이다. |
| `selection-cutoff-at` | 시간대가 명시된 `Instant`. 서울 날짜가 기준일보다 이르면 거절한다. 실제 데이터 확정·가용성을 증명하는 값은 아니다. |
| `eligible-markets` | 명시한 허용 집합. `KOSPI`·`KOSDAQ`만 허용하고 빈 집합을 거절한다. |
| `eligible-security-types` | 명시한 허용 집합. `COMMON_STOCK`·`PREFERRED_STOCK`만 허용하고 ETF·ETN 등은 허용하지 않는다. |
| `required-trading-dates` | 엄격히 증가하는 비어 있지 않은 거래일 목록이며 마지막 날짜는 기준일이다. 실제 거래일 달력·원천 확정 여부를 자동 검증하지 않는다. |
| `expected-venue-scope` | `KRX`, `NXT`, `INTEGRATED` 중 명시한다. KOSPI·KOSDAQ 소속과 다른 개념이며 현재 Broker 설정을 과거 행에 덧씌우지 않는다. |
| `minimum-average-trading-value-krw` | 양의 정수로 명시한다. 숨은 금액 기본값은 없다. |
| `max-candidate-count` | 양의 정수로 명시한다. 자격 통과 대상의 유동성 분류에 적용한다. |
| `eligibility-inputs` | 아래 자격 입력 목록. 생략하면 빈 목록이며, 누락된 대상은 기존 평가에서 미확인으로 남는다. 근거 없는 완료 평가를 만들지 않는다. |

활성화 상태의 평가 조건 누락·중복 대상·잘못된 날짜·허용 집합·금액·개수는 설정 바인딩 중 실패한다. 목록과 집합은 불변 복사하고 기존 요청 객체의 검증을 사용한다. 자격 입력 목록의 null 항목·대상 밖 종목·중복 종목은 거절하지만, 자격 근거 누락·미확인 상태는 정상적인 `INCOMPLETE` 평가의 입력이다. 비활성화 상태에서는 평가 조건을 요구하지 않는다.

## 종목 자격 입력

각 항목은 `eligibility-inputs[0].symbol`처럼 인덱스로 지정한다. `symbol` 외 근거 필드는 비어 있을 수 있으며 기본값으로 채우지 않는다.

| 항목 | 의미 |
| --- | --- |
| `symbol` | 원래 대상에 속하는 비어 있지 않은 문자열 종목 코드. YAML을 사용할 때도 `"005930"`처럼 문자열로 쓴다. |
| `as-of-date` | 근거가 설명하는 실제 기준일. 현재 정보에 과거 날짜만 붙이지 않는다. |
| `market` | 근거상 `KOSPI`, `KOSDAQ`, `OTHER`. 누락은 미확인이다. |
| `security-type` | 근거상 보통주·우선주·ETF·ETN·기타 유형. 허용 집합과 비교하며 누락은 미확인이다. |
| `listing-status` | 해당 기준일의 `LISTED`, `NOT_LISTED` 상태. 현재 상장 상태로 과거를 대체하지 않는다. |
| `source-reference` | 확인 가능한 원천 참조. 문자열을 입력했다고 원천 진위가 인증되는 것은 아니다. |
| `information-available-at` | 해당 정보가 알려진 시각의 `Instant`. 누락·cutoff 이후 정보는 확인 불가다. |
| `evidence-status` | `AS_OF_VERIFIED`, `CURRENT_ONLY`, `UNVERIFIED`. 미입력은 null이며 자동으로 `AS_OF_VERIFIED`를 넣지 않는다. |

`AS_OF_VERIFIED`는 과거 기준일의 상태와 당시 정보 가용성을 확인했다는 호출자의 선언이다. 설정으로 이 값을 주는 것만으로 원천이 검증되지는 않는다. 실제 확인 근거가 없으면 `CURRENT_ONLY`·`UNVERIFIED` 또는 근거 누락으로 전달한다. 종목 마스터 조회·외부 원천 인증·거래정지·기업행위 처리는 이 Runner가 하지 않는다.

## 실행 전 확인

1. 승인한 연구용 DB 주소·스키마와 기존 데이터 보존 방법을 확인한다. 정상 주문 프로세스와 분리하고 다른 평가·수집·보충 작업을 동시에 실행하지 않는다.
2. `V6__create_stock_candidate_evaluation_snapshot.sql`까지 이미 적용돼 있는지 확인한다. 아래 실행은 Flyway를 끄고 Hibernate `validate`만 사용하며, V6 적용은 별도 승인·검증 작업이다.
3. 대상·기준일·cutoff·허용 집합·거래일·시장 범위·최소 금액·최대 개수와 자격 근거를 결과 확인 전에 고정한다. 완료된 종목만 남기도록 사후에 대상을 줄이지 않는다.
4. 저장 일봉의 원천 단위·시간외 범위·과거 정보 가용성의 미검증 상태를 확인한다. 종목별 조회가 같은 DB 시점이라는 새 격리 보장도 추가하지 않았다.

읽기 전용 사전 점검 예시다.

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT id, symbol, trading_date, trading_value_krw, trading_venue_scope
FROM daily_price_bar
WHERE symbol IN ('005930', '000660')
  AND trading_date BETWEEN '2026-09-21' AND '2026-09-23'
ORDER BY symbol, trading_date;
```

## 격리 실행 예시

DB 환경변수는 [로컬 MySQL 문서](../../local-mysql-development.md)를 따른다. `bootRun`은 Docker Compose의 `.env`를 자동으로 읽지 않으므로 별도 PowerShell 프로세스 또는 실행 설정에서 DB 연결 환경변수를 전달한다. 비밀값을 명령 인자·문서·로그·Git에 넣지 않는다. 이 경로에는 KIS·OpenAI 자격 증명이 필요하지 않다.

아래 종목·날짜·금액은 실행 형태를 설명하는 값이지 추천 투자 후보·유동성 기준이나 검증된 과거 근거가 아니다. 자격 근거는 일부러 넣지 않고 한 종목의 코드만 전달하므로 **자격 단계의 `INCOMPLETE`를 기록하는 예시**다. 나머지 대상의 자격도 추정하지 않는다. 아래 명령은 **승인한 DB에 통합 평가 스냅샷 한 행을 실제 INSERT**하므로 DB·대상·조건을 확정한 뒤에만 실행한다. 이번 작업에서는 실행하지 않았다.

```powershell
$runArgs = @(
    '--spring.profiles.active=local'
    '--spring.main.web-application-type=none'
    '--spring.flyway.enabled=false'
    '--spring.jpa.hibernate.ddl-auto=validate'
    '--broker.kis.enabled=false'
    '--trade.execution.mode=VIRTUAL'
    '--agent.next-action.provider-type=RULE_BASED'
    '--agent.decision.moving-average.provider-type=RULE_BASED'
    '--agent.provider.ai.openai.enabled=false'
    '--harness.scheduler.enabled=false'
    '--broker.order.reconciliation.scheduler.enabled=false'
    '--broker.order.cancellation.scheduler.enabled=false'
    '--market.price.history.collection.bootstrap.enabled=false'
    '--market.price.history.collection.scheduler.enabled=false'
    '--market.price.history.collection.trading-value-backfill.enabled=false'
    '--market.index.history.collection.bootstrap.enabled=false'
    '--market.index.history.collection.scheduler.enabled=false'
    '--market.index.history.collection.backfill.enabled=false'
    '--backtest.swing-v1.experiment.manual.enabled=false'
    '--strategy.universe.liquidity.evaluation.manual.enabled=false'
    '--strategy.universe.candidate.evaluation.manual.enabled=true'
    '--strategy.universe.candidate.evaluation.manual.target-symbols=005930,000660'
    '--strategy.universe.candidate.evaluation.manual.selection-as-of-date=2026-09-23'
    '--strategy.universe.candidate.evaluation.manual.selection-cutoff-at=2026-09-23T09:00:00Z'
    '--strategy.universe.candidate.evaluation.manual.eligible-markets=KOSPI,KOSDAQ'
    '--strategy.universe.candidate.evaluation.manual.eligible-security-types=COMMON_STOCK'
    '--strategy.universe.candidate.evaluation.manual.required-trading-dates=2026-09-21,2026-09-22,2026-09-23'
    '--strategy.universe.candidate.evaluation.manual.expected-venue-scope=INTEGRATED'
    '--strategy.universe.candidate.evaluation.manual.minimum-average-trading-value-krw=1000000000'
    '--strategy.universe.candidate.evaluation.manual.max-candidate-count=2'
    '--strategy.universe.candidate.evaluation.manual.eligibility-inputs[0].symbol=005930'
)
.\gradlew.bat bootRun --args="$($runArgs -join ' ')"
```

`eligibility-inputs[0].symbol` 행도 생략하면 자격 입력 목록 자체가 비어 있는 경우를 확인할 수 있다. 검증한 실제 자격 입력을 전달할 때는 위 항목별 근거·실제 날짜·가용 시각을 함께 지정하고 선정 조건과 별개로 보존한다. 합성 테스트 값을 실제 종목의 검증 근거로 재사용하지 않는다.

Runner는 다른 스케줄이나 Runner를 자동으로 끄지 않는다. 위 격리 인자를 정상 주문 서버의 `paper-observation` 프로필과 합치지 않는다. `recorded` 로그 확인 뒤 프로세스가 계속 살아 있으면 `Ctrl+C`로 종료한다. 다음 정상 서버 실행에는 수동 평가 활성화 인자를 전달하지 않는다.

## 결과와 기록 확인

Runner는 통합 QueryService를 한 번 호출하고 반환 요청이 원래 요청과 같은지 확인한 뒤 Snapshot을 생성해 Store에 한 번 전달한다. 양의 저장 ID가 반환된 뒤에만 `Stock candidate evaluation recorded.` 로그가 나온다. 저장 실패·null/잘못된 ID·조회 또는 평가 예외는 호출자에게 전파하며 자동 재시도·기본 기록 생성·성공 로그로 바꾸지 않는다.

로그에는 `snapshotId`, `status`, `selectionAsOfDate`, `targetCount`, `eligibleCount`, `excludedCount`, `eligibilityUnverifiedCount`, `liquidityEvaluated`, `liquidityUnverifiedCount`, `selectedCount`를 남긴다. 자격 미확인으로 유동성 단계가 생략되면 `liquidityEvaluated=false`와 `liquidityUnverifiedCount=0`이다. 이 0은 유동성이 확인됐다는 뜻이 아니다. 전체 JSON·일봉·자격 입력과 출처 참조는 로그에 넣지 않는다.

| 결과 | 해석 |
| --- | --- |
| `COMPLETE`, 선정 있음 | 명시한 대상의 자격 분류와 적격 대상의 유동성 평가가 끝났으며 조건상 후보가 있다. 운영 사용 승인은 아니다. |
| `COMPLETE`, 선정 없음 | 전체 자격 제외 또는 유동성 기준 미달 등으로 후보가 없다. 이 결과도 기록한다. |
| `INCOMPLETE`, `liquidityEvaluated=false` | 자격 미확인 대상이 있으며 유동성 평가를 하지 않았다. 저장 일봉이 충분해도 자격을 추정하지 않는다. |
| `INCOMPLETE`, `liquidityEvaluated=true` | 자격은 확인됐지만 적격 대상의 필수 일봉·거래대금·시장 범위가 누락됐다. 부분 후보는 반환하지 않는다. |
| 예외 | 조회·평가·응답 확인·저장 중 실패했다. 성공 기록으로 해석하지 않는다. |

`eligibleCount + excludedCount + eligibilityUnverifiedCount`는 원래 대상 수와 같다. 자격상 제외된 종목의 시세 누락은 적격 종목의 유동성 평가를 막지 않는다. 전체 자격 제외라면 유동성을 평가하지 않은 `COMPLETE`이며 후보는 없다.

다음 `123`은 로그의 실제 ID로 바꾼다. `recorded_at`은 저장 요청 시각이지 과거 정보 가용 시각·선정 cutoff의 증거가 아니다.

```sql
SELECT id, recorded_at, selection_as_of_date, evaluation_status, snapshot_json
FROM stock_candidate_evaluation_snapshot
WHERE id = 123;
```

`StockCandidateEvaluationSnapshotStore.findById(id)`는 보존한 JSON을 복원하며 현재 일봉을 다시 조회해 결과를 교체하지 않는다. 저장된 요청·자격 결과의 입력·일봉을 기존 통합 평가 서비스에 전달하면 같은 정책 구현의 결과와 비교할 수 있다. 손상 JSON·지원하지 않는 형식·기준일/상태 컬럼 불일치는 오류다. 원천 인증·과거 정책 버전 검증·HTTP 조회 API는 이 작업에 포함하지 않는다.

같은 조건의 반복 실행도 새 ID를 만든다. 근거 보충 후 완료 평가가 나와도 이전 미완료 요청·입력·사유·JSON·저장 시각은 유지한다. 저장 응답이나 종료 상태가 불명확하면 먼저 DB를 확인한다. 완료 로그가 없다는 이유만으로 INSERT가 없었다고 단정하거나 자동 재실행하지 않는다.

## 검증 범위

관련 단위·설정·컨텍스트 테스트는 비활성화 무호출·빈 미등록, 조건 검증, 코드·날짜·enum 바인딩, 근거 기본값 미생성, 평가 뒤 저장 한 번 호출, 결과별 단계 건수와 실패 시 완료 로그 차단을 확인한다. H2 통합 테스트는 정상 선정·자격 미확인·유동성 미확인·전체 제외·미달·전체 누락의 직접 평가 및 저장 ID 복원 일치, 생략한 자격의 미확인 기록, 반복 실행의 새 ID와 이전 기록 유지, 기존 일봉·유동성 스냅샷 무변경을 확인한다.

H2 테스트는 준비한 일봉에 대해 Runner를 직접 호출한 것이며 실제 애플리케이션 프로세스 시작·MySQL 트랜잭션·별도 JVM 복원·DB 재시작·백업 복구·외부 원천 검증을 대신하지 않는다. 실제 실행 관측과 V6 적용은 별도 검증으로 남겨야 한다. 전체 설계와 미검증 경계는 [후보 Universe 계약](validation/swing-v1-candidate-universe-contract.md)을 따른다.
