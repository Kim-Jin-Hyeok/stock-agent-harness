# 거래대금 후보 수동 평가 MySQL 실측 검증 결과

## 판정

2026-10-03 승인된 로컬 MySQL에 기존 V5를 적용하고, 기존 수동 평가 Runner로 스냅샷 **3건만 새로 저장**했다. 삼성전자 단독 평가는 `COMPLETE`, 거래대금 정보가 없는 하이닉스를 포함한 평가는 `INCOMPLETE`였다. 삼성전자 단독 평가를 별도 JVM에서 반복했을 때 새 ID가 생성되고 이전 기록은 유지됐다.

다시 시작한 별도 JVM에서 세 ID를 기존 Store로 복원하고, 저장된 요청·입력 일봉만 기존 평가 서비스에 전달해 재평가했다. 세 건 모두 원래 결과와 정확히 일치했다. 기존 일봉 2,916행과 다른 7개 테이블의 데이터는 변경되지 않았고, 관측용 RestClient의 외부 HTTP 요청 시도는 0회였다.

**확인한 것은 제한된 대상의 평가·DB 저장·새 프로세스 복원 경로다.** 원천 거래대금의 금액 배율·시간외 포함 범위·과거 정보 가용성·시장 전체 모집단·종목 자격·전략 수익성을 검증한 결과가 아니다. `COMPLETE`를 투자 후보 승인으로 사용하지 않으며 Universe 계약의 `DESIGN_ONLY`, `runtimeSelectionImplemented=false`를 유지한다.

## 승인 범위와 실행 환경

사용자가 중단된 검증의 재개와 이번 작업에 필요한 권한을 승인했다. 기존 로컬 MySQL 컨테이너만 임시 기동하고, V4 상태에서 기존 V5 한 개를 적용한 뒤 평가 기록을 최대 3건 저장하는 범위로 실행했다. 앱 컨테이너 기동·재생성, 이미지 빌드, KIS·OpenAI 호출, 실제 주문, Commit·Push는 수행하지 않았다.

| 항목 | 실제 조건 |
| --- | --- |
| 운영 코드 기준 | `691164fa5d22b239cc3d73da4a83174eed50ae77`, 실행 전 tracked 작업 트리 깨끗함 |
| Java / DB | Amazon Corretto 21.0.12 / MySQL 8.4.11 |
| DB 대상 | 기존 로컬 `stock_agent_harness`, 호스트 포트 3307 |
| 앱 컨테이너 | 기존 ID `1b24cd2efd28`, 전후 정지 상태 유지 |
| MySQL 컨테이너 | 기존 ID `30f9f6f44294`, 기존 볼륨 사용, 재생성하지 않음 |
| DB 기동 | 2026-10-03 16:37:47 KST, `healthy` 확인 |
| 평가 프로필 | `local`, 웹 서버 없음, Flyway 비활성화, Hibernate `validate` |
| 외부 기능 | KIS·OpenAI 비활성화, 주문 실행 모드 `VIRTUAL` |
| 자동 실행 | Harness·주문 조회/취소·일봉/지수 수집 스케줄러와 다른 bootstrap·backfill·수동 백테스트 비활성화 |
| 실행 상한 | 각 관측 JVM 최대 180초, JDBC 연결 5초·소켓/조회 15초 |
| DB 연결 설정 | Hikari 연결 5초, 관측 세션의 `innodb_lock_wait_timeout=5` |

DB 접속 값은 기존 MySQL 컨테이너의 환경에서 내부적으로 읽어 자식 JVM 환경에만 전달했다. `.env`는 읽거나 수정하지 않았고 자격 증명을 인자·문서·로그에 넣지 않았다. 자식 JVM의 KIS·OpenAI 환경변수도 제거했다. 관측 계층은 Spring RestClient 빈의 외부 요청을 실행 전에 차단하고 시도 횟수를 센다. 이 차단은 관측 보조 코드에만 있으며 운영 소스는 바꾸지 않았다.

## 고정 입력과 사전 점검

