# 저장된 KIS 주식기본정보 분석 수동 실행기

## 목적과 실행 범위

수동 실행기는 [저장된 관측 분석 서비스](kis-stock-basic-info-stored-analysis.md)를 별도 프로세스에서 실행하고 종료한다. **지정한 마스터 배치와 DB 관측 한 건을 읽어 분석하며 KIS 인증·조회와 투자 서버 기동은 하지 않는다.**

[단건 수집 실행기](kis-stock-basic-info-manual-collection.md)는 새 응답을 API에서 받아 저장한다. 이번 실행기는 이미 보관된 입력만 읽는다. 키·secret·토큰을 요구하지 않고, 원문·마스터 재수집, 자동 재시도, 최신 관측 대체와 분석 결과 저장도 하지 않는다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/observation/analysis/runner/`다.

| 파일 | 책임 |
| --- | --- |
| `KisStockBasicInfoAnalysisRunner.java` | 마스터 파싱 후 지정한 ID를 분석하고 요약을 출력한다. 별도 `main`에서 Context를 열고 닫는다. |
| `config/KisStockBasicInfoAnalysisProperties.java` | 활성화 여부, 관측 ID, 마스터 루트와 수집 UUID를 바인딩한다. |
| `config/KisStockBasicInfoAnalysisConfiguration.java` | 관측 조회·마스터 파싱·분석과 기존 스키마 검증에 필요한 빈만 등록한다. |
| `build.gradle`의 `analyzeStockBasicInfo` | 전용 `main`을 실행한다. |
| `src/main/resources/application.yml` | 분석 수동 실행의 기본 비활성화 상태를 명시한다. |

테스트는 같은 `src/test/java/.../analysis/runner/`와 `runner/config/`에 둔다. 기존 분석 서비스, 정책, Store와 마스터 파서는 변경하지 않는다.

## 설정과 입력

| 설정 | 계약 |
| --- | --- |
| `market.stock.basic-info.analysis.manual.enabled` | 기본 `false`. 명시적으로 `true`여야 수동 구성을 등록한다. |
| `market.stock.basic-info.analysis.manual.observation-id` | 활성화 시 필수인 양의 `Long`. 기본 관측이나 종목을 선택하지 않는다. |
| `market.stock.basic-info.analysis.manual.observation-root` | 활성화 시 필수인 마스터 UUID 디렉터리의 부모 경로. 공백만 있는 값과 잘못된 경로 형식을 거절한다. |
| `market.stock.basic-info.analysis.manual.collection-id` | 활성화 시 필수인 마스터 배치 UUID. 목록을 받거나 최신 배치를 자동 선택하지 않는다. |
| `spring.datasource.url` | 대상 DB를 명시해야 한다. 미설정·공백이면 거절하고 내장 DB로 대체하지 않는다. |
| `spring.datasource.username`, `password` | 기존 DataSource 설정을 사용한다. 인증정보는 환경에서 전달하고 명령행에 넣지 않는다. |

Properties 검증은 경로의 형식만 확인한다. 실제 파일의 존재·수집 기록·크기·해시 검증은 [기존 마스터 배치 파싱 서비스](../master/stock-master-batch-parsing.md)가 담당한다. 비활성화된 전용 구성은 DB와 Parser·Runner 빈을 등록하지 않는다.

## 실행 순서와 결과

1. 전용 Context가 준비된 관측 테이블의 매핑을 검증한다.
2. `parseBatch(observationRoot, collectionId)`가 보존된 두 시장의 파일과 기록을 검증·파싱한다.
3. `analyze(observationId, masterBatch)`가 DB 관측을 복원하고 기존 분석 정책을 실행한다.
4. 관측 ID·마스터 수집 ID·요청 종목·원문 해시·식별 대조 사유·참고 유형·유형 사유·제한 상태와 사유를 출력한다.
5. 전용 Context를 닫고 종료한다.

마스터가 먼저 준비되어야 분석 서비스를 호출하지만, 활성화된 Context는 그 전에 DB 연결과 스키마 검증을 수행할 수 있다. 잘못된 마스터가 DB 연결 자체까지 항상 막는다고 해석하지 않는다.

Runner는 마스터 파서와 분석 서비스를 각각 한 번 호출한다. 반환된 마스터 수집 ID와 분석 관측 ID가 요청한 값과 같아야 하며, 분석 결과의 전체 마스터가 파싱한 입력과 같아야 한다. null 결과나 다른 입력의 결과를 완료로 출력하지 않는다.

완료 로그는 다음 형식이다. 실제 유형·상태·사유는 입력에 따라 달라진다.

```text
Stored stock basic info analysis complete. observationId=17, collectionId=..., symbol=0004Y0, inputSha256=..., matchReason=STANDARD_CODE_AND_MARKET_MATCH, referenceSecurityType=COMMON_STOCK, typeReason=BASIC_INFO_TYPE_SUPPLEMENTED, restrictionStatus=NO_EXCLUSION_SIGNAL_OBSERVED, restrictionReasons=[]
```

응답 본문·API 메시지·종목 이름·전체 마스터와 인증정보는 이 결과 로그에 넣지 않는다. `Stock basic info manual analysis process finished.`만 있고 분석 완료 로그가 없으면 분석 성공이 아니다. 비활성화 실행도 프로세스 종료 메시지는 남길 수 있다.

식별 불일치, 유형 미확정과 검토 필요·제외 신호는 분석 결과로 출력할 수 있다. **분석 완료는 투자 적격이나 주문 승인이 아니다.** 제한 신호 없음도 유형·상장 상태·신선도·NXT 거래 가능성이나 과거 시점 자격을 확정하지 않는다.

## 구성 격리와 DB 보호

Runner와 전용 구성은 정상 서버의 컴포넌트 스캔 대상이 아니다. `main`이 구성을 직접 등록하며 `WebApplicationType.NONE`으로 시작한다. 수동 플래그가 켜졌다는 이유만으로 정상 서버가 이 Runner를 실행하지 않는다.

전용 구성은 관측 Entity·Repository 패키지와 Store, UTC Clock, 기존 마스터 파싱 서비스와 분석 서비스를 등록한다. 전체 애플리케이션 스캔·전체 자동 구성·KIS 조회 설정을 가져오지 않는다. Broker·Harness·Agent·OpenAI·스케줄러·HTTP Client·웹 서버 빈을 등록하지 않는다.

DataSource·Hibernate JPA·Transaction 자동 구성만 명시적으로 가져온다. Flyway와 Spring SQL 초기화 자동 구성이 없으므로 해당 설정이 켜져 있어도 마이그레이션이나 SQL 초기화를 실행하지 않는다. Hibernate 전역·ORM contributor·Jakarta 및 이전 javax의 DDL 작업은 `validate`, 스크립트 생성은 `none`으로 고정한다. 준비된 테이블이나 컬럼이 없으면 생성·갱신하지 않고 실패한다.

분석 경로는 Store의 읽기 전용 조회만 사용한다. 일반 Repository나 관리자 SQL의 쓰기까지 제한하는 DB 접근 제어를 추가한 것은 아니다. 정상 종료는 `main`의 try-with-resources가 Context를 닫고, 시작 중 분석 실패는 Spring의 실패 정리로 DataSource 풀을 닫는다.

마스터 파일과 DB 관측의 시각·해시는 원래 입력대로 유지한다. 분석 시각을 수집 시각으로 덮어쓰거나 `informationAvailableAt`, `AS_OF_VERIFIED`와 거래 허가를 생성하지 않는다. 후보·Universe·백테스트·Risk·주문 경로에 연결하지 않는다.

## 실행 방법

DB나 보존 파일 없이 비활성화 실행을 확인할 수 있다.

```powershell
.\gradlew.bat analyzeStockBasicInfo --args='--market.stock.basic-info.analysis.manual.enabled=false' --offline --no-daemon
```

활성화하려면 관측 테이블이 준비된 DB와 완료된 마스터 배치가 필요하다. 마스터 입력은 `<observationRoot>/<collectionId>/manifest.json` 및 두 시장의 ZIP·MST·`observation.json` 총 7개 파일이다. 기존 파일을 그대로 사용하며 이번 실행을 위해 다시 다운로드하지 않는다.

기존 `local` 프로파일의 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 또는 명시적인 `SPRING_DATASOURCE_*` 환경변수로 DataSource를 지정한다. `.env` 파일은 자동으로 읽지 않으며 Docker Compose나 IntelliJ의 설정이 PowerShell의 Gradle 프로세스에 자동 상속된다고 가정하지 않는다. KIS·OpenAI 인증정보는 필요 없다.

다음은 활성화 형식 예시다. `<OBSERVATION_ID>`, `<MASTER_ROOT>`, `<COLLECTION_UUID>`를 실제 입력으로 바꾸어야 하며, `MASTER_ROOT`에 공백이 없다면 아래 형식으로 전달할 수 있다. 경로에 공백이 있으면 `--args` 내부에서도 경로를 따옴표로 묶거나 전용 환경변수 `MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_OBSERVATIONROOT`를 사용한다.

```powershell
.\gradlew.bat analyzeStockBasicInfo --args='--spring.profiles.active=local --market.stock.basic-info.analysis.manual.enabled=true --market.stock.basic-info.analysis.manual.observation-id=<OBSERVATION_ID> --market.stock.basic-info.analysis.manual.observation-root=<MASTER_ROOT> --market.stock.basic-info.analysis.manual.collection-id=<COLLECTION_UUID>' --offline --no-daemon
```

실제 MySQL 관측 ID 1의 분석은 별도 실환경 실행 검증으로 구분한다. 이번 구현 검증에서는 H2와 합성 마스터만 사용하고 실제 MySQL과 기존 보존 원본은 읽지 않는다.

Gradle의 `--offline`은 의존성 다운로드 제어다. DB 연결을 차단하는 옵션은 아니며 Gradle Wrapper의 배포판 준비도 별개다. 이 실행기 자체는 Broker API나 토큰 발급 경로를 등록하지 않는다.

## 검증 항목

신규 테스트는 Properties, Runner와 전용 Spring 구성 세 클래스로 구성한다. 관련 테스트만 실행하며 운영 DB와 KIS·계좌·주문·OpenAI 호출은 하지 않는다.

- 입력 바인딩·비활성화·잘못된 관측 ID·마스터 루트·UUID 거절을 확인한다.
- 마스터 파싱 후 관측 분석의 단일 호출 순서, 실패 시 중단·재시도 부재, 다른 입력의 결과 거절과 요약 로그를 확인한다.
- H2 관측과 임시 합성 마스터를 실제 파서·분석 서비스에 연결한다. 원본 7개 파일, 관측 행의 전체 값과 무관한 sentinel 테이블·행이 유지되는지 검사한다.
- Broker·KIS 조회·OpenAI·스케줄 플래그를 켜고 조회용 키는 비워도 관련 빈을 등록하지 않는다. Entity는 관측 Entity 하나만 등록한다.
- DDL 생성·갱신·삭제·SQL 스크립트·Flyway·SQL 초기화 옵션을 켜도 스키마를 바꾸지 않는다. 정상 분석, 관측 없음, 업무 실패·마스터 손상 및 스키마 실패에서 풀 종료를 확인한다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.runner.*' --offline --no-daemon
```

