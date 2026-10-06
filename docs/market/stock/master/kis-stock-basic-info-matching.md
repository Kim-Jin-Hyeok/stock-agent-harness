# KIS 마스터와 주식기본조회 표준코드 대조

## 목적과 범위

KIS 마스터에서 **호출자가 전달한 요청 종목코드의 행을 먼저 찾고**, 주식기본조회 응답의 표준코드와 시장을 대조한다. 다른 종목의 응답이 들어왔을 때 응답 표준코드로 마스터 행을 다시 선택하지 않고 불일치 사유와 양쪽 원문을 보존한다.

`STANDARD_CODE_AND_MARKET_MATCH`는 요청 종목에 해당하는 마스터 행과 응답의 두 비교 항목이 일치한다는 뜻이다. 상품번호의 관계, 실제 HTTP 요청, 입력 출처와 신선도, 상장 상태, 투자 후보 자격이나 주문 권한을 인증하지 않는다. 종목 유형 해석과도 별개의 결과다.

이름·가격·상품 유형·주식종류·거래 제한 값은 식별 대조에 사용하지 않는다. SPAC·ETF·거래정지·정리매매 사례도 표준코드와 시장이 같으면 이 제한된 대조에서 성공할 수 있다. **대조 성공 수를 투자 가능한 종목 수로 사용하지 않는다.**

후속 [현재 유형 보완 정책](../basicinfo/kis-stock-basic-info-type-resolution.md)은 이 결과를 단건 입력으로 받아 대조 성공 때만 마스터·API 유형을 조합한다. 이 대조 정책과 원문은 변경하지 않으며 참고 유형을 투자 자격이나 주문 허가로 연결하지 않는다.

## 패키지와 호출 계약

