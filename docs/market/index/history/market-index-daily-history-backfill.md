# 지수 일봉 과거 구간 보충 수집

## 목적과 범위

기존 `MarketIndexDailyHistoryCollectionService`는 마지막 저장일 다음 날부터 최신 구간만 수집한다. 따라서 이미 KOSPI 일봉이 저장되어 있으면 `initial-lookback-years`를 늘려도 최초 저장일 이전의 과거 이력은 보충하지 않는다.

`MarketIndexDailyHistoryBackfillService`는 이 앞쪽 구간만 수집한다. 전략 규칙이나 비용 모델을 변경하지 않고 백테스트의 관측 기간을 확장하기 위한 기능이다. 거래 신호를 만들어내거나 수익성을 보장하는 기능은 아니다.

신규 코드는 `com.stock.market.index.history.collection.backfill` 아래에 두며, 실행 설정은 `runner.config`, 수동 실행은 `runner`, 결과는 `result` 패키지로 구분한다. 기존 KIS Provider, 호출 지연, 최대 페이지 수와 저장소를 재사용하며 DB 스키마 변경은 없다.

## 수집 규칙

1. 요청 지수의 최초 저장일을 조회한다.
2. 최초 저장일이 요청 시작일 이전 또는 같은 날이면 외부 호출 없이 `NO_EARLIER_RANGE`로 종료한다.
3. 데이터가 있으면 조회 종료일을 `min(요청 종료일, 최초 저장일 - 1일)`로 제한한다. 데이터가 없으면 요청 구간 전체를 조회한다.
4. Provider 응답의 지수 ID와 모든 관측일을 실제 조회 범위와 대조한다. 범위를 벗어나면 저장하지 않고 실패한다.
5. 외부 조회가 끝난 뒤 저장소를 다시 확인하여 이미 존재하는 날짜는 삽입 대상에서 제외한다. 기존 행의 ID와 지수 값은 변경하지 않는다.
6. 새 날짜만 한 번의 `saveAllAndFlush`로 저장한다. Provider 또는 DB 실패는 호출자에게 전달한다.

중간 결측 보충, 저장된 지수 값의 정정과 최신 구간 수집은 이 기능의 범위가 아니다. 최초 저장일이 충분히 과거라는 사실만으로 전체 거래일이 빠짐없이 저장되었다고 보지 않는다.

별도 프로세스의 수집과 동시에 실행하지 않는다. 저장 직전 재조회는 이미 추가된 행을 보존하지만, 재조회 후 동시에 같은 날짜를 삽입하는 경합까지 없애지는 않는다. 마지막 경합은 기존 `(benchmark_id, observation_date)` 유니크 제약으로 차단되며 성공으로 숨기지 않고 실패한다. 원인을 확인한 뒤 재실행한다.

## 결과 해석

| 필드 | 의미 |
| --- | --- |
| `status=NO_EARLIER_RANGE` | 최초 저장일 앞에 조회할 구간이 없음. 외부 호출하지 않음 |
| `status=NO_DATA` | 조회했지만 반환 관측값이 없음. 새 행을 저장하지 않음 |
| `status=BACKFILLED` | 반환 관측값을 검증하고 신규 날짜 저장을 처리함. 전체 구간 확보를 보증하지 않음 |
| `requestedRange` | 사용자가 지정한 지수와 시작일·종료일 |
| `collectionRange` | 최초 저장일로 제한한 실제 Provider 요청 범위. 미조회 시 `null` |
| `fetchedCount` / `savedCount` | 반환 건수 / 새로 삽입한 건수. 중복 제외로 서로 다를 수 있음 |
| `fetchedFromDate` / `fetchedToDate` | 실제 반환된 첫 관측일 / 마지막 관측일. 빈 응답이면 `null` |

예를 들어 최초 저장일이 `2025-09-30`이고 `2023-09-25~2026-09-30`을 요청하면 실제 조회는 `2023-09-25~2025-09-29`다. 이 예시는 수집 범위 계산을 보여줄 뿐 해당 기간이 실제 수집되었다는 결과 기록은 아니다.

빈 응답이나 부분 응답은 전체 요청 기간을 확보한 것으로 해석하지 않는다. 시작일이 휴장일일 수도 있으므로 반환 첫날과 요청 시작일의 단순 비교만으로 결측을 단정하지도 않는다. 실제 데이터 목록과 필요한 백테스트 평가일을 대조한다.

기존 Provider의 `broker.kis.daily-price-history-max-pages` 한도 초과 또는 중간 API 오류는 예외로 종료하며 이 호출의 관측값을 저장하지 않는다. 기본 페이지 한도는 10, 요청 지연은 1초다. 한도를 무작정 늘리지 말고 필요한 경우 요청을 제한된 기간으로 나눈다. 앞쪽 이력을 여러 구간으로 나눌 때는 최초 저장일에 인접한 최신 구간부터 더 과거로 진행한다. 먼 과거부터 저장하면 최초 저장일이 이동하여 사이 구간을 이 기능으로 채울 수 없게 된다.

## 수동 실행

