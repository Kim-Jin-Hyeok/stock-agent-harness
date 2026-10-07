# 저장된 KIS 주식기본정보 분석 수동 실행기

## 목적과 실행 범위

수동 실행기는 [저장된 관측 분석 서비스](kis-stock-basic-info-stored-analysis.md)를 별도 프로세스에서 실행하고 종료한다. **지정한 마스터 배치와 DB 관측 한 건을 읽어 분석하며 KIS 인증·조회와 투자 서버 기동은 하지 않는다.**

[단건 수집 실행기](kis-stock-basic-info-manual-collection.md)는 새 응답을 API에서 받아 저장한다. 이번 실행기는 이미 보관된 입력만 읽는다. 키·secret·토큰을 요구하지 않고, 원문·마스터 재수집, 자동 재시도, 최신 관측 대체와 분석 결과 저장도 하지 않는다.

기본 모드는 기존 V2 분석이다. `include-market-warnings=true`와 `warning-market`을 명시하면 같은 보존 마스터에서 시장경보를 준비하고 [종합 분석 서비스](kis-stock-restriction-stored-analysis.md)를 실행한다. 여기에 `check-freshness=true`와 평가시각·두 유효기간을 명시하면 같은 종합 결과의 관측 신선도도 점검한다. 기본 명령과 로그는 유지하며 별도 실행기·Gradle Task를 만들지 않는다.

## 패키지와 파일

기준 경로는 `src/main/java/com/stock/market/stock/basicinfo/observation/analysis/runner/`다.

| 파일 | 책임 |
| --- | --- |
| `KisStockBasicInfoAnalysisRunner.java` | 마스터 파싱 후 지정한 ID를 분석하고 요약을 출력한다. 별도 `main`에서 Context를 열고 닫는다. |
| `config/KisStockBasicInfoAnalysisProperties.java` | 활성화 여부, 관측 ID, 마스터 루트·수집 UUID, 선택적 시장경보와 명시적 신선도 점검 조건을 바인딩한다. |
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
| `market.stock.basic-info.analysis.manual.include-market-warnings` | 기본 `false`. `enabled=true`인 수동 실행에서 이 값을 켜야 종합 분석을 실행한다. |
| `market.stock.basic-info.analysis.manual.warning-market` | 활성화된 종합 모드에서 필수인 `KOSPI` 또는 `KOSDAQ`. 기본 시장·목록·자동 시장 선택은 없다. 시장만 지정해도 종합 모드가 켜지지는 않는다. |
| `market.stock.basic-info.analysis.manual.check-freshness` | 미설정 시 `false`로 바인딩한다. 활성화된 점검에는 `include-market-warnings=true`도 필요하며 종합 모드를 자동으로 켜지 않는다. |
| `market.stock.basic-info.analysis.manual.evaluated-at` | 활성화된 신선도 점검에서 필수인 `Instant`. `Z` 또는 UTC 오프셋이 있는 시각을 명시한다. 실행 시각으로 자동 대체하지 않는다. |
| `market.stock.basic-info.analysis.manual.max-master-age` | 활성화된 신선도 점검에서 필수인 양의 `Duration`. 기본 유효기간은 없다. |
| `market.stock.basic-info.analysis.manual.max-basic-info-age` | 활성화된 신선도 점검에서 필수인 양의 `Duration`. 기본 유효기간은 없다. |
| `spring.datasource.url` | 대상 DB를 명시해야 한다. 미설정·공백이면 거절하고 내장 DB로 대체하지 않는다. |
| `spring.datasource.username`, `password` | 기존 DataSource 설정을 사용한다. 인증정보는 환경에서 전달하고 명령행에 넣지 않는다. |

Properties 검증은 경로의 형식만 확인한다. 실제 파일의 존재·수집 기록·크기·해시 검증은 [기존 마스터 배치 파싱 서비스](../master/stock-master-batch-parsing.md)가 담당한다. 비활성화된 전용 구성은 DB와 Parser·Runner 빈을 등록하지 않는다.

기존 네·여섯 인자 Properties 생성자는 유지하며 추가된 신선도 점검은 비활성화·조건 미지정으로 둔다. Spring 바인딩은 `@ConstructorBinding`으로 명시한 열 필드 생성자를 사용한다. 기존 세·여섯 인자 Runner 생성자도 유지한다. 활성화된 종합 모드에는 경보 Parser·관측 Policy·종합 Service가, 신선도 점검에는 추가로 신선도 Policy가 필요하다. 전체 비활성화나 점검 미사용 시에는 사용하지 않는 조건의 업무 검증을 생략하지만, 잘못된 시각·기간 형식의 바인딩 오류까지 허용하는 것은 아니다.

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

