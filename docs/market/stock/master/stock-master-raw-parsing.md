# KIS 종목 마스터 원문 파서와 로컬 대조

## 목적과 경계

보존한 KOSPI·KOSDAQ MST 바이트를 Java의 불변 원문 레코드로 읽는다. **이 파서는 필드 분리와 원문 보존을 담당하며, 종목 유형·상장 상태·과거 자격을 승인하지 않는다.** HTTP·파일 읽기·DB·Spring 빈·수집 Runner·스케줄러·주문 연결은 포함하지 않는다.

[원본 실측](validation/stock-master-collection-observation-01.md)에서 확인한 바이트 레이아웃을 사용한다. ETP 공백과 미정의 코드의 의미는 여전히 미확인이다. `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지하며, `StockEligibilityInput`·`AS_OF_VERIFIED`·운영 후보를 생성하지 않는다.

## 클래스와 입력

기준 경로는 `src/main/java/com/stock/market/stock/master/provider/kis/parsing/`다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockMasterParser` | `parse(KisStockMasterMarket market, byte[] content)`로 한 시장의 전체 MST 바이트를 읽는다. 외부 의존성과 실행 간 가변 상태가 없다. |
| `record.KisStockMasterRawRecord` | 행 번호·단축 코드·표준 코드·이름·열 개 원문 필드·LF 제외 원문 행을 보존한다. 날짜와 enum으로 변환하지 않으며 코스피 투자주의환기 미제공은 null로 남긴다. |
| `result.KisStockMasterParseResult` | 시장·입력 SHA-256·우리 파서 및 레이아웃 버전·불변 레코드 목록을 반환한다. 원문 순서와 연속 행 번호를 유지한다. |

전체 입력은 LF를 포함하는 추출 파일 바이트다. null·빈 입력을 거절하고 입력 크기는 최대 32 MiB로 제한한다. 이 값은 파서의 자원 보호 한도이며 KIS 공식 파일 규격이 아니다. 바이트를 전달하기 전의 파일 읽기·메모리 할당이나 manifest 대조는 이 API가 담당하지 않는다.

입력은 복사한 뒤 해석하고 해시를 계산한다. 파서는 입력 배열을 변경하지 않으며 반환 레코드는 원본 배열을 참조하지 않는다. 결과 목록은 `List.copyOf`로 보존한다. 입력 해시에는 LF·공백도 포함한다. 해시와 버전이 있다는 사실만으로 출처나 과거 정보 가용성이 인증되지는 않는다.

## 관측 레이아웃

현재 파서 버전은 `KIS_STOCK_MASTER_RAW_V2`, 레이아웃 버전은 `OBSERVED_2026_10_05_LF_V1`이다. V2는 SPAC·관리종목·코스닥 투자주의환기의 원문 추출 계약을 추가한 버전이며 파일 길이·LF 형식·기존 위치는 바꾸지 않았다. 두 값 모두 **프로젝트의 해석 계약**이며 KIS 공식 규격 버전이 아니다. 최초 V1 대조와 당시 보고서는 그대로 보존한다.

추가 필드 위치는 후속 [KIS 대체 원천 검증](validation/kis-stock-eligibility-source-validation-01.md)에서 보존한 공식 레이아웃과 원문으로 확인했다. 고정 revision은 `277ec0eb7a9b7f63b6807829286c80f36649dad2`이며, 해당 자료를 모든 과거·미래 파일의 보편 규격으로 확대하지 않는다.

| 구간 | KOSPI | KOSDAQ |
| --- | --- | --- |
| LF 제외 한 행 | 288 bytes | 282 bytes |
| 단축 코드·표준 코드·이름 | 9 + 12 + 40 bytes | 9 + 12 + 40 bytes |
| 나머지 필드 영역 | 227 bytes | 221 bytes |
| 줄바꿈 | 모든 행 LF 필수 | 모든 행 LF 필수 |

다음 위치는 LF 제외 행의 시작부터 센 **0-based 바이트 오프셋**이다. 이름을 먼저 문자열로 디코딩한 뒤 문자 수로 위치를 계산하지 않는다.

| 원문 필드 | 폭 | KOSPI 위치 | KOSDAQ 위치 |
| --- | --- | --- | --- |
| `rawGroup` | 2 | 61 | 61 |
| `rawEtp` | 1 | 83 | 79 |
| `rawPreferred` | 1 | 219 | 214 |
| `rawListingDate` | 8 | 166 | 161 |
| `rawSuspension` | 1 | 121 | 116 |
| `rawLiquidation` | 1 | 122 | 117 |
| `rawSpac` | 1 | 90 | 85 |
| `rawManagement` | 1 | 123 | 118 |
| `rawInvestmentCaution` | 1 | 필드 없음, null | 91 |
| `rawBaseDate` | 8 | 265 | 259 |

