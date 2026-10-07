# KIS 통합 사전 점검 실환경 검증 01

## 목적과 범위

2026-10-07 기존 MySQL 관측 ID 1과 보존 마스터 배치로 [수동 분석 실행기](../kis-stock-basic-info-manual-analysis.md)의 `run-precheck=true` 경로를 검증했다. 대상 구현은 `1a10e51150bfdad06956f6bd6729779ccae368ef`다.

최종 검증 상태는 **`PASSED`**, 사전 점검 판정은 **`BLOCKED`**다. 실행 결과가 직접 정책 계산과 같고 입력 보존·단일 관측 로드·정상 종료를 확인했다는 뜻이다. 제외 신호가 없어도 만료된 입력은 통과시키지 않았다. `CLEAR`, 투자 적격·거래 승인이나 `AS_OF_VERIFIED`를 확인한 결과가 아니다.

**이번 작업은 즉시 수행한 수동 검증이며 내일 10시 스케줄을 등록한 것이 아니다.** `2026-10-07T10:00:00Z`는 이전 증적과 대조하기 위해 고정한 평가시각이며 한국 시각으로 같은 날 19시다. 실제 실행시각이나 예약시각으로 사용하지 않았다.

운영 코드·테스트·DB 스키마·설정·`.env`·Compose는 변경하지 않았다. KIS 토큰·조회·계좌·주문, OpenAI와 마스터 다운로드 경로를 실행하지 않았다. 투자 앱도 기동하지 않았다. 구성·호출 경로 격리를 확인한 것이며 네트워크 패킷이나 계정 전체 사용 내역을 감사한 것은 아니다.

## 입력과 평가 조건

[신선도 실환경 검증](kis-stock-restriction-freshness-observation-01.md)과 같은 보존 입력·조건을 재사용했다. 최신 입력으로 자동 대체하거나 유효기간·수집시각을 바꾸어 통과시키지 않았다.

| 항목 | 실제 값 |
| --- | --- |
| DB | `stock_agent_harness`, MySQL `8.4.11`, 호스트 포트 `3307` |
| 관측 ID·요청 종목 | `1`·`005930` |
| 보존 응답 길이 | `1,860`바이트 |
| 원문 SHA-256 | `a4197f6db2f02997b8c0b38765952ee075a908a8572a3bfbd905f43acc0eecc8` |
| 마스터 수집 ID | `4ebd57c2-dd69-4b99-86c7-8efed3e531f7` |
| 마스터 범위·행 수 | `CURRENT_OBSERVATION`, KOSPI `2,578`행·KOSDAQ `1,825`행 |
| 경보 시장 | `KOSPI` |
| KOSPI 입력 SHA-256 | `630220921e86a7c8684a80afd6c8fc741924fe0214d60fecbc865cebe91d3547` |
| 평가시각 | `2026-10-07T10:00:00Z` |
| 마스터·기본정보 최대 나이 | `PT24H`·`PT1H` |
| 사전 점검 버전 | `KIS_STOCK_RESTRICTION_PRECHECK_V1` |

| 시각·나이 | 실제 값 |
| --- | --- |
| 선택한 KOSPI 파일 시작·종료 | `2026-10-05T09:32:35.795200200Z`·`2026-10-05T09:32:36.137515600Z` |
| 전체 마스터 배치 종료 | `2026-10-05T09:32:36.202168500Z` |
| 마스터 나이 | `PT48H27M24.2047998S` |
| API 요청 시작·응답 수신 | `2026-10-07T02:19:50.924212Z`·`2026-10-07T02:19:50.942352Z` |
| 기본정보 나이 | `PT7H40M9.075788S` |
| DB 기록 시각 | `2026-10-07T02:19:50.976509Z` |

관측 나이는 선택한 시장 파일 시작과 API 요청 시작으로 계산했다. DB 기록 시각은 보존 확인에만 사용했다. 평가시각을 명시한 것만으로 당시 정보 가용성이나 과거 자격을 생성하지 않는다.

## 실제 결과와 비교

