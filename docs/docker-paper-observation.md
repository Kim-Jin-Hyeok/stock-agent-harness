# Docker Paper Observation

## 목적

MySQL과 Spring Boot 애플리케이션을 Docker Compose로 함께 실행하여
KIS 모의투자 데이터와 가상 거래 결과를 관측한다. 아래 명령은 운영자가
점검 후 실행하는 PowerShell 예시이며, 실행 성공이나 복구 가능성을
검증했다는 뜻은 아니다. 검증이 필요한 항목은 마지막 절에 정리한다.

현재 파일 기준으로 적용되는 기본값은 다음과 같다. 환경변수나 실행
인자가 값을 덮어쓰면 달라질 수 있으므로 시작 전에 확인한다.

| 항목 | 기본값과 의미 |
| --- | --- |
| 활성 프로필 | `local,paper-observation` |
| KIS | 활성화, 모의투자 URL `https://openapivts.koreainvestment.com:29443` |
| 거래 실행 모드 | `VIRTUAL`: 허용된 거래는 앱 내부 가상 거래이며 Broker 주문 제출과 구분한다. |
| BUY / SELL 실행 허용 | 비활성화 (`TRADE_EXECUTION_ORDERS_ENABLED=false`) |
| Harness / 일봉 수집 스케줄러 | 둘 다 비활성화, 각각 명시적으로 `true`를 설정할 때만 실행 |
| 일봉 시작 시 수집 | 비활성화 (`MARKET_PRICE_HISTORY_COLLECTION_BOOTSTRAP_ENABLED=false`) |
| Broker 주문 정합성 / 취소 스케줄러 | 둘 다 비활성화 |
| Agent / 주문 수량 판단 Provider | 둘 다 `RULE_BASED`, OpenAI 비활성화 |
| 호스트 포트 | 앱 `127.0.0.1:8080`, MySQL `127.0.0.1:3307` (호스트의 IPv4 loopback에서만 접근) |
| 컨테이너 내부 DB 주소 | `mysql:3306` |
| 시간대 | JVM과 MySQL 컨테이너 `Asia/Seoul` |

`VIRTUAL`이어도 Broker 호출이 없는 환경은 아니다. 켜진 스케줄러가 현재가와
일봉을 조회할 수 있고, 포트폴리오 최초 조회는 모의계좌 잔액을 조회하여
DB에 초기 상태를 저장할 수 있다. 이 환경의 가상 거래 수익률과 잔고를
Broker의 실제 모의주문 체결 결과나 계좌 잔고와 같다고 간주하지 않는다.

## 파일과 최초 설정

관련 파일은 루트 `AGENTS.md`, `Dockerfile`, `.dockerignore`, `compose.yml`,
`.env.example`, `src/main/resources/application-paper-observation.yml`이다.
DB와 실행 시간창의 기본 설정은 `application-local.yml`, `application.yml`도
함께 확인한다.

모의투자 인증정보는 프로젝트 루트의 `.env`에 입력하고 Compose가 앱에
전달한다. 현재 `.gitignore`와 `.dockerignore`는 `.env`를 제외한다.
인증정보를 설정 파일, 이미지, 문서나 커밋에 직접 넣지 않는다.

`.env`가 없는 최초 설정에서만 복사한다. 기존 `.env`를 덮어쓰지 않는다.

```powershell
if (-not (Test-Path -LiteralPath .\.env)) {
    Copy-Item -LiteralPath .\.env.example -Destination .\.env
}
```

`compose.yml`, `.env.example`과 모의투자 프로필의 기본값을 바꿔도 기존
`.env`는 자동으로 갱신되지 않는다. 기존 `.env`에 두 스케줄러 값이
`true`로 남아 있으면 새 기본값 `false`를 덮어쓰므로 최초 점검 전에
확인한다. 바인딩 주소도 기존 `.env`의 `APP_BIND_ADDRESS`와
`DB_BIND_ADDRESS`가 우선하며, 두 값은 기본적으로 `127.0.0.1`을 유지한다.
`0.0.0.0` 같은 값을 지정하면 외부 인터페이스에도 포트가 공개된다.
셸 환경변수는 `.env`보다 우선하므로 실제 적용값은 아래 `config` 예시로
확인한다. 기존 `.env`를 예제 파일로 덮어쓰면 인증정보가 사라질 수 있다.

생성된 `.env`에서 다음 값을 실제 **모의투자** 정보로 채운다.

```text
KIS_PAPER_APP_KEY=
KIS_PAPER_APP_SECRET=
KIS_PAPER_ACCOUNT_NUMBER=
KIS_PAPER_ACCOUNT_PRODUCT_CODE=01
```

계좌번호는 앞 8자리만 `KIS_PAPER_ACCOUNT_NUMBER`에 넣고, 뒤 2자리는
`KIS_PAPER_ACCOUNT_PRODUCT_CODE`에 넣는다.

## 주문 실행 허용 설정

`TRADE_EXECUTION_ORDERS_ENABLED`는 `compose.yml`의 앱 `environment`를 통해
전달된다. 애플리케이션은 `application.yml`에서 이 값을
`trade.execution.orders-enabled`로 읽는다. 변수 생략 시 Compose와
애플리케이션의 기본값은 모두 `false`이며 `.env.example`도 `false`다.

| 설정 | Harness 주문 경로의 동작 |
| --- | --- |
| 생략 또는 `false` | Risk 승인을 받은 BUY / SELL도 실행하지 않고 `REJECTED`, `ORDER_EXECUTION_DISABLED`로 기록한다. |
| `true` | 기존 Risk 검증을 통과한 BUY / SELL만 실행 Handler에 전달한다. |

주문 비활성화는 스케줄러나 Agent 실행의 중단이 아니다. 활성 스케줄러는
데이터 조회와 판단을 계속할 수 있고 Run과 거절 이력을 저장한다.
Risk 거절과 승인된 HOLD의 기존 동작은 유지된다. 현재 Harness는 주문
차단에 따른 거래 `REJECTED`를 `EXECUTE_TRADE` 단계와 Run의 `FAILED`로
기록하므로, 실패 상태만으로 서버 장애라고 판단하지 말고 사유를 확인한다.

