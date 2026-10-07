# KIS 시장경보 포함 종합 점검 로컬 대조 01

## 검증 대상

2026-10-07 [시장경보 포함 종목 제한 사전 점검](../kis-stock-restriction-screening.md)을 기존 보존 자료로 대조했다. 새 다운로드·HTTP·토큰 발급·실제 DB 연결 없이 수행했다. 원문 관측과 종합 결과의 재현성을 확인했으며 최신 거래 가능 여부·과거 자격·후보 승인·주문 권한·순성과를 검증하지 않았다.

입력은 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`의 KOSPI 2,578행·KOSDAQ 1,825행과 기존 주식기본조회 사례 10건이다. 원래 마스터 수집 시각은 `2026-10-05T09:32:35.795200200Z`부터 `2026-10-05T09:32:36.202168500Z`까지이며 `CURRENT_OBSERVATION`을 유지한다. 검증 시각을 원천 관측·효력·과거 정보 가용 시각으로 바꾸지 않는다.

## 전체 원문과 이전 결과 보존

기존 `StockMasterBatchParsingService`로 manifest·시장별 파일과 전체 마스터를 확인했다. 원문 추출과 시장경보 관측을 다시 생성하여 직전 보고서의 전체 마스터 배치·두 시장의 관측 결과 4,403행과 비교했다. 필드·해시·버전·행 식별자·순서·관측 상태가 모두 같았다.

기존 제한 점검의 V1 10건과 V2 10건에 공통 마스터를 결합해 전체 결과를 복원·재평가했다. 20건 모두 기존 관측·판정·사유·버전·JSON 값과 배열 순서가 같았고 전체 JSON 왕복도 일치했다. V1을 새 점검에 입력하거나 V2로 이름만 바꾸지 않았다.

## 종합 점검 결과

새 정책에는 기존 V2와 같은 시장의 시장경보 관측을 전달했다. 각 요청 종목의 원문은 경보 `00`·예고 `N`이었고, 입력의 해당 시장 전체 마스터와 요청 행 연결이 확인됐다.

| 종합 상태 | 사례 수 | 새 사유 |
| --- | --- | --- |
| `EXCLUSION_SIGNAL_OBSERVED` | 5 | `BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED` |
| `REVIEW_REQUIRED` | 3 | `BASIC_INFO_REVIEW_REQUIRED` |
| `NO_EXCLUSION_SIGNAL_OBSERVED` | 2 | 새 사유 없음 |

기존 V2의 모든 구체적인 사유와 비적용 설명은 `basicInfoScreening` 안에 남는다. 새 결과가 기존 입력과 시장경보 입력의 객체 참조를 유지하는지 확인했고, 원본 전체를 포함한 기대 JSON·판정·사유·버전을 사례별로 대조했다. 종합 결과 10건의 전체 JSON 왕복과 반복 호출도 일치했다.

이는 실제 자료의 재현 사례다. 이 열 사례에는 경보·예고 Y나 미확인이 없으므로 그러한 새 판정 분기를 실제 자료로 검증했다고 주장하지 않는다. 두 시장의 경보·예고 조합, 식별 실패, 다른 시장·마스터·해시·버전·행 순서·관련 없는 행의 변경은 합성 테스트에서 검증했다.

## 증적 보존

직전 시장경보 관측의 원천 증적 104개·구현 증적 6개·최초 및 재현 보고서 2개, 총 112개의 SHA-256을 실행 전후 확인했다. 모두 같았다. 해당 증적의 기존 생산 코드·원문·보고서·검증 도구를 변경하지 않았다.

새 보고서의 최초·재현 결과는 `verifiedAt`만 제외한 전체 JSON 값과 배열 순서가 같다. 비교 시 정수·소수 정밀도를 유지했다. 추가 HTTP 요청·토큰 발급·DB 연결은 각각 0회다.

## 보고서와 복원

Git 제외 경로 `build/kis-stock-restriction-screening-observation-01/`에 `VerifyKisStockRestrictionScreening.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 도구는 운영 빌드에 등록하지 않았다.

중복을 줄이기 위해 전체 마스터는 보고서 루트의 `masterBatch`, 시장경보 관측 전체는 `marketWarningObservations`에 보존한다. 각 사례의 `combinedResultWithoutSharedEvidence`에는 두 공통 입력을 생략한 종합 결과를 담는다. 전체 결과를 복원하려면 다음을 결합한다.

1. `basicInfoScreening.observation.typeResolution.matchingResult.masterBatch`에 루트의 `masterBatch`를 넣는다.
2. 사례의 `marketWarningMarket`와 같은 시장의 `marketWarningObservations` 항목을 `marketWarningObservation`으로 넣는다.

실제 검증에서는 생략 전의 전체 종합 결과로 JSON 왕복과 기대 결과 대조를 수행했다. 요청 종목·원본 응답 경로·요청 및 응답 메타데이터도 사례마다 보존한다.

- 최초 보고서 SHA-256: `8c9a69173daa3c6455f72cd9a567ef2786e88501656f0a1af41c13e7ca3230b5`
- 재현 보고서 SHA-256: `d3a8236f01eb558bb33314a1ce51fabf3f8dcd0e0b30594174b9173eedeb65e6`

```powershell
.\gradlew.bat -I build/kis-stock-restriction-screening-observation-01/observation.init.gradle verifyKisStockRestrictionScreening --offline --no-daemon
.\gradlew.bat -I build/kis-stock-restriction-screening-observation-01/observation.init.gradle verifyKisStockRestrictionScreening '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료한 실행 기록이다. 보고서는 `CREATE_NEW`로 기존 파일 덮어쓰기를 거절한다. 도구·보고서·원본은 일반 Git 커밋 대상이 아니며 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 검증 범위

관련 테스트는 19개 클래스·415개가 실패·오류·건너뜀 없이 통과했다. 전체 프로젝트 테스트는 실행하지 않았다. 새 테스트의 메서드 선언 오타와 `Instant` JSON 설정 누락은 수정한 뒤 같은 관련 범위를 재실행했다.

보고서는 `combinedRestrictionScreeningImplemented=true`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`, `orderBlockingImplemented=false`다. 후보 선정·실제 주문에는 연결하지 않았으며 수집기·스케줄·Docker·계좌·주문·OpenAI를 실행하거나 실제 DB·설정을 변경하지 않았다.
