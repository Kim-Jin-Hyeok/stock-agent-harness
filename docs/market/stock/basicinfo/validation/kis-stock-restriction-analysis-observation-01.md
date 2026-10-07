# 저장된 KIS 관측의 시장경보 종합 분석 실환경 검증

2026-10-07 KST, [수동 분석 실행기의 종합 모드](../kis-stock-basic-info-manual-analysis.md#시장경보-포함-실행)를 기존 MySQL 관측 ID `1`과 보존 마스터로 검증했다. **종합 분석의 재현, 직접 정책 결과와의 일치, DB·파일 보존과 정상 종료를 확인했으며 최종 상태는 `PASSED`다.** 앞선 Docker 엔진 오류와 오프라인 사전 점검 기록을 유지하고, 엔진 정상화 후 별도 `run-01`에서 실환경 검증을 완료했다.

기준 구현 커밋은 `d2121dceeba1880e6405f7b13e6bde6b58081495`다. 운영 코드·테스트·Gradle 설정·스키마·`.env`·Compose는 변경하지 않았다. 오프라인 점검과 MySQL 검증의 증적을 구분하고 이전 검증 보고서도 유지한다.

## 입력과 분석 결과

이전 [실환경 기본 분석](kis-stock-basic-info-analysis-observation-01.md)의 MySQL 관측 ID `1`을 그대로 사용했다. 먼저 보관한 `005930` 수신 바이트와 기존 마스터로 오프라인 정책 결과를 계산했고, 이후 실제 DB 복원 바이트와 전체 서비스 결과를 대조했다. 두 경로의 결과가 같았다.

| 항목 | 실환경 확인 결과 |
| --- | --- |
| 수신 원문 | `received-005930.bin`, 1,860바이트 |
| 원문 SHA-256 | `a4197f6db2f02997b8c0b38765952ee075a908a8572a3bfbd905f43acc0eecc8` |
| 마스터 배치 | `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`, `CURRENT_OBSERVATION` |
| 마스터 행 수 | KOSPI 2,578행, KOSDAQ 1,825행 |
| 명시한 경보 시장 | `KOSPI` |
| KOSPI 마스터 SHA-256 | `630220921e86a7c8684a80afd6c8fc741924fe0214d60fecbc865cebe91d3547` |
| 식별 대조 | `STANDARD_CODE_AND_MARKET_MATCH` |
| 참고 유형 | `COMMON_STOCK`, `BASIC_INFO_TYPE_SUPPLEMENTED` |
| 요청 종목의 경보 행 | 441행, 표준코드 `KR7005930003`, 원문 경보 `00`·예고 `N` |
| 경보 관측 | `NO_WARNING_OBSERVED`, 예고 `N_OBSERVED` |
| 기본 V2 상태 | `NO_EXCLUSION_SIGNAL_OBSERVED` |
| 종합 상태 | `NO_EXCLUSION_SIGNAL_OBSERVED`, 종합 사유 없음 |
| DB 요청 시각 | `2026-10-07T02:19:50.924212Z` |
| DB 수신 시각 | `2026-10-07T02:19:50.942352Z` |
| DB 기록 시각 | `2026-10-07T02:19:50.976509Z` |

기본 V2 사유는 `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED`와 `MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE`이다. 코스피 필드 미제공 원문을 유지하면서 [V2의 비적용 규칙](../kis-stock-basic-info-restriction-screening.md)을 사용했다. 이전 기본 V1 실환경 검증의 `REVIEW_REQUIRED` 기록은 변경하지 않는다. 이번 V2 실환경 결과를 별도 증적으로 남기며 원문 공백을 임의로 N으로 보정하지 않는다.

경보 추출은 `KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1`, 경보 관측은 `KIS_STOCK_MARKET_WARNING_OBSERVATION_V1`, 종합 점검은 `KIS_STOCK_RESTRICTION_SCREENING_V1`이다. 기존 배치 파서·시장경보 파서와 정책을 직접 사용하고 새 다운로드나 원문 보정은 하지 않았다.

마스터의 수집 구간은 `2026-10-05T09:32:35.795200200Z`부터 `09:32:36.202168500Z`다. DB 관측의 요청·수신 시각은 10월 7일이므로 이 입력들을 동시 관측이나 최신 거래 자격의 증거로 해석하지 않는다. 분석 시각으로 원천 시각을 덮어쓰거나 `AS_OF_VERIFIED`·`informationAvailableAt`·거래 허가를 생성하지 않는다. 이 실제 사례는 경보 `00`·예고 `N`이며 경보 Y나 미확인 분기의 실제 관측 검증은 아니다.

## 사전 점검과 중단 기록

오프라인 점검은 `offline-02`에서 `2026-10-07T10:40:58.655208700Z`에 결과를 생성했다. Java 자식 프로세스는 종료 코드 0, 약 1.06초로 완료했고 제한된 환경변수만 전달했다. 사전 점검 전체는 약 4.08초이며 외부 API 응답 시간이 아니다.

검증 전후 1,086개 파일의 길이·SHA-256이 같았다. 비교 대상은 마스터 원본 7개, 기존 수신 바이트, 전체 `src` 파일, `.env`·Compose·`build.gradle`과 앞선 기본 분석·종합 점검 증적 디렉터리의 파일이다. `.env`는 내용을 로드하지 않고 해시만 비교했다. 새 검증 도구·보고서와 이번 문서 변경은 보존 대상에서 제외한다.

최초 `offline-01`은 샌드박스에서 자식 프로세스 로그 생성 전에 중단됐다. 실패 기록을 유지하고 승인된 별도 `offline-02`에서 오프라인 점검을 수행했다. 두 시도의 보존 파일 목록과 해시는 같다. 투자 분석 Runner나 MySQL 분석을 재시도한 것은 아니다.

최초 Docker 컨테이너 조회는 샌드박스 밖에서도 엔진 오류로 실패했다. Desktop 상태 조회는 `starting`, 최대 60초로 제한한 `docker desktop start`는 `Docker Desktop is already running`을 반환했지만 당시 엔진 연결은 정상화되지 않았다. 운영체제·WSL·Docker 설정을 변경하거나 Desktop·컨테이너를 강제 재시작하지 않았다.

엔진 오류 단계에서는 MySQL·투자 앱 컨테이너의 기동·종료 명령과 DB 조회를 실행하지 않았다. 이때의 `MYSQL_VERIFICATION_PENDING` 요약을 덮어쓰지 않고 보존한다. 사용자가 엔진 정상화를 알린 뒤 컨테이너 조회가 성공했고, 기존 DB·앱이 모두 `exited`임을 확인한 후 아래 실환경 검증을 진행했다.

## 실환경 검증 결과

`run-01`은 `2026-10-07T10:50:12.8815806Z`부터 `10:51:05.3457932Z`까지 실행되어 `PASSED`로 종료했다. 전체 약 52.46초에는 파일 점검·DB 기동과 건강 상태 대기·DB 대조·프로세스 기동·종료가 포함된다.

| 검증 대상 | 확인 결과 |
| --- | --- |
| DB | 기존 MySQL `8.4.11`, `stock_agent_harness`, 로컬 포트 `3307` |
| 격리 | 관측 Entity 하나, ApplicationRunner 하나와 종합 모드 빈 등록 |
| 외부 실행 경로 | Broker·Agent·주문·Provider·Client·스케줄러·웹 서버·Flyway·SQL 초기화 빈 없음 |
| DB 접근 | 점검 연결의 JDBC read-only 및 `@@session.transaction_read_only=1` |
| Runner 조회 | 점검 Context의 추가 진단 조회 전 관측 엔티티 로드 1건 |
| 전체 결과 | 종합 서비스 결과가 직접 정책 결과와 같고 전체 JSON 왕복도 일치 |
| 완료 요약 | 두 계획된 Runner의 기본·종합 로그가 직접 정책의 기대 로그와 정확히 일치 |
| 원문·시각 | DB 복원 바이트가 기존 1,860바이트 수신 원문과 같고 요청·수신·기록 시각 유지 |
| 전체 DB 덤프 | 전후 각각 1,852,760바이트, 전체 바이트 일치 |
| 덤프 SHA-256 | `002d51b4dbc134204bf6445aa24d28c68b455592033ac31f2a16105e974c554e` |
| DB 내용·메타데이터 | 기존 12개 테이블, 관측 1건, 성공한 Flyway V1~V7 유지 |
| DB 실행 환경 | 다른 접속자·활성 이벤트 각각 0개, 복제 구성 없음, `event_scheduler=ON` 유지 |
| 보존 파일 | 1,086개 파일의 길이·SHA-256 유지, 최초 통과한 오프라인 기준과도 일치 |
| 분석 풀 | 실제 main의 풀 종료 로그와 점검 풀의 `isClosed()` 확인 |
| MySQL 종료 | 종료 코드 0, `OOMKilled=false`, `Shutdown complete` 로그 확인 |
| 투자 앱 | 시작 시각·ID·종료 상태 유지, 검증 중 기동하지 않음 |

전체 덤프 비교에는 관측 테이블뿐 아니라 전략 포트폴리오·주문·실행·거래·시세와 Flyway 이력도 포함된다. 같은 옵션으로 스키마·데이터·루틴·이벤트·트리거를 덤프하고 바이트 단위로 대조했다. 덤프의 복원 시험은 이번 범위에 포함하지 않는다. 기존 MySQL 컨테이너·이미지·볼륨을 재생성하지 않았으며 검증 후 DB와 앱은 모두 중지 상태다.

실제 Gradle 분석 자식 프로세스는 약 13.28초, 별도 점검 Java 프로세스는 약 5.24초, 점검 내부 Context·분석·대조·풀 종료는 약 4.44초였다. 두 프로세스 모두 종료 코드 0이며 시간 제한에 걸리지 않았다. 이는 한 번의 로컬 실행값이며 외부 API 응답 시간이나 운영 처리량이 아니다.

KIS 인증·조회·계좌·주문·OpenAI와 마스터 재수집 경로는 실행하지 않았다. 외부 호출 격리는 등록 빈·구성과 코드 경로의 확인이며 물리 네트워크 패킷이나 계정 전체 사용량을 측정한 것은 아니다. 로컬 MySQL 연결은 수행했다. `NO_EXCLUSION_SIGNAL_OBSERVED`와 검증 절차의 `PASSED`는 투자 적격·주문 승인·전략 순성과를 뜻하지 않는다.

## 실행 절차

실행한 `Verify` 모드의 순서는 다음과 같다. 실행 중인 다른 서비스를 임의로 중지하지 않으며 기존 DB·앱 컨테이너가 모두 중지 상태일 때만 검증을 시작한다.

1. 같은 보존 파일을 재확인하고 기존 컨테이너·이미지·볼륨을 확인한다. 새 컨테이너나 볼륨을 만들지 않는다.
2. 기존 MySQL만 시작하고 제한된 시간 안에 건강 상태를 확인한다. 다른 DB 접속자·활성 이벤트·복제 구성과 준비된 관측·Flyway 이력이 예상 범위와 다르면 중단한다.
3. 전체 DB 덤프와 관측 메타데이터를 보관한다. 관측 ID `1`이 기존 종목·바이트·해시와 다르면 다른 관측으로 자동 대체하지 않는다.
4. 기존 `analyzeStockBasicInfo`와 실제 `main`을 종합 모드로 한 번 실행한다. `include-market-warnings=true`, `warning-market=KOSPI`를 명시하고 읽기 전용 JDBC·세션 설정을 전달한다.
5. 별도로 계획한 점검 Context에서 같은 Runner를 한 번 실행한다. 관측 Entity 하나, 종합 모드 빈, 금지된 Broker·Provider·Client·스케줄 빈의 부재와 읽기 전용 세션을 검사한다. 추가 진단 조회 전 Hibernate의 Runner 관측 엔티티 로드가 1건인지 확인한다.
6. 점검 Context 안에서 종합 서비스를 한 번 더 호출해 전체 결과를 같은 DB 원문과 마스터의 직접 정책 결과와 비교한다. 전체 JSON 왕복, 원문 수신 바이트와 세 가지 기존 시각도 대조한다. 이는 계획된 서비스 대조 호출이며 Runner의 단일 조회 횟수에 포함시키지 않는다.
7. 두 Runner의 기본·종합 요약을 직접 정책의 기대 요약과 비교한다. DB 전체 덤프·메타데이터·파일 해시를 전후 대조하고 분석 풀과 검증을 위해 켠 MySQL의 정상 종료를 확인한다. 분석 이후 실패해도 보존 대조와 종료를 시도하며 결과를 성공으로 처리하지 않는다.

인증정보는 기존 MySQL 컨테이너에서 메모리로 읽어 자식 환경에만 전달한다. 명령행·공개 문서에 넣지 않고 자식 로그를 마스킹한다. 기존 프로세스·SQL·덤프·해시 도구를 재사용하며 자동 분석 재시도는 없다. DB 준비를 기다리는 제한된 건강 상태 조회는 분석 재실행과 구분한다.

완료한 실행 명령은 다음과 같다. `run-01` 증적은 이미 존재하므로 같은 명령을 다시 실행하지 않는다. 추가 검증이 필요하면 새 실행 번호와 범위를 명시하며 기존 증적을 덮어쓰지 않는다.

```powershell
.\build\kis-stock-restriction-analysis-observation-01\observe.ps1 -Mode Verify -RunId run-01
```

## 증적과 변경 범위

Git 제외 경로 `build/kis-stock-restriction-analysis-observation-01/`에 준비 도구와 증적을 둔다. DB 덤프와 전체 분석 JSON에는 개인 자료가 포함될 수 있으므로 Git에 추가하거나 공개 로그로 출력하지 않는다.

| 경로 | 내용 |
| --- | --- |
| `observation.init.gradle` | 기존 기본 검증 Java와 새 종합 점검 Java를 새 출력 경로에 컴파일하는 임시 작업 |
| `KisStockRestrictionAnalysisObservation01.java` | 오프라인 직접 정책 계산과 후속 MySQL 종합 분석·격리·전체 결과 대조 |
| `observe.ps1` | 기존 보호 도구를 재사용한 오프라인 점검과 제한된 DB 검증·보존·종료 절차 |
| `offline-01/` | 최초 오프라인 점검 중단과 실행 전 파일 해시 |
| `offline-02/` | 통과한 오프라인 결과·프로세스 기록·전후 파일 해시 |
| `run-01/` | 통과한 MySQL 종합 분석·전체 결과·실행 로그·DB 덤프·전후 대조·종료 증적 |
| `verification-summary.json` | 엔진 오류 당시의 `MYSQL_VERIFICATION_PENDING` 기록, 그대로 보존 |
| `verification-completion.json` | 최종 `PASSED`와 실환경 결과·보존·종료 요약 |
| `evidence-manifest.json` | 사전 점검 당시 도구·컴파일 결과·증적 17개 파일의 길이·SHA-256, 그대로 보존 |
| `completion-evidence-manifest.json` | 기존 증적과 새 실환경 증적 43개 파일의 길이·SHA-256 |

보조 Java의 Java 21 컴파일·PowerShell 구문·오프라인 모드에 이어 실제 MySQL 감사 분기를 실행했다. 전체 분석 JSON의 SHA-256은 `a0f1f93e7cca66b7403ead987904b4a2c5d74f996e4592586e5a82f55c1e3288`이며 생성 파일과 점검 보고서의 해시가 같다. 이번 실환경 재개에서 운영 코드·검증 도구 변경이 없어 기존 JUnit과 전체 테스트 모음은 재실행하지 않았다. `gradlew clean`도 실행하지 않았다.

커밋 대상은 이 문서와 [수동 실행 안내](../kis-stock-basic-info-manual-analysis.md)의 완료 결과 링크다. 새 운영 패키지·클래스·Entity·테이블·의존성은 추가하지 않았다. 실제 DB 실행, 전체 결과 대조, DB·파일 보존과 정상 종료가 모두 확인되어 완료 요약을 별도 생성했다. 현재 관측을 과거 자격이나 신선도 검증으로 승격하지 않는다.
