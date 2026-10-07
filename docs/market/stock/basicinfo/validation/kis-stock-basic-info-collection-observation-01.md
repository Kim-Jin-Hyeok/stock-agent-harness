# KIS 주식기본조회 V7 적용 및 단건 수집 실환경 검증

2026-10-07 KST, 기존 DB를 백업하고 V7만 적용한 뒤 [단건 수집 수동 실행기](../kis-stock-basic-info-manual-collection.md)의 전용 구성과 실제 Runner로 `005930`을 조회했다. **KIS 응답 한 건 저장, 원문 복원·Parser 재해석, 기존 데이터 유지와 MySQL 정상 종료까지 검증했다.** 최종 증적 상태는 `PASSED`다. 투자 앱, 주문과 OpenAI는 실행하지 않았다.

최초에는 DB 접속 불가, 이후에는 V7 미적용으로 수집 전에 중단했다. 이 사전 점검 이력과 증적은 아래에 보존한다. 실제 수집 검증의 기준 커밋은 `dbac61d`이며, 운영 Java 코드와 기존 V1~V7 SQL은 수정하지 않았다.

## 실제 조회 및 저장 결과

| 항목 | 결과 |
| --- | --- |
| 대상 DB | MySQL `8.4.11`, `stock_agent_harness` |
| Flyway | 기존 이력 검증 성공. 목표 버전 V7, 신규 적용 정확히 1건, V7 checksum `-501964900` |
| 신규 테이블 | 필수 9개 컬럼, NOT NULL, 자동 증가 PK, `LONGBLOB`, `DATETIME(6)` 확인 |
| 관측 행 수 | 수집 전 0건 → 수집 후 1건 |
| 종목 및 관측 ID | `005930`, ID `1` |
| HTTP | 토큰과 주식기본조회 모두 200 |
| 원문 길이 | `1,860`바이트 |
| 원문 보존 | HTTP에서 읽은 바이트와 Store로 복원한 바이트가 정확히 일치 |
| 시각 | 요청·수신·기록 시각 모두 마이크로초 정밀도 확인 |
| Parser | `KIS_STOCK_BASIC_INFO_RAW_V1`, `rt_cd=0`, `msg_cd=KIOK0530`, 입력 해시 일치 |
| 원문 식별값 | `productNumber=00000A005930`, `standardCode=KR7005930003`, `rawMarket=STK` |
| 종료 | 수집 DataSource 풀 닫힘. MySQL 종료 코드 0. 투자 앱은 중지 상태 유지 |

원문 SHA-256은 `a4197f6db2f02997b8c0b38765952ee075a908a8572a3bfbd905f43acc0eecc8`이다. 원문은 JSON 재직렬화나 종목 코드 보정 없이 저장·복원했다. 현재 응답 관측은 투자 적격, 거래 허가 또는 과거 정보 가용 시점의 인증이 아니다.

## 백업과 기존 데이터 보존

전체 DB 논리 백업은 Git 제외 경로 `data/backups/mysql/mysql-kis-basic-info-20261007-01/backup.sql`에 보관한다. 기존 운영 도구의 `Export-Database` 함수를 재사용했고, 덤프 프로세스 정상 종료·파일 크기 `1,847,878`바이트·SHA-256을 확인했다. 백업 해시는 `459590fa4ed740a3d4f46d960b5a80c65f531268f63fe883e149bd2a9a4b222d`이다. 이 백업의 실제 복원은 이번 검증에 포함하지 않았다.

Flyway 이력과 신규 관측 테이블을 제외한 기존 10개 테이블을 동일 옵션·PK 순서로 덤프했다. 적용 전후 파일은 각각 `1,845,799`바이트이며 전체 바이트 비교와 SHA-256이 일치했다. 덤프에는 기존 테이블의 스키마와 내용이 함께 포함된다. 공통 해시는 `00d0b30b9496d792938ec5b5b41a3640c07151b728c7e79349f12e0f5e85c27d`다.