| 검증 항목 | 결과 |
| --- | --- |
| 종합 제한 상태·사유 | `NO_EXCLUSION_SIGNAL_OBSERVED`·빈 목록 |
| 신선도 상태 | `EXPIRED` |
| 신선도 사유 순서 | `MASTER_OBSERVATION_EXPIRED`, `BASIC_INFO_OBSERVATION_EXPIRED` |
| 사전 점검 판정 | `BLOCKED` |
| 기존 V2 상태 | `NO_EXCLUSION_SIGNAL_OBSERVED` |
| 기존 V2 사유 | `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED`, `MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE` |
| 실제 전체 결과와 직접 정책·이전 증적 | 일치 |
| 전체 JSON 직렬화·복원 | 일치 |
| 각 프로세스의 기본·종합·신선도·사전 점검 로그 | 각각 정확히 1회, 직접 계산과 일치 |
| 점검 Context의 Runner 관측 엔티티 로드 | `1`건 |
| 점검 Context의 사전 점검 Service·신선도 Policy·사전 점검 Policy 호출 | 각각 `1`회 |
| 점검 Context의 엔티티 삽입·수정·삭제 | 각각 `0`건 |
| 별도 Service 재분석 비교 | `0`회 |
| Runner 이후 부가 메타데이터 관측 조회 | `1`회 |

별도 점검은 실제 Service와 Policy에 위임해 호출 횟수를 세고 **실제 Service의 전체 반환값**을 캡처했다. 요약 재계산을 실제 반환값처럼 취급하거나 판정을 대체하지 않았다. 보존된 전체 제한 분석·신선도 결과와 현재 직접 정책 결과도 일치했다.

전체 사전 점검 JSON의 SHA-256은 `3f1d19ff68c0e5f8c3535a172420840b6733c739f568a08465154ccdd7344b97`이다. 추가 판정이 기존 제한·신선도 상태와 원래 사유를 변경하지 않았다. 정상 `BLOCKED`를 실행 실패로 취급하지 않았고 두 Runner 프로세스는 종료 코드 `0`으로 끝났다.

## 실행과 보호

도구와 증적은 Git 제외 경로 `build/kis-stock-restriction-precheck-observation-01/`에 새로 만들었다. 기존 기본 분석·종합 분석·신선도 검증의 읽기 전용 계산·격리·보존·종료 확인 기능을 재사용하고, 이전 소스·클래스·증적은 덮어쓰지 않았다.

1. 새 임시 Java 도구 컴파일과 PowerShell 구문 검사를 수행했다.
2. DB 없이 보존 마스터·응답·이전 전체 결과를 읽어 예상 `BLOCKED`를 계산했다.
3. 투자 앱과 MySQL이 모두 중지 상태임을 확인한 뒤 기존 MySQL 컨테이너만 시작했다. 재생성하지 않았다.
4. 다른 DB 클라이언트 `0`, 활성 이벤트 `0`, 복제 없음과 DB 상태를 확인하고 전체 덤프를 남겼다.
5. 기존 Gradle 명령의 통합 모드를 실행하고, 계획된 별도 점검 Context에서 실제 반환값·호출 횟수·빈 격리·읽기 전용 세션을 확인했다.
6. 두 실행의 네 완료 로그·전체 결과·직접 계산을 대조한 뒤 전체 DB·파일 보존과 정상 종료를 확인했다.
7. 이번 검증이 시작한 MySQL을 정상 종료했고, 투자 앱은 계속 중지 상태로 유지했다.

두 Runner는 실제 명령과 점검을 구분한 계획된 실행이며 재시도가 아니다. Runner의 관측 로드 1건과 별개로 메타데이터·SQL 상태 확인·덤프를 수행하므로 검증 전체가 DB 조회 한 번으로 끝난 것은 아니다.

점검 Context에는 ApplicationRunner 하나와 관측 Entity 하나만 있었다. Broker·Harness·Agent·OpenAI·스케줄러·HTTP Client·웹 서버·Flyway·SQL 초기화 빈은 없었다. JDBC 읽기 전용과 `@@session.transaction_read_only=1`을 확인했다. 자식 프로세스에는 허용한 환경변수만 전달하고 기존 컨테이너의 DB 인증정보는 메모리로 전달했다. 인증값은 명령행이나 증적에 넣지 않았고 로그의 DB 사용자·비밀번호를 마스킹했다.

성공한 실행 명령은 다음과 같다. 기존 RunId는 덮어쓰기를 거절하므로 재검증할 때는 새 식별자를 사용해야 한다.

