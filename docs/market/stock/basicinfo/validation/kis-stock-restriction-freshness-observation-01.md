# KIS 종합 분석 신선도 실환경 검증 01

## 목적과 범위

2026-10-07 기존 MySQL 관측 ID 1과 보존된 마스터 배치를 사용해 [수동 분석 실행기](../kis-stock-basic-info-manual-analysis.md)의 신선도 연결을 검증했다. 검증 대상 구현은 `e444bf5f02fa35caa98493f625b9403a3de5d114`다.

최종 검증 상태는 **`PASSED`**다. 이는 직접 정책 계산·실제 실행 결과의 일치, 입력 보존과 정상 종료를 확인했다는 뜻이다. 입력의 신선도는 **`EXPIRED`**이며 투자 적격·거래 승인이나 `AS_OF_VERIFIED`를 확인한 것이 아니다.

운영 코드·테스트·DB 스키마·설정·`.env`·Compose는 변경하지 않았다. KIS 토큰·조회·계좌·주문, OpenAI와 마스터 다운로드 경로를 실행하지 않았다. 투자 앱도 기동하지 않았다. 구성·호출 경로 격리를 확인한 검증이며 네트워크 패킷이나 계정 전체 사용 내역을 감사한 것은 아니다.

## 보존 입력과 평가 조건

| 항목 | 실제 값 |
| --- | --- |
| DB | `stock_agent_harness`, MySQL `8.4.11`, 호스트 포트 `3307` |
| 관측 ID·요청 종목 | `1`·`005930` |
| 보존 응답 길이 | `1,860`바이트 |
| 원문 SHA-256 | `a4197f6db2f02997b8c0b38765952ee075a908a8572a3bfbd905f43acc0eecc8` |
| 마스터 수집 ID | `4ebd57c2-dd69-4b99-86c7-8efed3e531f7` |
| 마스터 범위·행 수 | `CURRENT_OBSERVATION`, KOSPI `2,578`행·KOSDAQ `1,825`행 |
| 지정 경보 시장 | `KOSPI` |
| KOSPI 입력 SHA-256 | `630220921e86a7c8684a80afd6c8fc741924fe0214d60fecbc865cebe91d3547` |
| 평가시각 | `2026-10-07T10:00:00Z` |
| 마스터 최대 나이 | `PT24H` |
| 기본정보 최대 나이 | `PT1H` |
| 신선도 버전 | `KIS_STOCK_RESTRICTION_FRESHNESS_V1` |

평가시각과 유효기간은 이번 재현 검증을 위해 명시한 조건이다. 실행 시각을 대신 넣거나 운영 기본값으로 추가하지 않았다. 이전 [종합 분석 검증](kis-stock-restriction-analysis-observation-01.md)의 전체 결과와 같은 마스터·보존 응답을 재사용했고 재수집하지 않았다.

| 시각·나이 | 실제 값 |
| --- | --- |
| 선택한 KOSPI 파일 시작 | `2026-10-05T09:32:35.795200200Z` |
| 선택한 KOSPI 파일 종료 | `2026-10-05T09:32:36.137515600Z` |
| 전체 마스터 배치 종료 | `2026-10-05T09:32:36.202168500Z` |
| 마스터 나이 | `PT48H27M24.2047998S` |
| API 요청 시작 | `2026-10-07T02:19:50.924212Z` |
| API 응답 수신 | `2026-10-07T02:19:50.942352Z` |
| DB 기록 시각 | `2026-10-07T02:19:50.976509Z` |
| 기본정보 나이 | `PT7H40M9.075788S` |

마스터 나이는 선택한 시장 파일의 시작시각, 기본정보 나이는 API 요청 시작시각 기준이다. DB 기록 시각은 보존 확인용이며 신선도 계산에 사용하지 않았다. 두 수집 모두 평가시각 전에 완료됐지만 명시한 유효기간을 넘었다.

## 실제 판정과 비교 결과

