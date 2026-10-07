# KIS 주식기본조회 원문 관측 이력 저장

## 목적과 범위

[인증 연계 Provider](kis-stock-basic-info-configuration.md)가 반환하는 기존 `KisStockBasicInfoRawResponse`를 DB에 보존하고 ID로 복원한다. **원문 바이트는 변환 없이 저장하며, 해석에 실패한 응답도 추가 API 호출 없이 다시 조사할 수 있다.**

Store는 이미 수신한 응답만 받는다. HTTP·토큰·파서·종목 대조·제한 관측 정책을 의존하지 않으며, Provider와 저장 호출을 자동으로 연결하지 않는다. 자동 수집, 재시도, 응답 캐시, 스케줄과 후보 선정 연결도 추가하지 않는다.

조회 후 저장을 한 번에 수행하려면 별도 [단일 종목 수집 서비스](kis-stock-basic-info-collection.md)의 `collect(symbol)`을 명시적으로 호출한다. Provider와 Store 자체의 계약은 유지하며, 이 서비스가 요청 메타데이터를 대조한 뒤 Store에 원문을 전달한다.

저장된 ID를 파서·식별 대조·유형·제한 정책으로 분석하려면 별도 [저장된 주식기본정보 분석 서비스](kis-stock-basic-info-stored-analysis.md)의 `analyze(observationId, masterBatch)`를 호출한다. Store는 분석을 자동으로 시작하지 않으며, 이 경로는 외부 API 재수집이나 투자 적격 승인이 아니다.