Java의 `MS949` Charset으로 CP949 바이트를 엄격하게 디코딩하고 재인코딩하여 원문 바이트가 복원되는지 검사한다. 단축·표준 코드와 뒷부분은 출력 가능한 ASCII만 허용하며 이름의 제어문자는 거절한다. 이름의 40바이트 경계를 넘는 다중 바이트 문자도 거절한다.

단축·표준 코드·이름의 표시값에서 오른쪽 ASCII 공백 패딩만 제거한다. 앞자리 0·영문자·이름의 앞쪽 공백은 유지하고 `rawLine`에는 모든 패딩을 남긴다. 제공되는 원문 필드에는 패딩 제거를 적용하지 않는다. ETP 한 칸 공백, 우선주 `9`, 날짜의 여덟 칸 공백이나 `NOTADATE` 같은 문자열도 원문으로 유지한다. 날짜 문자열의 보존은 날짜 검증 성공을 뜻하지 않는다.

`rawSpac`·`rawManagement`는 정확히 한 글자의 원문을 요구한다. `rawInvestmentCaution`은 코스닥에서 한 글자를 그대로 추출하고 코스피에서는 null을 반환한다. null은 원천 레이아웃의 미제공, `" "`는 필드가 존재하는 공백 관측, `"N"`은 문자 N 관측으로 서로 다르다. 결과 객체는 코스피의 nonnull 투자주의환기나 코스닥의 null 투자주의환기를 거절한다. 원문 필드만 가진 record는 시장을 모르므로 이 제공 여부 검사는 시장이 있는 `KisStockMasterParseResult`에서 수행한다.

SPAC 필드는 `etpr_undt_objt_co_yn`, 관리종목은 `mang_issu_yn`, 코스닥 투자주의환기는 `invt_alrm_yn`이다. 투자주의환기를 별도 `mrkt_alrm_cls_code`의 투자주의·투자경고·투자위험으로 대체하지 않는다. Y·N·공백·소문자·미정의 문자는 boolean·자격·주문 허가로 변환하지 않는다.

## 실패와 미확인의 구분

행 길이·CP949 디코딩 및 바이트 복원·ASCII 영역·필수 식별자·LF 형식이 맞지 않거나 한 시장 안에 단축 코드 또는 표준 코드가 중복되면 `IllegalArgumentException`으로 중단한다. 오류에는 시장·행 번호를 표시하고 중복은 최초 행 번호도 표시한다. 앞선 정상 행만 반환하거나 오류 행을 건너뛰지 않는다. 결과는 한 시장 전체가 구조 검사를 통과한 경우에만 반환한다.

CRLF·단독 CR·마지막 LF 누락·빈 행은 현재 관측 레이아웃과 다르므로 거절한다. 미래 파일에 다른 정상 형식이 나타나더라도 자동으로 줄바꿈을 변환하거나 오프셋을 추정하지 않는다. 별도 원본 대조와 버전 변경이 필요하다. 두 시장 간 중복 검사는 단일 시장 파서의 책임이 아니며 이번 로컬 대조에서 따로 확인했다.

반면 미정의 그룹·ETP·우선주·거래정지 코드는 구조가 맞으면 그대로 반환한다. 공백을 `0`으로, 알 수 없는 유형을 보통주·`OTHER`로 바꾸지 않는다. 상장일·기준일을 `asOfDate`·`informationAvailableAt`으로 복사하지 않는다. 레이아웃 길이가 일치해도 동일 폭의 필드 의미 변경이나 모집단 완전성을 증명하지 못한다.

## 최초 V1 검증 결과

2026-10-05 아래 관련 테스트 37개가 실패·오류·건너뜀 없이 통과했다. 테스트는 별도 고정폭 합성 데이터를 사용하며 로컬 원본이나 외부 서비스를 요구하지 않는다. 기존 수집용 ZIP Fixture의 짧은 `CONTENT`를 마스터 레코드로 재사용하지 않았다. 전체 프로젝트 테스트와 수집기를 재실행하지 않았다.

```powershell
.\gradlew.bat test --tests "com.stock.market.stock.master.provider.kis.parsing.*" --offline --no-daemon
```

