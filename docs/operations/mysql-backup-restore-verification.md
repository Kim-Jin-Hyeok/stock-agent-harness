# MySQL 백업 및 격리 복원 검증

Run, 거래, 포트폴리오와 시세 데이터를 잃었을 때 백업으로 같은 내용을
복원할 수 있는지 확인하는 절차다. 원본 DB를 교체하지 않고 별도 MySQL에
복원한다. **2026-10-06 새 Compose 프로젝트의 일회용 MySQL에서 합성 데이터를
백업·격리 복원했고, 실제 `result.json`의 `PASSED`와 복원 대상 종료를 확인했다.
기존 투자 DB의 백업·복원이나 투자 운영 재개를 검증한 결과는 아니다.**

도구 구현 당시 첫 확인에서는 Docker Linux 엔진의 named pipe에 연결되지
않아 컨테이너 상태를 조회하지 못했다. 이 실패 이력은 보존한다. 이후 아래의
격리 검증에서는 엔진에 연결했고, 실제 조회한 컨테이너 상태를 기준으로
진행했다. 기존 앱·DB의 기동·종료·데이터 변경과 KIS/OpenAI 호출은 수행하지 않았다.

## 파일과 실행 범위

| 경로 | 역할 |
| --- | --- |
| `scripts/ops/mysql/Invoke-BackupRestoreVerification.ps1` | 읽기 전용 사전 확인과 명시적으로 요청한 백업·격리 복원 |
| `scripts/ops/mysql/Test-BackupRestoreVerification.ps1` | 실제 Docker를 사용하지 않는 안전장치·실패 흐름 테스트 |
| `data/backups/mysql/<verificationId>/` | SQL 백업, 비교용 덤프와 검증 결과. Git과 Docker 빌드에서 제외 |

Java 애플리케이션 패키지를 추가하지 않는다. Windows PowerShell 5.1과
Docker CLI에서 사용하는 운영 도구이며, 앱이나 Flyway migration을
실행하지 않는다. 기존 `.env`, Compose 설정과 원본 named volume을
수정하지 않는다.

지원 범위는 Compose의 `mysql` 서비스, MySQL 8.4,
`lower_case_table_names=0`, PK가 있는 InnoDB 기본 테이블이다. 시스템 DB,
뷰, PK 없는 테이블, 비트랜잭션 테이블과 replication 구성은 거부한다.
다른 환경에 그대로 적용하지 않는다.

## 사전 조건

1. Docker Desktop의 Linux 엔진이 실행 중이어야 한다. 원본 MySQL은 이미
   실행 중이어야 하며 도구가 대신 켜지 않는다.
2. 작업 종료와 이력 저장을 확인한 뒤 원본 앱과 모든 DB 쓰기 주체를
   멈춘다. 앱 종료 절차는 [모의투자 관측 문서](../docker-paper-observation.md)의
   안전한 종료 절차를 따른다. 이 도구는 앱을 자동 종료하지 않는다.
3. IntelliJ에서 실행한 앱, 다른 Compose 프로젝트·호스트의 앱, 수동 SQL,
   배치와 schema 변경도 중지한다. DataGrip 등 DB 클라이언트 연결도 닫는다.
   주문 허용 `false`만으로 수집·이력 저장이 멈추는 것은 아니다.
4. 원본 MySQL의 event scheduler는 `OFF`여야 한다. 다른 DB 클라이언트가
   보이면 사전 확인이 실패한다. Compose 앱 컨테이너를 제거하지 않고
   종료 상태로 유지해야 도구가 ID와 상태를 대조할 수 있다.
5. 컨테이너에 설정된 `MYSQL_ROOT_PASSWORD`가 실제 DB root 인증과 일치해야
   한다. 원본 인증은 컨테이너 안에서만 기존 환경변수로 수행한다.
6. 백업 SQL 3개와 새 MySQL 데이터 볼륨을 저장할 여유 공간이 필요하다.
   백업은 평문이므로 접근이 제한된 로컬 경로에서 수행한다.

사전 확인의 process list는 조회 순간의 상태일 뿐이다. 다른 쓰기 주체가
다시 연결하지 않도록 운영자가 통제해야 한다. 두 번의 원본 덤프가 같아도
중간에 발생했다가 되돌아온 모든 변경을 증명할 수는 없다.

## 실행 방법

저장소 루트의 PowerShell에서 컨테이너 목록부터 확인한다. 전체
`docker inspect`, `.Config.Env`, 해석된 Compose 설정은 출력하지 않는다.