## 시장경보 포함 실행

종합 모드도 마스터 파싱과 수집 ID 확인을 먼저 수행한다. 그 다음 아래 순서로 진행한다.

1. 파싱된 배치에서 `warning-market`에 지정한 시장의 전체 원문 결과를 선택한다.
2. 기존 `KisStockMasterMarketWarningParser.parse`를 한 번 호출하고 반환 원문 전체가 선택한 입력과 같은지 확인한다.
3. 기존 `KisStockMarketWarningObservationPolicy.evaluate`를 한 번 호출하고 반환 관측의 원문 추출 결과가 입력과 같은지 확인한다.
4. 종합 분석 서비스를 한 번 호출한다. 그 안에서 기존 분석 서비스가 한 번 실행되므로 요청 종목을 알아내기 위한 사전 DB 조회는 없다.
5. 반환 관측 ID·전체 마스터·준비한 경보와 결과의 일치를 검사한 뒤 기존 V2 요약과 종합 요약을 출력한다.

경보 준비 실패는 관측 분석 전에 중단된다. null 반환, 다른 원문·추출·경보·관측 ID·마스터의 반환은 완료로 출력하지 않는다. 종합 모드에서는 모든 연결 검사가 끝나야 두 완료 로그를 남기므로 중간 V2 완료를 전체 성공으로 혼동하지 않는다.

추가 완료 로그에는 다음 필드가 있다. 경보 코드 `02`가 연결된 예시이며 실제 상태와 사유는 입력에 따라 달라진다.

```text
Stored stock restriction analysis complete. observationId=17, collectionId=..., symbol=0004Y0, warningMarket=KOSDAQ, warningInputSha256=..., warningParserVersion=KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1, warningObservationVersion=KIS_STOCK_MARKET_WARNING_OBSERVATION_V1, combinedScreeningVersion=KIS_STOCK_RESTRICTION_SCREENING_V1, combinedRestrictionStatus=EXCLUSION_SIGNAL_OBSERVED, combinedRestrictionReasons=[MARKET_WARNING_INVESTMENT_WARNING_OBSERVED]
```

기존 로그의 `restrictionStatus`·`restrictionReasons`는 기존 V2 결과이고 추가 로그의 `combinedRestrictionStatus`·`combinedRestrictionReasons`는 종합 결과다. 경보 입력 해시는 지정한 시장 전체 마스터의 해시다. 원문·응답 메시지·전체 경보 행은 로그에 출력하지 않는다. 종합 모드의 완료 확인에는 추가 로그가 필요하며 프로세스 종료 메시지만으로 판정하지 않는다.

요청 종목의 시장과 다른 유효 시장을 명시하면 자동 교체하지 않는다. [종합 점검](kis-stock-restriction-screening.md)의 `MARKET_WARNING_MARKET_NOT_MATCHED` 진단과 `REVIEW_REQUIRED`를 출력하며, 잘못 선택한 시장의 경보 신호를 요청 종목에 붙이지 않는다. 제외·검토 결과도 분석 실행 자체는 완료할 수 있으며 투자 승인과는 다르다. 종합 정책·원문 추출·관측 버전과 판정 규칙은 변경하지 않는다.

## 신선도 포함 실행

신선도 점검은 종합 분석이 끝난 뒤 실행한다. DB를 다시 조회하거나 분석 서비스를 다시 호출하지 않고, 명시한 평가 조건과 이미 받은 전체 종합 결과를 기존 `KisStockRestrictionFreshnessPolicy.evaluate`에 한 번 전달한다. 반환 결과의 요청과 전체 분석이 입력과 같아야 하며, null·다른 입력 반환·정책 예외는 재시도 없이 중단한다. 결과를 DB에 추가 저장하지 않는다.

- 마스터 나이는 요청 종목과 대조한 시장 파일의 `startedAt`부터 계산한다. 전체 배치의 `finishedAt`이 평가시각 이후이면 시간 미확인으로 판정한다.
- 주식기본정보 나이는 API `requestStartedAt`부터 계산하고 `responseReceivedAt`이 평가시각 이후인지 확인한다. DB `recordedAt`으로 나이를 줄이지 않는다.
- 종료시각이 평가시각과 같은 것은 허용한다. 나이가 유효기간과 같거나 크면 `EXPIRED`다.
- 시장 또는 경보 원문 연결을 확인할 수 없거나 관측 완료가 평가시각 이후이면 `TIME_UNVERIFIED`다. 이 상태는 만료보다 우선하며 다른 입력에서 확인한 만료 사유도 유지한다.

