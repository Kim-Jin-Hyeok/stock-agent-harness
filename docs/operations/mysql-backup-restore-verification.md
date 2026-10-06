# MySQL 백업 및 격리 복원 검증

Run, 거래, 포트폴리오와 시세 데이터를 잃었을 때 백업으로 같은 내용을
복원할 수 있는지 확인하는 절차다. 원본 DB를 교체하지 않고 별도 MySQL에
복원한다. **도구 구현과 오프라인 테스트는 완료했지만, 실제 DB 복원
성공은 아직 확인하지 않았다.**

2026-10-06 확인 당시 Docker Linux 엔진의 named pipe에 연결되지 않았다.
컨테이너의 현재 상태는 조회하지 못했으며, 과거 관측 문서의 종료 상태를
현재 상태로 간주하지 않는다. 원본 앱·DB 기동, 종료, 데이터 변경과
KIS/OpenAI 호출은 수행하지 않았다.

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

## 이번 검증 결과

```powershell
.\scripts\ops\mysql\Test-BackupRestoreVerification.ps1
```

오프라인 35개 테스트가 통과했다. Windows 인자 quoting, SQL 바이트 보존,
파일 덮어쓰기 차단, timeout, 쓰기 주체·테이블 조건, 복원 격리, 동일 행 수의
내용 차이, 원본·백업 파일 변경, 경로 우회, 복원·종료 실패를 확인했다. 테스트용
Docker 응답과 데이터는 가짜이며 `build/mysql-backup-tool-tests-*/`에만
생성된다. Java 전체 테스트나 외부 API는 실행하지 않았다.

실제 Docker·MySQL의 덤프 옵션 호환성, 복원 소요시간과 실제 데이터
일치 여부는 엔진 기동 후 위 절차로 확인해야 한다. `result.json`의
실제 `PASSED`와 종료된 격리 자원을 확인하기 전에는 복구 검증 완료로
표시하지 않는다.

**DB 내용 복원 성공과 투자 운영 재개는 별개다.** 앱 기동, Broker 잔존
주문·계좌·체결 반영, Run 저장 정합성과 중복 실행을 확인하기 전에는
복원된 DB로 주문을 재개하지 않는다.