비활성 상태에서는 해당 BUY / SELL로 포트폴리오를 변경하거나 Broker에
주문을 접수하지 않는다. Broker 잔액 조회, 포트폴리오 초기화와 DB 쓰기를
모두 막는 설정은 아니다. 기존 주문의 체결 조회·취소·체결 반영 경로도
이 설정으로 차단하지 않으며, 별도 스케줄러 활성화 여부를 확인해야 한다.

이 관측 프로필은 허용 여부와 관계없이 거래 모드를 `VIRTUAL`로 유지한다.
`true`는 가상 거래를 허용하는 값이지 `BROKER` 모드나 실전 전환을
허용하는 값이 아니다. 판단만 관측할 때는 다음 값을 유지한다.

```text
TRADE_EXECUTION_ORDERS_ENABLED=false
```

가상 거래 결과도 관측하려면 시작 전 점검을 마친 뒤 운영자가 명시적으로
`true`를 선택한다. 설정 변경은 앱 컨테이너를 생성·재생성하고 새 앱을
시작해야 적용된다. `.env` 수정이나 기존 컨테이너의 `start`·`restart`만으로
반영되지 않으며 실행 중 즉시 전환하는 Kill Switch가 아니다. 이미
접수된 주문을 취소하거나 기존 보유 종목을 자동 청산하지도 않는다.
Handler 또는 Broker Provider 직접 호출까지 차단하는 전역 권한으로
간주하지 않는다.

## 시작 전 점검

### 1. 실행 위치와 Compose 프로젝트 고정

이 문서의 명령은 프로젝트 루트와 같은 PowerShell 세션에서 실행한다.
의도한 Docker context와 엔진을 대상으로 하는지, Compose v2를 사용할
수 있는지 먼저 확인한다.

```powershell
docker version
docker compose version
docker context show
docker compose ls --all
```

사용할 프로젝트 이름을 정하고 이후 모든 명령에서 동일하게 사용한다.
신규 환경의 예시는 다음과 같다.

```powershell
$paperProject = 'stock-agent-paper-observation'
docker compose -p $paperProject --env-file .\.env -f .\compose.yml config --quiet
```

기존 환경이 있다면 `docker compose ls --all`로 확인한 이름을
`$paperProject`에 설정한다. 프로젝트 이름을 바꾸면 별도의 컨테이너와
DB 볼륨을 만들 수 있으므로 기존 환경의 이름을 먼저 확인한다.

현재 named volume은 `stock-agent-mysql-data`이며 실제 Docker 이름은 보통
`<프로젝트명>_stock-agent-mysql-data`이다. 기존 데이터가 있다면 실제
볼륨과 프로젝트의 대응을 확인하고, 이름을 바꿔 빈 DB가 나타난 것을
데이터 복구나 초기화 성공으로 해석하지 않는다.

```powershell
docker volume ls --filter "label=com.docker.compose.project=$paperProject"
```

기존 중요 데이터가 있는 DB는 앱 시작 시 Flyway migration이 적용될 수
있다. 백업·복원 검증이 끝나지 않았다면 해당 DB를 관측 실험에 재사용하지
않는다. `.env`의 MySQL 사용자·비밀번호·DB 이름을 바꿔도 기존 볼륨의
사용자와 데이터가 자동으로 다시 초기화되는 것은 아니다.

### 2. 관측 설정과 자동 실행 여부 확인

- 모의투자 자격증명 네 값이 비어 있지 않고 모의계좌용인지 확인한다.
  실전 URL, 다른 프로필, `BROKER` 실행 모드로 덮어쓰지 않는다.
- 최초 서버·DB 점검에서는 `.env`의 `HARNESS_SCHEDULER_ENABLED`와
  `MARKET_PRICE_HISTORY_COLLECTION_SCHEDULER_ENABLED`를 `false`로 두고,
  `MARKET_PRICE_HISTORY_COLLECTION_BOOTSTRAP_ENABLED=false`와
  `TRADE_EXECUTION_ORDERS_ENABLED=false`를 유지한다.
  변경된 값은 앱 컨테이너를 생성·재생성할 때 적용되며 실행 중 앱을
  즉시 멈추는 스위치가 아니다. 기존 컨테이너의 `start`나 `restart`만으로
  변경된 `.env`가 반영되지는 않는다.
- 관측을 시작할 때만 필요한 스케줄러를 각각 명시적으로 켠다.
  Harness는 `HARNESS_SCHEDULER_ENABLED=true`, 일봉 수집은
  `MARKET_PRICE_HISTORY_COLLECTION_SCHEDULER_ENABLED=true`로 설정한다. 현재 기본
  Harness 설정에는 단타·스윙·장기 전략이 모두 포함되어 있으므로 스윙만
  실행되는 환경이라고 가정하지 않는다. 전략별 시간창과 실행 주기를 확인한다.
- 두 스케줄러의 활성화와 주문 허용은 별도로 선택한다. 판단만 관측하면
  주문 허용은 `false`, 가상 거래까지 관측하면 점검 후 `true`로 정한다.
  어떤 단계든 설정 해석 결과가 의도와 다르면 앱 시작을 보류한다.
- 기본 일봉 수집 cron은 월~금 `20:15` (`Asia/Seoul`)이며 시작 시 수집은
  꺼져 있다. 초기 일봉이 없는 DB는 예정 수집 전까지 필요한 데이터가
  부족할 수 있다. 부족한 상태에서 Run을 반복하여 성공으로 처리하지 않는다.
- 쉘 환경변수는 `.env` 값을 덮어쓸 수 있다. IDE 실행 인자, 다른 Compose
  파일, 추가 프로필 또는 수동 API 호출도 자동 실행 점검 대상이다.

