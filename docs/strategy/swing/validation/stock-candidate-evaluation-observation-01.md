# 통합 후보 수동 평가 MySQL 실측 검증 결과

## 결론과 검증 범위

2026-10-04 승인된 로컬 MySQL에 기존 V6를 적용하고 `StockCandidateEvaluationRunner`로 통합 평가 스냅샷 **3건만 새로 저장**했다. 자격 근거가 없는 경우와 `CURRENT_ONLY` 대조 입력은 모두 `INCOMPLETE`로 기록됐고, 같은 조건을 반복하면 이전 행을 수정하지 않고 새 ID가 생성됐다. 별도 JVM에서는 세 ID를 정확하게 복원하고 저장된 요청·자격 입력·일봉만으로 같은 평가 결과를 재현했다.

기존 일봉 2,916건, 기존 거래대금 평가 스냅샷 3건과 나머지 테이블은 변경되지 않았다. 앱 컨테이너는 정지 상태를 유지했고 관측 프로세스에서 KIS·OpenAI를 호출하지 않았다. MySQL만 임시 기동한 뒤 다시 정지했다. V6와 새 통합 평가 ID `1`, `2`, `3`은 DB 볼륨에 남겼으며 삭제하거나 마이그레이션을 되돌리지 않았다.

이번 결과는 **제한된 대상의 MySQL 저장·복원 경로 검증**이다. 실제 과거 자격 자료는 없으므로 `AS_OF_VERIFIED`나 확인된 시장·상장 상태를 만들어 `COMPLETE`를 유도하지 않았다. MySQL의 완료 평가 경로, 종목 자격 원천 인증, 전체 과거 모집단, 거래대금 단위·세션 범위와 전략 순성과는 검증하지 않았다. 전체 계약의 `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지한다.

## 실행 환경과 안전장치

사용자는 V6 적용, 최대 3건 기록, 별도 프로세스 복원과 기존 상태 복귀를 포함한 작업을 요청했고 실행 권한도 승인했다. 기존 MySQL 컨테이너만 사용했으며 앱 기동·컨테이너 재생성·이미지 빌드·외부 시세 수집·주문·Commit·Push는 하지 않았다. 운영 Java·설정·의존성과 `.env`는 수정하지 않았다.

| 항목 | 확인한 값 |
| --- | --- |
| 실행일 | 2026-10-04, Asia/Seoul |
| 운영 코드 revision | `ac1dffc614ccfd5fff5a28afde751a843027943a` |
| Java / MySQL | Amazon Corretto 21.0.12 / MySQL 8.4.11 |
| DB 주소 / 스키마 | 로컬 `127.0.0.1:3307` / `stock_agent_harness` |
| MySQL 컨테이너 | 기존 ID `30f9f6f44294`, 기존 볼륨 유지 |
| 앱 컨테이너 | 기존 ID `1b24cd2efd28`, 기존 이미지와 정지 시각 유지 |
| DB 기동 / 정지 | 11:11:52 / 11:16:28 KST |
| 마이그레이션 | V5 → V6, 기존 V6 한 개만 적용 |
| 평가 기록 | 통합 평가 테이블 0 → 3건 |

보조 코드와 원본 증거는 Git에서 제외되는 `build/stock-candidate-evaluation-observation-01/`에 둔다. 운영 패키지를 새로 만들지 않고 기존 Runner·QueryService·정책·Converter·Store를 사용했다. 각 JVM은 최대 180초, JDBC 연결은 5초, 소켓·SQL 조회는 15초, Hikari 연결과 MySQL 잠금 대기는 5초로 제한했다. 증거 파일은 신규 생성만 허용하고 이미 있으면 자동 재실행·덮어쓰기를 거절한다.

DB 자격 증명은 기존 MySQL 컨테이너 환경에서 내부적으로 읽어 자식 JVM 환경에만 전달했다. `.env`를 읽거나 변경하지 않았고 비밀값을 명령 인자·문서·로그에 넣지 않았다. 자식 JVM의 KIS·OpenAI와 상속된 Spring·전략·실행 설정 환경변수를 제거하고 별도 실행 인자를 고정했다.

평가 프로세스는 `local`, 웹 비활성화, Flyway 비활성화, Hibernate `validate`, `VIRTUAL`, 규칙 기반 Agent와 OpenAI 비활성화로 실행했다. Harness·주문 동기화/취소·일봉/지수 수집·보충·수동 백테스트·기존 거래대금 평가 Runner는 모두 껐다. 통합 평가 Runner만 세 평가 프로세스의 시작 시 활성화했고 복원 프로세스에서는 껐다. 정상 서버 설정에는 활성화 값을 남기지 않았다.

관측 보조 계층은 Spring `RestClient` 빈의 외부 요청을 차단하고 시도 횟수를 센다. 각 평가와 복원의 시도 횟수는 0이었다. 이는 해당 관측 경로의 확인이며 모든 네트워크 경로를 막는 운영 방화벽이나 영구 안전장치를 추가한 것은 아니다.

## 사전에 고정한 입력

원래 대상은 삼성전자 `005930`과 SK하이닉스 `000660`이다. 요청 계약에 따라 저장·평가 순서는 `000660`, `005930`으로 정렬됐고 앞자리 0은 유지됐다. 결과를 본 뒤 대상을 줄이거나 누락 메타데이터를 보충하지 않았다.

| 조건 | 고정한 값 |
| --- | --- |
| 기준일 | `2026-09-23` |
| 선정 cutoff | `2026-09-23T09:00:00.123456789Z` |
| 허용 시장 / 유형 | `KOSPI`, `KOSDAQ` / `COMMON_STOCK` |
| 필수 거래일 | `2026-09-21`, `2026-09-22`, `2026-09-23` |
| 기대 시세 시장 범위 | `INTEGRATED` |
| 최소 평균 거래대금 / 최대 후보 수 | `1000000000` / `2` |

조건과 나노초 cutoff는 직렬화·바인딩 검증을 위한 명시 입력이지 추천 투자 기준이나 실제 정보 가용 시각의 증거가 아니다. 기존 DB에는 두 종목의 해당 구간 일봉이 각각 3개 있다. 삼성전자는 거래대금·시장 범위가 있고 하이닉스는 두 필드가 null이다. 기존 값을 읽기만 했으며 실제 원화 단위나 과거 데이터 가용성이 검증됐다고 인정하지 않았다.

세 자격 입력 조건은 다음과 같다.

| 실행 조건 | 명시한 자격 입력 | 기대 사유 |
| --- | --- | --- |
| `missing` | 자격 입력 목록 생략 | 두 종목 모두 `AS_OF_DATE_UNVERIFIED` |
| `current-only` | `005930`의 코드·기준일과 `CURRENT_ONLY`만 전달 | `005930`은 `CURRENT_INFORMATION_ONLY`, `000660`은 `AS_OF_DATE_UNVERIFIED` |
| `repeat` | `missing`과 동일 | 최초 결과와 같은 내용, 별도 저장 ID |

`CURRENT_ONLY`는 **실제 종목 마스터를 조회한 결과가 아니라 미확인 대조 입력**이다. 기준일을 과거 평가일과 같게 전달해도 `CURRENT_ONLY` 선언을 과거 확인 자료로 승격하지 않는 분기를 검증했다. 시장·유형·상장 상태·원천 참조·정보 가용 시각은 모두 null이며 날짜만으로 과거 자료를 위조하거나 인증하지 않았다.

목록이 생략된 대상은 기존 평가 서비스가 코드와 null 근거, `UNVERIFIED`로 표현했다. 요청과 대상별 자격 결과·입력은 스냅샷에 보존했고 재평가에도 이 입력을 사용했다. 원래 요청에서 목록이 생략됐다는 전달 형태 자체를 별도 필드로 저장한 것은 아니다.

## V6 적용과 중간 실패 확인

읽기 전용 사전 점검에서 Flyway V5, 통합 평가 테이블 부재, 일봉 2,916건과 기존 거래대금 스냅샷 3건을 확인했다. 기존 SQL로 읽은 일봉과 고정 입력을 운영 평가 정책에 전달한 직접 대조 결과도 모두 `INCOMPLETE`였다.

최초 마이그레이션 보조 절차는 `migrate()`보다 먼저 호출한 `validate()`가 미적용 V6를 오류로 처리해 중단됐다. 오류는 `Detected resolved migration not applied to database: 6`이었다. DDL을 실행하지 않았고, 별도 읽기 프로세스 PID `20164`에서 실패 전후 DB 상태 전체가 같은지 확인했다. 이력·기존 행·통합 테이블 부재가 그대로였다.

실패 증거를 보존한 뒤 **보조 코드만** 수정했다. V5에서는 pending 대상이 기존 V6 한 개임을 먼저 확인하고 `validateOnMigrate=true`인 `migrate()`를 실행하도록 했다. 별도 `migrate-reviewed` 실행 PID `8348`에서 11:14:48 KST에 V6가 성공했다. 이력의 `execution_time`은 66ms, checksum은 `-822632640`, `success=1`이었다. 기존 V1~V5 이력은 바뀌지 않았다. `repair`, 이력 삭제, 실패 결과 덮어쓰기와 평가 자동 재시도는 하지 않았다.

실제 MySQL에서 새 테이블의 다음 열과 제약을 확인했다.

| 열 | 타입과 제약 |
| --- | --- |
| `id` | `BIGINT NOT NULL AUTO_INCREMENT`, 단일 기본 키 |
| `recorded_at` | `DATETIME(6) NOT NULL` |
| `selection_as_of_date` | `DATE NOT NULL` |
| `evaluation_status` | `VARCHAR(20) NOT NULL` |
| `snapshot_json` | `LONGTEXT NOT NULL` |

추가 인덱스·기준일 고유 제약은 없고 마이그레이션 직후 새 테이블은 비어 있었다. Flyway는 MySQL 8.4에 대해 검증된 지원 범위를 넘는다는 경고를 남겼지만 이번 V6 검증·적용은 성공했다. 이를 모든 마이그레이션이나 전체 DB 버전 호환성의 보장으로 해석하지 않는다.

## 실제 Runner 기록

각 행은 새 JVM과 새 Spring ApplicationContext의 시작 시 기존 Runner가 저장했다. 보조 코드가 Store에 직접 INSERT하도록 대체하지 않았다. 설정으로 바인딩된 요청·자격 입력이 고정한 대조 조건과 같은지도 확인했다.

| 조건 | JVM PID | 저장 ID | 상태 | JSON UTF-8 바이트 | Runner 로그 구간 |
| --- | --- | --- | --- | --- | --- |
| `missing` | 37152 | 1 | `INCOMPLETE` | 2,643 | 203ms |
| `current-only` | 22412 | 2 | `INCOMPLETE` | 2,656 | 171ms |
| `repeat` | 24416 | 3 | `INCOMPLETE` | 2,643 | 186ms |

세 실행 모두 `targetCount=2`, `eligibleCount=0`, `excludedCount=0`, `eligibilityUnverifiedCount=2`, `liquidityEvaluated=false`, `liquidityUnverifiedCount=0`, `selectedCount=0`이었다. 사유가 서로 달라도 전체 자격 미확인 건수는 같다. 유동성 미확인 건수 0은 유동성 확인 완료가 아니라 해당 단계가 실행되지 않았다는 뜻이다.

기록 로그는 각 실행에서 한 번 나왔고 ID·상태·단계별 건수가 실제 DB와 일치했다. Runner의 `started`와 `recorded` 로그 사이가 위 시간이다. 보조 코드의 애플리케이션 시작 직전부터 컨텍스트 종료·결과 작성 직전까지는 각각 약 5.469초, 5.288초, 5.294초였다. JVM 시작 전체 시간이나 다종목 성능 보장으로 사용하지 않는다.

JPA에서 복원한 `recordedAt`은 아래 UTC 시각과 같았다. 직접 SQL 문자열도 같은 숫자와 마이크로초를 반환했다.

| ID | 복원한 `Instant` |
| --- | --- |
| 1 | `2026-10-04T02:15:07.039918Z` |
| 2 | `2026-10-04T02:15:24.499403Z` |
| 3 | `2026-10-04T02:15:33.107008Z` |

예를 들어 ID 1의 SQL 문자열은 `2026-10-04 02:15:07.039918`이었다. 관측 JDBC의 세션 시간대는 `SYSTEM`, 시스템 시간대는 `KST`였지만 `DATETIME` 자체에 시간대가 있는 것은 아니다. SQL 숫자에 KST를 임의로 붙이지 않는다. 각 저장 시각은 실제 실행 구간 안에 있었고 기준일·상태 일반 컬럼도 JSON과 같았다. 과거 정보 가용 시각이나 실제 커밋 완료 시각을 증명하는 값은 아니다.

원래 대상·요청·자격 입력·사유·일봉·명시적인 null과 나노초 cutoff가 원본 LONGTEXT, Converter와 Store 복원에서 같은 객체 값으로 유지됐다. ID 1과 3의 내용은 같고 각각 다른 ID·저장 시각을 가졌다. 기존 행의 JSON·시각·상태를 갱신하지 않았다. 이번 두 동일 조건의 원본 UTF-8 SHA-256도 같았지만 JSON 필드·집합 순서까지 영구적으로 고정하는 운영 계약은 추가하지 않았다.

## 별도 JVM 복원과 재평가

평가 프로세스를 모두 종료한 뒤 PID `14348`의 새 JVM을 시작했다. 이 프로세스에는 통합·기존 거래대금 평가 Runner 빈이 없었다. Store에서 ID `1`, `2`, `3`을 읽어 각각의 원래 예상 스냅샷 전체와 비교했고 모두 일치했다. 없는 ID의 조회는 빈 Optional을 반환했다.

각 복원 결과의 요청, 대상별 자격 결과 안의 입력과 `inputHistories`만 기존 `StockCandidateEvaluationService`에 전달했다. 현재 일봉을 다시 조회해 재평가 입력을 교체하지 않았다. 세 결과 모두 저장된 평가와 같았으며 `CURRENT_INFORMATION_ONLY`와 근거 누락 사유의 구분도 유지됐다. 같은 코드 revision의 재현성 확인이지 정책 변경 뒤 과거 결과의 재현성 보장은 아니다.

복원 전후의 DB 비교는 데이터 보존을 확인하기 위해 별도로 수행했다. 이 비교에서 읽은 현재 일봉을 재평가에 사용하지 않았다. 새 기록이나 데이터 변경은 없었고 세 스냅샷·기존 테이블·열·인덱스·마이그레이션 이력이 그대로였다. 복원 검증 완료 시각은 11:15:57 KST다.

## 기존 데이터와 종료 상태

일봉은 ID·날짜·OHLCV·거래대금·시장 범위를 포함한 전체 행 비교로 보존을 확인했다. 나머지 테이블은 ID 순서 전체 행의 JSON 표현에 대한 SHA-256과 건수를 비교했다. 기존 테이블의 열·인덱스와 기존 평가 기록의 전체 값도 확인했다.

| 기존 테이블 | 전후 건수 |
| --- | --- |
| `daily_price_bar` | 2,916 |
| `daily_trading_value_selection_snapshot` | 3 |
| `broker_order` | 0 |
| `current_price_observation` | 80 |
| `harness_run_entity` | 82 |
| `harness_step_entity` | 2,075 |
| `market_index_daily_observation` | 731 |
| `strategy_portfolio` | 3 |
| `trade_record_entity` | 81 |

관측 JVM은 종료됐고 MySQL은 11:16:28 KST에 정상 정지해 `Exited (0)`임을 확인했다. 앱은 기존 `Exited (143)` 상태·ID·이미지·시작/종료 시각을 유지했다. 이번 검증에서 앱의 143 종료가 새로 발생한 것은 아니다. 기존 DB 볼륨을 재생성하거나 다른 프로젝트 컨테이너를 조작하지 않았다. 위 상태는 이번 검증 종료 시점의 관측이며 이후 실행 상태를 보장하지 않는다.

## 원본 증거와 관련 테스트

아래 파일은 모두 Git 제외 경로 `build/stock-candidate-evaluation-observation-01/`에 보관했다. `gradle clean`은 이 증거를 삭제할 수 있으므로 실행하지 않았다.

| 파일 | 내용 |
| --- | --- |
| `CandidateEvaluationObservation01.java`, `run-observation-01.ps1` | 제한된 실행·비밀값 처리·비교·복원 보조 절차 |
| `lifecycle-before/started/after.json` | 컨테이너 ID·이미지·볼륨·전후 상태 |
| `preflight-snapshot.json`, `inspect-result.json` | 변경 전 DB와 고정 입력 직접 평가 |
| `before-migration-snapshot.json`, `migrate-failure.json`, `migrate.log` | 최초 DDL 전 검증 실패 |
| `review-snapshot.json`, `review-result.json` | 실패 뒤 DB 무변경 확인 |
| `migrate-reviewed-before/after-snapshot.json`, `migrate-reviewed-result.json`, `migrate-reviewed.log` | V6 한 개 적용과 기존 상태 보존 |
| `missing/current-only/repeat-before/after-snapshot.json` | 각 평가의 신규 한 행과 이전 기록·기존 데이터 유지 |
| `missing/current-only/repeat-expected.json`, `*-raw-payload.json`, `*-result.json`, `*.log` | 원래 대조 결과·실제 JSON·ID·시각·사유·로그 |
| `restore-before/after-snapshot.json`, `restore-result.json`, `restore.log` | 별도 JVM 복원·저장 입력 재평가·읽기 전후 무변경 |
| `related-test-report.html`, 관련 JUnit XML 9개 | 실제 재실행한 관련 테스트 결과 |

`*-raw-payload.json`은 DB JSON을 파싱해 읽기 좋게 출력한 사본이다. 원래 JSON 문자열은 각 `*-after-snapshot.json`의 `snapshot_json` 값에 보존했다. `snapshotJsonUtf8Sha256`는 JDBC에서 읽은 원본 JSON 문자열의 UTF-8 바이트 해시이며 파싱 후 재직렬화한 사본이나 물리 DB 페이지의 해시가 아니다. 해시는 이번 관측의 비교 자료이며 서명·변조 방지·외부 SQL 권한 통제는 추가하지 않았다.

아래 관련 테스트만 `--rerun-tasks`로 재실행했다. **9개 클래스, 164개 통과, 실패·오류·건너뜀 0개**였으며 전체 테스트는 실행하지 않았다. MySQL 실측과 합성 H2 테스트의 증거는 구분한다.

```powershell
.\gradlew.bat test `
    --tests "com.stock.strategy.universe.candidate.evaluation.runner.*" `
    --tests "com.stock.strategy.universe.candidate.evaluation.query.*" `
    --tests "com.stock.strategy.universe.candidate.evaluation.snapshot.storage.*" `
    --tests "com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotMigrationTest" `
    --no-daemon --rerun-tasks
```

이번 MySQL 관측은 세 `INCOMPLETE` 입력의 저장·복원만 확인했다. 정상 선정·전체 제외·유동성 미확인 등 나머지 분기의 합성 H2 검증을 MySQL 실측으로 표시하지 않는다. DB 재기동 후 재조회, 장애·백업 복구, 동시 쓰기·잠금 충돌, 큰 JSON, 전체 시장 성능과 실제 원천 확인은 이번 범위 밖이다.

실행 안내는 [통합 후보 수동 평가](../stock-candidate-manual-evaluation.md), 전체 선정 경계는 [후보 Universe 계약](swing-v1-candidate-universe-contract.md), 기존 유동성 저장 경로 관측은 [거래대금 평가 MySQL 실측](daily-trading-value-selection-observation-01.md)을 따른다. 이번 기록을 운영 후보 자동 선정이나 실제 자금 투입 승인으로 사용하지 않는다.