| 검증 항목 | 결과 |
| --- | --- |
| 신선도 상태 | `EXPIRED` |
| 신선도 사유 순서 | `MASTER_OBSERVATION_EXPIRED`, `BASIC_INFO_OBSERVATION_EXPIRED` |
| 기존 V2 제한 상태 | `NO_EXCLUSION_SIGNAL_OBSERVED` |
| 기존 V2 사유 | `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED`, `MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE` |
| 기존 종합 제한 상태·사유 | `NO_EXCLUSION_SIGNAL_OBSERVED`·빈 목록 |
| 전체 실행 결과와 직접 계산 | 일치 |
| 전체 JSON 직렬화·복원 | 일치 |
| 계획된 각 프로세스의 기본·종합·신선도 로그 | 각각 정확히 한 번, 직접 계산과 일치 |
| 점검 Context의 Runner 관측 엔티티 로드 | `1`건 |
| 점검 Context의 Runner 신선도 정책 호출 | `1`회 |
| 별도 서비스 재분석 비교 | `0`회 |
| Runner 종료 후 부가 메타데이터 조회 | `1`회 |

별도 점검은 실제 정책을 위임 호출하면서 반환된 **전체 신선도 결과**를 캡처했다. 요약만 재계산해 실제 Runner 결과처럼 취급하지 않았다. 기존 종합 분석 전체가 보존되는지와 직접 정책 결과·이전 종합 분석 증적의 일치를 함께 확인했다.

전체 결과 JSON SHA-256은 `a8e536c2ac6d7e3fbca39bc56c53e632a39f7f048460138394aa2d0b395ef677`이다. 신선도 진단을 추가해도 기존 제한 상태·사유를 바꾸지 않았다. 따라서 제외 신호가 없으면서 입력은 만료된 두 결과가 함께 존재한다.

## 실행과 격리 확인

검증 도구와 증적은 Git 제외 경로 `build/kis-stock-restriction-freshness-observation-01/`에 새로 만들었다. 이전 도구·클래스·증적을 덮어쓰지 않고 기존 공통 보호·비교 기능만 재사용했다. 임시 Java 도구 컴파일과 PowerShell 구문 검사를 통과한 후 다음 순서로 실행했다.

1. DB 없이 보존 파일·이전 전체 결과·직접 정책을 대조하는 사전 점검을 수행했다.
2. 투자 앱과 MySQL이 모두 중지 상태임을 확인한 뒤 기존 MySQL 컨테이너만 시작했다. 재생성하지 않았다.
3. 다른 DB 클라이언트 `0`, 활성 이벤트 `0`, 복제 없음과 DB 상태를 확인하고 전체 덤프를 남겼다.
4. 기존 `analyzeStockBasicInfo`를 신선도 옵션과 함께 실행했다.
5. 처음부터 계획한 별도 점검 Context에서 실제 결과 캡처·호출 횟수·빈 격리·읽기 전용 세션을 확인했다.
6. 두 실행의 세 로그를 직접 계산과 대조하고 DB·파일 보존을 확인한 뒤 이번 검증이 시작한 MySQL만 정상 종료했다.

두 Runner 프로세스는 재시도가 아니라 실제 명령 실행과 점검을 구분한 계획된 실행이다. 자동 재시도는 `0`회다. Runner의 단일 조회와 별개로 부가 메타데이터 조회·SQL 상태 확인·전체 덤프가 있으므로 검증 전체가 DB 조회 한 번으로 끝난 것은 아니다.

각 Context에는 ApplicationRunner 하나와 관측 Entity 하나만 있었다. Broker·Harness·Agent·OpenAI·스케줄러·HTTP Client·웹 서버·Flyway·SQL 초기화 빈은 없었다. JDBC 읽기 전용과 `@@session.transaction_read_only=1`을 확인했다. 자식 프로세스는 허용한 환경변수만 받았고 기존 컨테이너의 DB 인증정보는 메모리로 전달했다. 명령행·증적에 인증값을 남기지 않고 자식 로그의 DB 사용자·비밀번호를 마스킹했다.

완료한 실행 명령은 다음과 같다. 이미 생성된 실행 디렉터리는 재사용·덮어쓰기를 거절하므로 재검증 시 새 `RunId`가 필요하다.

```powershell
.\build\kis-stock-restriction-freshness-observation-01\observe.ps1 -Mode Preflight -RunId offline-02
.\build\kis-stock-restriction-freshness-observation-01\observe.ps1 -Mode Verify -RunId run-01
```

## 보존과 정상 종료

전체 DB `12`개 테이블과 성공한 Flyway `V1`~`V7` 이력을 보존했다. 전후 덤프는 **1,852,760바이트 전체가 일치**했고 SHA-256도 `002d51b4dbc134204bf6445aa24d28c68b455592033ac31f2a16105e974c554e`로 같았다. DB 상태와 이벤트 스케줄러 설정도 유지됐다.