`MarketIndexDailyHistoryBackfillRunner`는 `market.index.history.collection.backfill.enabled=true`일 때 애플리케이션 시작마다 한 번 실행한다. 기본값은 `false`이며 시작일·종료일·지수 ID를 모두 명시해야 한다. 잘못된 설정이나 KIS Provider 미등록은 시작 실패로 처리한다. 종료일이 현재 수집 정책의 최근 확정 거래일보다 미래이면 조회 전에 거절한다.

로컬 전용 프로세스에서 실행한다. 다른 애플리케이션의 수집도 멈춘 상태인지 확인한다. `.env`는 Docker Compose가 읽지만 `bootRun`이 자동으로 읽지 않으므로 로컬 DB 접속 정보와 **모의투자용** `KIS_APP_KEY`, `KIS_APP_SECRET`, `KIS_ACCOUNT_NUMBER`, `KIS_ACCOUNT_PRODUCT_CODE`를 해당 PowerShell 프로세스의 환경변수로 먼저 설정한다. 비밀값을 명령 인자, 문서나 Git에 넣지 않는다. DB 환경변수는 [로컬 MySQL 문서](../../../local-mysql-development.md)를 따른다.

DB를 시작하고 실행 전 범위를 확인한다.

```powershell
docker compose up -d mysql
```

```sql
SELECT benchmark_id, MIN(observation_date), MAX(observation_date), COUNT(*)
FROM market_index_daily_observation
GROUP BY benchmark_id;
```

아래는 최초 저장일이 `2025-09-30`인 KOSPI 앞쪽 구간을 보충하는 예시다. 로컬 DB에 적용된 마이그레이션을 확인한 뒤 실행한다. 이 기능에는 새 마이그레이션이 없다. `local` 프로필은 기본적으로 Flyway를 실행하므로 이미 준비된 DB를 그대로 사용할 때는 예시처럼 비활성화한다.

```powershell
$runArgs = @(
    '--spring.profiles.active=local'
    '--spring.main.web-application-type=none'
    '--spring.flyway.enabled=false'
    '--broker.kis.enabled=true'
    '--broker.kis.base-url=https://openapivts.koreainvestment.com:29443'
    '--trade.execution.mode=VIRTUAL'
    '--agent.next-action.provider-type=RULE_BASED'
    '--agent.decision.moving-average.provider-type=RULE_BASED'
    '--agent.provider.ai.openai.enabled=false'
    '--harness.scheduler.enabled=false'
    '--broker.order.reconciliation.scheduler.enabled=false'
    '--broker.order.cancellation.scheduler.enabled=false'
    '--market.price.history.collection.bootstrap.enabled=false'
    '--market.price.history.collection.scheduler.enabled=false'
    '--market.index.history.collection.bootstrap.enabled=false'
    '--market.index.history.collection.scheduler.enabled=false'
    '--backtest.swing-v1.experiment.manual.enabled=false'
    '--market.index.history.collection.backfill.enabled=true'
    '--market.index.history.collection.backfill.benchmark-id=KOSPI'
    '--market.index.history.collection.backfill.from-date=2023-09-25'
    '--market.index.history.collection.backfill.to-date=2025-09-29'
)
.\gradlew.bat bootRun --args="$($runArgs -join ' ')"
```

이 Runner는 주문이나 백테스트를 실행하지 않는다. 다만 `broker.kis.enabled=true`는 다른 KIS Bean도 생성하므로 위와 같이 실행·수집 스케줄러를 모두 비활성화한 프로세스에서만 사용한다. 완료 로그 확인 후 프로세스가 계속 살아 있으면 `Ctrl+C`로 종료한다. Runner 자체가 애플리케이션 종료를 강제하지는 않는다. 다음 정상 실행에서는 보충 설정을 다시 비활성화한다.

## 확인 기준

- 시작 로그의 지수 ID, 요청 구간이 의도한 값인지 확인한다.
- 완료 로그의 실제 조회·반환 범위와 저장 건수를 확인한다. `BACKFILLED`만 보고 전체 기간이 확보되었다고 판단하지 않는다.
- 위 SQL로 실행 전후 범위·건수를 비교하고 기존 구간의 ID와 값을 별도로 비교한다. 신규 행 외의 변경이 없어야 한다.
- 같은 요청을 재실행했을 때 시작일까지 앞쪽 이력이 있으면 `NO_EARLIER_RANGE`이며 외부 조회와 저장이 없어야 한다. 부분 응답이었다면 남아 있는 앞쪽 구간만 다시 요청한다.
- DB에 필요한 종목·지수 관측일이 모두 있는지 확인한 후 [SWING_V1 수동 백테스트](../../../strategy/swing/swing-v1-manual-backtest.md)를 실행한다. 평가 기간과 전략·비용 모델을 먼저 고정하고, 결과를 본 뒤 유리한 기간만 골라 비교하지 않는다.

자동 테스트는 조회 범위 제한, 빈·부분 응답, 지수·날짜 불일치, Provider·저장 실패, 설정 비활성화와 실제 JPA 저장 후 재실행을 확인한다. H2 테스트만으로 실제 KIS 응답이나 MySQL 데이터 보충을 검증했다고 보지 않는다.
