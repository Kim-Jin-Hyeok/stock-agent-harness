# 일봉 거래대금 수동 보충 실행

## 목적과 경계

이미 저장된 일봉의 거래대금 또는 조회 시장 범위가 `null`이면 `DailyPriceTradingValueBackfillService`로 누락 필드만 보충한다. 증분 수집은 최신 저장일 이후만 조회하므로 과거 행의 누락 필드를 자동으로 채우지 않는다.

`DailyPriceTradingValueBackfillRunner`는 별도 로컬 프로세스의 시작 시 명시적으로 활성화한 경우에만 한 번 서비스를 호출한다. 일반 애플리케이션의 기본값은 비활성화다. 실행 플래그를 계속 켜두면 재시작마다 다시 실행되므로 정상 서버의 설정이나 `.env`에 활성화 값을 남기지 않는다.

신규 패키지는 `com.stock.market.price.history.collection.backfill.runner`, 설정은 그 아래 `config`다. 기존 Provider·페이지 상한·요청 간 대기·DB 보충 트랜잭션을 재사용하며 Controller·스케줄러·테이블·마이그레이션을 추가하지 않는다. 종목 선정, 백테스트, AI 판단과 주문은 이 Runner의 기능이 아니다.

이 문서는 실행 방법이며 실제 보충 결과 기록이 아니다. 이번 구현에서는 KIS 호출·MySQL 변경·Docker 조작을 수행하지 않았다. 실제 실행 대상·기간·조회 예산과 DB 변경은 별도로 승인한다.

후속 [사전 점검 결과 01](validation/daily-price-trading-value-backfill-observation-01.md)은 로컬 MySQL의 V4 미적용으로 실제 보충을 실행하지 못한 기록이다. 해당 점검은 보충·재실행이나 거래대금 단위 검증의 성공을 뜻하지 않는다.

별도 승인 후 수행한 [실측 검증 결과 02](validation/daily-price-trading-value-backfill-observation-02.md)에서는 로컬 V4 적용, 삼성전자 3행 보충과 독립 JVM 재실행의 조회 생략을 확인했다. 전체 기간 보충이나 거래대금 단위·시간외 범위 검증을 완료한 것은 아니다. 실제 실행 기록은 해당 문서를 따른다.

## 필수 설정

접두어는 `market.price.history.collection.trading-value-backfill`이다.

| 항목 | 계약 |
| --- | --- |
| `enabled` | 기본 `false`. 해당 수동 실행의 인자에서만 `true`로 지정한다. |
| `symbol` | 종목 하나를 명시한다. 문자열로 앞자리 0을 보존하며 운영 후보 목록을 자동 사용하지 않는다. |
| `from-date` / `to-date` | 양 끝을 포함한 날짜 범위. 시작일이 종료일보다 뒤면 거절한다. |
| `expected-venue-scope` | `KRX`, `NXT`, `INTEGRATED` 중 명시한다. KIS의 `daily-price-history-market` 설정과 반드시 같아야 한다. |
| `max-range-days` | 양의 정수로 명시하는 최대 달력 일수. 날짜 차이 + 1로 계산하므로 같은 날은 1일이다. 휴장일도 기간 상한에는 포함한다. |

활성화 상태에서 필수 값이 없거나 요청 기간이 상한을 넘으면 설정 바인딩을 실패시킨다. 비활성화 상태에서는 대상·기간·상한에 숨은 기본값을 넣지 않는다.

Runner는 서비스 호출 전에 KIS 활성화와 시장 범위 일치, 종료일이 `DailyPriceCollectionDatePolicy`의 최근 확정 거래일 이하인지 확인한다. 요청을 자동으로 줄이거나 시장 설정을 바꾸지 않는다. 이 정책은 설정된 거래일 달력과 일봉 제공 시각을 사용하며 원천 거래량·거래대금의 실제 확정을 증명하지 않는다.

`max-range-days`는 요청 범위 제한이고 API 호출 횟수 제한은 아니다. 차트 조회 페이지 수는 `broker.kis.daily-price-history-max-pages`를 따르며 토큰 발급 호출은 이 페이지 수에 포함되지 않는다. 페이지 상한 초과·Provider 실패는 자동 재시도 없이 실패한다.

## 실행 전 확인