패키지는 `com.stock.market.stock.master.matching.kisbasicinfo`다. 기존 `matching.kiskrx`와 분리하며 기존 마스터 배치와 [주식기본조회 파싱 결과](../basicinfo/kis-stock-basic-info-raw-parsing.md)를 재사용한다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoMatchingPolicy` | `match(StockMasterBatchParseResult, String requestedSymbol, KisStockBasicInfoParseResult)`로 한 요청과 한 응답을 대조한다. |
| `result.KisStockBasicInfoMatchResult` | 대조 버전, 마스터 배치 전체, 요청 종목코드, API 파싱 입력 전체, 비교한 시장·행, 사유를 보존한다. |
| `result.KisStockBasicInfoMatchReasonCode` | 제한된 대조 성공 또는 첫 불일치·미확인 사유를 표현한다. |

대조 버전은 `KIS_STOCK_BASIC_INFO_STANDARD_CODE_MARKET_V1`이다. Spring 빈이 아닌 무상태 Java 정책이며 파일·네트워크·DB를 읽지 않는다. 이미 파싱된 불변 입력을 보관하고 원문 입력 SHA-256, 수집 메타데이터, 원본 행과 파서 버전을 유지한다.

마스터 배치는 양 시장의 종목코드·표준코드 중복을 이미 거절한다. 정책은 이 계약을 그대로 사용하며 별도 중복 해소나 우선 시장 선택을 추가하지 않는다. 숫자 종목코드뿐 아니라 `0004Y0` 같은 영문 포함 종목코드도 정확한 문자열로 찾는다.

`requestedSymbol`은 호출자가 실제 요청값을 전달해야 한다. 정책은 그 값이 HTTP 요청에 쓰였는지 확인할 수 없다. null·공백 요청은 `IllegalArgumentException`, null 배치·API 입력은 `NullPointerException`으로 거절한다. 앞뒤 공백이나 대소문자를 보정하지 않으므로 비어 있지 않은 변형 요청은 해당 행을 찾지 못할 수 있다.

## 비교 순서와 사유

다음 순서에서 처음 해당하는 사유 하나를 반환한다. 뒤쪽 항목까지 검증했다는 뜻이 아니며 나머지 입력도 결과에 남는다.

| 순서 | 사유 | 조건 |
| --- | --- | --- |
| 1 | `REQUESTED_SYMBOL_NOT_FOUND` | 요청 종목코드가 마스터 배치에 없다. 비교 시장·행은 둘 다 null이다. |
| 2 | `API_STANDARD_CODE_BLANK` | API `std_pdno`가 빈 문자열 또는 공백이다. |
| 3 | `STANDARD_CODE_MISMATCH` | 요청 종목의 마스터 표준코드와 API `std_pdno`가 정확히 같지 않다. |
| 4 | `API_MARKET_UNVERIFIED` | API 시장 값이 이번 대조의 정확한 지원 코드 `STK`, `KSQ`가 아니다. |
| 5 | `MARKET_MISMATCH` | `STK → KOSPI`, `KSQ → KOSDAQ`로 대조했을 때 마스터 시장과 다르다. |
| 6 | `STANDARD_CODE_AND_MARKET_MATCH` | 요청 행이 있고 표준코드와 지원 시장 매핑이 모두 일치한다. |

`API_MARKET_UNVERIFIED`는 이번 대조에서 확인하지 못했다는 뜻이다. 다른 코드에 KIS 공식 정의가 없다는 뜻은 아니다. 예를 들어 `KNX`의 별도 시장 정의가 있어도 이번 두 시장 대조에는 사용하지 않는다. 현재가 요청의 `J/NX/UN`도 이 API의 시장 코드와 다르다.

표준코드와 시장에 trim·대소문자 변경·숫자 변환을 적용하지 않는다. `pdno`의 접두사를 제거하거나 뒤 여섯 글자를 잘라 종목코드를 만들지 않는다. 이름이 다르거나 상품번호가 미확인 표현이어도 표준코드와 시장 대조 결과를 바꾸지 않고 원문으로 남긴다.

예를 들어 요청이 `005930`인데 응답 표준코드가 배치에 존재하는 `111111`의 코드라면 `005930` 행을 보존하고 `STANDARD_CODE_MISMATCH`를 반환한다. 요청 코드가 배치에 없을 때도 응답 표준코드나 상품번호로 대신 연결하지 않는다.

## 결과 객체의 검증

결과 생성자는 null 입력·사유, 공백 버전·요청을 거절한다. 비교한 시장과 원본 행 전체가 요청 종목의 배치 행과 같아야 하며 사유도 위 순서와 일치해야 한다. 배치에 있는 다른 행, 식별자는 같지만 이름이나 원문이 바뀐 행, 잘못된 시장, 불일치 입력의 성공 사유를 직접 넣으면 거절한다.

요청 행을 찾은 경우에는 실패 결과도 비교 시장·행을 보존한다. `REQUESTED_SYMBOL_NOT_FOUND`일 때만 두 값이 null이다. 결과는 전체 JSON 왕복 후에도 같은 입력과 사유를 유지한다. 생성자의 검사는 전달된 값 사이의 정합성 검사이지 수집 출처·버전 진위·정책 실행의 증명이 아니다.

## 관련 테스트

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.master.matching.*' --tests 'com.stock.market.stock.master.parsing.result.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.parsing.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **454개가 모두 통과**했다. 신규 정책 50개·결과 25개, 기존 KIS·KRX 대조 50개, 마스터 배치 계약 4개, 주식기본조회 파서 관련 325개를 포함한 7개 클래스다. 실패·오류·건너뜀은 0개다. 전체 프로젝트 테스트는 실행하지 않았으며 최신 HTML 클래스 목록과 XML suite 이름으로 집계했다.

신규 테스트는 양 시장과 영문 포함 코드의 정상 대조, 다른 기존 종목 응답의 잘못된 연결 방지, 미존재 요청, 빈 표준코드, 시장 불일치, 공백·대소문자 미보정, 첫 사유 순서를 확인한다. 비교하지 않는 API 필드 14개, 서로 다른 이름, SPAC·거래정지·정리매매·관리 원문 보존, 반복 호출의 무상태성도 확인한다. 결과 생성자의 정합성 검사와 모든 사유의 전체 JSON 왕복을 포함한다.

테스트는 `src/test/java/.../master/matching/kisbasicinfo/`에 있다. `support/KisStockBasicInfoMatchingFixture.java`는 기존 마스터·API Fixture와 실제 파서를 재사용한다. 수집 메타데이터는 합성이며 HTTP·Spring·DB·로컬 실측 파일이 필요하지 않다.

## 보존 응답 대조

기존 [실전 응답 10건](validation/kis-stock-basic-info-observation-01.md)의 요청 기록과 원문을 읽고, 보존 마스터 배치를 기존 `StockMasterBatchParsingService`로 다시 검증·파싱했다. 배치는 KOSPI 2,578행·KOSDAQ 1,825행, 합계 4,403행이다. 기존 유형 해석 검증의 파싱 결과 전체와 새 파싱 결과가 같았고, 원래 응답 관측에서 기록한 시장·행 번호·표준코드·이름·제한 값도 다시 파싱한 마스터 행과 같았다.

| 보존 요청 종목 | 마스터 시장 | API 시장 | 대조 결과 |
| --- | --- | --- | --- |
| `005930`, `005935`, `000087`, `069500`, `000040`, `000300` | KOSPI | `STK` | 6건 일치 |
| `000250`, `0004Y0`, `007330`, `067770` | KOSDAQ | `KSQ` | 4건 일치 |

총 10건 모두 `STANDARD_CODE_AND_MARKET_MATCH`였다. 이 중 ETF·SPAC·관리·투자주의환기·거래정지·정리매매 사례도 포함되므로 후보 자격 승인은 0건이다. API 상품번호는 그대로 남았으며 접두사 제거로 이 결과를 만들지 않았다. 원래 요청 attempt·응답 metadata와 입력 SHA-256도 대조했다.

전체 마스터 배치를 포함한 결과 10건의 JSON 왕복과 반복 호출 결과가 같았다. 기존 증적 59개는 최초 검증 전후 해시가 같았고, 재현에서는 최초 보고서와 구현 5개를 포함한 65개가 전후 같았다. 최초와 재현의 결과·입력·집계·기존 증적 목록은 검증 시각과 추가 재현 메타데이터를 제외하고 전체 JSON 값이 같았다. 외부 API 요청·토큰 발급은 각각 0회다.

첫 도구의 재현 비교는 메모리에서 만든 숫자 노드와 JSON을 읽은 숫자 노드의 표현 차이로 실패했다. 최초 보고서와 도구를 바꾸지 않고 별도 재현 도구에서 정수·소수의 정밀도를 유지해 JSON 숫자 표현을 통일한 뒤 전체 값을 비교했다. 날짜의 나노초나 정수를 반올림해 통과시킨 것이 아니며, 운영 대조 정책을 수정하지 않았다.

마스터 수집과 API 응답은 서로 다른 관측 시점의 자료다. 이 대조는 보존 원문의 재현성 검증이며 동시점 상태·최신 상태·과거 자격이나 전체 시장의 응답 성공을 인증하지 않는다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-matching-observation-01/`에 최초·재현 Java 도구, 두 Gradle init 파일, `verification.json`, `verification-replay.json`을 보관한다. 보고서는 공통 마스터 배치를 루트에 한 번 저장하고, 각 행의 `matchingResultWithoutSharedBatch`와 결합해 전체 결과를 복원할 수 있다. 실제 JSON 왕복 검사는 공통 배치를 포함한 전체 결과로 수행했다.

