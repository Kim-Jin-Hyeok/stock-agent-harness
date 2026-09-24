# Monitoring Dashboard and JWT Authentication Design

## Purpose

이 문서는 모의투자 데이터를 서버에 축적한 뒤, 별도의 React 프로젝트에서 로그인하여 Run, 전략, 포트폴리오, 주문과 성과를 확인하기 위한 장기 방향을 정의한다.

현재 Spring Boot 프로젝트는 화면을 직접 제공하는 애플리케이션이 아니라 다음 책임을 가진 실행 서버로 유지한다.

```text
Scheduler
-> Investment Harness
-> Investment Agent
-> Broker 모의투자 API
-> MySQL 이력 저장
-> Monitoring API 제공
```

React 화면은 별도 프로젝트에서 개발하고 Spring Boot가 제공하는 인증 및 조회 API만 호출한다.

## Current Decision

다음 방향을 기본 결정으로 사용한다.

- Harness 서버와 React Dashboard는 별도 프로젝트로 관리한다.
- Dashboard 사용자는 JWT 기반 인증을 거친다.
- 브라우저는 MySQL이나 KIS API에 직접 접근하지 않는다.
- KIS App Key, App Secret, 계좌번호, Access Token은 백엔드 밖으로 전달하지 않는다.
- 초기 Dashboard는 조회 전용으로 시작한다.
- Scheduler 제어, Harness 수동 실행, 상태 초기화 같은 운영 명령은 조회 기능 이후에 별도 권한과 확인 절차를 적용한다.

이 결정은 JWT 기능을 지금 구현한다는 의미가 아니다. 모의투자 데이터를 안정적으로 축적하고 조회할 수 있는 서버 환경이 준비된 후 구현한다.

## Target Architecture

```text
KIS 모의투자 API
        |
stock-agent-harness
- Scheduler
- Harness / Agent
- Risk Guard
- Broker Integration
- Monitoring Query API
- JWT Authentication
        |
      MySQL
        |
     HTTPS API
        |
stock-agent-dashboard
- Login
- Run Monitoring
- Strategy Performance
- Portfolio / Order Charts
```

운영 초기에는 하나의 도메인에서 Reverse Proxy로 경로를 나누는 구성을 우선 검토한다.

```text
https://stock.example.com/      -> React 정적 파일
https://stock.example.com/api/  -> Spring Boot API
```

이 구성은 별도 Origin을 사용할 때보다 CORS와 Cookie 정책을 단순하게 만들 수 있다.

## Authentication Direction

인증은 Spring Security와 JWT를 사용한다.

기본 토큰 흐름은 다음 방향으로 설계한다.

```text
Login
-> 사용자 자격 증명 검증
-> 짧은 수명의 Access JWT 발급
-> 긴 수명의 Refresh Token 발급

API Request
-> Authorization: Bearer {accessToken}
-> 백엔드가 서명, 만료 시각, 사용자, 권한 검증

Access Token Expired
-> Refresh Token 검증
-> Access JWT 재발급

Logout
-> Refresh Token 세션 폐기
```

권장 저장 정책은 다음과 같다.

- Access JWT는 React 애플리케이션 메모리에 보관한다.
- Refresh Token은 `HttpOnly`, `Secure`, `SameSite` 속성을 가진 Cookie로 전달한다.
- Access JWT와 Refresh Token을 브라우저 `localStorage`에 장기 저장하지 않는다.
- Refresh Token은 회전 방식으로 갱신하고 서버에서 폐기 가능하게 관리한다.
- 초기 단일 서버 환경에서는 Refresh Token 세션 또는 Token Hash를 MySQL에 저장할 수 있다.
- 여러 서버가 필요한 시점 전에는 Redis를 도입하지 않는다.

Refresh Token Cookie를 사용하는 갱신 Endpoint는 Cookie 정책만 믿지 않고 CSRF 위협도 함께 검토해야 한다.

## Authorization Boundary

로그인 여부만 검사하지 않고 조회 권한과 운영 권한을 분리한다.

초기 권한 예시는 다음과 같다.

```text
MONITOR_READ
-> Run, 전략, 현재가, 포트폴리오, 주문, 성과 조회

HARNESS_OPERATE
-> Harness 수동 실행
-> Scheduler 시작 및 중지

HARNESS_ADMIN
-> 상태 초기화
-> 위험 설정 변경
-> 실제 투자 모드 전환과 관련된 관리 작업
```

개인용 단일 사용자 서비스로 시작하더라도 API 권한 경계는 유지한다. React에서 버튼을 숨기는 것만으로는 접근 제어가 되지 않으므로 모든 권한 검사는 Spring 서버에서 수행한다.

특히 다음 Endpoint는 인증 없이 외부에 노출하지 않는다.

```text
POST /api/harness/run
POST /api/harness/reset
Scheduler 제어 API
주문 및 주문 취소 API
```

## Monitoring API Direction

React는 DB 테이블 구조가 아니라 안정적인 조회 API 계약에 의존한다.

예상 조회 API는 다음과 같다.

```text
GET /api/monitoring/runs
GET /api/monitoring/runs/{runId}
GET /api/monitoring/strategies/{strategyId}/performance
GET /api/monitoring/strategies/{strategyId}/portfolio-history
GET /api/monitoring/prices?symbol=005930&from=...&to=...
GET /api/monitoring/orders
GET /api/monitoring/failures
```

