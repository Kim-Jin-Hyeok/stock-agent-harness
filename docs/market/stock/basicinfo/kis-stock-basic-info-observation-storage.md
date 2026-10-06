# KIS 주식기본조회 원문 관측 이력 저장

## 목적과 범위

[인증 연계 Provider](kis-stock-basic-info-configuration.md)가 반환하는 기존 `KisStockBasicInfoRawResponse`를 DB에 보존하고 ID로 복원한다. **원문 바이트는 변환 없이 저장하며, 해석에 실패한 응답도 추가 API 호출 없이 다시 조사할 수 있다.**

Store는 이미 수신한 응답만 받는다. HTTP·토큰·파서·종목 대조·제한 관측 정책을 의존하지 않으며, Provider와 저장 호출을 자동으로 연결하지 않는다. 자동 수집, 재시도, 응답 캐시, 스케줄과 후보 선정 연결도 추가하지 않는다.

HTTP 200으로 받은 업무 실패 응답이나 잘못된 JSON, UTF-8로 해석할 수 없는 바이트도 원문으로 보존한다. 저장 ID가 있다는 사실은 API 업무 성공, 종목 일치, 거래 가능 여부나 투자 적격의 승인이 아니다. 인증·통신 실패로 원문을 받지 못했으면 저장할 응답이 없으며, null 입력을 정상 관측으로 대체하지 않는다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/observation/`이다.

| 파일 | 책임 |
| --- | --- |
| `persistence/KisStockBasicInfoObservationEntity.java` | 원문과 메타데이터를 보존하고 복원 시 길이·해시·메타데이터를 검증한다. |
| `persistence/KisStockBasicInfoObservationRepository.java` | 기존 `JpaRepository` 패턴으로 저장과 ID 조회를 제공한다. |
| `storage/KisStockBasicInfoObservationStore.java` | 저장 트랜잭션과 읽기 전용 복원을 담당한다. |
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

## 검증 결과

2026-10-07 관련 7개 클래스의 122개 테스트가 모두 통과했다. 실패·오류·건너뛴 테스트는 0개다. 신규 관측 저장 테스트는 78개이고 기존 관련 회귀 테스트는 44개다. 전체 테스트 모음은 실행하지 않았다.

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

## 운영 경계

V7 파일은 추가했지만 실행 중인 MySQL이나 서버에 적용하지 않았다. Flyway가 활성화된 배포에서 미적용 V7은 다음 실행 시 적용 대상이 된다. 이번 작업은 서버 재기동이나 Docker·환경변수 변경 없이 코드와 H2 테스트만 수행했다.

기존 파서·Client·Provider·종목 대조·유형·제한·자격·후보·백테스트·주문 경로는 변경하지 않았다. 저장된 관측을 자동으로 읽어 투자 판단에 사용하는 기능도 없다. 실제 KIS 요청·토큰 발급·계좌·주문·OpenAI 호출은 0회이며, 기존 보존 증적과 커밋·Push를 변경하지 않았다.