테스트는 두 시장의 모든 추출 필드, 한글 40바이트 경계, 원문 재구성·해시·순서, 코드의 앞자리 0·영문자, 공백·미정의 코드 보존, 잘못된 길이·빈 행·디코딩·CRLF·식별자 중복·입력 크기 제한과 목록 불변성을 확인했다. 첫 실행의 제어문자 Fixture가 한글 바이트를 깨뜨려 발생한 테스트 실패는 ASCII 이름 데이터로 수정하고 관련 테스트를 다시 실행했다.

실제 대조는 기존 수집 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`의 MST를 읽었다. 로컬 도구와 결과는 Git 제외 경로 `build/stock-master-parsing-observation-01/VerifyStockMasterParsing.java`와 `verification.json`에 남겼다. 기존 PowerShell 관측과 독립적인 Java 파서 구현의 결과를 비교했다. Java 파일 소스 실행의 클래스 경로 문제로 도구와 파서 소스를 명시적으로 컴파일했고, JDK 캐시 접근 오류가 없는 컴파일도 별도로 확인했다. 재다운로드는 없었다.

| 대조 항목 | KOSPI | KOSDAQ |
| --- | --- | --- |
| 전체 행 | 2,578 | 1,825 |
| 입력 SHA-256과 manifest | 일치 | 일치 |
| `rawLine` 재인코딩 + LF로 전체 바이트 복원 | 일치 | 일치 |
| 그룹·ETP·우선주·거래정지·정리매매·분류 조합의 6종 집계 | 이전 관측과 일치 | 이전 관측과 일치 |
| 숫자 코드·그 외 코드·앞자리 0 건수 | 이전 관측과 일치 | 이전 관측과 일치 |
| 이전 관측 샘플의 모든 원문 필드 | 21행 일치 | 5행 일치 |

로컬 대조 시각은 `2026-10-05T10:22:18.470872700Z`다. 이전 검증 JSON의 SHA-256은 `08f2c7a40a738d99b05df6b0eef215cee109bed3d3307e3882b5ea1402fbea87`로 고정했다. 두 시장 간 단축·표준 코드 충돌은 각각 0이었고 검사 전후 기존 7개 보존 파일의 길이와 SHA-256은 동일했다. 대조 도구의 외부 요청 수는 0이며 DB·Docker·계좌·주문·OpenAI 연동을 실행하지 않았다.

현재 확인한 것은 원문 구조·추출·재현성이다. 미정의 값의 의미, upstream 규격 버전, 과거 시장 소속·상장 상태·모집단과 정보 가용 시각은 여전히 미확인이다. `eligibilityOrHistoricalPopulationVerified=false`를 유지하고 원문 읽기 성공을 종목 자격이나 전략 순성과 승인으로 사용하지 않는다.

## 제한 원문 추출 V2 검증

2026-10-06 [주식기본조회 실측](validation/kis-stock-basic-info-observation-01.md)에서 SPAC이 보통주 코드 `101`을 반환했고, 투자주의환기·정리매매 전용 원문 필드는 제공되지 않았다. V2는 해당 API의 성공으로 종목 자격을 승인하는 대신 기존 마스터에서 필요한 제한 원문을 꺼낸다. 유형 해석·KIS 및 KRX 제한 정책·`StockEligibilityPolicy`는 변경하지 않았다.

새 필드 추출·원문 폭·공백과 미정의 문자 보존·시장별 미제공 검사·JSON 왕복, 기존 바이트 복원·유형 및 제한 해석 회귀가 통과했다. 기존 `StockMasterBatchParsingServiceTest`의 심볼릭 링크 거절 테스트는 현재 파일 시스템에서 링크 생성이 허용되지 않아 assumption으로 건너뛰었다. 전체 프로젝트 테스트는 실행하지 않았다.

당시 문서의 458개 집계는 `TEST-*.xml` 파일명만 사용해 긴 클래스명의 축약 파일을 빠뜨린 부분 집계였다. 이를 전체 테스트 수로 사용하지 않는다. 후속 관측 상태 V2 확장 후 같은 범위 전체를 다시 확인한 결과는 **740개 중 739개 성공·1개 건너뜀·실패 및 오류 0개**이며 [제한 관측 상태 문서](kis-stock-trading-restriction.md)에 기록했다. 이 수치는 후속 테스트가 추가된 현재 결과이며 최초 원문 추출 작업의 테스트 수로 소급하지 않는다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.master.*' --tests 'com.stock.strategy.universe.eligibility.classification.*' --tests 'com.stock.strategy.universe.eligibility.restriction.*' --offline --no-daemon
```