그래프용 API는 원본 Entity 목록을 그대로 반환하지 않는다. 기간, 전략, 종목을 기준으로 필요한 시계열과 집계값만 반환한다.

Dashboard에서 우선 확인할 데이터는 다음과 같다.

- 전략별 Run 횟수와 성공·실패 상태
- 현재가 관측 이력과 Provider·Cache 출처
- 전략별 현금, 보유 종목, 평가금액 변화
- 주문 접수, 체결, 부분 체결, 취소 상태
- 매수·매도 이력
- Harness 실패 Step과 reasonCode
- 총 수익률, MDD, 승률, 거래 횟수

## Expected Backend Package Structure

Monitoring 조회 기능은 기존 실행 코드와 분리한다.

```text
src/main/java/com/stock/monitoring/
├── api/
│   ├── MonitoringRunController.java
│   ├── MonitoringPriceController.java
│   └── MonitoringPerformanceController.java
├── application/
│   └── MonitoringQueryService.java
├── query/
│   └── MonitoringQueryRepository.java
└── dto/
    ├── RunMonitoringResponse.java
    ├── PriceSeriesResponse.java
    └── StrategyPerformanceResponse.java
```

JWT 인증 기능은 다음 경계를 기본으로 검토한다.

```text
src/main/java/com/stock/auth/
├── api/
│   ├── AuthController.java
│   └── dto/
├── application/
│   └── AuthService.java
├── domain/
│   ├── UserAccount.java
│   └── RefreshTokenSession.java
├── persistence/
│   ├── UserAccountEntity.java
│   └── RefreshTokenSessionRepository.java
└── security/
    ├── SecurityConfiguration.java
    ├── JwtTokenProvider.java
    └── JwtAuthenticationFilter.java
```

실제 구현 시 클래스 수는 필요한 범위로 줄일 수 있다. 위 구조를 한 번에 전부 만들지 않는다.

## Expected Frontend Project Structure

React는 별도 Repository로 관리한다.

```text
stock-agent-dashboard/
└── src/
    ├── features/
    │   ├── auth/
    │   ├── runs/
    │   ├── strategies/
    │   ├── portfolios/
    │   └── orders/
    └── shared/
        ├── api/
        └── components/
```

프론트엔드는 Broker 응답 DTO나 JPA Entity를 직접 알지 않는다. Monitoring API의 Response DTO만 사용한다.

## Security Requirements

- 운영 환경은 HTTPS만 허용한다.
- JWT 서명 키는 Repository와 `application.yml`에 저장하지 않는다.
- 비밀번호는 평문으로 저장하지 않고 BCrypt 또는 Argon2 계열 해시를 사용한다.
- CORS를 사용할 경우 Dashboard의 정확한 Origin만 허용한다.
- 인증 실패 이유에 사용자 존재 여부나 내부 보안 정보를 노출하지 않는다.
- 로그에 비밀번호, JWT 원문, Refresh Token, KIS 인증정보를 기록하지 않는다.
- JWT에는 계좌번호, Broker Token, 포트폴리오 전체 같은 민감 데이터를 넣지 않는다.
- Access Token 만료 시간, Refresh Token 만료 시간과 폐기 정책은 운영 전에 테스트로 고정한다.

## Implementation Order

다음 순서로 점진적으로 구현한다.

```text
1. MySQL 기반 운영 영속 환경 준비
2. 모의투자 Scheduler 실행과 데이터 축적 검증
3. Monitoring 조회 API 계약 정의
4. 읽기 전용 Monitoring API 구현
5. Spring Security와 사용자 인증 경계 추가
6. Access JWT와 Refresh Token 흐름 구현
7. 별도 React Dashboard 프로젝트 생성
8. 로그인과 읽기 전용 화면 연결
9. 전략별 그래프와 성과 지표 추가
10. 운영 명령 API에 별도 권한과 확인 절차 추가
```

JWT 구현과 React 프로젝트 생성을 동시에 진행하지 않는다. 먼저 백엔드 인증 계약을 테스트로 고정한 뒤 React가 그 계약을 사용하도록 한다.

## Implementation Start Conditions

다음 조건이 충족되면 JWT와 Dashboard 구현을 시작한다.

- 운영 또는 운영에 준하는 MySQL에서 Run 이력이 재시작 후에도 유지된다.
- Scheduler가 모의투자 환경에서 정해진 주기로 실행된다.
- 현재가, 포트폴리오, 주문과 체결 이력이 필요한 수준으로 저장된다.
- 조회 화면에 필요한 기간별 API 범위가 정리된다.
- 실제 투자 전환과 무관하게 모의투자 데이터만으로 Dashboard를 검증할 수 있다.

## Out of Scope for Current Stage

현재 단계에서는 다음 작업을 수행하지 않는다.

- Spring Security 의존성 추가
- 사용자 또는 Refresh Token Entity 생성
- JWT 발급·검증 코드 구현
- 로그인 API 구현
- React 프로젝트 생성
- 운영 명령 Endpoint 추가
- Redis 기반 Token 저장소 도입

현재 작업의 목적은 미래 구현 방향을 문서로 고정하여 데이터 축적 작업과 Dashboard 작업의 경계를 잃지 않게 하는 것이다.