보존 파일 **1,140개**의 길이·SHA-256이 전후 같았다. 대상은 원본 마스터 7개, 보존 응답, 전체 소스·설정·`.env`·Compose·빌드 설정, 재사용한 보호 도구와 기존 기본 분석·제한 점검·종합 분석 증적이다. 이번 새 도구·증적과 수정 대상 문서는 비교 대상에서 제외했다. 이전 `MYSQL_VERIFICATION_PENDING`과 실패·보완 이력도 유지한다.

실제 명령은 풀 종료 로그를 남겼고 별도 점검은 `poolClosed=true`를 확인했다. MySQL은 같은 컨테이너 ID·이미지·마운트를 유지한 채 종료 코드 `0`, `OOMKilled=false`와 정상 종료 로그를 확인했다. 투자 앱의 ID·이미지·시작·종료 상태는 변하지 않았고 계속 중지 상태다. Docker Desktop이나 엔진 설정은 변경하지 않았다.

## 실행 시간과 증적

실제 검증은 `2026-10-07T11:51:55.6047643Z`부터 `11:52:46.5103659Z`까지 약 **50.91초**였다. Gradle 명령 프로세스는 **12.77초**, 별도 점검 프로세스는 **4.92초**, 점검 내부 분석 구간은 **3.65초**였다. DB 시작·보존 확인·종료까지 포함한 검증 시간이며 외부 API 응답 지연이 아니다. 두 프로세스 모두 종료 코드 `0`이고 제한시간 초과는 없었다.

| 증적 경로: 새 `build/` 루트 기준 | 내용 |
| --- | --- |
| `offline-01/result.json` | 샌드박스 사전 실행 실패와 파일 보존 확인 |
| `offline-02/offline-preflight.json`, `result.json` | 승인 후 오프라인 대조 성공 |
| `run-01/freshness-result.json` | 실제 정책 반환의 전체 신선도 결과 |
| `run-01/freshness-verification.json` | 입력·판정·호출 횟수·격리·풀 종료 |
| `run-01/summary-comparison.json` | 직접 계산과 두 프로세스의 세 로그 비교 |
| `run-01/database-before.sql`, `database-after.sql`, `database-preservation.json` | 전체 DB 전후 바이트·상태 비교 |
| `run-01/files-before.json`, `files-after.json` | 보존 파일 길이·해시 |
| `run-01/gradle-analysis.log`, `audit.log` | 인증값을 마스킹한 실행 로그 |
| `run-01/shutdown.json`, `result.json` | 정상 종료와 최종 `PASSED` |

최초 `offline-01`은 샌드박스 제약으로 자식 로그·결과를 만들기 전에 실패했다. DB·Runner는 실행하지 않았고 파일 보존은 확인했다. 해당 실패 기록을 남긴 채 승인 후 별도 `offline-02`로 실행해 성공했다. 이를 자동 재시도나 분석 실패 후 성공으로 기록하지 않는다.

## 한계와 커밋 범위

이번 실제 입력 사례는 `EXPIRED` 판정만 검증했다. `FRESH`·`TIME_UNVERIFIED`, 경계값과 실패 경로는 이전 H2·합성 파일·단위 테스트 검증이며 실제 MySQL에서 해당 사례까지 확인한 것은 아니다. 평가시각이 명시됐다고 과거 자격·당시 정보 가용성이나 현재 거래 가능 여부가 확인되는 것도 아니다.

운영 코드와 테스트가 바뀌지 않아 이번 작업에서 JUnit을 재실행하지 않았다. 직전 신선도 연결 구현의 관련 6개 클래스·246개 통과 기록은 유지하고, 이번에는 임시 도구 컴파일·구문 검사·오프라인 대조·실제 MySQL 실행을 수행했다. `gradlew clean`은 실행하지 않았다.

커밋 대상은 이 검증 문서와 수동 실행 안내 문서 두 개다. 임시 도구·전체 JSON·SQL 덤프·실행 로그는 Git 제외된 `build/`에만 두며 인증정보나 원문을 커밋하지 않는다. 추천 메시지는 `chore: KIS 종합 분석 신선도 실환경 검증`이다.