```powershell
docker ps -a --format "table {{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}"
```

아래 이름은 기본 Compose 예시다. 다른 프로젝트명이나 인스턴스라면
조회한 실제 ID 또는 이름으로 바꾼다. `WriterContainers`에는 동일 DB에
접근하는 모든 알려진 앱 컨테이너를 넣는다.

```powershell
$sourceContainer = 'stock-agent-harness-mysql-1'
$writerContainers = @('stock-agent-harness-app-1')
$database = 'stock_agent_harness'

.\scripts\ops\mysql\Invoke-BackupRestoreVerification.ps1 `
    -SourceContainer $sourceContainer `
    -WriterContainers $writerContainers `
    -Database $database
```

기본 `Preflight`는 컨테이너·볼륨 식별, 종료된 쓰기 주체, DB·클라이언트 버전,
테이블·PK·엔진, 실제 행 수와 Flyway 이력을 조회한다. 파일·복원 자원은
만들지 않는다. 알려진 Compose 앱이 목록에서 빠졌거나 실행 중이면
진행하지 않는다. Docker 엔진이 꺼져 있으면 이 단계부터 실패한다.

사전 조건을 직접 확인한 다음에만 다음 명령을 실행한다.
`ConfirmSourceQuiescent`는 모든 쓰기와 schema 변경을 중단했다는 운영자
확인이다. 자동 잠금이나 주문 Kill Switch가 아니다.

```powershell
$verificationId = 'mysql-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)

.\scripts\ops\mysql\Invoke-BackupRestoreVerification.ps1 `
    -Mode Verify `
    -SourceContainer $sourceContainer `
    -WriterContainers $writerContainers `
    -Database $database `
    -VerificationId $verificationId `
    -ConfirmSourceQuiescent
