# 보존된 KIS 종목 마스터 배치 해석

## 목적과 경계

완료된 수집 배치를 읽고 수집 기록과 파일을 대조한 뒤, 기존 원문 파서로 KOSPI·KOSDAQ 전체를 해석한다. **두 시장 모두 검증에 성공한 경우에만 수집 메타데이터와 불변 해석 결과를 함께 반환한다.** 일부 파일이나 첫 시장의 성공만으로 배치 성공을 반환하지 않는다.

이 서비스는 [수동 수집](stock-master-manual-collection.md)과 [원문 파서](stock-master-raw-parsing.md)를 연결하는 읽기 전용 기능이다. 재다운로드·ZIP 재추출·원본 수정·디렉터리 생성·DB 저장을 하지 않는다. Spring 빈 등록, 수집 Runner·스케줄러·운영 Universe·백테스트·주문 연결도 추가하지 않는다.

`CURRENT_OBSERVATION`은 현재 파일을 관측했다는 범위다. `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지하며 `StockEligibilityInput`이나 `AS_OF_VERIFIED`를 생성하지 않는다. 수집 시각을 과거 적용일·정보 공개 시각으로 변환하지 않는다.

## 클래스와 호출 계약

기준 경로는 `src/main/java/com/stock/market/stock/master/parsing/`다.

| 클래스 | 책임 |
| --- | --- |
| `StockMasterBatchParsingService` | `parseBatch(Path observationRoot, UUID collectionId)`로 기존 배치를 찾아 메타데이터·파일을 검증하고 두 시장을 해석한다. 생성자에 기존 `KisStockMasterParser`를 받으며 Spring 설정에 의존하지 않는다. |
| `result.StockMasterBatchParseResult` | 기존 `StockMasterCollectionResult`와 두 시장의 `KisStockMasterParseResult`를 보존한다. 결과 목록을 방어적으로 복사하고 해시 연결·버전 일치·식별자 중복을 검사한다. |

`observationRoot`는 UUID 디렉터리의 부모다. 서비스는 `<observationRoot>/<collectionId>/manifest.json`과 기존 수집 계약의 시장별 ZIP·MST·`observation.json`, 총 7개 파일을 요구한다. 누락된 경로나 `.partial`만 있는 배치를 만들거나 복구하지 않는다.

반환하는 수집 기록은 manifest에서 읽은 값 그대로다. 시장 결과는 manifest의 목록 순서와 관계없이 KOSPI·KOSDAQ 순서로 반환하고, 각 MST의 원문 행 순서·필드 값은 기존 파서가 유지한다. null 인자는 `NullPointerException`, 파일·메타데이터·해석 실패는 수집 ID와 원인을 담은 `IOException`으로 전달한다. 시장별 처리 실패에는 시장과 원문 파서의 행 번호도 남긴다.

## 검증 순서와 읽기 한도

1. 관측 루트와 UUID 배치 디렉터리가 존재하는지 확인하고 배치의 실제 경로가 다른 위치로 연결되지 않는지 검사한다.
2. manifest를 기존 수집 결과 record로 복원한다. 형식 버전 1, `CURRENT_OBSERVATION`, 요청한 수집 ID, 두 시장의 단일 기록, 시각 범위·고정 출처 URL·상대 경로·해시 형식 등 기존 생성자 계약을 적용한다.
3. 시장별 `observation.json`을 같은 record로 읽어 manifest의 해당 시장 기록과 모든 값이 일치하는지 확인한다.
4. ZIP과 MST의 실제 크기·SHA-256을 기록과 대조한다. 파일 경로는 선택한 배치 아래의 일반 파일이어야 하며 실제 경로가 다른 위치로 연결되면 거절한다.
5. 검증한 MST 바이트를 기존 `KisStockMasterParser`에 전달한다. 두 시장의 파서·레이아웃 버전 및 입력 해시 연결을 확인하고 시장 간 단축 코드·표준 코드 충돌도 거절한다.

| 입력 | 최대 읽기 크기 | 메모리 보존 |
| --- | --- | --- |
| manifest와 각 observation JSON | 파일마다 64 KiB | 제한 안에서 전체 바이트를 읽는다. |
| ZIP | 파일마다 64 MiB | 스트리밍으로 해시만 계산하고 ZIP 본문은 보관하지 않는다. |
| MST | 파일마다 32 MiB | 제한 안에서 전체 바이트를 읽어 파서에 전달한다. |

상한은 프로젝트의 자원 보호 한도이며 KIS 공식 규격이 아니다. JSON은 중복 속성·뒤따르는 별도 JSON 값·알 수 없는 속성을 거절한다. ZIP·MST는 기록된 크기가 상한 이하여야 하고 사전 실제 크기도 기록과 같아야 한다. 읽는 동안에도 기록된 크기보다 많은 바이트를 받으면 중단하며, 읽은 크기가 줄어들거나 해시가 달라지면 실패한다. JSON에도 사전 크기와 실제 읽기 한도를 적용한다.

## 원문과 검증의 한계

ETP 공백과 미정의 그룹·우선주·거래정지 코드, 원문 날짜 문자열은 파서 결과 그대로 남긴다. 공백을 `0`으로 채우거나 미정의 값을 보통주·`OTHER`로 분류하지 않는다. 메타데이터 대조와 원문 구조 검증을 통과해도 종목 유형·현재 주문 가능성·과거 모집단·전략 수익성을 승인하지 않는다.

ZIP은 수집 당시 구조·추출 크기·CRC를 검사한 보존 원본이다. 이번 서비스는 기록된 ZIP 바이트의 크기·해시를 확인할 뿐 압축 해제를 반복하거나 ZIP과 MST의 관계를 새로 증명하지 않는다. 수집 기록 자체에 전자서명이나 외부 인증을 추가하지 않았으므로 manifest와 모든 파일을 일관되게 바꾸는 행위까지 출처 검증으로 탐지할 수는 없다.

신뢰할 수 있고 실행 중 변경하지 않는 관측 디렉터리를 전제로 한다. 실제 경로 대조와 마지막 파일의 `NOFOLLOW_LINKS` 읽기는 경로 우회를 제한하지만, 여러 파일을 원자적으로 잠그는 스냅샷이나 적대적인 동시 디렉터리 교체에 대한 완전한 방어를 제공하지 않는다.

## 관련 테스트

테스트 경로는 `src/test/java/com/stock/market/stock/master/parsing/`와 기존 `provider/kis/parsing/`다. 새 Fixture는 모의 Client와 기존 수집 서비스를 사용하여 실제 형태의 JSON·ZIP·MST 배치를 만든다. 외부 HTTP와 로컬 실측 원본을 요구하지 않는다.

```powershell
.\gradlew.bat test --tests "com.stock.market.stock.master.parsing.*" --tests "com.stock.market.stock.master.provider.kis.parsing.*" --offline --no-daemon
```

2026-10-05 관련 테스트 80개 중 **79개 통과, 1개 건너뜀, 실패·오류 0개**로 Gradle 실행이 성공했다. 새 서비스 39개 중 38개 통과·1개 건너뜀, 새 결과 record 4개 통과, 기존 원문 파서 관련 37개 통과다. 전체 프로젝트 테스트나 수집용 테스트 전체를 다시 실행하지 않았다.

완료 배치·원본 불변·정확한 메타데이터 연결·목록 불변성, 잘못된 JSON·수집 ID·형식·범위·시각·시장 구성·출처·상대 경로, 누락 파일·같은 길이의 변조·크기 한도, 읽기 중 크기 증가·감소, 두 번째 시장 해석 실패와 시장 간 코드 충돌을 확인했다. 실제 읽기 중 크기 변화는 `Files.newInputStream` 응답을 모의하여 재현했다.

심볼릭 링크 파일과 배치 디렉터리를 실제로 생성하는 테스트 1개는 이 Windows 환경의 링크 생성 권한 부족으로 건너뛰었다. 문자열 상대 경로 이탈 거절과 일반 파일 검증이 통과했다고 해서 실제 링크 생성 테스트까지 통과한 것으로 기록하지 않는다.

## 보존 원본 대조

[수집 실측](validation/stock-master-collection-observation-01.md)의 기존 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`를 새 서비스로 읽었다. 수집 시각은 `2026-10-05T09:32:35.795200200Z`부터 `2026-10-05T09:32:36.202168500Z`까지이며, 이번 대조 시각은 `2026-10-05T11:01:22.249018100Z`다. 대조 시각을 새 수집 시각으로 기록하지 않았다.