기존 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`를 V2로 다시 파싱하고, 고정한 공식 C 레이아웃의 필드 폭으로 위치를 독립 계산해 4,403행의 세 필드를 바이트와 대조했다. 집계는 이전 KIS 대체 원천 검증의 고정 `preflight.json`과 일치했다.

| 원문 필드 | KOSPI Y | KOSPI N | KOSPI 미제공 | KOSDAQ Y | KOSDAQ N | KOSDAQ 미제공 |
| --- | --- | --- | --- | --- | --- | --- |
| SPAC | 0 | 2,578 | 0 | 71 | 1,754 | 0 |
| 관리종목 | 47 | 2,531 | 0 | 136 | 1,689 | 0 |
| 투자주의환기 | 0 | 0 | 2,578 | 87 | 1,738 | 0 |

이 자료의 제공 필드에는 공백·미정의 값이 없지만, 이후 파일도 그렇다고 가정하지 않는다. 제한이 같은 행에 겹칠 수 있어 세 필드의 Y 건수를 서로 다른 종목 수로 더하지 않는다.

기존 V1 거래정지 관측 보고서의 모든 원문 record와 V2에서 추가 필드만 제외한 record를 JSON 값으로 대조했다. 모든 기존 필드·행 번호·시장·순서가 같고 전체 MST 바이트와 입력 해시도 유지됐다. 이전 원문·보고서·규격·주식기본조회 증적 61개는 실행 전후 SHA-256이 같았다. V2 결과 전체의 JSON 왕복도 일치했다.

V1 JSON을 새 record로 직접 읽으면서 누락 필드를 N이나 null로 채우지 않는다. 기존 보고서는 V1 증적으로 남기고 원래 MST 바이트에서 V2 결과를 새로 생성한다. 특히 코스피의 원천 미제공과 과거 버전의 미추출은 같은 의미가 아니다.

로컬 검증 도구와 보고서는 Git 제외 경로 `build/kis-stock-master-restriction-parsing-observation-01/`에 보관한다. 저장 JSON과 메모리 숫자 노드 타입 차이로 최초 재현 비교가 실패했으며, 비교 양쪽을 저장 JSON 형식으로 다시 읽도록 도구를 보정한 후 재현이 성공했다. 최초 보고서를 덮어쓰지 않았고 검증 시각을 제외한 전체 JSON 값·배열 순서가 일치했다.

- 최초 보고서 SHA-256: `cf439ca56af7732db20959a2842a43064f6594b86d00ee90bf7109f001f6b719`
- 재현 보고서 SHA-256: `c9f7658f44619b0f58e74e5bb2c228199c9d5ba043401374e53b94d87c83068c`

```powershell
.\gradlew.bat -I build/kis-stock-master-restriction-parsing-observation-01/observation.init.gradle verifyKisRestrictionParsing '-PobservationReport=verification-replay.json' --offline --no-daemon
```

이미 존재하는 보고서는 덮어쓰기를 거절한다. 위 최초·재현 보고서를 보존하고 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다. 검증 도구는 운영 빌드에 연결하지 않았다. 외부 API·토큰 발급·계좌·주문·OpenAI 호출과 DB·Docker·스케줄·설정·`.env` 변경은 없다.

추출된 값은 보존 자료의 문자 관측이다. 최신성·적용 시점·원천 간 불일치·후보 제외 정책은 별도 검증이 필요하며 `AS_OF_VERIFIED`, `informationAvailableAt`, 후보·과거 모집단·주문 가능 여부를 생성하지 않는다.

## 후속 배치 해석

후속 [보존된 배치 해석 서비스](stock-master-batch-parsing.md)는 완료 manifest와 시장별 관측, ZIP·MST 크기·해시를 대조한 뒤 이 파서를 호출한다. 두 시장이 모두 성공하고 시장 간 식별자 충돌도 없을 때만 전체 결과를 반환한다. 원문 파서 자체의 입력·책임은 바꾸지 않았으며, 수집 Runner·DB·종목 자격·운영 주문에는 연결하지 않는다.

추출된 다섯 제한 원문은 [KIS 종목 제한 관측 상태 V2](kis-stock-trading-restriction.md)에서 Y·N·미확인·원천 미제공으로 독립 해석한다. 이 후속 정책은 파서·원문·유형 결과를 변경하지 않으며 운영 후보나 주문 허가를 생성하지 않는다.