```

검증 ID는 `mysql-`로 시작하는 영문·숫자·하이픈 조합이다. 이미 있는
증거 디렉터리·복원 컨테이너·볼륨은 재사용하지 않는다. 실패한 ID로
재실행하거나 증거를 덮어쓰지 않는다.

## 검증 순서와 성공 기준

1. 원본 DB를 `mysqldump`로 읽는다. SQL stdout을 파일 스트림에 직접
   복사하고 정상 종료 후 `.partial`을 `backup.sql`로 변경한다.
   PowerShell의 `>`나 `Out-File`로 SQL 인코딩을 바꾸지 않는다.
2. 검증별 새 named volume과 컨테이너를 만든다. 원본 컨테이너의 정확한
   image ID를 사용하고 `--pull=never`로 이미지 다운로드를 막는다.
   네트워크는 `none`, 호스트 포트는 없으며 원본 볼륨을 마운트하지 않는다.
3. entrypoint의 임시 초기화 서버가 아니라 PID 1이 `mysqld`인 상태를
   확인하고 복원한다. 새 DB는 event scheduler와 binlog를 끈다.
   애플리케이션은 실행하지 않는다. 초기화 흐름은
   [공식 MySQL 이미지 entrypoint](https://github.com/docker-library/mysql/blob/master/8.4/docker-entrypoint.sh)를 기준으로 한다.
4. 원본과 복원 DB·클라이언트의 버전, 테이블 목록·엔진·PK, 테이블별 실제 행 수,
   Flyway 버전·스크립트·checksum·성공 이력을 비교한다.
5. 복원 DB와 원본 DB를 같은 옵션으로 다시 덤프한다.
   **`backup.sql`, `restored.sql`, `source-after.sql`의 SHA-256과 최종
   `backup.sql` 파일 해시가 모두 같아야 한다.** 행 수만 같고 JSON,
   거래 수량이나 스키마가 다르면 성공으로 처리하지 않는다.
6. 원본·앱의 ID, 이미지, 볼륨과 기동·종료 시각이 그대로인지 확인하고,
   이 검증이 만든 대상만 종료한다. 종료 확인 실패도 `FAILED`다.

덤프는 `--single-transaction --quick`을 사용한다. PK 순서 정렬,
`--hex-blob`, `--tz-utc`, 일자·일반 주석 제외와 동일 옵션으로 덤프 바이트를
비교한다. InnoDB snapshot 중에도 concurrent DDL은 안전하지 않으므로
schema 변경을 중지해야 한다.
[MySQL 공식 mysqldump 문서](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)

덤프에는 DB 스키마와 모든 지원 테이블의 내용이 포함된다. 따라서
Run·Step·거래 기록, 포트폴리오 JSON, 주문의 체결·적용 수량/금액,
시세와 후보 평가 snapshot도 비교 대상이다. NULL·문자열·바이너리는
SQL 표현 그대로 비교하며, 차이를 지우는 SQL 정규화는 하지 않는다.
복원 클라이언트는 `--comments --binary-mode=1`로 SQL 주석을 보존하고
CRLF/NUL 자동 처리를 방지한다.
[MySQL 공식 클라이언트 옵션 문서](https://dev.mysql.com/doc/refman/8.4/en/mysql-command-options.html)

현재 저장소 migration은 `V1`~`V6`이지만 실행 중 DB의 적용 상태는
직접 조회한다. 저장소 migration 파일에 대한 Flyway validate나
애플리케이션의 JPA schema 검증은 이 절차에 포함하지 않는다.

## 증거와 실패 처리

| 파일 | 내용 |
| --- | --- |
| `preflight.json` | 원본·앱 식별과 초기 테이블별 행 수·Flyway 이력 |
| `backup.sql` | 정상 종료한 원본 논리 백업 |
| `restored-state.json` | 복원 DB의 테이블별 행 수·Flyway 이력 |
| `restored.sql` | 복원 DB의 재덤프 |
| `source-after.sql` | 원본 변경 여부 비교용 재덤프 |
| `result.json` | `PASSED`/`FAILED`, 실패 단계·사유, 바이트 수·해시, 시작/종료 및 소요시간, 복원 대상 종료 여부 |
| `*.partial` | 덤프 미완료 가능성이 있는 파일. 복구 성공의 증거로 사용하지 않음 |

초기 사전 조건에서 실패하면 증거 디렉터리가 없다. 디렉터리 생성 후
실패하면 가능한 범위에서 `result.json`을 기록하고 소유권을 확인한
복원 대상을 종료한다. 디스크 부족·강제 종료로 결과 파일까지 생성되지
못했다면 성공으로 간주하지 않는다.

일반 외부 명령은 60초, 덤프·복원은 각각 300초 제한이다. 기동 재시도는
120초 동안 수행하며, 개별 명령의 timeout 때문에 실제 대기시간은 더
길어질 수 있다. 클라이언트 timeout은 서버 측 작업의 즉시 취소를
보장하지 않으므로 재시도 전에 원본 DB 활동과 검증 자원을 확인한다.

컨테이너 이름은 `stock-restore-<verificationId>`, 볼륨은 같은 이름에
`-data`를 붙인다. 둘 다 `com.stock.backup-verification=<verificationId>`
라벨이 있어야 복원·종료 대상으로 인정한다. 검증 후 컨테이너·볼륨과
파일은 보존한다. 삭제는 해당 ID, 라벨, 원본 볼륨과의 차이를 확인하고
별도 승인한 뒤 수행하며, prune이나 원본 볼륨 삭제를 사용하지 않는다.

원본 root 비밀번호는 명령 인자·증거에 넣지 않는다. 새 DB는 네트워크와
포트가 없는 일회성 로컬 검증 대상으로만 빈 root 비밀번호를 사용한다.
Docker 권한을 가진 사용자는 접근할 수 있으므로 운영 DB나 공유 환경의
인증 방식으로 사용하면 안 된다. native stderr는 개인 데이터가 포함될
수 있어 공개 로그에 남기지 않고, 실패 단계와 exit code로 먼저 조사한다.

Git/Docker 제외는 암호화나 별도 보관소 백업이 아니다. 이 도구는 DB
사용자·권한, 서버 설정, binlog/PIT 복구, 자동 주기·보관 정책과 외부
저장소를 백업하지 않는다.

## 오프라인 검증 기록

```powershell
.\scripts\ops\mysql\Test-BackupRestoreVerification.ps1
```

오프라인 35개 테스트가 통과했다. Windows 인자 quoting, SQL 바이트 보존,
파일 덮어쓰기 차단, timeout, 쓰기 주체·테이블 조건, 복원 격리, 동일 행 수의
내용 차이, 원본·백업 파일 변경, 경로 우회, 복원·종료 실패를 확인했다. 테스트용
Docker 응답과 데이터는 가짜이며 `build/mysql-backup-tool-tests-*/`에만
생성된다. Java 전체 테스트나 외부 API는 실행하지 않았다.

아래 격리 검증에서 실제 Docker·MySQL의 덤프 옵션 호환성, 복원 소요시간과
합성 데이터 일치를 확인했다. 다른 DB에서도 `result.json`의 실제 `PASSED`와
종료된 격리 자원을 확인하기 전에는 복구 검증 완료로 표시하지 않는다.

**DB 내용 복원 성공과 투자 운영 재개는 별개다.** 앱 기동, Broker 잔존
주문·계좌·체결 반영, Run 저장 정합성과 중복 실행을 확인하기 전에는
복원된 DB로 주문을 재개하지 않는다.

## 격리 MySQL 실제 검증 기록 — 2026-10-06

**최종 결과: `PASSED` — 일회용 DB의 Preflight, 백업·격리 복원, 내용 대조와
복원 대상 종료를 통과했다.** Docker Linux 엔진 `29.5.3`, Compose `v5.1.4`,
Windows PowerShell 5.1, MySQL `8.4.11`에서 실행했다. Verify 시각은
`2026-10-06 12:46:47.740`~`12:47:40.462` KST다.

### 사용한 자원과 쓰기 중단 근거

| 항목 | 실제 사용값·확인 결과 |
| --- | --- |
| 새 Compose 프로젝트 | `stock-backup-smoke-20261006t034202z-b2c8afea` |
| 백업 원본 | DB `backup_smoke`, 컨테이너 `stock-backup-smoke-20261006t034202z-b2c8afea-mysql-1`, ID `f5ea34189b8710b24de725a274518812cde130f29926138ea2a5b8e102bb0fa9` |
| 새 원본 볼륨 | `stock-backup-smoke-20261006t034202z-b2c8afea_mysql-data`; 생성 전 목록에 없었고 Compose 소유 label과 `/var/lib/mysql` mount 확인 |
| 앱 컨테이너 | `stock-backup-smoke-20261006t034202z-b2c8afea-app-1`; 전체 검증 동안 `created`, `StartedAt=0001-01-01T00:00:00Z`, `Running=false`. 앱은 한 번도 시작하지 않음 |
| 초기화 쓰기 주체 | `stock-backup-smoke-20261006t034202z-b2c8afea-migration`; 별도 Java/Flyway 초기화 작업만 수행한 뒤 `exited`, 종료 코드 0. 앱과 함께 `WriterContainers`에 포함 |
| 원본 네트워크 | 전용 `_isolated` 네트워크의 `Internal=true`, 호스트 포트 없음. 초기화 종료 후 연결된 컨테이너는 원본 MySQL 하나뿐이며 원본 볼륨 사용자도 하나 |
| DB 내부 쓰기 주체 | Preflight 전과 Verify 직전에 `event_scheduler=OFF`, `log_bin=0`, 다른 DB 클라이언트 0, replication channel 0 확인 |
| 환경·인증정보 | 명시적인 빈 `--env-file`과 별도 Compose 파일 사용. 실제 `.env` 미열람·미사용. 새 앱의 KIS 인증정보 8개와 `OPENAI_API_KEY` 공란을 값 출력 없이 단언. KIS·OpenAI·주문 비활성화 |
| MySQL 이미지 | 원본·복원 모두 `sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d`. 로컬 이미지 사용, pull 없음 |
| 복원 대상 | `stock-restore-mysql-20261006t034202z-b2c8afea`, ID `141d5bbd5c9133c836e68cbe48d7615d97d8e373216122a13c378462f1049288` |
| 새 복원 볼륨 | `stock-restore-mysql-20261006t034202z-b2c8afea-data`; 원본과 다르며 컨테이너·볼륨의 `com.stock.backup-verification=mysql-20261006t034202z-b2c8afea` label 확인 |
| 복원 격리 | `NetworkMode=none`, 호스트 포트 없음, 새 복원 볼륨만 mount, event scheduler·binlog 비활성화. 복원 앱 미실행 |

초기화는 기존 격리 Runtime Smoke 앱 이미지에서 가져온 Flyway `11.7.2`와
JDBC 라이브러리로 수행했다. 이미지에 포함된 migration 6개의 SHA-256이
현재 저장소의 V1~V6 파일과 같음을 확인하고, 새 DB에 실제 `migrate()`와
`validate()`를 실행했다. Flyway 이력을 수동으로 만든 것은 아니다.
초기화와 합성 표식·테이블 행 삽입 이후에는 추가 DB 쓰기·DDL을 실행하지 않았다.

기본 `Preflight`는 `ConfirmSourceQuiescent` 없이 성공했다. 이어서 소유 label,
전용 mount·네트워크, 앱·초기화 작업의 중지 상태와 DB 접속자를 다시 확인한
후에만 `Verify -ConfirmSourceQuiescent`를 사용했다. 알려진 쓰기 주체 두 개를
모두 명시했고 기존 컨테이너는 입력 대상으로 지정하지 않았다.

### 표식·Flyway·테이블 내용 비교

Preflight와 `restored-state.json`의 DB·클라이언트 버전, 테이블 이름·PK·엔진,
행 수와 Flyway 이력이 일치했다. 모든 테이블은 PK가 있는 InnoDB다.
덤프 파일의 SHA-256을 `result.json`과 별도로 다시 계산했고, 각 테이블의
`INSERT` 행 수도 DB 행 수와 대조했다. 세 덤프에서 테이블별 `INSERT` 내용
해시가 모두 같았으며 전체 덤프 일치로 스키마 내용까지 확인했다.

| 테이블 | 원본·복원 행 수 | 세 덤프의 행 내용 |
| --- | --- | --- |
| `backup_smoke_probe` | 3 | 일치 |
| `flyway_schema_history` | 6 | 일치 |
| `harness_run_entity` | 1 | 일치 |
| `harness_step_entity` | 1 | 일치 |
| `trade_record_entity` | 1 | 일치 |
| `strategy_portfolio` | 1 | 일치 |
| `broker_order` | 1 | 일치 |
| `current_price_observation` | 1 | 일치 |
| `daily_price_bar` | 1 | 일치 |
| `market_index_daily_observation` | 1 | 일치 |
| `daily_trading_value_selection_snapshot` | 1 | 일치 |
| `stock_candidate_evaluation_snapshot` | 1 | 일치 |

총 12개 테이블, 19행이다. Run·Step·거래·포트폴리오 JSON·주문·시세·후보
snapshot에는 합성 행을 한 개씩 넣었다. 예를 들어 합성 주문의 요청 수량 7,
체결 수량/금액 3/210000, 포트폴리오 적용 수량/금액 2/140000이 그대로
복원됐다. Broker API 주문은 실행하지 않았다.

표식 `backup-restore-probe`의 문자열 UTF-8 HEX는
`EBB3B5EC9B9020ED919CEC8B9D2027205C0D0A6E756C00656E64`,
바이너리 HEX는 `000A0DFF5C27`이다. 한글·작은따옴표·역슬래시·CRLF·NUL,
JSON의 `quantity=7`과 `cashKrw=123456789`이 포함된다. `null-probe`의
SQL NULL과 `empty-probe`의 빈 문자열·빈 바이너리·`{}`도 구분되어 유지됐다.
원본 표식의 Verify 전후 조회 결과와 세 덤프의 표식 행이 일치했고 비교 중
표식을 재삽입하지 않았다. 복원 DB의 값은 종료 전에 도구가 생성한
`restored.sql`로 대조했으며 추가 조회를 위해 복원 컨테이너를 재시작하지 않았다.

| Flyway 버전 | 원본·복원 checksum | success |
| --- | --- | --- |
| 1 | -854770445 | 1 |
| 2 | -174800260 | 1 |
| 3 | -1797630604 | 1 |
| 4 | -575525917 | 1 |
| 5 | 1968956706 | 1 |
| 6 | -822632640 | 1 |

각 버전은 한 번씩 성공했으며 installed rank, description, script, checksum과
success가 일치한다. 설치 시각·실행시간을 포함한 전체 이력 행도 덤프에서 일치했다.

### 실제 result.json 값

원본 파일은 `data/backups/mysql/mysql-20261006t034202z-b2c8afea/result.json`이다.
다음은 실제 파일에서 발췌한 값이며 결과 파일은 수정하지 않았다.

```json
{
  "verificationId": "mysql-20261006t034202z-b2c8afea",
  "status": "PASSED",
  "stage": "compare",
  "startedAt": "2026-10-06T03:46:47.7401637+00:00",
  "finishedAt": "2026-10-06T03:47:40.4623186+00:00",
  "durationMs": 52721,
  "targetStopped": true,
  "backup": {
    "file": "backup.sql",
    "bytes": 14218,
    "sha256": "70D9541E13C817564CBA974910EF821E94324C80113B748938DA675C5A6FA264",
    "durationMs": 1147
  },
  "restoreStartedAt": "2026-10-06T03:47:22.6101531+00:00",
  "restoreFinishedAt": "2026-10-06T03:47:24.4035533+00:00",
  "restoreDurationMs": 1788,
  "restoredDump": {
    "file": "restored.sql",
    "bytes": 14218,
    "sha256": "70D9541E13C817564CBA974910EF821E94324C80113B748938DA675C5A6FA264",
    "durationMs": 619
  },
  "sourceAfterDump": {
    "file": "source-after.sql",
    "bytes": 14218,
    "sha256": "70D9541E13C817564CBA974910EF821E94324C80113B748938DA675C5A6FA264",
    "durationMs": 894
  },
  "failureStage": null,
  "failureReason": null
}
```

`backup.sql`, `restored.sql`, `source-after.sql` 모두 14,218바이트이며 위
SHA-256과 같다. 최종 `backup.sql` 재계산 해시도 같고 `.partial` 파일은 없다.
전체 Verify는 52.721초, SQL 복원은 1.788초였다. 이 소요시간은 이번 소규모
합성 데이터 기준이며 운영 데이터 규모의 복원 시간으로 해석하지 않는다.

### 실패 이력·환경 우회와 최종 보존 상태

다음 실패는 성공으로 표시하지 않았다. 준비·출력 래퍼의 실패이며, 위
Verify의 `failureStage`·`failureReason`과 구분해 기록한다.

| 실패 단계 | 실제 원인 | 처리·영향 |
| --- | --- | --- |
| PowerShell 도구 로드 | 실행 정책이 dot sourcing을 차단 | Docker 자원 생성 전 실패. 실행 프로세스에만 `Bypass` 적용; 사용자·시스템 실행 정책 미변경 |
| Docker 경로 해석 | `Get-Command docker -CommandType Application`이 `docker.exe`와 확장자 없는 `docker`를 함께 반환해 `ProcessStartInfo.FileName`이 잘못됨 | 기준 목록 수집 래퍼 실패, 자원 생성 전. 공식 `docker.exe`를 작업 디렉터리에 복사하고 이번 프로세스 PATH에서만 원래 Docker bin 경로를 제외해 단일 실행 파일로 해석. Compose 실행도 확인 |
| 앱 JAR 전체 추출 | Windows 경로 길이 제한으로 긴 클래스 경로 생성 실패 | 새 MySQL·앱 생성 후 발생, migration·표식 삽입 전. 필요한 라이브러리 JAR만 짧은 `lib/` 경로에 추출하고 migration 파일은 ZIP entry에서 직접 비교 |
| 독립 대조 요약 출력 | PowerShell 5.1의 `Measure-Object`가 `OrderedDictionary`의 `rows` 속성을 읽지 못함 | 독립 내용 비교 JSON 저장 후 출력 단계 실패. 저장한 JSON을 다시 읽어 12개 테이블·19행을 확인. 백업·복원 결과 파일에 영향 없음 |

백업 도구 소스는 변경하지 않았다. **Docker 경로 해석 문제는 기본 실행
환경에 남아 있으며, 이번 성공은 프로세스 PATH 우회를 적용한 결과다.**
운영 환경에 적용하기 전 실행 파일 해석을 별도로 확인해야 한다.

도구가 복원 MySQL을 종료했고 `targetStopped=true`, 실제 `exited`, 종료 코드
0을 확인했다. 독립 대조 후 소유 label과 전용 mount를 다시 확인하고 이번
원본 MySQL만 종료했다(`exited`, 종료 코드 0). 앱은 계속 `created`, 초기화
작업은 종료 코드 0으로 중지된 상태다. 네 컨테이너와 두 새 볼륨, 전용 내부
네트워크 및 증거 파일을 모두 보존했다. `down`, `down -v`, prune과 자원
삭제는 실행하지 않았다.

전후 메타데이터 대조에서 기존 컨테이너 4개의 ID·이미지·실행 상태·시작/종료
시각·mount가 같았다. 기존 볼륨 3개(앞선 Runtime Smoke 볼륨 포함)와 기존
네트워크 ID·이름 목록도 유지됐다. 기존 DB 접속·데이터 조회/수정, 기존
컨테이너의 start/stop, 실제 `.env` 및 KIS/OpenAI 인증정보 사용은 없었다.
커밋·푸시는 하지 않았다. 추적 파일 변경은 이 문서뿐이다.

SQL·도구 결과는 `data/backups/mysql/mysql-20261006t034202z-b2c8afea/`에,
초기화 코드·실행 환경·Preflight 명령 출력·쓰기 중단 근거·표식 조회·테이블별
해시·기존 자원 전후 대조와 실패 이력은
`build/mysql-backup-smoke-20261006t034202z-b2c8afea/`에 보존한다. 두 경로는
Git·Docker 빌드에서 제외되며 문서에는 검증 요약만 남긴다.