제외한 Flyway 테이블은 기존 V1~V6 행을 별도로 비교하여 그대로임을 확인했다. 신규 변경은 성공한 V7 이력 한 행과 새 관측 테이블·관측 한 행이다. `.env`, Compose와 V1~V7 마이그레이션 파일도 적용 전후 해시가 동일하다. 백업과 SQL 덤프에는 개인 투자 데이터가 포함될 수 있으므로 Git에 추가하거나 공개 로그로 출력하지 않는다.

## 호출 횟수와 소요시간

검증 보조 코드가 전용 RestClient에 관측용 interceptor만 추가했다. 고정 실전 호스트의 토큰 경로와 `005930` 주식기본조회 경로만 허용하고, 요청 종류별 실행 한도를 1회로 제한했다. 응답 캡처는 주식기본조회 원문만 대상으로 하며 토큰 본문과 인증 헤더는 증적에 저장하지 않는다. HTTP 실행 시 DB 트랜잭션도 없었다.

| 구간 | 관측값 |
| --- | --- |
| 토큰 실행 | 1회, 약 `324.69ms` |
| 주식기본조회 실행 | 1회, 약 `17.14ms` |
| 수집 컨텍스트 기동부터 저장·복원·종료 | 약 `4.24초` |
| 자동 재시도 | 0회 |

시간은 이번 한 건의 시작·종료 벽시계 시각 차이다. 평균 응답 시간이나 대량 수집 처리량을 보증하지 않는다. 횟수는 interceptor 실행 기준이며 물리 네트워크 재전송이나 다른 프로세스의 계정 전체 호출 수를 뜻하지 않는다. 새 프로세스는 인메모리 토큰 캐시를 공유하지 않으므로 이 실행기를 반복 기동하여 대량 수집하지 않는다.

## 준비 과정과 정상 종료

기존 백업 도구는 `event_scheduler=OFF`와 다른 DB 접속자 0개를 요구한다. 최초 준비에서는 이벤트 스케줄러가 ON이라 중단했다. 다른 접속자와 활성 DB 이벤트가 모두 0개임을 확인하고 추가 승인 범위에서 이를 일시 OFF로 전환했다. 검증 완료 후 원래 ON으로 복원했으며 영구 설정 파일은 변경하지 않았다.

별도 Flyway `validate`는 미적용 V7을 `RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED`로 보고했다. 기존 이력 checksum 오류가 아니었다. Pending이 V7 하나뿐이고 파일명도 예상한 값임을 확인한 뒤, 미적용 상태만 검증에서 허용하는 `*:pending`을 사용했다. 기존 checksum 검증은 유지했고 성공한 검증 결과에서 `validateCount=7`을 확인했다. `repair`, baseline, clean과 기존 SQL 수정은 수행하지 않았다. Flyway는 MySQL 8.4가 해당 버전의 시험 지원 범위보다 새롭다는 경고도 남겼으며 의존성 업그레이드는 하지 않았다.

수집 프로세스는 전용 구성·실제 Runner·Provider·Store를 사용했다. 웹 서버, 스케줄러와 Flyway 빈이 없고, 등록 Entity가 관측 Entity 하나임을 확인했다. 호출 한도·허용 경로·바이트 캡처의 보조 검사도 실제 호출 전에 통과했다. 투자 서버 전체를 기동하지 않았다.

마지막에는 `docker compose stop --timeout 60 mysql`로 중지했다. `2026-10-07T02:21:30.414788Z`의 `Shutdown complete` 로그, 종료 코드 `0`, `OOMKilled=false`를 확인했다. MySQL과 투자 앱 모두 `exited` 상태다.

## 검증 범위

대상은 `local` 프로파일의 `localhost:3307/stock_agent_harness`와 종목 `005930` 하나다. 전용 실행기로 조회한 원문을 한 건 저장하고, 해당 관측 ID로 복원하여 무결성과 기존 Parser의 해석 결과를 확인한다. 원문 저장 성공, KIS 업무 성공과 투자 적격 판정은 별개다.

최초 사전 점검은 Docker 기동과 DB 변경을 제외했다. 이후 사용자의 명시적인 요청으로 MySQL 기동·백업·V7 적용·KIS 단건 조회·저장·검증·종료까지 진행했다. 필요한 DB 인증정보와 `.env`의 조회용 키 두 개만 자식 프로세스 환경으로 전달했으며 `.env` 파일 자체는 변경하지 않았다. 주문, 투자 서버 기동, OpenAI와 스케줄 활성화는 제외했다.