다음은 Compose가 전달할 값 중 비밀이 아닌 항목만 출력하는 예시다.
JSON 전체와 `$paperResolved` 자체를 출력하거나 공유하면 자격증명이
노출될 수 있으므로 선택된 항목만 확인한다. 앱 내부의 최종 설정까지
검증하는 명령은 아니므로 위 프로필과 설정 파일 점검도 필요하다.
주문 허용, 두 스케줄러, bootstrap과 바인딩 주소의 출력이 선택한 실행 단계의 기대값과
다르면 시작을 보류하고 `.env`와 쉘 환경변수를 대조한다.

```powershell
$paperResolved = docker compose -p $paperProject --env-file .\.env -f .\compose.yml config --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $null -eq $paperResolved) {
    throw 'Compose 설정 확인 실패. 시작하지 않는다.'
}
$paperAppPort = [int]$paperResolved.services.app.ports[0].published
$paperDbPort = [int]$paperResolved.services.mysql.ports[0].published
$paperAppBindAddress = $paperResolved.services.app.ports[0].host_ip
$paperDbBindAddress = $paperResolved.services.mysql.ports[0].host_ip
$paperResolved.services.app.environment |
    Select-Object SPRING_PROFILES_ACTIVE, DB_HOST, DB_PORT,
        TRADE_EXECUTION_ORDERS_ENABLED, HARNESS_SCHEDULER_ENABLED,
        MARKET_PRICE_HISTORY_COLLECTION_SCHEDULER_ENABLED,
        MARKET_PRICE_HISTORY_COLLECTION_BOOTSTRAP_ENABLED,
        AGENT_NEXT_ACTION_PROVIDER_TYPE,
        MOVING_AVERAGE_ORDER_DECISION_PROVIDER_TYPE, OPENAI_ENABLED
[pscustomobject]@{ AppBindAddress = $paperAppBindAddress; AppHostPort = $paperAppPort; MySqlBindAddress = $paperDbBindAddress; MySqlHostPort = $paperDbPort }
Remove-Variable paperResolved
```

### 3. 앱 한 인스턴스만 실행

같은 DB·모의계좌·전략에 연결되는 앱은 한 인스턴스만 운영한다.
IntelliJ, `bootRun`, `java -jar`, 다른 worktree의 Compose, 다른 호스트의
앱도 포함한다. 실행 이력 조회나 종목 중복 검사만으로 다중 인스턴스의
동시 실행을 막는다고 가정하지 않는다.

```powershell
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Select-Object ProcessId, ParentProcessId, Name
```

Java 목록만으로 어떤 앱인지 단정하지 말고 PID를 IDE 실행 상태와
대조한다. 다른 Java 프로그램을 일괄 종료하지 않는다. 동일 앱이 있으면
아래 종료 절차로 먼저 종료하고, 다른 호스트의 실행 여부도 확인한다.
이미 실행 중인 관측 앱에는 상태 조회만 수행한다.

시작 명령의 `--scale app=1`은 **선택한 Compose 프로젝트 안에서만** 앱을
하나로 제한한다. 같은 프로젝트 이름을 유지하고 다른 앱을 먼저 종료하는
운영 절차가 함께 필요하다. 포트 변경, 다른 프로젝트 이름, `docker compose
run app`으로 두 번째 앱을 우회 실행하지 않는다.

### 4. 포트 충돌 확인

위에서 확인한 실제 호스트 포트를 사용한다. 기본값은 앱 `127.0.0.1:8080`,
DB `127.0.0.1:3307`이고 컨테이너 사이의 DB 포트는 항상 현재 Compose의 `3306`이다.

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in @($paperAppPort, $paperDbPort) } |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

리스너가 있으면 PID와 `docker ps`를 대조하여 기존 앱, MySQL 또는 다른
서비스인지 확인한다. 기존 관측 컨테이너의 리스너라면 새 앱을 만들지
않고 상태를 확인한다. 무관한 서비스의 포트와 충돌할 때만 `.env`의
`APP_PORT` 또는 호스트용 `DB_PORT`를 조정하고 설정 확인을 다시 수행한다.
임의로 프로세스를 강제 종료하지 않는다.

포트가 비어 있어도 다른 포트나 호스트에서 같은 DB·계좌에 연결한 앱이
없다는 증거는 아니다. 포트 점검과 단일 인스턴스 점검을 모두 통과해야 한다.
기본 포트 매핑은 `127.0.0.1`에만 바인딩한다. `APP_BIND_ADDRESS`나
`DB_BIND_ADDRESS`를 변경했다면 실제 외부 노출 범위와 방화벽도 확인한다.

### 5. 시작을 보류하는 조건

모의투자 설정이 불명확하거나, 중복 앱을 배제하지 못하거나, 기존 DB와
볼륨을 식별하지 못하면 시작을 보류한다. DB 인증·migration 실패,
미해결 Broker 주문, 이전 비정상 종료의 미확인 기록도 먼저 조사한다.
이력 초기화, 볼륨 삭제 또는 Broker 주문 재전송으로 문제를 덮지 않는다.

## 시작과 상태 확인

### 앱 시작

위 점검을 마치고 자동 실행 여부를 정한 뒤 실행한다. 이미 활성
Run이나 수집이 진행 중이면 이 명령으로 앱을 교체하지 않는다.

```powershell
docker compose -p $paperProject --env-file .\.env -f .\compose.yml up -d --build --scale app=1
docker compose -p $paperProject --env-file .\.env -f .\compose.yml ps --all
```

`mysql`은 healthcheck 통과 후 앱 시작의 선행 조건이다. `app`은 컨테이너
내부의 `GET /api/health`를 curl로 확인한다. DB 연결이 정상이면 HTTP 200,
실패하면 503을 반환하므로 HTTP 오류도 healthcheck 실패로 처리한다.
10초 간격, 요청 제한 3초, healthcheck 제한 5초, 시작 유예 60초,
재시도 12회다. 런타임 이미지에 curl을 설치한다. `Up`만으로 정상이라고
판정하지 않고 `healthy`와 응답 본문을 확인한다. healthcheck는 상태를
표시하며 컨테이너를 자동 재시작하는 정책은 아니다.
선택한 프로젝트의 앱이 하나인지 `docker ps`에서도 다시 확인한다.

