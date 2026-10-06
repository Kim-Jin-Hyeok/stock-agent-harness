# KIS 주식기본조회 현재 종목 유형 해석

## 목적과 범위

[원문 응답 파서](kis-stock-basic-info-raw-parsing.md)의 결과에서 시장·상품 유형·증권 그룹·주식종류 조합을 해석한다. **정확한 `STK/KSQ + 300 + ST + 101`은 `COMMON_STOCK`, 마지막 종류가 `201` 또는 `202`이면 `PREFERRED_STOCK`으로 반환한다.** 그 밖의 조합은 유형을 null로 유지하고 구체적인 사유를 남긴다.

이 결과는 API가 표시한 주식 유형의 해석이다. 보통주 종류가 SPAC·관리종목·거래정지 종목에도 나타날 수 있으므로 투자 후보 승인과 구분한다. 종목 식별 연결·상장 상태·관측 신선도·과거 자격·주문 가능 여부를 인증하지 않으며 후보 목록·백테스트·주문에 연결하지 않는다.

현재 지원 범위는 프로젝트 계약이다. 공식 정의가 있는 다른 코드까지 모두 해석하거나, 같은 필드의 마스터·KRX 표현을 이 API에 그대로 적용하지 않는다. KIS 마스터의 ETP 공백이나 미확정 유형을 이번 결과로 덮어쓰지 않는다.

## 패키지와 호출 계약

