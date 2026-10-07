# KIS 마스터 시장경보 원문 추출

## 목적과 경계

기존 [종목 마스터 원문 파서](stock-master-raw-parsing.md)의 결과에서 시장경보 구분 코드와 시장 경고위험 예고 원문을 별도로 꺼낸다. **투자주의환기와 시장경보를 같은 필드로 취급하지 않고, 기존 RAW_V2와 제한 점검 V1·V2 계약을 변경하지 않는다.**

이번 계약은 필드의 바이트 위치·폭·식별자·원문 정합성을 검사한다. 코드의 의미를 enum이나 boolean으로 해석하거나 후보를 제외하지 않는다. 거래 가능 여부·제한 효력·주문 권한도 승인하지 않는다. `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다.

## 패키지와 입력

기준 패키지는 `com.stock.market.stock.master.provider.kis.parsing.warning`이며 파일은 `src/main/java/com/stock/market/stock/master/provider/kis/parsing/warning/`에 둔다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockMasterMarketWarningParser` | `parse(KisStockMasterParseResult source)`로 한 시장의 기존 결과를 검증하고 두 원문 필드를 추출한다. |
| `record.KisStockMasterMarketWarningRawRecord` | 행 번호·단축 코드·표준 코드와 두 원문 값을 보존한다. |
| `result.KisStockMasterMarketWarningParseResult` | 기존 결과 전체를 `source`로 보존하고, 새 불변 목록과 추출 버전·근거 revision을 반환한다. |

Spring 빈이나 새 Provider 인터페이스가 아닌 무상태 일반 Java 클래스다. 파일·HTTP·DB 접근, 토큰 발급과 수집 실행은 없다. 파일을 읽어 완료 배치를 확인하는 책임은 기존 `StockMasterBatchParsingService`에 남는다.

입력은 `KIS_STOCK_MASTER_RAW_V2` / `OBSERVED_2026_10_05_LF_V1` 조합만 받는다. 다른 버전의 같은 길이 파일도 자동으로 호환된다고 간주하지 않는다. 기존 파서와 record에 필드를 추가하거나 RAW_V3로 올리지 않으므로 이전 결과에 새 필드를 기본값으로 채우지 않는다.

## 필드와 검증된 위치

추출 버전은 `KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1`이다. 공식 KIS 저장소에서 보존한 revision `277ec0eb7a9b7f63b6807829286c80f36649dad2`의 C 헤더 선언과 기존 MST 바이트로 위치를 대조했다. 이는 해당 보존 레이아웃의 근거이며 모든 과거·미래 파일의 보편 규격을 인증하지 않는다.

다음 위치는 LF를 제외한 행 시작부터 센 **0-based 바이트 오프셋**이다. 한글 이름을 포함한 문자열의 문자 인덱스로 계산하지 않는다.

| 원문 | Java 필드 | 폭 | KOSPI | KOSDAQ |
| --- | --- | --- | --- | --- |
| `mrkt_alrm_cls_code` | `rawMarketWarningCode` | 2 bytes | 124 | 119 |
| `mrkt_alrm_risk_adnt_yn` | `rawMarketWarningRiskPreannouncement` | 1 byte | 126 | 121 |
| LF 제외 전체 행 | `source.records[].rawLine` | 시장별 고정 | 288 bytes | 282 bytes |

코드 `00`·`01`·`02`·`03`, 두 칸 공백, `0 `·` 0`·`??`·소문자·미정의 문자도 출력 가능한 ASCII와 필드 폭이 맞으면 그대로 보존한다. 예고 필드도 Y·N·한 칸 공백·소문자·미정의 문자를 바꾸지 않는다. trim·대문자화·누락 값 보완·boolean 변환을 하지 않는다.

`rawInvestmentCaution`은 기존 투자주의환기 원문이며 이번 시장경보 코드와 별개다. 코스피에서 투자주의환기가 null이거나 제한 점검 V2의 비적용 설명이 있어도 이번 두 필드를 생략하거나 `00`·`N`으로 채우지 않는다.

## 정합성 및 실패 처리

`source`의 모든 `rawLine`을 엄격한 CP949 인코더로 바이트로 복원하고 LF를 결합한다. 행 폭과 기존 32 MiB 입력 보호 한도를 확인한 후, 원래 `KisStockMasterParser`로 전체를 재해석한다. 입력 해시·시장·버전·행 순서·식별자·이름·기존 열 개 원문 필드까지 `source`와 같아야 한다.

원문이 짧거나 길고, CP949로 복원되지 않거나, 원문과 입력 해시·기존 필드가 다르면 `IllegalArgumentException`으로 중단한다. 앞선 정상 행만 반환하거나 다른 위치를 추정하지 않는다. null 입력은 `NullPointerException`으로 거절한다. 오류 메시지에 전체 원문 행을 넣지 않는다.

