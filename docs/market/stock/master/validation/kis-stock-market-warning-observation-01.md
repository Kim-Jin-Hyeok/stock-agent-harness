# KIS 마스터 시장경보 원문 로컬 대조 01

## 검증 대상

2026-10-07 [별도 시장경보 원문 추출 계약](../kis-stock-market-warning-raw-parsing.md)을 기존 보존 파일로 대조했다. 새 다운로드·HTTP·토큰 발급·실제 DB 연결 없이 수행했다. 현재 자료의 값과 재현성을 확인했으며 최신 자격·과거 정보 가용성·후보 승인·주문 허가·수익성을 검증하지 않았다.

입력 배치는 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`이다. 원래 수집 시각은 `2026-10-05T09:32:35.795200200Z`부터 `2026-10-05T09:32:36.202168500Z`까지이며 `CURRENT_OBSERVATION`으로 유지한다. 이번 검증 시각을 원천 관측 시각이나 제한 효력 시각으로 대체하지 않는다.

| 시장 | 행 수 | 기존 MST SHA-256 |
| --- | --- | --- |
| KOSPI | 2,578 | `630220921e86a7c8684a80afd6c8fc741924fe0214d60fecbc865cebe91d3547` |
| KOSDAQ | 1,825 | `3a9bcc3db9a5c6b7fd9857ced658b5aa16b16df9e9fde39ab6a62d8080f36b12` |

## 위치와 전체 행 대조

공식 KIS 저장소의 보존 revision은 `277ec0eb7a9b7f63b6807829286c80f36649dad2`다. 로컬 `build/stock-master-type-mapping-validation-01/`의 C 헤더 선언에서 폭을 합산해 위치를 독립 계산했다. 추출 코드의 오프셋만 그대로 사용하는 대조는 하지 않았다. 헤더 수집 메타데이터는 같은 디렉터리의 `source-capture.json`에 보존돼 있다.

| 항목 | KOSPI | KOSDAQ |
| --- | --- | --- |
| LF 제외 전체 행 | 288 bytes | 282 bytes |
| `mrkt_alrm_cls_code` 위치·폭 | 124 / 2 bytes | 119 / 2 bytes |
| `mrkt_alrm_risk_adnt_yn` 위치·폭 | 126 / 1 byte | 121 / 1 byte |
| 헤더 SHA-256 | `383cb7a4bb6f7359bc742781afd18f87c95e9a939502d2e548593a1be0de24e4` | `d1660f8a3829cff4bcfc5c4e4f4bce588fa36f6f3dd388471c70d0776ea139d4` |
| 전체 원문 행·식별자·순서와 새 두 필드 | 일치 | 일치 |
| 새 결과 전체 JSON 왕복·반복 호출 | 일치 | 일치 |

위치는 0-based 바이트 오프셋이다. 두 시장 합계 4,403행의 원문 필드 8,806개를 비교했다. 기존 배치 전체는 직전 [제한 점검 V2 대조](../../basicinfo/validation/kis-stock-basic-info-restriction-screening-observation-02.md)의 `masterBatch`와 동일했다. 새 결과의 `source`도 원래 결과 전체를 그대로 보존한다.

## 보존 자료의 문자 집계

| 원문 | KOSPI | KOSDAQ |
| --- | --- | --- |
| 시장경보 `00` | 2,572 | 1,784 |
| 시장경보 `01` | 3 | 21 |
| 시장경보 `02` | 3 | 20 |
| 시장경보 `03` | 0 | 0 |
| 예고 `N` | 2,578 | 1,820 |
| 예고 `Y` | 0 | 5 |
| 각 필드의 공백·그 밖의 문자 | 0 | 0 |

이는 해당 파일의 문자 빈도이며 이번 코드에 제한 상태 해석을 추가하지 않았다. 예고 Y와 경보 코드는 같은 행에서 겹칠 수 있어 서로 다른 제외 종목 수로 더하지 않는다. 자료에 공백·미정의 문자가 없었다고 이후 입력도 그렇다고 가정하지 않는다. 그러한 값의 원문 보존은 합성 테스트로 별도 확인했다.

## 기존 계약 보존

기존 보고서의 V1 10건과 V2 10건에 공통 `masterBatch`를 다시 결합했다. 전체 결과를 각 버전으로 복원·재평가하고 JSON 값·배열 순서까지 비교했다. 20건 모두 같은 판정·사유·버전·관측을 유지했고 전체 JSON 왕복도 일치했다.

| 기존 상태 | V1 | V2 |
| --- | --- | --- |
| 제외 신호 | 5 | 5 |
| 검토 필요 | 4 | 3 |
| 제한 신호 없음 | 1 | 2 |

새 원문 추출을 수행해도 이 점검은 시장경보를 평가하지 않는다. 위 수치를 최종 투자 적격이나 전체 제한 검사 통과 건수로 해석하지 않는다.

직전 대조의 원천 증적 87개·구현 증적 6개·최초 및 재현 보고서 2개와 이번에 별도로 고정한 헤더 2개, 총 97개를 실행 전후 SHA-256으로 확인했다. 모두 동일했다. 첫 실행은 헤더가 직전 95개 목록에 없어서 보고서 생성 전에 중단됐으며, 헤더 두 개를 확인된 해시로 별도 고정한 후 최초 및 재현 실행이 성공했다. 생산 코드나 원본 증적을 그 실패 때문에 변경하지 않았다.

## 보고서와 재현

Git 제외 경로 `build/kis-stock-master-market-warning-observation-01/`에 다음을 보관한다.

- `VerifyKisStockMasterMarketWarning.java`: 원본·헤더·기존 결과와 새 계약을 로컬에서 대조한다.
- `observation.init.gradle`: 운영 빌드에 연결하지 않은 로컬 컴파일·실행 작업이다.
- `verification.json`: 최초 성공 결과이며 덮어쓰지 않는다.
- `verification-replay.json`: 검증 시각만 제외한 전체 JSON이 최초 결과와 같다.

보고서는 기존 `masterBatch`, `source`를 포함한 새 시장별 결과 전체, 원문 빈도, 기존 점검 비교 수와 증적·구현 해시를 담는다. 추가 HTTP 요청·토큰 발급·DB 연결 수는 각각 0이며 `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`, `marketWarningInterpretationImplemented=false`다.

- 최초 보고서 SHA-256: `b5863a125860f0c70e63bc9b3b81761505022f0987e37d2d0236182b010960ab`
- 재현 보고서 SHA-256: `4789dd403b0f04b50575a89aed8acaa1270b357290ab62928223d70f67b45336`

```powershell
.\gradlew.bat -I build/kis-stock-master-market-warning-observation-01/observation.init.gradle verifyKisStockMasterMarketWarning --offline --no-daemon
.\gradlew.bat -I build/kis-stock-master-market-warning-observation-01/observation.init.gradle verifyKisStockMasterMarketWarning '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 이미 완료한 실행 기록이다. 두 보고서는 `CREATE_NEW`로 기존 파일 덮어쓰기를 거절하므로 그대로 재실행하면 중단된다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다. 도구·보고서·원본은 일반 Git 커밋 대상이 아니다.

관련 테스트 397개 중 396개 성공·기존 심볼릭 링크 테스트 1개 건너뜀을 별도로 확인했다. 전체 프로젝트 테스트·수집기·스케줄·Docker·계좌·주문·OpenAI 호출은 실행하지 않았으며 설정과 실제 DB를 변경하지 않았다.
