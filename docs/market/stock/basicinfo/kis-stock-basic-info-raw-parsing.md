# KIS 주식기본조회 원문 응답 파서

## 목적과 범위

보존한 `search-stock-info` JSON 바이트에서 성공 응답의 선택 필드를 불변 Java 레코드로 추출한다. **파싱 성공은 JSON 구조와 필수 문자열을 확인했다는 뜻이며, 종목 유형·거래 가능 여부·후보 자격을 승인했다는 뜻이 아니다.** 빈 값·미정의 코드·상품번호·날짜·응답 메시지를 정규화하지 않고 입력 바이트의 SHA-256과 파서 버전을 남긴다.

근거는 [KIS 주식기본조회 실전 응답 검증](../master/validation/kis-stock-basic-info-observation-01.md)의 보존 응답 10건과 해당 검증에서 고정한 공식 규격이다. 실전 전용 조회의 원문 해석을 추가하는 작업이며, 기존 모의투자 서버 설정을 바꾸거나 실전 주문 기능을 연결하지 않는다.

## 패키지와 호출 계약

기준 패키지는 `com.stock.market.stock.basicinfo.provider.kis.parsing`이다. MST 파일을 읽는 `stock.master.provider.kis.parsing`과 분리한다. 두 출처는 구조와 제공 필드가 다르며, API 응답으로 마스터의 제한 정보를 덮어쓰지 않는다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoParser` | `parse(byte[] content)`로 JSON을 검사하고 `KisStockBasicInfoParseResult`를 반환한다. |
| `record.KisStockBasicInfoRawRecord` | 선택한 `output` 문자열 16개를 원문 값 그대로 보존한다. |
| `result.KisStockBasicInfoParseResult` | 입력 SHA-256·파서 버전·`rt_cd`·`msg_cd`·`msg1`·원문 레코드 한 건을 보존한다. |

Spring 어노테이션·새 인터페이스·HTTP·파일·DB 접근 없이 동작한다. 외부 파일 읽기와 응답 수집은 호출자의 책임이다. 테스트는 동일한 `src/test/java/.../parsing/` 경로의 세 클래스에 두고 합성 JSON 헬퍼는 `support/KisStockBasicInfoParsingFixture.java`에 둔다.

입력은 null과 빈 바이트를 거절하며 최대 1 MiB다. 크기 한도는 프로젝트의 자원 보호 기준이며 KIS 공식 API 제한이 아니다. 입력 배열을 복사한 뒤 파싱하고 해시를 계산하며 원래 배열을 수정하지 않는다. 결과는 배열이나 JSON 노드를 참조하지 않는 문자열 레코드다.

현재 파서 버전은 `KIS_STOCK_BASIC_INFO_RAW_V1`이다. 이는 프로젝트의 필드 추출 계약 버전이며 KIS 공식 규격 버전이 아니다. 입력 해시에는 메시지 패딩·공백·JSON escape·선택하지 않은 필드까지 원래 바이트 전체가 포함된다. 해시만으로 원천 진위나 관측 시점이 인증되지는 않는다.

## 필드 계약

루트는 JSON 객체이며 `rt_cd`, `msg_cd`, `msg1`은 반드시 문자열이어야 한다. `rt_cd`는 정확히 `"0"`인 경우만 성공으로 읽는다. 숫자 `0`, `"00"`, `"0 "`를 성공으로 보정하지 않는다. `msg_cd`와 `msg1`은 빈 문자열이나 공백도 그대로 보존한다.

`output`은 배열이 아닌 **단일 객체**여야 한다. 다음 16개 필드가 모두 존재하고 문자열이어야 하며, 빈 문자열과 미정의 값은 허용한다.

| JSON 필드 | 레코드 필드 | 보존 범위 |
| --- | --- | --- |
| `pdno` | `productNumber` | 원문 상품번호. 단축 종목코드로 변환하지 않는다. |
| `std_pdno` | `standardCode` | 원문 표준코드. 다른 출처와 자동 연결하지 않는다. |
| `prdt_name` | `name` | 원문 상품명. 마스터 표시명이나 약어로 대체하지 않는다. |
| `prdt_type_cd` | `rawProductType` | 원문 상품 유형 코드. 투자 후보 유형으로 확정하지 않는다. |
| `mket_id_cd` | `rawMarket` | 원문 시장 코드. |
| `scty_grp_id_cd` | `rawSecurityGroup` | 원문 증권 그룹 코드. |
| `stck_kind_cd` | `rawStockKind` | 원문 주식종류 코드. 빈 값을 `101` 또는 `000`으로 채우지 않는다. |
| `tr_stop_yn` | `rawSuspension` | 원문 거래정지 값. |
| `admn_item_yn` | `rawManagement` | 원문 관리종목 값. |
| `scts_mket_lstg_dt` | `rawKospiListingDate` | 원문 유가증권시장 상장일 문자열. |
| `scts_mket_lstg_abol_dt` | `rawKospiDelistingDate` | 원문 유가증권시장 상장폐지일 문자열. |
| `kosdaq_mket_lstg_dt` | `rawKosdaqListingDate` | 원문 코스닥 상장일 문자열. |
| `kosdaq_mket_lstg_abol_dt` | `rawKosdaqDelistingDate` | 원문 코스닥 상장폐지일 문자열. |
| `lstg_abol_dt` | `rawDelistingDate` | 원문 상장폐지일 문자열. |
| `nxt_tr_stop_yn` | `rawNxtSuspension` | 원문 NXT 정지 값. KRX 거래정지 값과 합치지 않는다. |
| `cptt_trad_tr_psbl_yn` | `rawCompetitiveTradingPermission` | 원문 경쟁매매거래가능 값. 주문 허가를 생성하지 않는다. |

선택하지 않은 루트·`output` 필드가 추가돼도 이 계약을 만족하면 허용한다. 다만 선택 필드가 누락되면 비슷한 이름의 새 필드나 기본값으로 대체하지 않는다. 결과 레코드는 전체 API 응답의 복제본이 아니므로 선택하지 않은 필드를 재현하려면 원본 바이트를 별도로 보존해야 한다.

레코드 생성자는 16개 문자열의 nonnull을 검사하며 문자열 길이·코드 정의·날짜를 해석하지 않는다. 결과 생성자는 소문자 64자리 SHA-256, nonblank 파서 버전, 정확한 성공 코드 `"0"`, nonnull 메시지와 레코드를 검사한다.

## 실패와 원문 보존

잘못된 JSON·객체가 아닌 루트·누락 또는 배열 형태의 `output`·필수 문자열 누락·null·숫자·boolean·배열·객체 자료형을 거절한다. JSON 키 중복은 escape로 표현한 동일 키와 선택하지 않은 중첩 필드에서도 거절한다. 정상 JSON 뒤의 추가 객체·값·문자·주석도 허용하지 않는다.

구조나 성공 코드가 맞지 않으면 `IllegalArgumentException`으로 중단하며 부분 결과나 정상 기본값을 반환하지 않는다. null 입력은 `NullPointerException`이다. 오류 메시지는 위치와 고정 필드명을 표시할 수 있지만 원문 값·API 오류 메시지·Jackson 예외 원문과 cause를 노출하지 않는다. 성공 결과에 보존한 `message`를 로그에 출력하는지는 호출자가 별도로 통제해야 한다.

반대로 공백·미정의 코드·유효하지 않은 날짜 문자열은 구조가 맞으면 그대로 반환한다. `pdno=00000A0004Y0`를 `0004Y0`로 줄이거나, 표준코드와 요청 코드가 맞는지 이 파서 안에서 판단하지 않는다. 요청 종목·HTTP 상태·수집 시각은 입력에 없으며 결과에 만들어 넣지 않는다.

## 관련 테스트

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.provider.kis.parsing.*' --tests 'com.stock.market.stock.master.provider.krx.parsing.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **512개가 모두 통과**했다. 새 파서 222개·원문 레코드 81개·결과 객체 22개, 기존 KRX 파서 관련 187개를 포함한 6개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 보고서의 클래스 목록과 모든 XML suite 이름을 대조해 긴 클래스명의 축약 XML도 집계했다.

테스트는 16개 원문 필드와 응답 메시지, 앞자리 0·영문자를 포함한 상품번호, 공백·미정의 문자열·빈 날짜, 입력 해시와 반복 호출, 입력 배열 변경으로부터의 독립성을 확인한다. 필수 항목별 누락·잘못된 자료형, 실패 코드·중복 키·추가 토큰·잘못된 UTF-8·입력 크기 경계도 확인한다. 합성 SPAC·거래정지 사례는 해석으로 값이 바뀌지 않는지 검사하며 자격 정책을 테스트하는 것은 아니다.

테스트 Fixture는 합성 데이터이며 로컬 보존 파일·Spring·DB·외부 서비스를 요구하지 않는다. API 원문 10건과의 대조는 별도 오프라인 검증으로 구분한다.

## 보존 응답 대조

기존 실전 관측의 `build/kis-stock-eligibility-source-validation-02/*-response.bin` 10건을 읽고 모든 선택 필드·응답 코드·메시지·입력 해시를 대조했다. 원문 JSON의 문자열과 새 파서 결과가 정확히 일치했고, 기존 PowerShell 검증과 겹치는 선택 필드도 같았다.

| 검증 항목 | 결과 |
| --- | --- |
| 성공 응답 파싱 | 10건 |
| 선택한 `output` 문자열 대조 | 160개 일치 |
| `rt_cd`·`msg_cd`·`msg1` 대조 | 30개 일치 |
| 기존 검증과 공통인 선택 필드 | 140개 일치 |
| 주식종류 빈 값 | `069500`·`067770` 두 건 그대로 보존 |
| 원문·메타데이터·기존 도구·보고서 등 기존 증적 | 37개 SHA-256 무변경 |
| 전체 결과 객체 JSON 왕복 | 10건 일치 |
| 최초·재현 보고서 전체 JSON 비교 | `verifiedAt`만 제외하고 일치 |
| 이번 검증의 외부 API·토큰 요청 | 각각 0회 |

예를 들어 삼성전자의 `prdt_name`은 `삼성전자보통주`, SPAC 사례는 `디비금융제14호기업인수목적`이다. 마스터의 표시명이나 기존 관측 표의 짧은 이름으로 덮어쓰지 않는다. 상품번호도 `00000A005930`, `00000A0004Y0` 그대로 보존한다.

이번 검증은 2026-10-06에 이미 수집한 응답을 다시 읽은 것이며 최신 종목 상태를 재조회한 것이 아니다. 이전 실측의 토큰 1회·종목 요청 10회 기록과 이번 오프라인 요청 0회를 혼동하지 않는다. 선택한 10개 사례의 성공으로 전체 시장·코드 조합·과거 모집단·API 응답의 모든 필드를 검증했다고 주장하지 않는다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-parsing-observation-01/`에 `VerifyKisStockBasicInfoParsing.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 보고서는 10건의 전체 파싱 결과, 원문 경로와 SHA-256, 기존 증적 37개의 해시, 현재 구현·검증 도구의 해시를 포함한다. `sourceCaseSymbol`은 기존 관측 사례의 식별자이며 API 상품번호에서 추출한 종목코드가 아니다.

- 최초 보고서 SHA-256: `fb8601be043db4b251d5012c0cdd6c6ed2ed22adb351d31c2558889ae7df6312`
- 재현 보고서 SHA-256: `db899b6e83c759bd68c7c816a4215dee94a4b0bd542967d60a9e8d9b8a5bda85`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-parsing-observation-01/observation.init.gradle verifyKisStockBasicInfoParsing --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-parsing-observation-01/observation.init.gradle verifyKisStockBasicInfoParsing '-PobservationReport=verification-replay.json' --offline --no-daemon
```

두 검증은 성공했다. 위 명령은 완료된 실행의 기록이며, 이미 존재하는 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 도구는 운영 빌드에 등록하지 않았고 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

`ST/101`로 SPAC을 일반 보통주 후보로 승인하지 않고, 빈 폐지일을 상장 중으로 변환하지 않는다. NXT 정지의 `N`을 KRX 거래 가능으로 바꾸지 않는다. 이 API에 없는 SPAC·투자주의환기·정리매매 전용 필드를 이름이나 주식종류로 추정하지 않는다.

기존 마스터·KRX 파서, 식별 연결·유형 및 제한 정책·`StockEligibilityInput`·`StockEligibilityPolicy`는 변경하지 않았다. `AS_OF_VERIFIED`, `informationAvailableAt`, 상장 상태, 후보 목록이나 주문 허가를 생성하지 않는다. `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다.

HTTP Client·인증·Provider·수집 스케줄·DB·스키마·Spring 빈·의존성·설정·`.env`·Docker는 이번 변경에 포함하지 않는다. 서버 시작·정지·재배포, 계좌·주문·OpenAI 호출과 커밋·Push도 실행하지 않았다.