새 결과의 `records`는 `List.copyOf`로 보존한다. 결과 생성자도 원문을 다시 추출하여 목록 전체를 비교하므로 직접 생성이나 JSON 복원에서 행 누락·중복·재정렬, 다른 식별자·원문 값·버전·revision 주입을 거절한다. 새 원문 record 하나만으로는 전체 출처와 시장을 검증할 수 없으며, 이 연결 검사는 `source`가 있는 결과에서 수행한다.

이 검사는 내부 정합성을 보장하는 것이지 출처의 진위나 최신성을 인증하는 서명 검증이 아니다. 원문과 해시를 함께 바꾼 입력을 과거 증적으로 인증하지 않으며, 파일·manifest의 출처 검증과 `AS_OF_VERIFIED`·`informationAvailableAt`은 별도 책임이다.

## 기존 동작 유지

기존 `KisStockMasterRawRecord`, `KisStockMasterParseResult`와 `StockMasterBatchParseResult`의 필드·버전·JSON을 변경하지 않는다. 새 결과는 기존 결과를 감싼 별도 계약이며 기존 소비자가 자동으로 새 필드를 사용하지 않는다.

후속 [시장경보 관측 상태 해석](kis-stock-market-warning-observation.md)은 이 추출 결과를 입력으로 받아 경보 코드와 예고를 별도 관측 상태로 해석한다. 이 파서의 원문 계약·버전·JSON은 그대로 유지하며, 공백·미정의 문자를 정상 값으로 보정하지 않는다. 관측 상태 추가도 후보 제외·주문 차단 연결을 뜻하지 않는다.

[제한 관측](../basicinfo/kis-stock-basic-info-restriction-observation.md)과 [사전 점검 V1·V2](../basicinfo/kis-stock-basic-info-restriction-screening.md)의 상태·사유·JSON도 그대로 유지한다. 해당 점검은 아직 이번 시장경보와 예고를 평가하지 않는다. 따라서 점검의 `NO_EXCLUSION_SIGNAL_OBSERVED`를 시장경보 검사 통과나 최종 투자 후보 승인으로 사용하지 않는다.

`StockEligibilityPolicy`·후보 선정·유동성 평가·백테스트·Risk Guard·주문·스케줄·AI Prompt에 연결하지 않는다. DB·스키마·라이브러리·설정·`.env`·Docker 변경도 없다.

## 관련 테스트와 보존 자료 대조

테스트는 같은 `src/test/java/.../parsing/warning/` 경로 아래 파서·원문 record·결과와 `support/` Fixture로 구성한다. 기존 고정폭 Fixture를 재사용하며 실제 보존 파일이나 외부 서비스가 없어도 실행할 수 있다.

2026-10-07 관련 범위 **17개 클래스·397개 테스트 중 396개 통과, 1개 건너뜀, 실패·오류 0개**를 확인했다. 이 중 새 테스트는 59개다. Windows에서 심볼릭 링크 생성 권한이 필요한 기존 `StockMasterBatchParsingServiceTest.rejectsFileLinksAndBatchDirectoryLinksWhenSupported`는 assumption으로 건너뛰었다. 현재 실행 시각의 XML suite 이름을 기준으로 긴 클래스명의 축약 파일도 포함했다. 전체 프로젝트 테스트는 실행하지 않았다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.master.provider.kis.parsing.*' --tests 'com.stock.market.stock.master.parsing.*' --tests 'com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.*' --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --offline --no-daemon
```

새 테스트는 두 시장의 바이트 위치·인접 필드·한글 이름, 공백과 미정의 값 보존, 모든 행의 식별자·순서, 미지원 버전, 잘못된 폭·인코딩·해시·기존 필드, 목록 불변성과 전체 JSON 왕복·변조 거절을 검증한다. 기존 원문·배치 파서와 제한 점검·저장된 분석 경로도 같은 실행에서 확인했다.

별도 로컬 검증에서는 기존 4,403행의 두 필드 8,806개가 고정 헤더에서 독립 계산한 위치의 MST 바이트와 일치했다. 기존 V1·V2 결과 20건의 전체 JSON·판정·사유는 유지됐고, 보존 증적 97개는 검사 전후 해시가 같았다. 최초·재현 결과도 검증 시각을 제외한 전체 JSON이 동일했다. 상세 집계·보고서 해시·재현 경로는 [시장경보 원문 대조 기록](validation/kis-stock-market-warning-observation-01.md)에 남긴다.

추출 성공은 시장경보 해석·최신 거래 가능 여부·과거 모집단·전략 순성과 검증의 완료를 뜻하지 않는다. 추가 API·토큰·계좌·주문·OpenAI 호출은 없었고 실제 DB와 Docker를 사용하지 않았다.