신선도 결과는 요청 조건과 원래 종합 분석을 함께 보존하는 별도 진단이다. 기존 V2·종합 상태와 사유를 변경하지 않는다. **`FRESH`도 투자 적격·거래 승인이나 과거 시점의 정보 가용성 증명이 아니다.**

추가 완료 로그의 형식은 다음과 같다. 아래 기간과 상태는 예시이며 운영 기본값이 아니다.

```text
Stored stock restriction freshness check complete. observationId=1, collectionId=..., symbol=005930, evaluatedAt=2026-10-07T10:00:00Z, maxMasterAge=PT24H, maxBasicInfoAge=PT1H, freshnessStatus=EXPIRED, freshnessReasons=[MASTER_OBSERVATION_EXPIRED, BASIC_INFO_OBSERVATION_EXPIRED], freshnessVersion=KIS_STOCK_RESTRICTION_FRESHNESS_V1
```

기본·종합 완료 로그는 신선도 정책 호출 전에 출력한다. 따라서 `check-freshness=true` 실행에서 앞의 두 로그만으로 전체 성공을 판단하지 않는다. 신선도 완료 로그와 프로세스 정상 종료까지 확인해야 한다. `EXPIRED`나 `TIME_UNVERIFIED` 자체는 진단 결과이므로 실행 실패와 구분한다. 원문·종목 이름·API 메시지·전체 마스터는 추가 로그에도 출력하지 않는다.

## 구성 격리와 DB 보호

Runner와 전용 구성은 정상 서버의 컴포넌트 스캔 대상이 아니다. `main`이 구성을 직접 등록하며 `WebApplicationType.NONE`으로 시작한다. 수동 플래그가 켜졌다는 이유만으로 정상 서버가 이 Runner를 실행하지 않는다.

전용 구성은 관측 Entity·Repository 패키지와 Store, UTC Clock, 기존 마스터 파싱 서비스와 분석 서비스를 등록한다. 전체 애플리케이션 스캔·전체 자동 구성·KIS 조회 설정을 가져오지 않는다. Broker·Harness·Agent·OpenAI·스케줄러·HTTP Client·웹 서버 빈을 등록하지 않는다.

활성화된 종합 모드에서만 경보 Parser·관측 Policy·종합 Service 세 빈을 추가한다. 기본 모드는 이 빈들을 등록하지 않고 기본 분석만 실행한다. Runner 팩터리는 `ObjectProvider`로 선택적 의존성을 받아 종합 모드일 때만 조회한다. 전체 수동 실행이 비활성화되면 종합 옵션이 켜져 있어도 관련 빈과 DB를 구성하지 않는다.

신선도 Policy도 활성화된 전용 구성에서 `check-freshness=true`인 경우에만 등록·조회한다. Policy를 정상 서버의 컴포넌트로 등록하지 않으며 전체 수동 실행이 비활성화되면 점검 옵션만 켜져 있어도 등록하지 않는다.

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

종합 분석은 같은 명령에 두 옵션을 추가한다. 아래 `KOSDAQ`는 형식 예시이며 분석하려는 보존 입력에 맞는 시장을 명시해야 한다. 기존 명령에서 이 옵션을 생략하면 기존 V2 분석을 유지한다.

```powershell
.\gradlew.bat analyzeStockBasicInfo --args='--spring.profiles.active=local --market.stock.basic-info.analysis.manual.enabled=true --market.stock.basic-info.analysis.manual.observation-id=<OBSERVATION_ID> --market.stock.basic-info.analysis.manual.observation-root=<MASTER_ROOT> --market.stock.basic-info.analysis.manual.collection-id=<COLLECTION_UUID> --market.stock.basic-info.analysis.manual.include-market-warnings=true --market.stock.basic-info.analysis.manual.warning-market=KOSDAQ' --offline --no-daemon
```

두 옵션의 환경변수 이름은 `MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_INCLUDEMARKETWARNINGS`와 `MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_WARNINGMARKET`다. `.env` 파일이나 실제 실행 환경의 값을 이번 변경에서 추가·변경하지 않았다. `application.yml`에는 종합 옵션의 기본값 `false`만 명시하며 시장은 기본 지정하지 않는다.

신선도까지 점검하려면 위 종합 명령의 `--args` 안에 다음 네 옵션을 함께 추가한다. 평가시각은 실제 비교 기준으로 바꾸어야 한다. `PT24H`·`PT1H`는 검증 예시일 뿐이며 적절한 운영 유효기간을 확정한 것은 아니다.