스케줄러를 끈 최초 점검이 끝난 뒤 관측을 켜려면 아래 종료 절차로 기존
앱의 종료를 확인하고, 필요한 스케줄러 값을 명시적으로 `true`로 변경하여 같은 프로젝트로
다시 시작한다. 주문 허용은 판단만 관측할지 가상 거래까지 관측할지에
따라 별도로 정한다. `.env` 수정만으로 실행 중 앱에 반영되지는 않는다.

### 로그와 조회 API

```powershell
docker compose -p $paperProject --env-file .\.env -f .\compose.yml logs --tail=200 app mysql
docker compose -p $paperProject --env-file .\.env -f .\compose.yml logs --follow --tail=100 app
```

`Ctrl+C`는 로그 추적만 끝내며 백그라운드 컨테이너를 종료하지 않는다.
다음 내용을 확인하고 관측 시작 시각, 프로젝트명과 변경 버전을 기록한다.

- 앱의 활성 프로필과 시작 완료, MySQL 접속, Flyway 적용/검증 및 Hibernate
  스키마 검증에 오류가 없는지 확인한다.
- 최초 점검에서는 `Harness scheduler triggered`나 일봉 수집 완료 로그가
  새로 발생하지 않아야 한다. 자동 실행이 발견되면 앱을 먼저 종료하고 설정을 조사한다.
- 관측을 켠 상태에서는 `Investment Harness started`, `Investment Harness
  finished` 또는 실패 로그, `Harness scheduler completed`와 저장된 Run을
  대조한다. 실패·Risk 거절·API 한도·인증 오류도 관측 결과로 기록한다.
- 주문 비활성 상태의 BUY / SELL은 `ORDER_EXECUTION_DISABLED` 거절과
  Run의 `FAILED`로 기록될 수 있다. 판단과 차단 이력이 남았는지 확인하고
  Provider 오류나 다른 실패를 설정 차단으로 일괄 분류하지 않는다.
- 일봉 수집은 `Daily price history scheduled collection completed`의
  `status`, `fetchedCount`, `savedCount`와 DB의 최신 거래일을 함께 확인한다.
  실행 시간창 밖의 skip 로그나 수집 전 빈 테이블 자체는 서버 장애의 증거가 아니다.

서버와 저장 이력 조회는 다음 GET으로 확인한다. 포트를 바꿨다면 위에서
얻은 `$paperAppPort`를 사용한다.

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$paperAppPort/api/harness/runs" -TimeoutSec 10
```

정상 응답과 빈 배열은 저장 이력이 없을 수 있다는 뜻이다. Broker 인증,
데이터 신선도, 전략 성과 또는 종료 정합성까지 증명하지 않는다.
점검을 위해 `POST /api/harness/run`, `/api/harness/reset`, `/api/portfolio/reset`을
호출하지 않는다. `GET /api/portfolio`도 초기 상태가 없으면 Broker 조회와
DB 저장을 유발할 수 있으므로 단순 연결 확인에 사용하지 않는다.

### DB 상태 확인

앱 로그와 함께 DB 자체를 확인한다. 아래는 기본 DB 사용자와 이름의
예시이며 변경했다면 실제 값으로 바꾼다. 비밀번호는 프롬프트에 입력하고
명령행·로그에 포함하지 않는다.

```powershell
docker compose -p $paperProject --env-file .\.env -f .\compose.yml exec mysql mysql --user=stock --password --database=stock_agent_harness
```

MySQL 프롬프트에서 조회만 수행한다.

```sql
SELECT DATABASE(), CURRENT_USER(), NOW(), @@session.time_zone;
SHOW TABLES;
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;

SELECT run_id, strategy_id, strategy_version, horizon, status, started_at, finished_at
FROM harness_run_entity ORDER BY started_at DESC LIMIT 20;
SELECT strategy_id, strategy_version, horizon, JSON_VALID(snapshot_json) AS snapshot_json_valid
FROM strategy_portfolio ORDER BY strategy_id, strategy_version, horizon;
SELECT symbol, COUNT(*) AS bar_count, MAX(trading_date) AS latest_trading_date
FROM daily_price_bar GROUP BY symbol;
SELECT status, COUNT(*) AS order_count FROM broker_order GROUP BY status;
SELECT run_id, status, cumulative_filled_quantity, portfolio_applied_quantity,
       cumulative_filled_amount_krw, portfolio_applied_amount_krw
FROM broker_order
WHERE status IN ('PENDING', 'PARTIALLY_FILLED')
   OR cumulative_filled_quantity > portfolio_applied_quantity
   OR cumulative_filled_amount_krw > portfolio_applied_amount_krw;
