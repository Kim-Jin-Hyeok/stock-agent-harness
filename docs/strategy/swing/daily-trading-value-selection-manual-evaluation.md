# 거래대금 후보 수동 평가와 DB 기록

## 목적과 경계

`DailyTradingValueSelectionEvaluationRunner`는 명시한 대상·조건으로 저장 일봉을 평가하고 전체 평가 스냅샷을 DB에 한 번 저장하는 수동 진입점이다. 기존 QueryService·평가 정책·JSON 변환기·SnapshotStore를 재사용한다. 파일 결과 저장소, Controller, 새로운 계산·중간 서비스는 추가하지 않는다.

Runner는 `com.stock.strategy.universe.liquidity.evaluation.runner`, 설정은 그 아래 `config` 패키지에 둔다. `ApplicationRunner`이므로 별도 프로세스 시작 시 실행한다. `enabled`는 기본 `false`이며 명시적인 `true`일 때만 빈을 등록한다. 활성화 값을 일반 서버 설정이나 `.env`에 남기면 재시작마다 새 평가·새 DB 행이 생기므로 해당 수동 실행의 인자에서만 켠다. 애플리케이션 종료는 강제하지 않는다.

이 문서는 실행 안내이지 실 DB 관측 결과가 아니다. 이번 구현에서는 Docker·MySQL·KIS·OpenAI를 호출하지 않았다. `COMPLETE`는 명시한 대상의 계산·분류 완료이며 종목 자격·원천 단위·과거 정보 가용성 검증이나 후보 적용·주문 승인이 아니다. 전체 Universe 계약의 `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지한다.

## 필수 설정

접두어는 `strategy.universe.liquidity.evaluation.manual`이다.

| 항목 | 계약 |
| --- | --- |
| `enabled` | 기본 `false`. 수동 실행에만 `true`를 전달한다. |
| `target-symbols` | 비어 있지 않은 종목 코드 목록. 문자열 앞자리 0을 보존하고 중복·빈 코드를 거절한다. 운영 후보나 DB에 있는 종목 목록으로 자동 대체하지 않는다. |
| `selection-as-of-date` | 평가 기준일 `S`. 일봉 조회의 종료일이다. |
| `required-trading-dates` | 엄격히 증가하고 `S`로 끝나는 거래일 목록. 모두 `S` 이하여야 하며 빈 목록·중복·미래 날짜를 거절한다. |
| `expected-venue-scope` | `KRX`, `NXT`, `INTEGRATED` 중 명시한다. 알려진 저장 범위가 다르면 평가를 실패시키며 현재 Broker 설정을 과거 행에 덧씌우지 않는다. |
| `minimum-average-trading-value-krw` | 양의 정수로 명시한다. 숨은 금액 기본값을 사용하지 않는다. |
| `max-candidate-count` | 양의 정수로 명시한다. 유동성 기준을 충족한 계산 결과 중 이 수까지만 선정한다. |

활성화 상태에서 필수 설정이 없거나 기존 요청 계약을 위반하면 바인딩 단계에서 시작을 실패시킨다. 비활성화 상태에서는 평가 조건을 요구하지 않는다. 목록을 불변 복사하고 기존 요청 객체의 검증·정렬을 재사용한다. 실제 거래일 달력, 데이터 확정 시각, 원천 배율과 종목 자격은 이 바인딩이 확인하지 않는다.

## 실행 전 확인

1. 승인한 연구용 DB의 주소·스키마를 확인한다. 주문 프로세스나 다른 수집·보충 작업과 분리하고 작은 명시 대상부터 시작한다. 여러 종목 조회가 한 시점의 고정 DB 스냅샷이라는 격리 보장을 추가한 것은 아니다.
2. `V5__create_daily_trading_value_selection_snapshot.sql`까지 이미 적용된 DB인지 확인한다. 아래 명령은 Flyway를 끄고 Hibernate `validate`만 수행하며 테이블을 생성·수정하지 않는다. 필요한 마이그레이션은 별도 승인 후 진행한다.
3. 대상 전체와 거래일 목록, 기준일·시장 범위·최소 금액·최대 개수를 결과 확인 전에 고정한다. 누락 종목이나 미달 결과가 나오더라도 성공한 종목만 남기도록 입력을 사후 수정하지 않는다.
4. 저장 일봉의 거래대금·시장 범위와 원천 의미의 미검증 범위를 확인한다. 누락은 `INCOMPLETE`로 기록할 수 있지만 원천 단위·모집단 검증 없이 실제 후보에 적용하지 않는다.

사전 점검 예시는 읽기 전용 SQL이다.

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

DB 환경변수는 [로컬 MySQL 문서](../../local-mysql-development.md)를 따른다. `bootRun`은 Docker Compose의 `.env`를 자동으로 읽지 않으므로 별도 PowerShell 프로세스 또는 실행 설정에 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 전달한다. 비밀값을 실행 인자·문서·로그·Git에 넣지 않는다. 이 평가 경로에는 KIS·OpenAI 자격 증명이 필요하지 않는다.

아래 종목·날짜·금액은 설명용이며 권장 투자 후보·유동성 기준이나 검증된 원천 데이터라는 뜻이 아니다. 명령은 **승인한 DB에 평가 스냅샷 한 행을 실제로 INSERT**한다. 대상과 조건을 확정한 후에만 실행한다.

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
    '--strategy.universe.liquidity.evaluation.manual.enabled=true'
    '--strategy.universe.liquidity.evaluation.manual.target-symbols=005930,000660'
    '--strategy.universe.liquidity.evaluation.manual.selection-as-of-date=2026-09-23'
    '--strategy.universe.liquidity.evaluation.manual.required-trading-dates=2026-09-21,2026-09-22,2026-09-23'
    '--strategy.universe.liquidity.evaluation.manual.expected-venue-scope=INTEGRATED'
    '--strategy.universe.liquidity.evaluation.manual.minimum-average-trading-value-krw=1000000000'
    '--strategy.universe.liquidity.evaluation.manual.max-candidate-count=2'
)
.\gradlew.bat bootRun --args="$($runArgs -join ' ')"
```