## 최초 사전 점검 이력

| 항목 | 결과 |
| --- | --- |
| 점검 시작 시 저장소 | 미커밋 변경 없음. 기준 커밋은 `9276639967b7a6427f8d5d6175b7951a5597fd9a`. |
| Docker 상태 조회 | `docker compose ps --format json`이 종료 코드 1로 실패했다. Docker Desktop Linux 엔진의 named pipe가 없었다. |
| 호스트 DB 연결 | `127.0.0.1:3307`, `[::1]:3307` 모두 `ConnectionRefused`. IPv6는 IPv6용 소켓으로 확인했다. |
| 조회용 인증정보 | `.env`의 `KIS_READONLY_APP_KEY`, `KIS_READONLY_APP_SECRET` 값 존재만 확인했다. 유효한 인증정보인지는 확인하지 않았다. |
| 실행 프로세스 환경 | 조회용 키와 secret이 없었다. Java/Gradle은 `.env`를 자동으로 읽지 않는다. |
| DB 인증정보 | `.env`의 DB 사용자명과 비밀번호 값 존재만 확인했다. DB 인증과 권한은 확인하지 못했다. |
| V7 및 관측 테이블 | DB 접속 불가로 미확인. 테이블이 없다고 판정한 것은 아니다. |

DB 호스트는 `application-local.yml` 기본값 `localhost`, 포트와 DB 이름은 `.env` 설정을 기준으로 했다. 비밀 값은 출력하거나 증적에 저장하지 않았으며 `.env`도 수정하지 않았다.

## V7 적용 전 사전 점검 이력

기존 `stock-agent-harness-mysql-1`을 `docker compose start mysql`로 시작하고 `healthy` 상태를 확인했다. 컨테이너 내부의 기존 DB 사용자 인증으로 접속하여 읽기 전용 트랜잭션에서 DB 이름, 버전, Flyway 이력과 `information_schema`를 조회했다. 인증정보를 명령행 값이나 증적에 넣지 않았다.

| 항목 | 확인 결과 |
| --- | --- |
| 대상 DB | `stock_agent_harness`, MySQL `8.4.11`, 세션 시간대 `SYSTEM` |
| Flyway V1 | `create initial schema`, 성공 |
| Flyway V2 | `create daily price bar`, 성공 |
| Flyway V3 | `create market index daily observation`, 성공 |
| Flyway V4 | `add daily price trading value`, 성공 |
| Flyway V5 | `create daily trading value selection snapshot`, 성공 |
| Flyway V6 | `create stock candidate evaluation snapshot`, 성공 |
| Flyway V7 | 적용 이력 없음 |
| `kis_stock_basic_info_observation` | 대상 스키마의 테이블·컬럼 조회 결과 없음 |

V7은 원문 저장에 필요한 테이블을 생성한다. 수동 실행기는 스키마 검증만 하고 마이그레이션을 수행하지 않으므로, 이 사전 점검 당시에는 실제 수집을 시도하지 않았다. 백업·마이그레이션은 후속 승인 범위에서 위의 실제 검증 절차로 수행했다.

최초 중지에서는 종료 코드 `137`, `OOMKilled=false`를 확인했지만 `Shutdown complete` 로그는 없었다. 원인을 확정하지 않고 MySQL을 다시 시작하여 `healthy`, DB 접속과 동일한 V1~V6 이력을 확인했다. 이후 `docker compose stop --timeout 60 mysql`로 중지하여 `2026-10-07T01:31:05.751737Z`의 `Shutdown complete` 로그와 종료 코드 `0`을 확인했다. 최종 상태는 MySQL과 투자 앱 모두 `exited`다. 기동 후 이력이 조회된다는 사실은 기존 모든 행의 무결성 비교를 대신하지 않는다.

앞선 두 사전 점검에서는 토큰 발급, 주식기본조회, 주문과 OpenAI 요청, 관측 INSERT와 스키마 변경이 각각 0회였다. 이후 실제 수집 단계의 결과와 혼동하지 않는다.

## 후속 재실행 조건