평가 조건은 최초 저장 전에 고정했다. 이 숫자·종목은 저장 경로를 확인하기 위한 대조 조건이며 권장 유동성 기준이나 검증된 투자 대상이 아니다. 불완전 결과를 성공시키기 위해 종목을 사후 삭제하거나 누락 메타데이터를 보충하지 않았다.

| 조건 | 값 |
| --- | --- |
| 기준일 `S` | 2026-09-23 |
| 필수 거래일 목록 | 2026-09-21, 2026-09-22, 2026-09-23 |
| 기대 조회 시장 범위 | `INTEGRATED` |
| 최소 평균 거래대금 인자 | `1000000000` |
| 최대 후보 수 | 2 |
| 완전 입력 대조 | `005930` 단독 |
| 불완전 입력 대조 | `000660`, `005930` 전체 유지 |

읽기 전용 사전 점검에서 Flyway V4까지 `success=1`, 신규 평가 테이블 미생성, 기존 일봉 2,916행을 확인했다. 삼성전자 세 날짜의 거래대금·시장 범위는 이전 [일봉 보충 관측](../../../market/price/history/validation/daily-price-trading-value-backfill-observation-02.md)과 같았다. 하이닉스의 같은 날짜 OHLCV는 존재하지만 거래대금·시장 범위가 모두 `null`이었다.

삼성전자 세 거래대금 정수의 합계는 `27053101613398`, 분모는 3이다. 원천 의미 검증 없이 이 합계가 실제 원화 기준임을 확정하지 않는다. 하이닉스 누락은 `0`이나 종가 × 거래량으로 대체하지 않았다. 직접 평가한 두 대조 조건이 각각 `COMPLETE`·`INCOMPLETE`인지 확인한 뒤 실제 Runner 실행으로 넘어갔다.

## V5 적용 결과

별도 Flyway 프로세스에서 목표 버전을 5로 제한했다. pending 항목이 기존 `V5__create_daily_trading_value_selection_snapshot.sql` 한 개임을 확인하고 한 번 적용했다. V1~V4의 이력은 그대로였고 V5는 `success=1`이었다. 완료 시각은 2026-10-03 16:40:07 KST다.

새 테이블의 5개 열을 실제 MySQL에서 확인했다. `id BIGINT AUTO_INCREMENT`, `recorded_at DATETIME(6)`, `selection_as_of_date DATE`, `evaluation_status VARCHAR(20)`, `snapshot_json LONGTEXT`이며 모두 `NOT NULL`이다. 마이그레이션 직후 테이블은 비어 있었다. 기존 일봉 데이터·열·인덱스와 다른 테이블의 데이터 해시·건수는 사전 상태와 같았다.

이후 평가 프로세스는 Flyway를 끄고 Hibernate `validate`만 사용했다. 새 마이그레이션을 작성하거나 기존 마이그레이션을 수정하지 않았다.

## 평가와 저장 결과

각 행은 새 JVM·ApplicationContext에서 기존 `DailyTradingValueSelectionEvaluationRunner`가 시작 시 한 번 실행되어 저장한 결과다. 보조 코드가 Runner 대신 스냅샷을 INSERT하지 않았다. 기존 QueryService·평가 정책·JSON 변환기·Store를 그대로 사용했다.

| 실행 | JVM PID | 새 ID | 상태 | 대상 | 계산 | 선정 | 미확인 | JSON UTF-8 바이트 |
| --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 삼성전자 단독 | 24452 | 1 | `COMPLETE` | 1 | 1 | 1 | 0 | 1411 |
| 하이닉스 포함 | 4172 | 2 | `INCOMPLETE` | 2 | 1 | 0 | 1 | 1797 |
| 삼성전자 같은 조건 반복 | 33840 | 3 | `COMPLETE` | 1 | 1 | 1 | 0 | 1411 |

불완전 기록의 `unverifiedSymbols`에는 `000660`이 남고, 두 종목의 전체 입력 일봉 및 삼성전자의 성공한 계산 값도 보존됐다. 선정 목록은 비어 있다. 반복 기록은 ID 1을 갱신하지 않고 ID 3을 생성했으며, 복원된 평가 스냅샷은 첫 결과와 동일했다. ID 1·2는 마지막 실행 후에도 원래 행 전체가 같았다.