Runner는 다른 기능을 자동으로 끄지 않는다. `paper-observation` 프로필이나 정상 주문 서버의 실행 설정과 합치지 않는다. `recorded` 로그 확인 후 프로세스가 계속 살아 있으면 `Ctrl+C`로 종료한다. 다음 정상 서버 실행에는 수동 평가 활성화 인자를 전달하지 않는다.

## 결과 해석과 확인

시작 로그에는 입력 요청, 저장 ID 반환 뒤의 `recorded` 로그에는 `snapshotId`, `status`, `selectionAsOfDate`, `targetCount`, `calculatedCount`, `selectedCount`, `unverifiedCount`를 남긴다. 전체 입력 일봉·스냅샷 JSON·자격 증명은 로그에 넣지 않는다. `recorded`는 저장 경로 완료이며 모든 데이터가 완전하다는 의미가 아니다.

| 결과 | 해석 |
| --- | --- |
| `COMPLETE`, `selectedCount > 0` | 명시 대상 전체를 계산·분류했고 유동성 조건상 선정 종목이 있다. 실제 후보 사용 승인은 아니다. |
| `COMPLETE`, `selectedCount = 0` | 계산은 완료됐지만 기준을 충족한 후보가 없다. 이 결과도 저장한다. |
| `INCOMPLETE` | 누락·미확인 입력과 성공한 계산을 모두 보존하고 선정 결과는 비어 있다. 전체 누락도 같은 상태로 저장한다. |
| 예외 | 조회·평가·응답 검증·저장 중 실패했다. 성공 ID나 `recorded` 로그로 바꾸지 않고 자동 재시도하지 않는다. |

다음 SQL의 `123`은 로그의 실제 `snapshotId`로 바꾼다. 시간 열은 저장 요청 시각이며 과거 정보 가용 시각이나 평가 기준 시각을 증명하지 않는다.

```sql
SELECT id, recorded_at, selection_as_of_date, evaluation_status, snapshot_json
FROM daily_trading_value_selection_snapshot
WHERE id = 123;
```

Java의 `SnapshotStore.findById(id)`는 해당 JSON을 복원한다. 현재 일봉을 다시 읽어 결과를 교체하지 않는다. 복원한 `evaluationResult.request()`와 `inputHistories()`를 기존 평가 서비스에 전달하면 동일 정책 구현에서 저장 결과와 비교할 수 있다. 산술 검산과 과거 정책 버전·원천 진위 검증은 별도다. HTTP 조회 API는 이번에 추가하지 않는다.

반복 실행은 같은 조건이어도 새 ID를 생성한다. 메타데이터 보충 뒤의 완료 평가는 이전 불완전 스냅샷을 덮어쓰지 않는다. 실행 중 중단되거나 저장 응답이 불명확하면 DB 상태를 먼저 확인한다. 완료 로그가 없다는 이유만으로 저장되지 않았다고 단정하고 재시도하지 않는다.

관련 단위·컨텍스트·H2 통합 테스트로 비활성화 무호출, 필수값 차단, 평가 후 저장 순서, 완전·불완전·무선정 결과, 예외 시 완료 로그 차단, ID 복원 일치와 기존 일봉·스냅샷 보존을 확인한다. H2 통합 테스트는 데이터를 준비한 뒤 Runner를 직접 호출하며 시작 시 자동 Runner 실행과 구분한다. MySQL의 실제 실행·격리·재시작 검증을 대신하지 않는다.

전체 설계와 미검증 경계는 [후보 Universe 계약](validation/swing-v1-candidate-universe-contract.md)을 따른다.