## 구현 검증 결과

2026-10-07 신규 세 클래스의 **61개 테스트가 모두 통과**했다. 분석 서비스·마스터 파싱·기존 단건 수집 구성까지 포함한 관련 테스트는 9개 클래스, 175개이며 **174개 통과·1개 건너뜀**, 실패·오류는 0개다. 최신 HTML 보고서와 실행 대상 XML의 suite 이름으로 집계했으며 전체 프로젝트 테스트는 실행하지 않았다.

| 신규 테스트 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoAnalysisRunnerTest` | 17 |
| `KisStockBasicInfoAnalysisPropertiesTest` | 25 |
| `KisStockBasicInfoAnalysisConfigurationTest` | 19 |

건너뛴 테스트는 기존 `StockMasterBatchParsingServiceTest.rejectsFileLinksAndBatchDirectoryLinksWhenSupported()`다. 현재 Windows 파일시스템에서 심볼릭 링크 생성 권한을 사용할 수 없어 해당 조건부 검증을 실행하지 못했다. 이를 통과한 것으로 계산하지 않는다.

같은 명령에서 `analyzeStockBasicInfo`의 비활성화 실행도 확인했다. Broker·KIS 조회·Harness 스케줄·OpenAI 플래그를 켜도 분석 완료 로그 없이 프로세스가 정상 종료했다. 활성화 분석과 실패 시 풀 종료는 H2·합성 파일 통합 테스트로 확인했으며 실제 MySQL 분석은 실행하지 않았다.

검증에 사용한 명령은 다음과 같다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --tests 'com.stock.market.stock.master.parsing.*' --tests 'com.stock.market.stock.basicinfo.collection.runner.config.KisStockBasicInfoCollectionConfigurationTest' analyzeStockBasicInfo --args='--market.stock.basic-info.analysis.manual.enabled=false --broker.kis.enabled=true --market.stock.basic-info.kis.enabled=true --harness.scheduler.enabled=true --agent.provider.ai.openai.enabled=true' --offline --no-daemon
```