```powershell
.\build\kis-stock-restriction-precheck-observation-01\observe.ps1 -Mode Preflight -RunId offline-02
.\build\kis-stock-restriction-precheck-observation-01\observe.ps1 -Mode Verify -RunId run-01
```

## 보존과 실행 시간

전체 DB `12`개 테이블과 성공한 Flyway `V1`~`V7` 이력을 보존했다. 전후 덤프는 **1,852,760바이트 전체가 일치**했고 SHA-256도 `002d51b4dbc134204bf6445aa24d28c68b455592033ac31f2a16105e974c554e`로 같았다. DB 메타데이터와 이벤트 스케줄러 설정도 유지됐다.

선정한 보존 파일 **1,195개**의 길이·SHA-256이 전후 같았다. 원본 마스터 7개, 보존 응답, 전체 소스·설정·`.env`·Compose·빌드 설정·공통 보호 도구와 기존 분석·제한·종합·신선도 검증 증적이 대상이다. 이번 새 도구·증적과 수정 대상 문서는 제외했다. 이전 실패·보완 기록도 유지했다.

MySQL은 같은 컨테이너 ID·이미지·마운트를 유지한 채 정상 종료했고 종료 코드 `0`, `OOMKilled=false`와 종료 로그를 확인했다. 분석 풀도 닫혔다. 투자 앱의 ID·이미지·시작·종료 상태는 바뀌지 않았다. Docker Desktop·엔진 설정은 변경하지 않았다.

실제 검증은 `2026-10-07T13:09:37.2846403Z`부터 `13:10:28.4714102Z`까지 약 **51.19초**였다. Gradle 명령 프로세스 **12.90초**, 별도 점검 프로세스 **4.99초**, 점검 내부 구간 **3.62초**였다. DB 시작·보존 확인·종료를 포함한 시간이며 외부 API 지연이나 예약 스케줄 실행 시간이 아니다. 제한시간 초과는 없었다.

## 증적과 한계

| 새 `build/` 루트 기준 경로 | 내용 |
| --- | --- |
| `offline-01/result.json` | 첫 사전 실행 실패와 파일 보존 확인 |
| `offline-02/offline-preflight.json`, `offline-precheck-result.json` | 승인 후 오프라인 직접 계산 성공 |
| `run-01/precheck-result.json`, `precheck-verification.json` | 실제 전체 반환값·호출 횟수·격리·읽기 전용·풀 종료 |
| `run-01/summary-comparison.json` | 네 로그와 직접 계산의 일치 |
| `run-01/database-before.sql`, `database-after.sql`, `database-preservation.json` | 전체 DB 전후 바이트·상태 비교 |
| `run-01/files-before.json`, `files-after.json` | 선정한 보존 파일의 길이·해시 |
| `run-01/gradle-analysis.log`, `audit.log` | 인증값을 마스킹한 실행 로그 |
| `run-01/shutdown.json`, `result.json` | 정상 종료와 최종 `PASSED` |

최초 `offline-01`은 샌드박스 실행에서 자식 프로세스 시작 단계에 실패했고 자식 로그·결과를 생성하지 못했다. DB·Runner는 실행하지 않았고 파일 보존은 확인했다. 실패 기록을 남긴 채 승인 후 새 `offline-02`에서 성공했으며 분석 실패 후 자동 재시도로 기록하지 않는다.

이번 실제 입력은 `EXPIRED`로 인한 `BLOCKED` 사례만 검증했다. `CLEAR`, 제한 신호로 인한 차단·시각 미확인·실패 경로는 이전 H2·단위 테스트 범위이며 이번 실제 MySQL 사례로 검증한 것은 아니다. 최신 제한 상태·현재 거래 가능 여부·전략 성과·운영 후보 선정·실제 주문 차단 효과도 확인하지 않았다.

운영 코드와 테스트를 변경하지 않아 JUnit을 재실행하지 않았다. 직전 관련 10개 클래스·412개 통과 기록은 유지하고, 이번에는 임시 도구 컴파일·구문 검사·오프라인 대조·실제 MySQL 실행을 수행했다. `gradlew clean`은 실행하지 않았다.

커밋 대상은 이 검증 문서와 수동 실행 안내 문서 두 개다. 임시 도구·원문·전체 JSON·SQL 덤프·실행 로그는 Git 제외된 `build/`에만 둔다. 추천 메시지는 `chore: KIS 통합 사전 점검 실환경 검증`이다.