HTTP 200으로 받은 업무 실패 응답이나 잘못된 JSON, UTF-8로 해석할 수 없는 바이트도 원문으로 보존한다. 저장 ID가 있다는 사실은 API 업무 성공, 종목 일치, 거래 가능 여부나 투자 적격의 승인이 아니다. 인증·통신 실패로 원문을 받지 못했으면 저장할 응답이 없으며, null 입력을 정상 관측으로 대체하지 않는다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/observation/`이다.

| 파일 | 책임 |
| --- | --- |
| `persistence/KisStockBasicInfoObservationEntity.java` | 원문과 메타데이터를 보존하고 복원 시 길이·해시·메타데이터를 검증한다. |
| `persistence/KisStockBasicInfoObservationRepository.java` | 저장·ID 조회와 평가시각 기준 ID 전용 JPQL 조회를 제공한다. |
| `storage/KisStockBasicInfoObservationStore.java` | 저장 트랜잭션, 읽기 전용 복원과 관측 ID 선택을 담당한다. |
| `src/main/resources/db/migration/V7__create_kis_stock_basic_info_observation.sql` | 새 관측 테이블만 추가한다. |

Store는 `@Component`이며 Repository와 기존 공통 `Clock` 빈을 주입받는다. 조회 인증 설정이 꺼져 있어도 이미 받은 응답을 저장·복원할 수 있으며, 빈 생성 시 외부 조회를 실행하지 않는다. 새 설정이나 별도 응답 DTO·공통 인터페이스는 없다.

## 저장 계약

`save(KisStockBasicInfoRawResponse)`는 새로운 Entity를 생성하여 `saveAndFlush`한 뒤 생성된 `Long` ID를 반환한다. DB 기록 시각은 주입된 `Clock`에서 한 번 얻는다. DB 실패는 ID를 반환하거나 자동으로 재시도하지 않고 전달한다.

| 컬럼 | 저장 내용 |
| --- | --- |
| `id` | 자동 생성 관측 ID |
| `requested_symbol` | 보정하지 않은 요청 종목 코드 |
| `http_status` | 기존 원문 응답 계약의 HTTP 200 |
| `request_started_at` | 요청 시작 시각 |
| `response_received_at` | 원문 수신 시각 |
| `recorded_at` | DB 저장을 요청한 시각 |
| `content_length` | 원문 바이트 수 |
| `content_sha256` | 원문 바이트의 소문자 SHA-256 |
| `raw_content` | 변환하지 않은 원문 바이트 |

`raw_content`는 `@Lob byte[]`와 MySQL `LONGBLOB`으로 저장한다. 문자열 변환, JSON 재직렬화, Base64 저장이나 업무 응답 필드의 해석은 하지 않는다. 입력 크기는 기존 원문 DTO의 1바이트 이상·1MiB 이하 계약을 따른다. Entity 생성·외부 배열 접근·복원 DTO 모두 배열을 복사하여 반환된 배열의 변경이 관측 내용에 반영되지 않게 한다.

같은 종목·수집 시각·원문·해시여도 매번 새 행을 추가한다. 종목이나 해시를 기준으로 조회하여 덮어쓰거나 중복을 제거하지 않는다. Entity의 관측 컬럼은 JPA `updatable=false`로 지정한다. 이 규칙은 Store의 추가 저장 계약이며, Repository의 삭제 메서드나 직접 SQL·DB 관리자 작업까지 차단하는 접근 제어는 아니다.

키·secret·토큰·요청 헤더를 별도 컬럼에 기록하지 않으며 원문을 로그로 출력하는 기능도 추가하지 않는다. 수신 원문 자체는 그대로 보존하므로 접근자나 Entity 전체를 임의로 직렬화해 로그에 남기지 않는다.

## 시각 정밀도

세 시각은 Entity를 만들 때 `Instant.truncatedTo(ChronoUnit.MICROS)`로 먼저 정규화한다. SQL 컬럼은 기존 프로젝트와 같은 `DATETIME(6)`이다. JDBC나 DB의 반올림에 의존하지 않으므로 `.123456789`는 `.123456000`, `.999999999`는 같은 초의 `.999999000`으로 저장한다.

저장 전에 받은 응답 객체는 변경하지 않는다. 다만 **복원 응답의 시각은 마이크로초 정밀도이므로 원래 나노초 정밀도 객체와 완전히 같은 값이라고 가정해서는 안 된다.** 원문 바이트·요청 코드·HTTP 상태는 그대로 유지한다. 마이크로초보다 짧은 요청에서는 정규화된 시작과 수신 시각이 같을 수 있다.

요청 시작과 수신의 순서는 기존 원문 DTO 규칙으로 검사한다. DB 기록 시각과 수신 시각의 선후 관계는 강제하지 않는다. 서로 다른 Clock 상태나 시계 보정 때문에 DB 기록 시각이 더 이를 수도 있으며, 이 저장소는 이를 거래소 정보의 효력 시각으로 바꾸지 않는다.

수신·기록 시각은 `informationAvailableAt`이나 과거 종목 자격 기준 시각의 인증이 아니다. 현재 응답을 과거 백테스트의 자격 근거로 소급하거나 `AS_OF_VERIFIED`와 거래 허가를 생성하지 않는다.

## 조회와 무결성

`findById(Long)`는 읽기 전용 트랜잭션에서 `Optional<KisStockBasicInfoRawResponse>`를 반환한다. null과 0 이하 ID는 DB 조회 전에 거절한다. 해당 ID가 없을 때만 빈 결과를 반환한다.

복원 시 원문이 nonnull·비어 있지 않음·1MiB 이하인지, 기록 길이가 실제 바이트 수와 같은지, SHA-256이 같은지를 확인한다. 실패하면 `IllegalStateException`을 던지며 원문·해시를 보정하거나 재저장하지 않는다.

세 시각의 nonnull·마이크로초 정밀도를 확인하고 기존 원문 DTO 생성자로 요청 코드·HTTP 상태·요청과 수신 순서를 다시 검사한다. 복원 오류 메시지는 고정 설명과 행 ID만 포함하며 저장된 입력값이나 원래 검증 예외를 노출하지 않는다. DB 접근 실패와 복원 실패를 `Optional.empty()`로 숨기지 않는다.

해시 검사는 저장 내용과 기록된 해시의 일치 검사다. 원천의 진위, 메타데이터 전체의 변조 방지, 데이터 신선도나 거래 가능성을 증명하지 않는다. 원문과 해시를 함께 바꾼 경우를 탐지하는 서명이나 외부 원장 기능은 없다.

## 평가시각 기준 관측 ID 선택

`findLatestObservationId(String symbol, Instant evaluatedAt)`는 읽기 전용 트랜잭션에서 `Optional<Long>`을 반환한다. 호출자가 종목과 평가시각을 명시하며, 조회 중 현재 `Clock`을 읽지 않는다. 종목은 기존 원문 DTO와 같은 6자리 대문자 영숫자 규칙을 따르고 공백 제거·대문자 변환은 하지 않는다. 잘못된 종목이나 null 평가시각은 DB 접근 전에 거절한다.

선택 조건과 우선순위는 다음과 같다.

1. `requested_symbol`이 요청 종목과 같아야 한다.
2. `response_received_at <= evaluatedAt`와 `recorded_at <= evaluatedAt`를 모두 만족해야 한다. 수신했어도 평가시각 이후에 저장된 행은 제외한다.
3. `request_started_at DESC`, `response_received_at DESC`, `id DESC` 순으로 정렬한다. 늦게 도착하거나 저장된 과거 요청이 더 최근에 시작한 요청을 대신하지 않는다.
4. DB에서 첫 행의 ID 하나만 조회한다. 조건에 맞는 행이 없을 때만 빈 결과를 반환하며 DB 오류를 빈 결과로 숨기거나 재시도하지 않는다.

동일 시각도 허용한다. 저장 시각은 마이크로초 정밀도이므로 조회에 바인딩하는 평가시각도 마이크로초로 **내림**한다. 저장된 시각에 대한 `<=` 비교 결과는 유지하면서 JDBC 반올림으로 평가시각 직후의 행이 포함되는 것을 막는다. 예를 들어 기준시각이 `.123456999`이면 `.123456000` 행은 허용하고 `.123457000` 행은 제외한다.

조회는 `select observation.id`와 `PageRequest.of(0, 1)`을 사용한다. Entity나 `raw_content` LOB를 로드하거나 모든 행을 가져온 뒤 Java에서 정렬하지 않는다. 새 DTO·설정·스키마·인덱스는 추가하지 않았다. 현재의 기능 검증은 데이터 증가 시 조회 성능을 보장하지 않으며, 실제 실행계획과 조회량을 확인한 뒤 인덱스 필요성을 판단한다.

**성공한 응답만 고르지 않는다.** HTTP·JSON·업무 성공·원문 해시는 이 ID 조회의 필터가 아니다. 최신 행이 실패 응답이나 손상된 원문이면 그 ID를 그대로 반환하며, 기존 `findById`와 분석 경로가 오류를 드러내게 한다. 과거 정상 응답으로 조용히 대체하지 않는다. 신선도 만료도 ID 선택에서 걸러내지 않고 기존 신선도 정책이 판정한다.

이 선택은 저장 메타데이터에 대한 시간 경계일 뿐 정보의 실제 효력·가용 시각을 인증하지 않는다. `AS_OF_VERIFIED`, 과거 백테스트 적격, 신선도 통과나 주문 허가를 생성하지 않는다. 선택 후 별도 `findById` 또는 분석 서비스를 호출한다면 **ID 조회 1회와 원문 조회 1회는 각각 별도의 DB 조회**다.

현재 Harness·Scheduler·통합 사전 점검 서비스와는 연결하지 않았다. 기존 수동 실행의 명시적 관측 ID 계약도 유지한다. 관측이 없거나 만료된 경우의 실행 차단·수집 정책을 이 메서드가 결정하지 않는다.

## 검증 결과

저장 기능 최초 구현 시 2026-10-07 관련 7개 클래스의 122개 테스트가 모두 통과했다. 실패·오류·건너뛴 테스트는 0개다. 당시 신규 관측 저장 테스트는 78개이고 기존 관련 회귀 테스트는 44개다. 전체 테스트 모음은 실행하지 않았다. 아래 수치와 명령은 최초 구현 당시의 검증 기록이다.

| 검증 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoObservationEntityTest` | 37 |
| `KisStockBasicInfoObservationStoreTest` | 15 |
| `KisStockBasicInfoObservationStoreIntegrationTest` | 17 |
| `KisStockBasicInfoObservationMigrationTest` | 9 |
| `KisStockBasicInfoRawResponseTest` | 21 |
| `StockCandidateEvaluationSnapshotStoreIntegrationTest` | 18 |
| `StockCandidateEvaluationSnapshotMigrationTest` | 5 |