- 최초 보고서 SHA-256: `a4a597879a4fe2fb97b0aa8c42409f72526c7e39f0b1bc578476115b3155fabf`
- 재현 보고서 SHA-256: `5ebbcacc14e638512889a57270c4d378e452ba71a50f3e890435a7561f9de4e8`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-matching-observation-01/observation.init.gradle verifyKisStockBasicInfoMatching --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-matching-observation-01/replay.init.gradle verifyKisStockBasicInfoMatchingReplay --offline --no-daemon
```

위 명령은 완료된 최초·별도 재현 실행의 기록이다. 두 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 최초 도구의 `-PobservationReport=verification-replay.json` 비교 경로 대신 별도 재현 명령을 사용했다. 재현 보고서의 `replayVerification`에는 최초 보고서 해시, 숫자 비교 방법, 전체 일치 여부, 65개 증적 보존과 재현 도구 해시를 추가했다. 이 필드의 첫 구현 해시와 재현 도구 해시는 서로 구분한다.

도구와 실측 파일은 일반 Git 커밋 대상이 아니며 운영 빌드에 등록하지 않았다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

기존 [현재 종목 유형 해석](../basicinfo/kis-stock-basic-info-type-classification.md), KIS·KRX 대조와 유형 보완, 거래 제한 관측, `StockEligibilityPolicy`, 후보 목록·백테스트·주문을 변경하지 않았다. 이 결과로 유형이나 제한 값을 덮어쓰거나 `StockEligibilityInput`, `AS_OF_VERIFIED`, `informationAvailableAt`, 거래 허가를 생성하지 않는다.

`runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. 현재는 입력 대조 계약과 재현 검증이며 순수익 개선이나 모의투자 운용 승인이 아니다.

HTTP Client·Provider·인증·DB·스키마·Spring 빈·라이브러리·스케줄·설정·`.env`·Docker와 서버 상태 변경은 없다. 계좌·주문·OpenAI 호출 및 커밋·Push도 수행하지 않았다.