```text
--market.stock.basic-info.analysis.manual.check-freshness=true
--market.stock.basic-info.analysis.manual.evaluated-at=<EVALUATED_AT_WITH_OFFSET>
--market.stock.basic-info.analysis.manual.max-master-age=PT24H
--market.stock.basic-info.analysis.manual.max-basic-info-age=PT1H
```

환경변수로 전달할 경우 이름은 다음과 같다. `application.yml`에는 신선도 옵션이나 유효기간 기본값을 추가하지 않았고 `.env`도 자동으로 읽지 않는다.

```text
MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_CHECKFRESHNESS
MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_EVALUATEDAT
MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_MAXMASTERAGE
MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_MAXBASICINFOAGE
```

실제 MySQL 관측 ID 1의 분석은 별도 실환경 실행 검증으로 구분한다. 실행기 구현 당시에는 H2와 합성 마스터만 사용하고 실제 MySQL과 기존 보존 원본은 읽지 않았다. 이후 [보존된 실제 입력의 분석 검증](validation/kis-stock-basic-info-analysis-observation-01.md)을 별도로 완료했다.

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

## 실환경 분석 검증

2026-10-07 [MySQL 관측 ID 1과 실제 보존 마스터의 분석](validation/kis-stock-basic-info-analysis-observation-01.md)을 검증했다. 기존 Gradle 실행기와 별도 점검 프로세스의 요약이 같고, 분석 서비스의 전체 결과가 기존 정책 직접 호출과 일치했다. 전체 DB 12개 테이블의 덤프와 원본·소스·설정 파일도 유지됐으며 분석 풀과 MySQL을 정상 종료했다.

당시 V1 결과의 참고 유형은 `COMMON_STOCK`이지만 제한 사전 점검은 코스피의 투자주의환기 필드 미제공으로 `REVIEW_REQUIRED`였다. 이 보존 결과와 실환경 증적은 변경하지 않는다. 현재 기본 정책은 [V2](kis-stock-basic-info-restriction-screening.md)이며 확인한 코스피 보통주의 비적용 설명을 분리한다. V2 보존 응답 비교는 [별도 로컬 대조](validation/kis-stock-basic-info-restriction-screening-observation-02.md)이고 MySQL 관측 ID 1의 분석 실행기를 재실행한 결과는 아니다.

분석 재현 검증의 성공을 투자 적격이나 거래 허가로 해석하지 않는다. 후속 증적 비교 도구의 중단·보완 이력은 실환경 검증 문서에 구분하여 기록했다. 실행기 운영 코드와 설정은 변경하지 않았다.

## 시장경보 종합 모드 검증

종합 모드 추가 후인 2026-10-07 관련 19개 클래스·409개 테스트가 실패·오류·건너뜀 없이 통과했다. Runner·Properties·전용 구성 세 클래스는 기존 61개에서 120개로 늘었으며 59개 검증 사례를 추가했다. 현재 실행의 XML suite 이름과 시각으로 집계해 이전 실행의 잔여 XML과 긴 클래스명의 축약 파일을 구분했다. 전체 프로젝트 테스트는 실행하지 않았다.

