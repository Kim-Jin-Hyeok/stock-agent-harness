# KIS 주식기본조회 단건 수집 수동 실행기

## 목적과 실행 범위

수동 실행기는 기존 [단일 종목 수집 서비스](kis-stock-basic-info-collection.md)를 별도 프로세스에서 한 번 호출하고 종료한다. **전체 투자 서버를 기동하지 않고 종목 하나의 원문을 준비된 DB에 저장하는 진입점이다.** 저장 완료 로그의 관측 ID는 원문 보존 결과이며 업무 성공이나 투자 적격 판정이 아니다.

실환경 점검은 [단건 수집 사전 점검 기록](validation/kis-stock-basic-info-collection-observation-01.md)에 별도로 남긴다. 2026-10-07 MySQL 기동과 DB 접속은 확인했지만 V7과 관측 테이블이 없어 수집 전에 중단했다. MySQL은 정상 종료했으며, 실제 KIS 조회와 원문 저장 검증 완료를 뜻하지 않는다.

웹 서버·Harness·투자 Agent·주문·취소·OpenAI·스케줄·다른 수집 Runner를 등록하지 않는다. 전종목 반복, 자동 재시도, 응답 캐시, 토큰 영속화와 후보 선정 연결도 포함하지 않는다. 실제 주문 설정이나 기존 모의투자 연결은 변경하지 않는다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/collection/runner/`다.

| 파일 | 책임 |
| --- | --- |
| `KisStockBasicInfoCollectionRunner.java` | 명시적인 활성화 상태에서 단건 수집을 호출하고 양의 관측 ID만 저장 완료로 기록한다. 별도 `main`에서 컨텍스트를 열고 닫는다. |
| `config/KisStockBasicInfoCollectionProperties.java` | 수동 활성화 여부와 종목 하나를 바인딩하고 검증한다. |
| `config/KisStockBasicInfoCollectionConfiguration.java` | 수동 프로세스에 필요한 인증·관측 저장·DB 트랜잭션·스키마 검증만 등록한다. |
| `build.gradle`의 `collectStockBasicInfo` | 기존 마스터 수집 작업처럼 전용 `main`을 실행한다. |
| `src/main/resources/application.yml` | 수동 수집의 기본 비활성화 상태를 명시한다. |

테스트는 같은 `src/test/java/.../collection/runner/`의 Runner 테스트와 `runner/config/`의 Properties·Configuration 테스트에 둔다. 기존 서비스·Provider·Store와 원문 응답 DTO는 수정하지 않는다.

## 설정과 실패 조건

| 설정 | 계약 |
| --- | --- |
| `market.stock.basic-info.collection.manual.enabled` | 기본 `false`. `true`여야 수동 구성을 등록한다. |
| `market.stock.basic-info.collection.manual.symbol` | 활성화 시 필수인 단일 종목. 정확히 여섯 자리 대문자 영숫자만 받는다. |
| `market.stock.basic-info.kis.enabled` | 수동 활성화와 별도로 `true`여야 한다. |
| `market.stock.basic-info.kis.app-key`, `app-secret` | 기존 전용 인증 설정을 사용한다. 모의투자 키로 대체하지 않는다. |
| `spring.datasource.url` | 대상 DB를 명시해야 한다. 미설정·공백이면 거절하고 임시 내장 DB로 대체하지 않는다. |
| `spring.datasource.username`, `password` | 기존 Spring DataSource 설정을 사용한다. 별도 수집 전용 자격 설정은 추가하지 않는다. |

종목을 기본값으로 선택하거나 목록·쉼표 구분 문자열을 순회하지 않는다. trim, 소문자 변환과 코드 보정도 없다. 수동 설정과 기존 Provider 모두 각 진입점에서 같은 종목 형식을 검증한다. 잘못된 설정·스키마 오류는 수집 전에 실패한다.

Runner는 `collect(symbol)`을 한 번 호출한다. 반환 ID가 null 또는 0 이하면 실패하며 저장 완료 로그를 남기지 않는다. 조회·저장 예외는 그대로 전달하고 재호출하지 않는다. 별도 `main`도 실행 예외를 성공으로 바꾸지 않으므로 실패한 실행은 정상 종료로 처리되지 않는다.

## 구성 격리와 DB 보호

Runner는 `@Component`가 아니며, 수동 구성에도 `@Configuration` 등 컴포넌트 stereotype을 붙이지 않는다. 전용 `main`이 구성 클래스를 직접 등록한다. 일반 서버의 컴포넌트 스캔에서 수동 Runner와 구성을 발견하지 않으므로 수동 플래그가 켜졌다는 이유만으로 일반 서버 시작 시 수집하지 않는다.

활성화된 전용 구성은 다음 기반만 사용한다.

- 기존 `KisStockBasicInfoConfiguration`의 전용 인증과 수집 서비스.
- 기존 관측 Store와 `KisStockBasicInfoObservationRepository`가 있는 패키지의 JPA Repository.
- `KisStockBasicInfoObservationEntity`가 있는 패키지의 Entity와 UTC Clock.
- 명시적으로 가져온 DataSource·Hibernate JPA·Transaction 자동 구성.

넓은 애플리케이션 스캔이나 전체 자동 구성은 사용하지 않는다. Flyway와 Spring SQL 초기화 자동 구성도 가져오지 않으며, 설정에서 활성화해도 마이그레이션이나 `schema.sql` 초기화를 실행하지 않는다.

Hibernate 스키마 작업은 `validate`, 스크립트 생성은 `none`으로 고정한다. 프로파일·명령행의 `create`, `create-drop`, `update`, JPA 스키마 생성 옵션이 수집을 스키마 관리 작업으로 바꾸지 못하게 한다. Jakarta·이전 javax 키와 Hibernate의 전역·`orm` contributor 설정을 함께 고정한다. JPA 속성은 Spring에서 다시 병합되므로 값을 삭제하는 대신 검증·비생성 값으로 덮어쓴다. JPA의 `validate` 값 해석은 현재 프로젝트의 Hibernate 구현을 사용한다.

준비된 `kis_stock_basic_info_observation` 테이블이 없거나 매핑과 맞지 않으면 기동을 중단한다. 기존 V7 적용은 별도의 배포·마이그레이션 절차이며 이 실행기가 대신 수행하지 않는다. 수집 시 의도한 관측 INSERT는 수행하지만 테이블을 생성·갱신·삭제하지 않는다.

수집 Runner에는 트랜잭션을 걸지 않는다. 외부 조회는 DB 트랜잭션 밖에서 실행하고 원문 수신 뒤 Store의 기존 저장 트랜잭션을 사용한다. 정상 종료 시 전용 컨텍스트가 닫혀 DataSource 풀과 HTTP 클라이언트도 종료된다.

## 실행 방법

먼저 실제 호출 없이 비활성화 기동과 종료를 확인할 수 있다.

```powershell
.\gradlew.bat collectStockBasicInfo --args='--market.stock.basic-info.collection.manual.enabled=false' --offline --no-daemon
```

활성화된 실행에는 준비된 DB와 조회용 인증정보가 필요하다. 현재 `local` 프로파일은 기존 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 환경변수를 사용한다. 실행 전에 실제 대상 DB·스키마와 관측 테이블 준비 여부를 확인한다.

`KIS_READONLY_APP_KEY`, `KIS_READONLY_APP_SECRET`은 Gradle에서 시작하는 Java 프로세스가 상속할 환경에 있어야 한다. Spring과 이 실행기는 `.env` 파일을 자동으로 읽지 않는다. Docker Compose나 IntelliJ 실행 설정에 넣은 값이 PowerShell의 Gradle 실행에 자동으로 전달된다고 가정하지 않는다. 키·secret·DB 비밀번호는 Gradle 인자에 넣지 않는다.

다음 명령은 실제 KIS 인증·조회와 DB 저장을 수행하는 **활성화 예시**다. 이번 검증에서는 실행하지 않았다.

```powershell
.\gradlew.bat collectStockBasicInfo --args='--spring.profiles.active=local --market.stock.basic-info.collection.manual.enabled=true --market.stock.basic-info.collection.manual.symbol=005930 --market.stock.basic-info.kis.enabled=true' --offline --no-daemon
```

Gradle의 `--offline`은 Gradle 의존성 접근을 제어하는 옵션이지 애플리케이션의 KIS·DB 통신을 차단하는 옵션이 아니다.

저장 완료 시 다음 형식으로만 새 수집 결과를 기록한다.

```text
Stock basic info raw observation saved. symbol=005930, observationId=17
```

원문·해시·키·secret·토큰은 Runner의 결과 로그에 넣지 않는다. 종료 메시지 `Stock basic info manual process finished.`만 있고 관측 저장 메시지가 없으면 관측 저장 완료로 해석하지 않는다. 비활성화 실행도 종료 메시지는 남길 수 있다.

**프로세스 재실행 사이에는 인메모리 토큰 캐시를 공유하지 않는다.** 활성화된 새 프로세스는 다시 토큰을 발급할 수 있으므로 반복 기동을 토큰 캐시 재사용으로 착각하지 않는다. 이번 실행기를 짧은 간격의 반복 검증이나 대량 수집 도구로 사용하지 않으며, 실제 호출 범위는 별도로 확인한다.

## 검증 결과

2026-10-07 관련 10개 클래스의 170개 테스트가 모두 통과했다. 신규 실행기 테스트 38개와 기존 관련 회귀 테스트 132개를 포함한다. 실패·오류·건너뛴 테스트는 0개이며 전체 테스트 모음은 실행하지 않았다.

| 신규 검증 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoCollectionRunnerTest` | 8 |
| `KisStockBasicInfoCollectionPropertiesTest` | 16 |
| `KisStockBasicInfoCollectionConfigurationTest` | 14 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.collection.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.config.KisStockBasicInfoConfigurationTest' --tests 'com.stock.broker.kis.config.KisConfigurationTest' --tests 'com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProviderTest' --tests 'com.stock.market.stock.basicinfo.observation.storage.*' collectStockBasicInfo --args='--market.stock.basic-info.collection.manual.enabled=false' --offline --no-daemon
```

설정·Runner 테스트는 비활성화, 단일 종목 검증, 수집 호출 1회, 오류·잘못된 ID 처리와 저장 완료 로그 부재를 확인했다. 컴포넌트 스캔 테스트는 수동 구성과 Runner가 일반 스캔 후보가 아님을 확인한다.

전용 Spring 구성은 테스트가 H2에 미리 준비한 관측 테이블을 사용한다. 실제 구현을 Mock HTTP와 연결하여 단건 원문 저장·복원·해시와 시각 정밀도, HTTP 호출 중 트랜잭션 부재, 전용 풀 종료를 확인했다. Entity는 관측 Entity 하나만 등록했고, 관련 없는 sentinel 테이블과 행은 그대로 남았다. Broker·Harness·OpenAI·스케줄 플래그를 켜도 해당 빈은 등록되지 않았다.

DDL 생성·갱신·종료 시 삭제와 JPA 스크립트·Flyway·SQL 초기화를 활성화한 테스트 설정에서도 스키마는 변경되지 않았다. 관측 테이블이나 컬럼이 없으면 실제 기동이 API 요청 전에 실패했고, 기존 불완전 테이블의 컬럼과 행도 유지됐다. 이 H2 매핑 검증은 실제 MySQL 또는 V7 마이그레이션 적용 검증을 대신하지 않는다.

Gradle 작업은 수동 활성화를 명시적으로 끈 상태에서 별도 Java 프로세스 기동·종료까지 확인했다. 실제 KIS API·토큰 발급, 운영 MySQL·Flyway 적용, Docker·실행 서버·`.env` 변경은 수행하지 않았다. 기존 증적은 유지하며 수신 시각을 과거 정보 가용 시점으로 인증하거나 투자 적격·거래 허가를 생성하지 않는다.
