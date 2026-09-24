# Local MySQL Development

## Purpose

로컬 환경에서 MySQL 8과 Flyway를 사용하여 Harness Run, 현재가 관측, 포트폴리오, 거래와 Broker 주문 이력을 재시작 후에도 유지한다.

테스트는 빠른 실행을 위해 H2와 Hibernate의 테스트 스키마 생성을 계속 사용한다. 로컬 MySQL에서는 Flyway가 스키마를 생성하고 Hibernate는 Entity와 실제 스키마가 일치하는지만 검증한다.

## Files

```text
compose.yml
.env.example
src/main/resources/application-local.yml
src/main/resources/db/migration/V1__create_initial_schema.sql
src/test/resources/application.properties
```

## Start MySQL

프로젝트 루트에서 다음 명령을 실행한다.

```powershell
docker compose up -d mysql
docker compose ps
```

기본 접속 정보는 다음과 같다.

```text
host: localhost
port: 3307
database: stock_agent_harness
username: stock
password: stock-local-password
```

이 값들은 로컬 개발 전용 기본값이다. 배포 환경의 비밀번호로 사용하지 않는다.

## Environment Overrides

Docker Compose 설정을 변경하려면 `.env.example`을 참고하여 프로젝트 루트에 `.env`를 만든다. `.env`는 Git에 포함되지 않는다.

```powershell
Copy-Item .env.example .env
```

Docker Compose는 `.env`를 자동으로 읽지만 로컬에서 직접 실행한 Spring Boot 프로세스는 `.env`를 자동으로 읽지 않는다. 기본값과 다른 DB 설정을 사용한다면 같은 값을 환경변수로 전달해야 한다.

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "3307"
$env:DB_NAME = "stock_agent_harness"
$env:DB_USERNAME = "stock"
$env:DB_PASSWORD = "stock-local-password"
```

## Start Application

`local` 프로필을 활성화하여 실행한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

시작 로그에서 다음 순서를 확인한다.

```text
Flyway migration validation
-> pending migration application
-> Hibernate schema validation
-> Spring Boot started
```

`application-local.yml`은 다음 정책을 사용한다.

```text
Flyway
-> classpath:db/migration의 SQL을 순서대로 적용

Hibernate ddl-auto=validate
-> 테이블을 생성하거나 변경하지 않음
-> Entity와 DB 스키마가 다르면 시작 실패
```

## Stop and Preserve Data

다음 명령은 컨테이너와 네트워크만 제거하며 MySQL 데이터 볼륨은 유지한다.

```powershell
docker compose down
```

다시 실행하면 기존 데이터가 유지된다.

```powershell
docker compose up -d mysql
```

다음 명령은 MySQL 데이터 볼륨까지 삭제한다.

```powershell
docker compose down -v
```

`-v`는 로컬 데이터를 전부 지워도 되는 경우에만 사용한다.

## Migration Rules

- 이미 적용된 `V1__create_initial_schema.sql`은 수정하지 않는다.
- Entity 필드를 변경할 때는 다음 버전의 Migration을 추가한다.
- 예를 들어 컬럼을 추가한다면 `V2__add_example_column.sql`을 만든다.
- Hibernate `ddl-auto=update`로 운영 스키마를 변경하지 않는다.
- Migration 적용 후에는 Hibernate `validate`가 통과해야 한다.
- 운영 DB에 적용하기 전에 별도 MySQL 환경에서 Migration을 검증한다.

## Test Policy

일반 테스트에서는 `src/test/resources/application.properties`가 Flyway를 비활성화한다.

```text
JUnit repository tests
-> H2
-> Hibernate test schema

Local runtime verification
-> MySQL 8
-> Flyway migration
-> Hibernate validate
```

MySQL 전용 타입과 Migration 자체는 로컬 MySQL 기동 검증에서 확인한다.