| 종합 모드 추가 당시 테스트 클래스 | 테스트 수 |
| --- | --- |
| `KisStockBasicInfoAnalysisRunnerTest` | 46 |
| `KisStockBasicInfoAnalysisPropertiesTest` | 34 |
| `KisStockBasicInfoAnalysisConfigurationTest` | 40 |

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.screening.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.warning.*' --tests 'com.stock.market.stock.master.provider.kis.parsing.warning.*' --offline --no-daemon
```

기존 분석 서비스·종합 분석 서비스·수동 실행기와 직접 연결되는 종합 점검·경보 추출·관측 테스트만 실행했다. 기본 로그·기본 명령의 분기, 선택적 빈 등록, 필수 시장과 환경변수 바인딩, 호출 순서·횟수, 단계별 실패·null·다른 반환 입력에 대한 중단을 확인했다.

H2와 합성 보존 파일로 두 시장의 경보·예고 및 미확인 사례를 실행했다. 직접 정책으로 계산한 상태·사유·입력 해시와 실행 로그가 같았고 Hibernate의 관측 엔티티 로드는 실행당 1건이었다. DB 관측의 바이트·길이·해시·요청 및 수신 시각·기록 시각·행 수, sentinel 테이블·행과 원본 7개 파일이 유지됐다. 다른 시장을 명시하면 검토 필요로 남고, 관측 누락·업무 실패·마스터 손상과 스키마 오류에서는 완료 로그 없이 중단하고 연결 풀을 닫았다. DDL 변경·SQL 스크립트·Flyway·초기화와 Broker·OpenAI 빈 격리도 유지됐다.

같은 Gradle Task의 비활성화 실행도 확인했다. 종합 옵션과 Broker·조회·스케줄·OpenAI 플래그를 켜더라도 분석 완료 없이 종료했다.

```powershell
.\gradlew.bat analyzeStockBasicInfo --args='--market.stock.basic-info.analysis.manual.enabled=false --market.stock.basic-info.analysis.manual.include-market-warnings=true --broker.kis.enabled=true --market.stock.basic-info.kis.enabled=true --harness.scheduler.enabled=true --agent.provider.ai.openai.enabled=true' --offline --no-daemon
```

위 H2 검증은 종합 모드를 실제 MySQL이나 기존 운영 관측 ID로 실행한 결과가 아니다. 기존 실제 입력의 증적을 이번 H2 검증으로 대체하지 않는다. 이 구현 검증에서는 KIS·계좌·주문·OpenAI 호출, Docker·서버 상태 변경과 후보 선정·주문 연결이 없다. 현재 관측을 과거 자격이나 `AS_OF_VERIFIED`로 승격하지 않는다.

## 종합 모드 실환경 검증 결과

2026-10-07 [기존 입력의 종합 모드 실환경 검증](validation/kis-stock-restriction-analysis-observation-01.md)을 완료했다. Docker 엔진 정상화 후 MySQL 관측 ID 1과 같은 보존 마스터를 사용해 실제 Gradle 실행기와 별도 점검 Context를 실행했다. 두 기본·종합 요약과 전체 서비스 결과가 직접 정책 결과와 같았고 실제 종합 상태는 `NO_EXCLUSION_SIGNAL_OBSERVED`였다.

최종 상태는 `PASSED`다. 전체 DB 12개 테이블의 덤프 1,852,760바이트가 전후 같았고 보존 파일 1,086개의 길이·해시도 유지됐다. 분석 풀과 MySQL은 정상 종료했으며 투자 앱은 기동하지 않았다. 이전 `MYSQL_VERIFICATION_PENDING` 기록과 V1 실환경 검증·종합 정책 로컬 대조 증적은 덮어쓰지 않고 유지한다. 외부 API·후보 선정·주문은 실행하지 않았으며 이 분석 재현 성공은 최신 거래 가능 여부나 투자 적격 승인이 아니다.

## 신선도 연결 검증 결과

2026-10-07 신선도 연결 구현 시 관련 6개 클래스·246개 테스트가 실패·오류·건너뜀 없이 통과했다. Runner 61개·Properties 50개·전용 구성 54개와 순수 신선도 계약·정책 81개를 합한 수다. 앞의 61개·120개 집계는 각 구현 단계의 기록으로 유지한다. 전체 프로젝트 테스트는 실행하지 않았다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.observation.analysis.runner.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.freshness.*' --offline --no-daemon
```

조건 바인딩·선택적 빈 등록·정책 단일 호출·전체 입력 보존·단계별 실패와 로그를 확인했다. H2·합성 마스터의 `FRESH`·`EXPIRED`·`TIME_UNVERIFIED` 진단과 원본·DB 보존, 실패 시 풀 종료도 검증했다.

이후 같은 날 [신선도 실환경 검증](validation/kis-stock-restriction-freshness-observation-01.md)을 기존 MySQL 관측 ID 1과 보존 마스터로 완료했다. 평가시각 `2026-10-07T10:00:00Z`, 마스터 24시간·기본정보 1시간 조건에서 두 입력 모두 만료됐고, 직접 계산·실제 실행기·계획된 별도 점검 결과가 일치했다. 기존 종합 상태 `NO_EXCLUSION_SIGNAL_OBSERVED`는 그대로 남았다.

실환경 검증 상태는 `PASSED`지만 입력 신선도는 `EXPIRED`다. 전체 DB 덤프 1,852,760바이트와 보존 파일 1,140개가 유지됐고 MySQL을 정상 종료했다. 이번 실환경 검증은 운영 코드·설정·테스트를 변경하지 않아 JUnit을 재실행하지 않았다. 투자 앱 기동·외부 API·주문도 실행하지 않았으며 `FRESH`·`TIME_UNVERIFIED`의 실환경 사례까지 검증한 것은 아니다.
