# KIS 시장경보 관측 상태 로컬 대조 01

## 검증 대상

2026-10-07 [시장경보 관측 상태 해석](../kis-stock-market-warning-observation.md)을 기존 보존 자료로 검증했다. 추가 HTTP 요청·토큰 발급·실제 DB 연결은 각각 0회다. 이전 [원문 추출 대조](kis-stock-market-warning-observation-01.md)의 4,403행을 그대로 사용했으며 새 다운로드와 계좌·주문·OpenAI 호출은 없다.

입력 배치는 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`이고 원래 수집 시각은 `2026-10-05T09:32:35.795200200Z`부터 `2026-10-05T09:32:36.202168500Z`까지다. 기존 `CURRENT_OBSERVATION`을 유지하며 이번 검증 시각을 원천 관측 시각·효력 시각·과거 정보 가용 시각으로 바꾸지 않는다.

## 전체 자료 대조

기존 `StockMasterBatchParsingService`로 manifest·시장별 파일과 전체 마스터를 확인한 뒤 원문 추출 결과를 재생성했다. 이전 보고서의 전체 `masterBatch`와 시장별 원문 추출 결과가 같았다. 새 정책은 그 결과를 그대로 `source`로 보존하고 모든 행에 두 관측 상태만 추가한다.

| 항목 | KOSPI | KOSDAQ |
| --- | --- | --- |
| 전체 행 | 2,578 | 1,825 |
| 이전 전체 마스터·추출 결과·해시·식별자·순서 | 일치 | 일치 |
| 정확한 원문과 두 관측 상태 | 일치 | 일치 |
| 원본을 포함한 전체 JSON 왕복 | 일치 | 일치 |
| 반복 정책 호출 | 일치 | 일치 |

합계 4,403행의 관측 상태 8,806개를 비교했다. 원문 코드별 기대 상태는 로컬 검증 도구에서 별도 고정했으며 실제 정책의 변환 메서드로 기대값을 만들지 않았다. 새 결과의 각 원문 record는 입력의 원래 record 참조를 유지했다.

## 관측 집계

| 상태 | KOSPI | KOSDAQ |
| --- | --- | --- |
| `NO_WARNING_OBSERVED` | 2,572 | 1,784 |
| `INVESTMENT_CAUTION_OBSERVED` | 3 | 21 |
| `INVESTMENT_WARNING_OBSERVED` | 3 | 20 |
| `INVESTMENT_RISK_OBSERVED` | 0 | 0 |
| 경보 `VALUE_UNVERIFIED` | 0 | 0 |
| 예고 `N_OBSERVED` | 2,578 | 1,820 |
| 예고 `Y_OBSERVED` | 0 | 5 |
| 예고 `VALUE_UNVERIFIED` | 0 | 0 |

각 수치는 해당 보존 파일의 문자 해석 결과다. 최신 지정 여부·제한 효력이나 주문 불가능을 인증하지 않는다. 두 상태는 같은 행에 함께 존재하므로 경보와 예고 건수를 서로 다른 제외 종목 수로 더하지 않는다. 자료에 없던 투자위험 코드·공백·미정의 값은 합성 테스트에서 별도로 검증했다.

## 기존 계약과 증적 보존

기존 제한 점검 보고서의 V1 10건과 V2 10건에 공통 마스터 배치를 결합해 전체 결과를 복원했다. 각 버전으로 재평가한 결과의 관측·판정·사유·버전·전체 JSON 값과 배열 순서가 기존 보고서와 같았고, 20건의 전체 JSON 왕복도 일치했다.

| 기존 점검 상태 | V1 | V2 |
| --- | --- | --- |
| 제외 신호 | 5 | 5 |
| 검토 필요 | 4 | 3 |
| 제한 신호 없음 | 1 | 2 |

이번 해석은 기존 점검에 연결하지 않았다. 따라서 새 경보 관측이 있어도 위 상태가 바뀌지 않으며, 제한 신호 없음을 시장경보 검사 통과나 최종 후보 승인으로 사용하지 않는다.

이전 원문 대조의 보존 증적 97개·구현 증적 5개·최초 및 재현 보고서 2개, 총 104개의 SHA-256을 실행 전후 대조했다. 모두 동일했다. 해당 증적의 기존 생산 코드·원본 마스터·보고서·검증 도구를 변경하지 않았다.

## 보고서와 재현

Git 제외 경로 `build/kis-stock-market-warning-observation-01/`에 `VerifyKisStockMarketWarningObservation.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 로컬 도구는 운영 빌드에 등록하지 않았다.

보고서는 전체 마스터 배치·원문 추출 결과를 포함한 새 관측 결과, 상태 집계, 기존 점검 비교 수와 증적·구현 해시를 담는다. `marketWarningInterpretationImplemented=true`이고 `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`, `marketWarningOrderBlockingImplemented=false`다.

- 최초 보고서 SHA-256: `6bda35ba013027ad4020bf086b642888b4305ec6b103ca7a653358a77e3c66ef`
- 재현 보고서 SHA-256: `bcb2da2947243caf2e449ede27f6d5a19db4913778c663f06ee6078326128b81`

최초·재현 결과는 `verifiedAt`만 제외한 전체 JSON 값과 배열 순서가 같았다. 비교 과정에서 정수·소수 정밀도를 유지했다. 다음 명령은 이미 완료한 실행의 기록이다.

```powershell
.\gradlew.bat -I build/kis-stock-market-warning-observation-01/observation.init.gradle verifyKisStockMarketWarningObservation --offline --no-daemon
.\gradlew.bat -I build/kis-stock-market-warning-observation-01/observation.init.gradle verifyKisStockMarketWarningObservation '-PobservationReport=verification-replay.json' --offline --no-daemon
```

보고서는 `CREATE_NEW`로 기존 파일 덮어쓰기를 거절한다. 도구·결과·원본은 일반 Git 커밋 대상이 아니며, 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

관련 테스트는 363개가 실패·오류·건너뜀 없이 통과했다. 전체 프로젝트 테스트·수집기·스케줄·Docker를 실행하지 않았고 실제 DB·설정·계좌·주문 상태를 변경하지 않았다. 현재 관측으로 과거 종목 자격이나 전략 순성과를 검증하지 않는다.
