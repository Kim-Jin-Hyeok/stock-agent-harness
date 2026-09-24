# Docker Paper Observation

## 목적

MySQL과 Spring Boot 애플리케이션을 Docker Compose로 함께 실행한다.
모의투자 인증정보는 프로젝트 루트의 `.env`에 한 번만 입력하고,
Compose가 애플리케이션 컨테이너에 환경변수로 전달한다.

`.env`는 Git과 Docker 이미지에 포함되지 않는다. 실제 인증정보를
`compose.yml`, `application.yml`, `Dockerfile`에 직접 작성하지 않는다.

## 파일

```text
Dockerfile
.dockerignore
compose.yml
.env.example
```

## 최초 설정

프로젝트 루트에서 예제 파일을 복사한다.

```powershell
Copy-Item .env.example .env
```

생성된 `.env`에서 다음 값만 실제 모의투자 정보로 변경한다.

```text
KIS_PAPER_APP_KEY=
KIS_PAPER_APP_SECRET=
KIS_PAPER_ACCOUNT_NUMBER=
KIS_PAPER_ACCOUNT_PRODUCT_CODE=01
```

계좌번호는 앞 8자리만 `KIS_PAPER_ACCOUNT_NUMBER`에 넣고, 뒤 2자리는
`KIS_PAPER_ACCOUNT_PRODUCT_CODE`에 넣는다.

## 전체 실행

IntelliJ에서 실행 중인 서버가 있다면 먼저 종료한다. 기본적으로 앱
컨테이너도 호스트의 `8080` 포트를 사용하기 때문이다.

```powershell
docker compose up -d --build
docker compose ps
```

앱 컨테이너에는 다음 설정이 적용된다.

```text
SPRING_PROFILES_ACTIVE=local,paper-observation
DB_HOST=mysql
DB_PORT=3306
```

호스트에서 MySQL에 직접 접속할 때는 `.env`의 `DB_PORT`를 사용한다.
기본값은 `3307`이다. 컨테이너 사이에서는 MySQL 서비스 이름과 내부
포트인 `mysql:3306`을 사용한다.

## 확인

```powershell
docker compose logs -f app
```

애플리케이션 시작 후 다음 API로 서버와 DB 연결을 확인한다.

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/harness/runs"
```

## 종료

데이터 볼륨을 유지하면서 종료한다.

```powershell
docker compose down
```

MySQL 데이터까지 삭제해야 하는 경우에만 다음 명령을 사용한다.

```powershell
docker compose down -v
```