```

Flyway 이력에 현재 migration `V1`~`V6`의 성공이 있는지, 로그의 Run이
전략·버전별로 저장되었는지, 포트폴리오 JSON이 유효한지 확인한다.
필요한 Run의 `harness_step_entity`, `trade_record_entity`,
`current_price_observation`도 `run_id`로 대조한다. JSON 문법의 유효성만으로
잔고·체결·거래비용 정합성이 증명되지는 않는다.

이 프로필의 가상 거래가 새로운 Broker 주문 행을 만든다고 기대하지
않는다. 기존 미완료 주문이나 미반영 체결이 있으면 관측 시작을 보류하고
별도 정합성 검증 대상으로 남긴다. 비활성화된 정합성·취소 스케줄러가
이를 자동으로 처리한다고 가정하지 않는다. `exit`로 MySQL 클라이언트를
나가면 DB 서버는 계속 실행된다.

기본 바인딩에서는 호스트 DB 도구로 `127.0.0.1:$paperDbPort`에 접속한다. 앱의 DB
주소는 `mysql:3306`이므로 호스트용 포트를 앱 내부 주소로 사용하지 않는다.

## 안전한 종료 절차

일반 종료는 **신규 실행 차단 확인 → 진행 작업 확인 → 앱 종료 → DB와
로그 확인·보존 → MySQL 종료 → 컨테이너 정리** 순서로 진행한다.
다음 절차가 진행 중 작업의 완료를 보장하는 기능으로 구현·검증된 것은 아니다.

1. 수동 실행 요청을 보내는 클라이언트를 멈추고 다른 앱 인스턴스가 없는지
   확인한다. 계획된 종료는 전략 실행 시간창과 일봉 수집 시각을 피한다.
   `.env`의 스케줄러나 주문 허용 값을 `false`로 바꾸는 것만으로 현재 앱의
   신규 실행·주문이 즉시 차단되지는 않는다. 런타임 차단·작업 배출 기능은
   아직 검증 대상이다.
2. 최근 로그의 Harness 시작과 종료/실패, 수집 완료를 대조하고 DB 저장을
   확인한다. `Investment Harness finished`는 이력 저장 전에 출력되므로
   로그만 보고 저장 완료로 판정하지 않는다. Run 이력은 종료 시 저장되어
   진행 중 Run이 목록에 없을 수 있고, 시작 로그에는 `runId`가 없으므로
   전략·버전·시각도 함께 대조한다. 작업 종료를 확인할 수 없다면 일반 종료
   완료로 기록하지 않는다.
3. 앱부터 종료한다. 아래 `60`초는 운영 예시이며 작업 완료가 보장된
   제한시간이 아니다. 실제 종료시간과 timeout 초과 동작은 검증해야 한다.

   ```powershell
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml stop --timeout 60 app
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml ps --all
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml logs --tail=200 app
   ```

4. 앱이 종료 상태인지 확인하고 실행 중이면 다음 단계로 넘어가지 않는다.
   MySQL은 계속 켜 둔 상태에서 위 SQL로 마지막 Run, 단계·거래 기록과
   포트폴리오를 대조한다. 종료 시각, 종료 상태, 누락·불일치, 미완료 주문
   여부를 기록하고 필요한 로그와 조회 결과를 접근이 제한된 저장소에
   보존한다. 컨테이너를 제거하면 기존 컨테이너 로그를 조회하지 못할 수
   있으므로 정리 전에 보존한다. 로그에 포함될 수 있는 계좌정보·토큰을
   공개하거나 저장소에 커밋하지 않는다.
5. 확인이 끝나면 MySQL을 종료하고 종료 로그를 확인한 후 정리한다.

   ```powershell
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml stop --timeout 60 mysql
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml ps --all
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml logs --tail=100 mysql
   ```

   MySQL도 종료 상태인지, shutdown 오류나 timeout이 없었는지 확인하고
   종료 로그를 보존한 뒤 컨테이너와 네트워크를 정리한다.

   ```powershell
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml down --timeout 60
   docker compose -p $paperProject --env-file .\.env -f .\compose.yml ps --all
   docker volume ls --filter "label=com.docker.compose.project=$paperProject"
   ```

   포트 점검도 다시 수행하여 리스너가 해제됐는지 확인한다. 남아 있다면
   다른 컨테이너나 프로세스인지 조사한다. 기본 `down`은 named volume을
   유지하지만, 볼륨 존재가 백업이나 데이터 정합성의 증거는 아니다.

`down -v`, `docker volume rm`, volume prune은 관측 종료 절차에 포함하지
않는다. 데이터 삭제는 별도 승인과 복구 검증이 필요한 작업이다.

긴급 중단이 필요하면 앱을 우선 멈추고 DB와 증거를 유지한다. timeout으로
강제 종료되었거나 진행 작업을 확인하지 못한 경우에는 비정상 종료로
기록하고, 다음 시작 전에 누락된 Run·단계·거래와 포트폴리오 불일치를
조사한다. 앱 종료가 Broker의 잔존 주문을 취소하는 것은 아니다.

## 아직 검증이 필요한 운영 항목

다음은 완료된 기능이나 성공한 운영 절차로 간주하지 않는다. 별도 검증 시
환경·버전·시각·실패 조건과 실제 결과를 기록한다.

| 항목 | 필요한 검증과 남은 위험 |
| --- | --- |
| 실제 기동과 상태 확인 | Docker 빌드, 앱 한 인스턴스, DB 인증, Flyway, 조회 API, 예정 수집과 Run 저장을 실제 환경에서 확인해야 한다. MySQL healthcheck와 앱 `Up`만으로 전체 정상 판정은 불가능하다. |
| 다중 실행 차단 | 현재 Harness의 이력 조회와 실행 사이에 분산 잠금이 없다. 다른 프로젝트·호스트 또는 수동 요청과 스케줄러의 동시 실행에 대한 차단은 별도 검증/개선이 필요하다. 단일 인스턴스 운영 규칙만으로 모든 동시 호출을 막지는 못한다. |
| 종료와 재시작 정합성 | 신규 실행 차단, 진행 작업 완료 대기, SIGTERM/timeout, DB 장애 중 종료를 검증해야 한다. Run·단계·거래·포트폴리오 저장이 모두 함께 완료된다고 가정하지 않는다. 재시작 뒤 같은 볼륨에서 기록·잔고 유지와 중복 실행 여부를 대조한다. |
| 백업·복원 | [백업 및 격리 복원 도구와 절차](operations/mysql-backup-restore-verification.md)를 추가했다. 실제 DB 복원 성공은 미확인이다. 쓰기 주체를 중지한 상태에서 새 볼륨·네트워크 없는 MySQL에만 복원하고, 행 수·Flyway 이력과 SQL 내용 해시를 비교한다. 복원 앱은 실행하지 않으며, named volume 유지나 파일 생성만으로 복구 성공을 판정하지 않는다. |
| Broker 계좌 정합성 | 가상 잔고와 Broker 모의계좌를 구분하고, 기존 미완료 주문·체결 반영·재시작 후 상태를 확인해야 한다. 현재 프로필에서는 주문 정합성·취소 자동 처리가 꺼져 있다. |
| 접근 통제와 비밀 보호 | 기본 호스트 포트는 `127.0.0.1` 전용이지만 API 인증 통제가 마련되어 있지 않다. 기존 `.env`·셸 환경변수의 바인딩 재정의, 실제 외부 접근 차단과 로그·설정 출력의 자격증명 노출 여부를 검증해야 한다. |
| 저장 공간과 장애 대응 | Compose에 로그 순환과 재시작 정책이 명시되어 있지 않다. 로그·DB 용량, 디스크 부족, API 오류·rate limit, 컨테이너 종료 감지와 복구 절차를 검증해야 한다. |

## 격리 Runtime Smoke 검증 기록 — 2026-10-06

**1차 시도 결과: `BLOCKED_DOCKER_ENGINE`. 이 시도는 실제 기동 성공으로 판정하지 않았다.**
루트 `AGENTS.md`를 읽고 요청된 Compose healthcheck와 격리 설정만 변경했다.
Docker context는 `desktop-linux`, Docker Client는 `29.5.3`, Compose는 `v5.1.4`다.
`docker version`과 `docker info --format '{{.ServerVersion}}'`에서 다음 오류가
발생했다. 서버 버전은 확인되지 않았다.

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

엔진 연결 실패 후 이미지 빌드, `up`, `stop`, `restart`, `down`을 실행하지
않았다. Docker Desktop이나 다른 Docker context를 임의로 시작·전환하지
않았다. 실제 `.env`와 KIS 인증정보를 읽거나 전달하지 않았고, 기존
컨테이너·DB 볼륨에 접근하거나 변경하지 않았다. 커밋과 푸시도 하지 않았다.

| 확인 항목 | 결과와 근거 |
| --- | --- |
| 앱 healthcheck 연결 | `compose.yml`에서 컨테이너 내부 `/api/health`를 curl로 조회하고 HTTP 오류를 실패로 처리한다. Dockerfile 런타임에 curl을 설치하도록 변경했다. 이미지 빌드는 미실행이다. |
| 격리 Compose 정적 검증 | 빈 전용 env 파일을 명시하고 `compose.yml` + `compose.runtime-smoke.yml`의 `config --quiet`, JSON 해석 및 설정 단언을 통과했다. 엔진 연결이나 런타임 성공을 의미하지 않는다. |
| 프로젝트명 | 정적 검증에 `stock-agent-runtime-smoke-20261006-b48dc792`를 사용했다. 실제 리소스 생성은 없었다. |
| 프로필·외부 연동 | `local`만 활성화. KIS, Harness, 주식·지수 일봉 bootstrap/scheduler/backfill, 주문 정합성·취소 scheduler, 수동 runner, 주문 실행, OpenAI 등 비활성화 플래그 16개가 모두 `false`다. Provider는 둘 다 `RULE_BASED`, 거래 모드는 `VIRTUAL`이다. |
| 인증정보·환경 격리 | KIS 인증정보 4개와 OpenAI API key는 공란이다. `KIS_PAPER_*`는 상속하지 않는다. 앱·MySQL environment 전체를 `!override`로 교체하고 전용 테스트 DB 자격증명만 사용한다. |
| 포트·DB·볼륨 | 앱 `127.0.0.1:18080`, MySQL `127.0.0.1:13307`, DB `runtime_smoke`. 기존 기본 포트는 병합되지 않았다. 볼륨은 `stock-agent-runtime-smoke-20261006-b48dc792_runtime-smoke-mysql-data`, 네트워크는 같은 프로젝트의 `_default`로 해석됐다. 포트 충돌과 실제 mount는 미검증이다. |
| 기존 health API 테스트 | `./gradlew.bat test --tests com.stock.health.api.HealthControllerTest --no-daemon` 성공. 7개, 실패·오류·skip 0개. DB 정상/실패의 200/503과 Broker 조회·포트폴리오 초기화 미호출을 검증한다. Mockito/H2 테스트이며 Compose/MySQL 기동 검증이 아니다. |
| 앱·MySQL 실제 상태 | **미검증** — Docker 엔진 연결 실패. |
| Flyway V1~V6 적용·Hibernate validate | **미검증** — 격리 MySQL과 앱을 기동하지 못했다. |
| 정상 종료·재시작·격리 DB 데이터 유지 | **미검증** — 데이터 삽입이나 재시작을 실행하지 않았다. |

### 격리 검증 재현 절차

아래는 엔진 연결 복구 후 격리 검증을 재현하는 절차다. 기존 관측 환경의 `.env`를 사용하는
앞 절 명령과 혼용하지 않는다. `!override`를 위해 Compose 2.24.4 이상이
필요하다. 새 프로젝트명을 매번 생성하고, 모든 명령에 같은 `$smokeArgs`를
사용한다. 전용 빈 env 파일은 `.gradle` 아래에 두어 실제 `.env` 자동 로딩을
막는다. 고정 포트가 사용 중이면 기존 프로세스를 종료하지 않고 검증을 중단한다.

```powershell
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker 엔진 연결 실패: 실제 기동 검증 중단.' }
$smokeProject = ('stock-agent-runtime-smoke-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)).ToLowerInvariant()
$smokeEnvDirectory = Join-Path (Get-Location) '.gradle\runtime-smoke'
[void][IO.Directory]::CreateDirectory($smokeEnvDirectory)
$smokeEnvFile = Join-Path $smokeEnvDirectory 'empty.env'
[IO.File]::WriteAllText($smokeEnvFile, '')
$smokeArgs = @('compose', '-p', $smokeProject, '--env-file', $smokeEnvFile, '-f', '.\compose.yml', '-f', '.\compose.runtime-smoke.yml')
docker @smokeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose 설정 오류: 시작하지 않는다.' }
Get-NetTCPConnection -State Listen -ErrorAction Stop |
    Where-Object { $_.LocalPort -in @(18080, 13307) } |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

시작 전에 선택한 프로젝트의 컨테이너·네트워크와
`${smokeProject}_runtime-smoke-mysql-data` 볼륨이 이미 존재하지 않는지 확인한다.
기존 리소스가 있거나 포트가 점유돼 있으면 진행하지 않는다. 설정 JSON에서
위 표의 비활성화·인증정보·단일 포트·전용 볼륨 값을 재확인한다. 아래 각
Docker 명령은 종료 코드 0을 확인한 뒤 다음 단계로 진행한다.

| 단계 | 명령과 통과 기준 |
| --- | --- |
| 최초 기동 | `docker @smokeArgs up --build --detach --wait --wait-timeout 180 --scale app=1`. `docker @smokeArgs ps --all`에서 앱 한 개와 MySQL이 모두 `healthy`여야 한다. |
| 앱·DB health | `Invoke-RestMethod http://127.0.0.1:18080/api/health -TimeoutSec 10`의 `status`, `app`, `db`가 모두 `UP`이어야 한다. `docker @smokeArgs logs --no-color app`에서 Flyway 적용·Hibernate validate·앱 시작 완료를 확인한다. 자동 수집·Harness 실행·외부 호출 흔적이 있으면 실패다. |
| DB 기준값 저장 | 아래 SQL로 V1~V6 성공 이력, checksum, 실행·거래·주문 행 수를 저장하고 격리 DB에만 표식 행을 삽입한다. `docker @smokeArgs ps -q mysql`로 얻은 컨테이너의 `/var/lib/mysql` mount가 전용 볼륨인지 확인한다. |
| 정상 종료 | `docker @smokeArgs stop --timeout 30 app`, 이어서 `docker @smokeArgs stop --timeout 30 mysql`. `ps --all`과 State에서 둘 다 `exited`인지 확인한다. JVM은 SIGTERM 종료 코드 143일 수 있으므로 Docker 이벤트의 signal 15, Tomcat graceful shutdown 및 Hikari shutdown 완료 로그를 함께 확인한다. MySQL은 종료 코드 0과 shutdown 완료를 확인한다. SIGKILL/timeout, OOM 또는 shutdown 오류가 있으면 실패다. |
| 종료 후 재기동 | `docker @smokeArgs up --detach --wait --wait-timeout 180 --scale app=1`. health, Flyway 이력·checksum, mount, 표식 행과 실행·거래·주문 행 수가 기준값과 같아야 한다. |
| 재시작 | 앱 `stop --timeout 30 app` → DB `restart --timeout 30 mysql` → DB `up --detach --wait --wait-timeout 180 mysql` → 앱 `up --detach --wait --wait-timeout 180 --scale app=1 app` 순서로 각각 `docker @smokeArgs`를 실행한다. 앱을 DB의 `healthy` 확인 뒤 시작하고 health와 DB 기준값을 비교한다. 일괄 `restart` 뒤 복구됐다는 사실만으로 재시작 성공을 판정하지 않는다. |
| 검증 후 정리 | 로그·조회 결과를 기록한 뒤 앱, MySQL 순서로 `stop --timeout 30`하고 `docker @smokeArgs down --timeout 30`한다. 해당 프로젝트의 컨테이너·네트워크만 제거되고 전용 DB 볼륨은 유지돼야 한다. `-v`, volume 삭제·prune은 사용하지 않는다. |

격리 MySQL 접속은 `docker @smokeArgs exec mysql mysql -u smoke -p runtime_smoke`를
사용하고 프롬프트에 전용 테스트 비밀번호를 입력한다. 앱 API로 Harness나
포트폴리오를 실행·초기화하지 않는다. 다음 표식 테이블은 격리 DB 검증용이며
프로젝트 migration에 추가하지 않는다.

```sql
SELECT version, description, checksum, success
FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;
SELECT COUNT(*) AS run_count FROM harness_run_entity;
SELECT COUNT(*) AS trade_count FROM trade_record_entity;
SELECT COUNT(*) AS order_count FROM broker_order;
CREATE TABLE runtime_smoke_probe (
    probe_id VARCHAR(64) PRIMARY KEY,
    marker VARCHAR(64) NOT NULL
);
INSERT INTO runtime_smoke_probe VALUES ('restart-probe', 'persist-after-restart');
SELECT probe_id, marker FROM runtime_smoke_probe;
```

최초 기동에서 Flyway 버전 `1`~`6`이 각각 한 번 성공하고, 실행·거래·주문
행 수가 모두 0이어야 한다. 재기동·재시작 뒤에는 `CREATE`/`INSERT`를 반복하지
않고 `SELECT`만 수행한다. 동일한 표식 행, migration checksum과 행 수 유지가
확인돼야 데이터 유지로 판정한다. 실제 통과 여부와 종료 시각을 새 검증 기록에
남기고 이번 `BLOCKED_DOCKER_ENGINE` 결과를 기동 성공으로 덮어쓰지 않는다.

## 실제 격리 검증 결과 — 2026-10-06 재시도

**최종 결과: `PASS_WITH_ORDERED_RESTART`. 기동, 정상 종료, DB 준비를 기다리는
재시작 절차와 데이터 유지가 확인됐다. 일괄 `compose restart`는 실패 사례다.**
앞 절의 엔진 연결 실패 기록은 1차 시도 이력으로 보존한다. 이번에는 Docker
Desktop 실행 후 `desktop-linux` 엔진 `29.5.3`, Compose `v5.1.4`에 연결했다.
실제 검증·정리 시간은 `2026-10-06 11:04:37`~`11:16:51` KST다.

| 환경 | 실제 사용값 |
| --- | --- |
| 새 프로젝트 | `stock-agent-runtime-smoke-20261006t020355z-9798d4b7` |
| 호스트 포트 | 앱 `127.0.0.1:18080`, MySQL `127.0.0.1:13307`; 시작 전 점유 없음 확인 |
| DB·새 볼륨 | DB `runtime_smoke`, `stock-agent-runtime-smoke-20261006t020355z-9798d4b7_runtime-smoke-mysql-data`; 사전 목록에 없었고 실제 mount와 Compose 소유 label 확인 |
| 프로필·인증정보 | `local`만 활성화. 빈 전용 `--env-file` 사용. 실제 앱 environment를 inspect해 KIS 인증정보 4개와 OpenAI API key 공란, `KIS_PAPER_*` 미상속 확인 |
| 실행 차단 | 실제 앱 environment의 비활성화 플래그 16개를 해석된 설정과 대조. KIS·Harness·주식/지수 일봉 수집·주문·OpenAI 비활성화, `VIRTUAL` 및 두 `RULE_BASED` Provider 확인 |
| 빌드 이미지 | `Dockerfile`의 Java 21 런타임과 curl로 빌드. 앱 image ID `sha256:79b21434d13fcfdaac05056326ebd9646a5eb03f4abe4c5fe1f889a60918d802` |

| 검증 단계 | 관측 시각 KST | 실제 결과 |
| --- | --- | --- |
| 이미지 빌드·최초 확인 | 11:08:35 | Docker build/up 종료 코드 0. 앱·MySQL 모두 `healthy`; `/api/health` HTTP 200, `{"status":"UP","app":"UP","db":"UP"}`. 새 DB에 V1~V6 성공 이력 확인, Hibernate validate 후 앱 시작 완료. |
| 앱 → DB 정상 종료 | 11:09:17 / 11:10:27 | 앱 SIGTERM(signal 15), 종료 코드 143. Tomcat graceful shutdown, JPA·Hikari shutdown 완료. MySQL 종료 코드 0 및 shutdown 완료. OOM·SIGKILL 없음. |
| 종료 후 재기동 | 11:11:56 | 두 컨테이너 `healthy`, HTTP 200/UP. 동일 볼륨, 표식 행, V1~V6 checksum 유지. |
| 일괄 `compose restart` | 11:12:17 | **실패 사례**: DB 준비 중 앱이 시작돼 Flyway의 DB 연결 실패(`Communications link failure`)로 앱 초기화가 한 번 실패했다. 뒤이은 `up --wait`로 11:12:55에 복구됐고 데이터는 유지됐으나, 이를 오류 없는 재시작 성공으로 판정하지 않았다. |
| 순서를 제어한 재시작 | 11:14:21~11:15:01 | 앱 stop → MySQL restart → MySQL healthy 대기 → 앱 up/healthy 대기. 모든 Docker 명령 종료 코드 0. 해당 구간에 앱 초기화 오류 없이 Flyway 6개 검증·앱 시작 완료. HTTP 200/UP, 동일 표식과 checksum 유지. |
| 최종 종료·정리 | 11:16:02~11:16:51 | 앱 graceful 종료(143), DB shutdown 완료(0). `down` 종료 코드 0. 이번 프로젝트 컨테이너·네트워크 제거, 이번 DB 볼륨 유지. 볼륨 삭제·prune 미실행. |

`runtime_smoke_probe`에 최초 확인 때만 넣은
`restart-probe|persist-after-restart` 행은 재기동·재시작 후 모두 정확히 1개로
유지됐다. 비교 단계에서 표식 재삽입은 하지 않았다. 각 단계의 Run, 거래,
Broker 주문, 포트폴리오, 주식·지수 일봉 행 수는 모두 0이다. 실제 기동 로그에
`Harness scheduler is disabled`가 있고 자동 수집·주문 실행 흔적은 없었다.

Flyway 이력은 각 버전이 한 번 성공했으며 아래 checksum은 모든 비교 단계에서
동일했다.

| 버전 | checksum | success |
| --- | --- | --- |
| 1 | -854770445 | 1 |
| 2 | -174800260 | 1 |
| 3 | -1797630604 | 1 |
| 4 | -575525917 | 1 |
| 5 | 1968956706 | 1 |
| 6 | -822632640 | 1 |

실제 `.env`와 KIS 인증정보는 읽거나 사용하지 않았다. 모든 SQL·inspect·stop·
restart·down 대상은 소유 label과 전용 mount를 확인한 이번 프로젝트였다.
기존 DB에는 접속하거나 SQL을 실행하지 않았다. 전후 메타데이터 목록을 비교해
기존 컨테이너 4개의 ID·실행 상태, 기존 볼륨 2개와 기존 네트워크 목록이
동일함을 확인했다. 추가된 DB 볼륨은 위 전용 볼륨 1개뿐이며 검토를 위해 유지한다.

관측된 제한은 두 가지다. 일괄 재시작의 DB 준비 순서 문제 때문에 위의 순서
제어 절차를 사용해야 한다. 또한 기동 로그에 Flyway가 MySQL 8.4를 지원 검증
범위 밖으로 알리는 경고가 남았다. 이번 V1~V6 적용·검증은 통과했지만 이 결과가
해당 버전 조합의 모든 migration 호환성을 보증하지는 않는다.

PowerShell의 Docker progress stderr가 `NativeCommandError`로 처리된 첫
빌드 래퍼와 대문자 날짜를 넣은 프로젝트명 검사 실패도 실제 리소스 기동
결과와 구분했다. 프로젝트명은 소문자로 고쳤고 Docker stdout/stderr를 별도
파일로 수집해 프로세스 종료 코드를 확인했다. 기존 health API 테스트 7개는
앞선 실행에서 통과했고 Java 소스 변경은 없다.

원시 로그, 컨테이너 State, 단계별 health/Flyway/표식 비교, 전후 리소스 목록과
`final-result.json`은 로컬 `.gradle/runtime-smoke/20261006T020355Z-9798d4b7/`에
보관한다. 이 디렉터리는 Git에서 제외하며, 검증 요약은 이 문서에 남긴다.
이 검증은 외부 연동과 자동 실행을 끈 앱·DB 기동 확인 범위다.