1. 로컬의 지정된 DB와 모의투자용 KIS 자격 증명을 사용한다. 운영 주문 프로세스와 같은 환경에서 실행하지 않는다. 같은 DB에 쓰는 다른 수집·보충 작업도 멈춘 상태인지 확인한다.
2. `V4__add_daily_price_trading_value.sql`까지 이미 적용된 DB인지 확인한다. 이 실행에서 마이그레이션을 자동 적용하지 않는다. 아래 명령은 Flyway를 비활성화하고 Hibernate의 `validate`만 수행한다. 스키마가 준비되지 않았다면 별도 승인한 마이그레이션 작업이 먼저다.
3. 종목 하나의 짧고 확정된 기간으로 시작한다. 기존 일봉이 없으면 이 기능은 `NO_TARGETS`이며 새로운 일봉을 만들지 않는다.
4. 조회 시장 설정과 기존 원천의 가격·거래량 조정 기준을 확인한다. 기존 OHLCV나 이미 알려진 값이 다르면 보충은 중단한다. 충돌을 통과시키려고 원본을 바꾸거나 시장 범위를 추정하지 않는다.
5. 실행 전 행의 ID·OHLCV·메타데이터를 날짜순으로 내보내 원본과 해시를 보존한다. 변경 후 비교 결과와 실행 로그도 함께 보존하되 Key·Secret·토큰·계좌 접속 정보는 포함하지 않는다.
6. 대상 DB의 잠금 대기 정책을 확인한다. 다른 프로세스와 동시 실행하지 않는다. 이 Runner는 별도 DB 잠금 타임아웃 설정을 추가하지 않으며 H2 테스트가 MySQL의 잠금·타임아웃 검증을 대신하지 않는다.

다음 SQL은 `005930`, `2026-09-21~2026-09-23`이라는 설명용 예시다. 이 종목이나 기간을 수익성 검증 후보로 선정하거나 실제 데이터가 준비되었다고 주장하는 것은 아니다.

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT id, symbol, trading_date,
       open_price_krw, high_price_krw, low_price_krw, close_price_krw, volume,
       trading_value_krw, trading_venue_scope
FROM daily_price_bar
WHERE symbol = '005930'
  AND trading_date BETWEEN '2026-09-21' AND '2026-09-23'
ORDER BY trading_date;

SELECT COUNT(*) AS stored_count,
       SUM(CASE WHEN trading_value_krw IS NULL OR trading_venue_scope IS NULL
                THEN 1 ELSE 0 END) AS missing_metadata_count
FROM daily_price_bar
WHERE symbol = '005930'
  AND trading_date BETWEEN '2026-09-21' AND '2026-09-23';