패키지는 `com.stock.strategy.universe.eligibility.classification.kis.basicinfo`다. 기존 마스터 정책은 `classification.kis`에 그대로 두며 기존 `StockSecurityType`을 재사용한다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoTypeClassificationPolicy` | `classify(KisStockBasicInfoParseResult)`로 한 응답의 유형을 해석한다. |
| `result.KisStockBasicInfoTypeClassificationResult` | 파싱 입력 전체, nullable 유형, 사유, 해석 버전과 공식 규격의 참조·해시를 보존한다. |
| `result.KisStockBasicInfoTypeClassificationReasonCode` | 해석 성공과 첫 미확인·비지원 사유를 표현한다. 후보 자격 상태가 아니다. |

Spring 빈이 아닌 일반 Java 정책이며 변경 가능한 실행 상태가 없다. 파싱 결과의 `rawRecord`, 원문 입력 SHA-256, 파서 버전, 성공 코드와 메시지를 그대로 보관한다. 상품번호·표준코드·이름·날짜·제한 값에 trim·숫자 변환·기본값 보충을 적용하지 않는다. null 인자는 `NullPointerException`으로 거절한다.

결과 생성자는 파싱 입력과 사유의 nonnull, 해석 버전·규격 참조의 nonblank, 규격 SHA-256의 소문자 64자리 형식을 검사한다. `TYPE_INTERPRETED`일 때만 유형이 nonnull이어야 하고, 그 유형은 보통주 또는 우선주여야 한다. 미확인·비지원 사유에 ETF·OTHER 등의 기본 유형을 넣으면 거절한다. 생성자는 원문 조합을 다시 해석하거나 정책 실행·규격 진위를 인증하지 않는다.

## 해석 순서와 사유

다음 순서에서 처음 해당하는 사유 하나를 반환한다. 나머지 미확인 값도 입력 전체에 남으므로 첫 사유만 보고 다른 필드가 검증됐다고 간주하지 않는다.

| 순서 | 사유 또는 결과 | 조건 |
| --- | --- | --- |
| 1 | `MARKET_VALUE_UNVERIFIED` | 원문 시장 코드가 보존 공식 규격의 정의 목록에 없다. 공백·미정의 값·대소문자 및 공백 변형도 포함한다. |
| 2 | `MARKET_UNSUPPORTED` | 공식 정의는 있지만 지원 시장 `STK`, `KSQ`가 아니다. 예: `KNX`. |
| 3 | `PRODUCT_TYPE_VALUE_UNVERIFIED` | 응답 상품 유형이 정확한 `300`이 아니다. |
| 4 | `SECURITY_GROUP_VALUE_UNVERIFIED` | 증권 그룹이 보존 공식 규격의 정의 목록에 없다. |
| 5 | `SECURITY_GROUP_UNSUPPORTED` | 정의된 그룹이지만 `ST`가 아니다. `EF/EN/FE` 등의 조합도 여기서 보류한다. |
| 6 | `STOCK_KIND_VALUE_UNVERIFIED` | 주권의 종류가 공백·미정의 값·표기 변형이다. |
| 7 | `TYPE_INTERPRETED` | 위 조건을 만족하고 종류가 `101`, `201`, `202`다. |
| 8 | `TYPE_COMBINATION_UNSUPPORTED` | 종류의 정의는 있지만 이번 지원 조합이 아니다. `000`, `203`~`220`, `301`, `401` 등이 해당한다. |

시장 코드는 현재가 요청의 `J/NX/UN`이나 KRX의 `KOSPI/KOSDAQ`와 다르다. 그룹과 종류도 정확한 문자열로 비교한다. `EF/101`을 보통주로, 주권의 빈 종류를 `101`이나 `000`으로 보정하지 않는다. `ST/000`도 보통주나 OTHER로 반환하지 않는다.

공식 규격은 `201`~`220`을 우선주 계열로 설명하지만 이번 버전은 실측에서 확인한 `201`, `202`만 해석한다. 다른 정의된 코드는 오류로 단정하지 않고 비지원 조합으로 남긴다. 두 지원 시장에 같은 규칙을 적용하되, 보존 응답의 우선주 두 건은 모두 `STK`다. `KSQ/201`, `KSQ/202`의 지원과 합성 테스트가 해당 조합의 실측 성공을 뜻하지는 않는다.

`prdt_type_cd` 응답 필드의 공식 설명란은 공백이며 요청 인자 `PRDT_TYPE_CD`에는 `300`·`301`·`302`·`306` 정의가 있다. 정책은 요청 규격을 응답 허용 목록으로 확대하지 않고 실제 응답에서 확인한 정확한 `300`만 지원한다. 다른 응답 상품 유형은 미확인으로 남긴다.

## 규격과 입력 근거

- 해석 버전: `KIS_STOCK_BASIC_INFO_CURRENT_TYPE_V1`
- 공식 규격 참조: `build/kis-stock-eligibility-source-validation-01/apiportal-specification.json`
- 공식 규격 SHA-256: `1fbf349c755a3f54469450e0b7ca89ee3aa3a779e4a33286348cc2e198c0567a`

근거는 [KIS 대체 원천 검증](../master/validation/kis-stock-eligibility-source-validation-01.md)에서 보존한 공식 상세 JSON과 후속 [실전 응답 10건](../master/validation/kis-stock-basic-info-observation-01.md)이다. 규격을 새로 다운로드하거나 Broker를 호출하지 않았다. 해석 버전은 프로젝트 규칙의 버전이며 KIS 공식 규격의 버전이 아니다.

규격 해시와 실제 입력 해시는 서로 다르다. 규격 해시는 코드 정의를 검토한 자료를, `parseResult.inputSha256`은 호출자가 전달한 원문 바이트를 가리킨다. 정책은 규격 파일을 읽지 않으므로 실행 환경에 해당 `build/` 파일이 없어도 동작한다. 입력 출처·HTTP 상태·수집 시각과 진위 검증은 별도의 수집 증적이 필요하다.

## 관련 테스트

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.classification.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.parsing.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **698개가 모두 통과**했다. 새 정책 142개·결과 객체 40개, 기존 유형 해석 관련 191개, 주식기본조회 파서 관련 325개를 포함한 12개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 보고서의 클래스 목록과 모든 XML suite 이름을 대조해 축약된 XML 파일명도 집계했다.

테스트는 지원 조합 6개, 공식 정의된 비지원 시장·그룹·종류, 공백과 표기 변형, 상품 유형 미확인, 첫 사유 우선순위, 이름 기반 추정 금지, 반복 호출의 무상태성과 입력 전체 보존을 확인한다. SPAC·거래정지·관리종목·빈 폐지일·NXT 관련 원문을 유지하고 타입 해석을 자격 판단으로 바꾸지 않는 것도 확인한다. 결과 생성자의 타입·사유·메타데이터 검사와 전체 JSON 왕복을 포함한다.

테스트는 동일 패키지 구조의 `src/test/java/.../classification/kis/basicinfo/` 아래에 두며 `support/KisStockBasicInfoTypeClassificationFixture.java`는 기존 파서 Fixture와 실제 파서를 재사용한다. Spring·DB·네트워크·로컬 보존 파일이 필요하지 않다.

## 보존 응답 대조

기존 파서 검증의 보존 응답 10건을 다시 읽었다. 새 파싱 결과 전체가 기존 결과와 같았고, 별도로 고정한 사례별 기대 유형·사유·규칙 메타데이터와 새 정책 결과 전체가 일치했다.

| 원래 관측 사례 | 유형 | 사유 |
| --- | --- | --- |
| `005930` | `COMMON_STOCK` | `TYPE_INTERPRETED` |
| `000250` | `COMMON_STOCK` | `TYPE_INTERPRETED` |
| `005935` | `PREFERRED_STOCK` | `TYPE_INTERPRETED` |
| `000087` | `PREFERRED_STOCK` | `TYPE_INTERPRETED` |
| `069500` | null | `SECURITY_GROUP_UNSUPPORTED` |
| `0004Y0` | `COMMON_STOCK` | `TYPE_INTERPRETED` |
| `000040` | `COMMON_STOCK` | `TYPE_INTERPRETED` |
| `007330` | `COMMON_STOCK` | `TYPE_INTERPRETED` |
| `000300` | `COMMON_STOCK` | `TYPE_INTERPRETED` |
| `067770` | null | `STOCK_KIND_VALUE_UNVERIFIED` |

보통주 종류 6건·우선주 2건·미확정 2건이며 전체 10건과 합계가 같다. ETF 사례는 그룹이 `EF`라서 비지원 그룹 사유를, 정리매매 원문 사례는 `ST`의 종류가 빈 값이라서 종류 미확인 사유를 반환한다. 이 사유는 해당 종목의 자격 판정이나 Broker 오류를 뜻하지 않는다.

보통주 종류 6건에는 기존 마스터 관측의 SPAC·관리종목·투자주의환기·거래정지 사례가 포함된다. **6건을 투자 가능한 일반 종목 수로 사용하지 않는다.** 특히 `0004Y0`의 SPAC 여부는 API 이름이나 `101`에서 추정한 값이 아니라 이전 마스터 관측의 사례 선택 근거다. 이 API의 미제공 제한 필드를 새로 생성하지 않는다.

전체 결과 10건의 JSON 왕복과 반복 호출 결과가 같았다. 최초·재현 보고서는 `verifiedAt`을 제외한 전체 JSON 값과 배열 순서가 일치했다. 원문·메타데이터·공식 규격·기존 파서와 도구·보고서 등 기존 증적 45개의 SHA-256도 검사 전후 같았다. 외부 API 요청·토큰 발급은 각각 0회다.

이번 대조는 2026-10-06에 수집한 원문을 읽은 재현 검증이며 최신 시장 상태 조회가 아니다. 표본 10건으로 전체 시장·모든 코드 조합·과거 모집단과 주문 가능성을 검증했다고 간주하지 않는다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-type-classification-observation-01/`에 `VerifyKisStockBasicInfoTypeClassification.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 보고서에는 전체 결과 10건, 유형·사유 집계, 기존 증적 45개의 해시와 새 구현·검증 도구의 해시를 남긴다. `sourceCaseSymbol`은 이전 관측의 사례 식별자이며 상품번호에서 추출한 단축코드가 아니다.

- 최초 보고서 SHA-256: `7066bccd8b09282175ba3b7e35c3e8d50438e36422440e44f8b9cdfb25469271`
- 재현 보고서 SHA-256: `2f9c10277a673719048796028815f078af8b5b70c3e55dde559f9bb427c647b5`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-type-classification-observation-01/observation.init.gradle verifyKisStockBasicInfoTypeClassification --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-type-classification-observation-01/observation.init.gradle verifyKisStockBasicInfoTypeClassification '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료된 실행의 기록이다. 최초·재현 검증은 모두 성공했고 이미 존재하는 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 임시 도구는 운영 빌드에 등록하지 않았으며 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

기존 마스터·KRX 파서, 식별 연결, 유형 보완, 제한 관측, `StockEligibilityPolicy`와 후보 목록을 변경하지 않았다. KRX를 필수 운영 출처로 추가하거나 삭제하지 않고, 마스터와 주식기본조회 결과를 자동으로 연결하지 않는다. 유형 해석만으로 `StockEligibilityInput`·`AS_OF_VERIFIED`·`informationAvailableAt`·상장 상태·거래 허가를 생성하지 않는다.

`DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. 유형 해석의 구현과 재현성 검증이며 순수익 개선이나 모의투자 운용 승인으로 해석하지 않는다.

HTTP Client·인증·Provider·스케줄·DB·스키마·Spring 빈·라이브러리·설정·`.env`·Docker 변경과 서버 시작·정지·재배포는 없다. 계좌·주문·OpenAI 호출과 커밋·Push도 수행하지 않았다.
