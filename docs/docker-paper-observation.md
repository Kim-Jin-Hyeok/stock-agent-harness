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
| 거래 실행 | `VIRTUAL`: 앱 내부 가상 거래이며 Broker 주문 제출과 구분한다. |
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
  `MARKET_PRICE_HISTORY_COLLECTION_BOOTSTRAP_ENABLED=false`를 유지한다.
  변경된 값은 앱 컨테이너를 생성·재생성할 때 적용되며 실행 중 앱을
  즉시 멈추는 스위치가 아니다. 기존 컨테이너의 `start`나 `restart`만으로
  변경된 `.env`가 반영되지는 않는다.
- 관측을 시작할 때만 필요한 스케줄러를 각각 명시적으로 켠다.
  Harness는 `HARNESS_SCHEDULER_ENABLED=true`, 일봉 수집은
  `MARKET_PRICE_HISTORY_COLLECTION_SCHEDULER_ENABLED=true`로 설정한다. 현재 기본
  Harness 설정에는 단타·스윙·장기 전략이 모두 포함되어 있으므로 스윙만
  실행되는 환경이라고 가정하지 않는다. 전략별 시간창과 실행 주기를 확인한다.
- 기본 일봉 수집 cron은 월~금 `20:15` (`Asia/Seoul`)이며 시작 시 수집은
  꺼져 있다. 초기 일봉이 없는 DB는 예정 수집 전까지 필요한 데이터가
  부족할 수 있다. 부족한 상태에서 Run을 반복하여 성공으로 처리하지 않는다.
- 쉘 환경변수는 `.env` 값을 덮어쓸 수 있다. IDE 실행 인자, 다른 Compose
  파일, 추가 프로필 또는 수동 API 호출도 자동 실행 점검 대상이다.

다음은 Compose가 전달할 값 중 비밀이 아닌 항목만 출력하는 예시다.
JSON 전체와 `$paperResolved` 자체를 출력하거나 공유하면 자격증명이
노출될 수 있으므로 선택된 항목만 확인한다. 앱 내부의 최종 설정까지
검증하는 명령은 아니므로 위 프로필과 설정 파일 점검도 필요하다.
두 스케줄러, bootstrap과 바인딩 주소의 출력이 선택한 실행 단계의 기대값과 다르면
시작을 보류하고 `.env`와 쉘 환경변수를 대조한다.

```powershell
$paperResolved = docker compose -p $paperProject --env-file .\.env -f .\compose.yml config --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $null -eq $paperResolved) {
    throw 'Compose 설정 확인 실패. 시작하지 않는다.'
}
$paperAppPort = [int]$paperResolved.services.app.ports[0].published
$paperDbPort = [int]$paperResolved.services.mysql.ports[0].published
$paperAppBindAddress = $paperResolved.services.app.ports[0].host_ip
$paperDbBindAddress = $paperResolved.services.mysql.ports[0].host_ip
$paperResolved.services.app.environment | Select-Object SPRING_PROFILES_ACTIVE, DB_HOST, DB_PORT, HARNESS_SCHEDULER_ENABLED, MARKET_PRICE_HISTORY_COLLECTION_SCHEDULER_ENABLED, MARKET_PRICE_HISTORY_COLLECTION_BOOTSTRAP_ENABLED, AGENT_NEXT_ACTION_PROVIDER_TYPE, MOVING_AVERAGE_ORDER_DECISION_PROVIDER_TYPE, OPENAI_ENABLED
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

`mysql`은 healthcheck 통과 후 앱 시작의 선행 조건이다. `app`에는 별도
healthcheck가 없으므로 `Up`만으로 서버와 DB가 정상이라고 판정하지 않는다.
선택한 프로젝트의 앱이 하나인지 `docker ps`에서도 다시 확인한다.

스케줄러를 끈 최초 점검이 끝난 뒤 관측을 켜려면 아래 종료 절차로 기존
앱의 종료를 확인하고, 필요한 스케줄러 값을 명시적으로 `true`로 변경하여 같은 프로젝트로
다시 시작한다. `.env` 수정만으로 실행 중 앱에 반영되지는 않는다.

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

Flyway 이력에 현재 migration `V1`~`V3`의 성공이 있는지, 로그의 Run이
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
   `.env`의 스케줄러 값을 `false`로 바꾸는 것만으로 현재 앱의 신규 실행이
   차단되지는 않는다. 런타임 차단·작업 배출 기능은 아직 검증 대상이다.
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
| 백업·복원 | 구현·검증 완료로 표시하지 않는다. 백업 시 쓰기 일관성, DB 버전과 도구, 자격증명 취급, 별도 볼륨/DB로 복원, Flyway 이력·Run·단계·거래·포트폴리오 비교를 검증해야 한다. 복원 앱의 스케줄러·bootstrap을 끄고 원본 앱과의 중복 실행을 막아야 한다. named volume 유지와 파일 생성만으로 복구 성공을 판정하지 않는다. |
| Broker 계좌 정합성 | 가상 잔고와 Broker 모의계좌를 구분하고, 기존 미완료 주문·체결 반영·재시작 후 상태를 확인해야 한다. 현재 프로필에서는 주문 정합성·취소 자동 처리가 꺼져 있다. |
| 접근 통제와 비밀 보호 | 기본 호스트 포트는 `127.0.0.1` 전용이지만 API 인증 통제가 마련되어 있지 않다. 기존 `.env`·셸 환경변수의 바인딩 재정의, 실제 외부 접근 차단과 로그·설정 출력의 자격증명 노출 여부를 검증해야 한다. |
| 저장 공간과 장애 대응 | Compose에 로그 순환과 재시작 정책이 명시되어 있지 않다. 로그·DB 용량, 디스크 부족, API 오류·rate limit, 컨테이너 종료 감지와 복구 절차를 검증해야 한다. |