```

## 격리된 수동 실행

DB는 이미 기동·준비된 상태여야 한다. DB 환경변수는 [로컬 MySQL 문서](../../../local-mysql-development.md)를 따른다. `bootRun`은 Docker Compose의 `.env`를 자동으로 읽지 않으므로 해당 PowerShell 프로세스 또는 실행 설정에 DB 값과 모의투자용 `KIS_APP_KEY`, `KIS_APP_SECRET`, `KIS_ACCOUNT_NUMBER`, `KIS_ACCOUNT_PRODUCT_CODE`를 전달한다. 비밀값을 명령 인자·로그·문서·Git에 넣지 않는다.

아래 명령은 **승인한 DB에 누락 값을 실제로 반영한다.** 대상·기간·시장·예산을 확정한 다음에만 실행한다. 예시 상한 3은 3개의 달력 날짜, 페이지 상한 1은 차트 페이지 최대 한 번을 뜻한다. 상한이나 대기 시간을 무작정 늘리거나 줄이지 않는다.

```powershell
$runArgs = @(
    '--spring.profiles.active=local'
    '--spring.main.web-application-type=none'
    '--spring.flyway.enabled=false'
    '--broker.kis.enabled=true'
    '--broker.kis.base-url=https://openapivts.koreainvestment.com:29443'
    '--broker.kis.daily-price-history-market=INTEGRATED'
    '--broker.kis.daily-price-history-max-pages=1'
    '--broker.kis.daily-price-history-request-delay=1s'
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
    '--market.index.history.collection.backfill.enabled=false'
    '--backtest.swing-v1.experiment.manual.enabled=false'
    '--market.price.history.collection.trading-value-backfill.enabled=true'
    '--market.price.history.collection.trading-value-backfill.symbol=005930'
    '--market.price.history.collection.trading-value-backfill.from-date=2026-09-21'
    '--market.price.history.collection.trading-value-backfill.to-date=2026-09-23'
    '--market.price.history.collection.trading-value-backfill.expected-venue-scope=INTEGRATED'
    '--market.price.history.collection.trading-value-backfill.max-range-days=3'
)
.\gradlew.bat bootRun --args="$($runArgs -join ' ')"
```

Runner는 다른 기능의 설정을 자동으로 끄지 않는다. 위와 같이 웹 서버·주문 처리·AI·하네스·다른 수집 경로를 끈 전용 프로세스에서만 사용한다. 기존 모의투자 서버의 실행 명령을 덮어쓰거나 `paper-observation` 프로필을 함께 사용하지 않는다.

완료 로그를 확인한 뒤 프로세스가 계속 살아 있으면 `Ctrl+C`로 종료한다. Runner가 애플리케이션 종료를 강제하지는 않는다. 실행 도중 중단·접속 장애로 완료 여부가 불명확하면 DB 상태를 먼저 확인하고 성공이나 롤백을 추정하지 않는다. 다음 정상 서버 시작에는 보충 활성화 인자를 전달하지 않는다.

## 결과 해석과 비교

시작 로그에는 요청 범위·기대 시장 범위·최대 달력 기간·설정상 최근 확정일·페이지 상한·요청 대기를 기록한다. 완료 로그에는 실제 서비스 결과를 기록한다. 서비스 실패는 애플리케이션 시작으로 전파하며 완료 로그를 남기거나 자동 재시도하지 않는다.

| 결과 | 의미 |
| --- | --- |
| `NO_TARGETS` | 요청 기간에 기존 행이 없거나 모두 완전하다. Provider를 호출하지 않았으며 DB를 변경하지 않았다. 일봉 전체 기간의 존재를 보증하지 않는다. |
| `BACKFILLED` | 기존 행의 누락 필드를 한 행 이상 채웠다. 신규 일봉을 삽입하거나 기존 OHLCV를 정정하지 않았다. |
| `ALREADY_FILLED` | 외부 조회 사이 다른 작업이 대상을 같은 값으로 채웠다. 이번 요청은 DB 변경을 하지 않았다. |
| `requestedRange` / `collectionRange` | 지정 범위 / 첫 누락일~마지막 누락일의 실제 조회 범위. 미조회 시 후자는 `null`이다. |
| `targetCount` / `fetchedCount` / `updatedCount` | 최초 누락 대상 행 수 / Provider 반환 행 수 / 이번 요청이 변경한 행 수. 필드 수나 API 호출 수가 아니다. |

실행 전후 SQL 결과와 원본을 대조하여 다음을 확인한다.

- 행 수와 각 ID·종목·날짜·OHLCV가 동일하다. 응답에만 있는 날짜는 삽입하지 않는다.
- 기존에 알려진 값은 바뀌지 않았다. 확인된 `0`은 누락이 아니며 다른 값으로 덮어쓰지 않는다.
- 처음 `null`이던 필드만 응답의 알려진 거래대금·명시한 조회 범위로 채워졌다.
- 실패하면 이번 요청의 변경이 남지 않는다. 다른 작업이 먼저 커밋한 변경까지 되돌리는 기능은 아니다.
- 성공 후 같은 요청을 재실행하면 `NO_TARGETS`이며 추가 Provider 조회와 DB 변경이 없다.

조회 범위의 기존 날짜 누락, 종목·OHLCV·알려진 메타데이터 충돌, 시장 범위 불일치, 페이지 상한 초과와 DB 실패는 중단 사유다. 날짜를 삭제하거나 충돌 필드를 지워 성공으로 만들지 않는다. 원천·설정·DB 상태를 확인한 뒤 승인된 범위에서 재실행한다.

실행 성공은 거래대금 원 단위 배율·시간외 포함 범위·수정주가·기업행위·과거 시점 정보의 검증을 뜻하지 않는다. 원본 응답 정수를 그대로 보존하는 현 저장 계약과 [후보 Universe 계약](../../../strategy/swing/validation/swing-v1-candidate-universe-contract.md)의 미검증 조건을 유지한다. 검증 전 평균 거래대금 계산이나 운영 후보 확대에 자동 연결하지 않는다.

## 자동 테스트

설정·Runner·Spring 빈 등록 테스트는 기본 비활성화, 필수 값·포함 기간 경계, 미확정 종료일, KIS 시장 범위 불일치, 정확히 한 번의 호출, 실패 전파와 결과 로그를 확인한다. 설정 바인딩 오류나 Runner의 실행 조건 실패에서는 보충 서비스를 호출하지 않는다.

기존 보충 서비스와 H2 통합 테스트는 누락 필드만 반영, 재실행 시 조회 생략, 동시 갱신 보호 및 실제 DB 제약조건 오류에 따른 전체 롤백을 검증한다. 테스트는 실제 KIS·OpenAI를 호출하거나 운영 DB를 사용하지 않는다. MySQL·Broker 실행 검증은 별도 단계다.