각 프로세스에서 저장 행이 정확히 1건 증가했으며, `recorded` 로그의 ID·상태가 실제 DB와 일치했다. DB의 원본 LONGTEXT를 기존 변환기로 해석한 결과, Store의 ID 조회 결과와 저장 직전 DB 입력의 직접 평가 결과가 모두 같았다.

| 실행 | 저장 시각 UTC | Runner 로그 구간 | Spring 실행부터 관측·종료까지 |
| --- | --- | ---: | ---: |
| ID 1 | 2026-10-03T07:40:24.054433Z | 185ms | 약 5.211초 |
| ID 2 | 2026-10-03T07:40:40.612526Z | 197ms | 약 5.348초 |
| ID 3 | 2026-10-03T07:40:56.642239Z | 176ms | 약 5.114초 |

Runner 로그 구간은 `started`에서 `recorded`까지의 밀리초 차이다. 프로세스 구간에는 Spring 초기화·검증·증거 저장·종료가 포함되며 JVM 최초 생성부터의 전체 시간은 아니다. 작은 명시 대상 세 번의 결과를 전 시장 처리량이나 운영 지연 보장으로 일반화하지 않는다.

`recorded_at`의 직접 SQL 문자열은 각각 `2026-10-03 07:40:24.054433`, `07:40:40.612526`, `07:40:56.642239`였고, JPA로 읽은 `Instant`도 위 UTC 시각과 일치했다. 세 `Instant`가 실제 실행 시각 범위 안에 있는지 확인했다. MySQL의 `DATETIME` 자체에는 시간대가 없으므로 위 SQL 문자열에 KST를 임의로 붙이지 않는다. 과거 데이터의 제공 시각을 증명하는 열도 아니다.

## 별도 프로세스 복원과 재평가

PID 38308의 새 JVM에서는 수동 Runner를 비활성화하고 해당 빈이 없음을 확인했다. `SnapshotStore.findById(1/2/3)`로 세 건을 복원해 최초 저장 시 보존한 원본 객체와 비교했다.

그 뒤 기존 `DailyTradingValueSelectionEvaluationService`에 복원된 `request()`와 `inputHistories()`만 전달했다. 현재 DB 일봉을 다시 조회해 결과를 바꾸지 않았다. ID 1·3은 `COMPLETE`, ID 2는 `INCOMPLETE`로 원래 평가 결과 전체와 일치했다. 동일 코드 revision의 재현성 확인이며 과거 정책 버전 변경 후의 재현성까지 보장한 것은 아니다.

복원 직전·직후 DB 비교에서 새 행·데이터 변경은 없었다. 기존 세 스냅샷·일봉·다른 테이블·마이그레이션 이력·평가 열 정의도 같았다. 검증 완료 시각은 2026-10-03 16:41:05 KST다. 별도 JVM 재기동은 확인했지만 MySQL 재기동 뒤 재조회·장애 복구·동시 쓰기 충돌은 이번 실측 범위에 넣지 않았다.

## 데이터 보존과 종료 상태

일봉 전체를 종목·날짜순으로 읽어 ID·OHLCV·거래대금·시장 범위를 포함한 행 전체의 정확한 일치를 비교했다. 기존 열 정의와 인덱스도 그대로였다. 다른 테이블은 ID순 전체 행을 Java JSON으로 직렬화한 SHA-256과 건수를 단계별로 비교했으며 모두 일치했다. 외부 수집·주문 프로세스가 없는 이번 격리 실행의 비교이지 다종목 조회에 새 DB 스냅샷 격리 계약을 추가한 것은 아니다.

| 테이블 | 실행 전후 건수 |
| --- | ---: |
| `daily_price_bar` | 2916 |
| `broker_order` | 0 |
| `current_price_observation` | 80 |
| `harness_run_entity` | 82 |
| `harness_step_entity` | 2075 |
| `market_index_daily_observation` | 731 |
| `strategy_portfolio` | 3 |
| `trade_record_entity` | 81 |

