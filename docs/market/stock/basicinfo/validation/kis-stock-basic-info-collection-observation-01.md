# KIS 주식기본조회 단건 수집 실환경 사전 점검

2026-10-07 KST, 커밋 `9276639`의 [단건 수집 수동 실행기](../kis-stock-basic-info-manual-collection.md)를 실제 KIS와 MySQL에 연결하기 전에 실행 환경을 점검했다. **MySQL 기동과 DB 접속은 확인했지만, V7과 관측 테이블이 없어 수집 전에 중단했다. 실제 KIS 조회와 원문 저장 검증은 아직 완료되지 않았다.** 점검에 사용한 MySQL은 정상 종료했으며 투자 앱은 기동하지 않았다.

## 검증 범위

대상은 `local` 프로파일의 `localhost:3307/stock_agent_harness`와 종목 `005930` 하나다. 전용 실행기로 조회한 원문을 한 건 저장하고, 해당 관측 ID로 복원하여 무결성과 기존 Parser의 해석 결과를 확인한다. 원문 저장 성공, KIS 업무 성공과 투자 적격 판정은 별개다.

토큰 발급 최대 1회와 주식기본조회 1회를 계획하며 자동 재시도하지 않는다. DB 준비 확인 전에는 KIS를 호출하지 않는다. 최초 범위에서 Docker 기동도 제외했지만, 이후 사용자의 명시적인 요청으로 기존 MySQL만 기동·확인·종료했다. DB 스키마 변경, 투자 서버 기동, 주문, OpenAI와 스케줄 활성화는 수행하지 않았다.

## 최초 접속 실패

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

## MySQL 기동 후 확인

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

V7은 원문 저장에 필요한 테이블을 생성한다. 수동 실행기는 스키마 검증만 하고 마이그레이션을 수행하지 않으므로, 이 상태에서 실제 수집을 시도하지 않았다. V7 적용은 별도 승인이 필요한 DB 변경이며 백업·마이그레이션도 아직 수행하지 않았다.

최초 중지에서는 종료 코드 `137`, `OOMKilled=false`를 확인했지만 `Shutdown complete` 로그는 없었다. 원인을 확정하지 않고 MySQL을 다시 시작하여 `healthy`, DB 접속과 동일한 V1~V6 이력을 확인했다. 이후 `docker compose stop --timeout 60 mysql`로 중지하여 `2026-10-07T01:31:05.751737Z`의 `Shutdown complete` 로그와 종료 코드 `0`을 확인했다. 최종 상태는 MySQL과 투자 앱 모두 `exited`다. 기동 후 이력이 조회된다는 사실은 기존 모든 행의 무결성 비교를 대신하지 않는다.

두 점검에서 토큰 발급, 주식기본조회, 주문과 OpenAI 요청은 각각 0회이며 관측 INSERT와 스키마 변경도 0회다. 전용 수집 프로세스를 시작하지 않았으므로 관측 ID, 저장 원문과 Parser 재해석 결과는 없다. 이 수치는 이번 점검의 작업 범위이며 다른 프로세스의 호출 여부를 보증하지 않는다.

## 재개 조건과 순서

1. Docker Desktop과 대상 MySQL을 준비한다. 투자 앱을 기동할 필요는 없다. 이번 점검에서 MySQL 기동·접속·정상 종료까지 확인했다.
2. 기존 DB 백업과 이미 커밋된 V7 적용 범위를 승인받아 별도 마이그레이션으로 수행한다. 기존 V1~V6을 수정하거나 전체 투자 서버를 기동하여 대신 적용하지 않는다. 적용 후 성공한 V7 이력과 관측 테이블의 컬럼·자료형을 확인하고 수집 전 관측 행 수와 최대 ID를 기록한다.
3. 필요한 DB 설정과 조회용 키만 수집 자식 프로세스의 환경으로 전달한다. `.env` 전체를 무조건 로드하거나 비밀 값을 명령행 인자에 넣지 않는다. 인증정보는 로그와 증적에서 제외한다.
4. 기존 전용 실행기로 `005930`을 한 번 수집한다. 실패나 결과 불명확 상태에서 반복 기동하지 않는다. 새 프로세스끼리는 인메모리 토큰 캐시를 공유하지 않는다.
5. 반환된 관측 ID로 DB를 다시 읽어 종목 코드, HTTP 상태, 원문 길이와 SHA-256, 마이크로초 시각 정밀도를 확인한다. 기존 복원 로직과 Parser를 사용하며 재해석에 외부 API를 호출하지 않는다.
6. 관측 행 증가가 한 건인지 확인하고, 원문 저장 결과와 KIS 업무 응답 결과를 분리하여 기록한다. 과거 정보 가용 시점, 투자 적격 또는 거래 허가를 생성하지 않는다.

DB에서 원문과 저장된 해시가 일치하는 것은 저장 데이터 내부의 무결성 확인이다. 수신 전 원문까지 동일함을 주장하려면 저장 전후의 별도 비교 증거도 있어야 한다.

## 증적과 변경 범위

로컬 증적은 Git에서 제외된 `build/kis-stock-basic-info-collection-observation-01/`에 저장했다. `preflight.json`은 최초 접속 실패, `preflight-02.json`은 DB 접속·V7 미적용·컨테이너 종료 확인 결과다. 기존 증적을 덮어쓰지 않으며 실제 수집 결과는 별도 파일로 추가한다. `gradlew clean`은 기존 관측 증적을 삭제할 수 있으므로 실행하지 않는다.

파일 변경은 사전 점검 기록과 실행 문서의 링크뿐이다. 새 Java 패키지, 운영 코드, 테스트, Gradle 설정, Docker 설정, 마이그레이션과 `.env`는 변경하지 않았다. MySQL의 기동·종료는 수행했지만 투자 앱은 기동하지 않았다. `gradlew classes --offline --no-daemon`은 성공했고 테스트를 재실행하지 않았다. 기존 H2와 Mock HTTP 검증을 실제 MySQL 원문 저장 또는 KIS 검증 결과로 대체하지 않는다.