| 대조 항목 | KOSPI | KOSDAQ |
| --- | --- | --- |
| 전체 행 | 2,578 | 1,825 |
| 입력 SHA-256과 수집 메타데이터 | 일치 | 일치 |
| `rawLine` 재인코딩과 LF로 전체 MST 바이트 복원 | 일치 | 일치 |
| 이전 관측 표본의 행 번호와 모든 필드 | 21행 일치 | 5행 일치 |

반환한 수집 record는 manifest와 같았고 두 시장 간 식별자 충돌 없이 완료됐다. 원본 7개 파일의 크기·SHA-256은 이전 관측 기록과 같았으며 실행 전후에도 변경되지 않았다. 기존 대조 기록의 SHA-256은 `08f2c7a40a738d99b05df6b0eef215cee109bed3d3307e3882b5ea1402fbea87`이다. 이번 도구가 비교한 집계는 행 수와 표본이며, 원문 파서 단계의 6종 분포 집계를 다시 실행했다고 주장하지 않는다.

도구와 결과는 Git 제외 경로 `build/stock-master-batch-parsing-observation-01/VerifyStockMasterBatchParsing.java` 및 `verification.json`에 남겼다. 외부 요청은 0회이며 재수집·DB·Docker·계좌·주문·OpenAI 실행은 없었다. `eligibilityOrHistoricalPopulationVerified=false`를 유지한다.

## 후속 유형 매핑 검토

[유형 매핑 근거 재검증](validation/stock-master-type-mapping-validation-01.md)은 공식 규격·예제를 고정하고 같은 배치의 원문 조합을 대조한 별도 문서 검증이다. ETP 공백과 미정의 코드 때문에 전체 개별주 자동 매핑은 보류한다. 배치 해석 서비스의 성공 계약과 원문 보존 동작을 변경하지 않았으며, 이 검토를 종목 자격·운영 후보·주문 승인으로 연결하지 않는다.