변경된 것은 기존 V5 적용 이력·평가 테이블 생성과 평가 스냅샷 3행 추가뿐이다. 검증용 기록을 지우거나 스키마를 V4로 되돌리지 않았다. **V5와 ID 1·2·3은 로컬 DB 볼륨에 남아 있다.**

관측 JVM은 종료됐고 MySQL도 다시 정지해 `Exited (0)`임을 확인했다. 앱은 기존 `Exited (143)` 상태·ID·이미지를 그대로 유지했다. 앱의 143은 이번 검증에서 새로 발생한 종료 코드가 아니다. `.env`, Compose 설정, 운영 Java·설정·의존성은 수정하지 않았다.

## 증거와 관련 테스트

관측 보조 코드와 원본 증거는 Git에서 제외되는 `build/daily-trading-value-selection-observation-01/`에 보존했다. DB가 평가 스냅샷의 영속 저장소이며 이 문서는 실행 조건과 결과·한계만 기록한다. 보조 소스와 증거를 운영 패키지에 추가하지 않는다.

| 증거 | 내용 |
| --- | --- |
| `TradingValueSelectionObservation01.java`, `run-observation-01.ps1` | 기존 Runner 실행, 요청 상한·비활성화 인자·HTTP 차단, 상태 비교와 프로세스 상한 |
| `preflight-snapshot.json`, `preflight-result.json` | 최초 DB 상태와 두 대조 조건의 직접 평가 |
| `before-migration-snapshot.json`, `after-migration-snapshot.json`, `migration-result.json` | V4에서 V5 한 개 적용과 기존 데이터 보존 |
| `complete/incomplete/repeat-before/after-snapshot.json` | 각 실행 전후 DB 행·열·이력·기존 테이블 건수와 해시 |
| `complete/incomplete/repeat-expected.json`, `*-result.json`, `*.log` | 직접 평가 원본, 저장 ID·시각·상태·바이트 수·Runner 로그 |
| `restore-before/after-snapshot.json`, `restore-result.json` | 독립 JVM의 세 ID 복원·저장 입력 재평가와 무변경 확인 |

결과 파일의 `snapshotJsonHash`는 Java ObjectMapper로 **JSON 문자열 값을 다시 직렬화한 바이트**의 SHA-256이다. LONGTEXT 원문 UTF-8 바이트 자체의 해시나 원천 진위 증거로 해석하지 않는다. 이번 일치 판정은 해시만이 아니라 원문 복원 객체와 전체 DB 행 비교를 함께 사용했다.

보조 코드 최초 컴파일과 Docker 상태 점검은 샌드박스의 접근 제한으로 실패했다. 해당 실행은 DB 쓰기나 평가 실행 전이었으며, 도구 승인을 받은 뒤 같은 읽기·컴파일 작업만 재실행했다. V5 적용·평가 저장·복원은 각각 한 번 성공했고, 불명확한 쓰기 결과에 대한 자동 재시도는 없었다.

아래 관련 테스트만 `--rerun-tasks`로 실제 재실행했다. 73개 통과, 실패 0개·건너뜀 0개였으며 전체 테스트는 실행하지 않았다. MySQL 실측과 H2 통합 테스트의 증거는 구분한다.

```powershell
.\gradlew.bat test --tests "com.stock.strategy.universe.liquidity.evaluation.runner.*" --tests "com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.*" --no-daemon --rerun-tasks
```

실행 방법은 [거래대금 후보 수동 평가](../daily-trading-value-selection-manual-evaluation.md), 미검증 원천·모집단 경계는 [후보 Universe 계약](swing-v1-candidate-universe-contract.md)과 [거래대금 원천 의미 검증](../../../market/price/history/validation/daily-price-trading-value-source-validation-01.md)을 따른다. 이번 저장 경로 검증만으로 전략 승격·후보 확대·실제 자금 운용을 허용하지 않는다.