1. MySQL과 쓰기 주체 상태를 실제로 조회한다. 현재 검증이 끝난 DB는 V7 적용·관측 1건 저장 상태이므로 빈 테이블이나 V6이라고 가정하지 않는다.
2. V7은 이미 적용했다. 중복 적용, 이력 수정, `repair`나 기존 관측 삭제로 검증 초기 상태를 만들지 않는다. 새 검증 범위·증적 경로·호출 예산을 정하고 필요한 백업과 스키마 검증을 수행한다.
3. 필요한 DB 설정과 조회용 키만 수집 자식 프로세스의 환경으로 전달한다. `.env` 전체를 무조건 로드하거나 비밀 값을 명령행 인자에 넣지 않는다. 인증정보는 로그와 증적에서 제외한다.
4. 기존 전용 실행기로 `005930`을 한 번 수집한다. 실패나 결과 불명확 상태에서 반복 기동하지 않는다. 새 프로세스끼리는 인메모리 토큰 캐시를 공유하지 않는다.
5. 반환된 관측 ID로 DB를 다시 읽어 종목 코드, HTTP 상태, 원문 길이와 SHA-256, 마이크로초 시각 정밀도를 확인한다. 기존 복원 로직과 Parser를 사용하며 재해석에 외부 API를 호출하지 않는다.
6. 관측 행 증가가 한 건인지 확인하고, 원문 저장 결과와 KIS 업무 응답 결과를 분리하여 기록한다. 과거 정보 가용 시점, 투자 적격 또는 거래 허가를 생성하지 않는다.

DB에서 원문과 저장된 해시가 일치하는 것은 저장 데이터 내부의 무결성 확인이다. 수신 전 원문까지 동일함을 주장하려면 저장 전후의 별도 비교 증거도 있어야 한다.

## 증적과 변경 범위

로컬 증적은 Git에서 제외된 `build/kis-stock-basic-info-collection-observation-01/`에 저장했다. `preflight.json`은 최초 접속 실패, `preflight-02.json`은 V7 적용 전 DB 점검과 종료 확인 결과다. `run-01/`은 백업 준비 실패 이력이고 `run-02/`에 실제 검증 결과를 보존했다. 기존 증적을 덮어쓰지 않았다. `gradlew clean`은 기존 관측 증적을 삭제할 수 있으므로 실행하지 않는다.

| 실제 검증 증적 | 내용 |
| --- | --- |
| `preflight-ready.json`, `database-before.json` | 백업·원본 덤프 정보, 적용 전 테이블·행 수·Flyway 이력 |
| `migration-result.json`, `flyway-validation-before-v7.json` | V7 한 건 적용과 성공한 checksum 검증 |
| `schema-result.json` | 관측 테이블의 9개 컬럼 검증 |
| `collection-attempt.json`, `http-audit.json` | 단건 실행 계획, 실제 요청 횟수와 HTTP 상태 |
| `received-005930.bin`, `verification.json` | 수신 원문, 복원·Parser·시각·ID 결과 |
| `original-tables-before.sql`, `original-tables-after.sql` | 기존 10개 테이블 비교용 비공개 덤프 |
| `original-data-comparison.json`, `database-final.json` | 데이터·기존 이력·설정 파일 보존과 최종 행 수 |
| `event-scheduler-restoration.json`, `shutdown.json` | 임시 설정 복원과 컨테이너 종료 |
| `verification-summary.json`, `evidence-manifest.json` | 최종 `PASSED`, 덤프 전체 바이트 비교와 증적 해시 목록 |

커밋 대상은 검증 문서와 기존 실행·저장 문서의 최신화다. 보조 Java·PowerShell·Gradle init 파일과 원문·덤프는 `build/`에만 두며 운영 패키지를 추가하지 않았다. 기존 운영 코드, 테스트, Gradle 설정, Docker 설정, V1~V7 SQL과 `.env`는 그대로다. DB에는 V7과 관측 한 건을 남겼다. 빌드와 보조 검사, 실제 MySQL·KIS 검증을 수행했으며 기존 JUnit 또는 전체 테스트 모음을 재실행하지 않았다. 신규 데이터가 자동으로 투자 판단에 연결되는 기능은 이번 범위가 아니다.