테스트 파일은 같은 `src/test/java/.../observation/persistence/`, `storage/`에 두고 공통 입력·해시·정규화 Fixture는 `observation/support/`에 둔다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponseTest' --tests 'com.stock.strategy.universe.candidate.evaluation.snapshot.storage.StockCandidateEvaluationSnapshotStoreIntegrationTest' --tests 'com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotMigrationTest' --offline --no-daemon
```

Entity와 Store의 단위 테스트는 바이트·해시 유지, JSON 미해석, 방어적 배열 복사, 시각 내림과 같은 정규화 시각, 잘못된 메타데이터·무결성 거절, 반복 저장의 새 Entity 생성과 DB 실패 전파를 확인한다.

H2 JPA 테스트는 영속성 컨텍스트를 비운 뒤 실제 저장된 행을 재조회한다. 1MiB의 임의 바이트와 업무 실패·잘못된 JSON·비 UTF-8 응답을 보존했고, 같은 관측의 반복 저장과 앞선 실패 응답의 유지를 확인했다. 정상 응답을 기존 파서로 재평가한 결과와 입력 해시는 저장 전과 같았다. 직접 SQL로 손상시킨 행은 복원을 거절하고 그대로 남았다.

V7 SQL은 H2 MySQL 모드에서 적용해 1MiB BLOB·중복 관측·모든 관측 컬럼의 NOT NULL을 확인했다. 먼저 저장한 V6 후보 평가 행도 유지됐다. 이 검증은 실제 MySQL의 Flyway 적용이나 성능 검증을 대신하지 않는다.

### ID 선택 추가 검증

2026-10-07 ID 선택 구현 후 관련 4개 클래스의 121개 테스트가 모두 통과했다. 신규 검증은 29개이며 실패·오류·건너뛴 테스트는 0개다. 전체 테스트 모음은 실행하지 않았다.

| 검증 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoObservationStoreTest` | 28 |
| `KisStockBasicInfoObservationStoreIntegrationTest` | 33 |
| `KisStockBasicInfoObservationEntityTest` | 37 |
| `KisStockRestrictionPrecheckServiceIntegrationTest` | 23 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.storage.*' --tests 'com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntityTest' --tests 'com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckServiceIntegrationTest' --offline --no-daemon
```

단위 테스트는 명시적 평가시각의 내림, 첫 페이지 크기 1, 추가 원문 조회·Clock 접근·쓰기 없음, 입력 오류와 DB 오류 전파를 확인했다. H2 테스트는 종목 분리, 미래 수신·저장 제외, 기준시각 동일 및 -1ns·+999ns 경계, 늦게 도착한 과거 요청, 수신시각·ID 동률 정렬, 없는 관측, 업무 실패·잘못된 JSON·손상된 행의 선택과 기존 복원 거절을 확인했다.

영속성 컨텍스트와 Hibernate 통계를 초기화한 후 ID 조회는 SQL 1회·조회 1행·Entity 로드 0건·Entity 삽입/수정/삭제 0건이었다. 생성 SQL도 ID 컬럼만 선택하고 `fetch first ? rows only`로 제한했다. 기존 통합 사전 점검 서비스의 명시적 ID 조회·실패 차단 회귀 테스트도 유지됐다. 실제 MySQL 조회 성능, 인덱스와 실행계획은 이번에 검증하지 않았다. Docker와 실제 DB를 기동하거나 외부 API·토큰·OpenAI·계좌·주문을 호출하지 않았다.

## 운영 경계

저장 기능 구현 당시에는 V7 파일과 H2 테스트만 추가하고 실제 MySQL에 적용하지 않았다. 이후 2026-10-07 [V7 적용 및 단건 수집 실환경 검증](validation/kis-stock-basic-info-collection-observation-01.md)에서 기존 DB 백업 후 V7만 적용했다. `005930` 원문 1,860바이트를 ID 1로 저장·복원하고, 기존 10개 테이블의 전체 덤프와 V1~V6 이력이 유지됨을 확인했다. MySQL은 정상 종료했으며 투자 앱은 기동하지 않았다.

기존 파서·Client·Provider·종목 대조·유형·제한·자격·후보·백테스트·주문 경로는 변경하지 않았다. 저장된 관측을 자동으로 읽어 투자 판단에 사용하는 기능도 없다. 저장 기능 구현 검증에서는 외부 API 호출이 0회였고, 후속 실환경 검증에서만 토큰 1회와 주식기본조회 1회를 실행했다. 계좌·주문·OpenAI 호출은 없었으며 상세 범위와 증적은 위 기록에서 구분한다.
