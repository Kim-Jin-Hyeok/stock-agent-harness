# KIS 마스터와 주식기본조회 현재 유형 보완

## 목적과 범위

[표준코드와 시장 대조](../master/kis-stock-basic-info-matching.md)가 성공한 단건에 한해 마스터와 주식기본조회의 유형 해석으로 **별도의 현재 참고 유형**을 만든다. 마스터가 보류한 보통주·우선주 유형을 API 해석으로 보완하되, 원천별 값·유형·사유·규칙 근거를 덮어쓰지 않는다.

대조가 실패하면 참고 유형은 항상 null이다. 양쪽 유형이 확인됐지만 서로 다르면 어느 한쪽을 우선하지 않고 충돌을 남긴다. 참고 유형은 상장 상태·신선도·과거 자격·투자 후보·주문 가능성의 승인이 아니다. ETF 참고 유형을 보존하는 것도 ETF 매매 허용을 뜻하지 않는다.

이 정책은 현재 보존 관측의 유형을 조합한다. 서로 다른 관측 시점을 같게 만들거나 현재 자료를 과거 시점의 근거로 소급하지 않는다.

후속 [마스터와 주식기본조회 제한 관측](kis-stock-basic-info-restriction-observation.md)은 이 결과를 받아 원천별 제한 문자를 독립적으로 기록한다. 대조 실패 시 API 관측은 연결하지 않으며 유형·참고 유형·사유와 후보 자격은 변경하지 않는다.

## 패키지와 호출 계약

패키지는 `com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution`이다. 기존 마스터 정책과 [주식기본조회 유형 정책](kis-stock-basic-info-type-classification.md)을 생성자로 받아 재사용한다. KIS·KRX 보완 정책이나 원천별 지원 조합은 변경하지 않는다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoTypeResolutionPolicy` | `resolve(KisStockBasicInfoMatchResult)`로 한 대조 결과의 참고 유형을 만든다. |
| `result.KisStockBasicInfoTypeResolutionResult` | 보완 버전, 대조 입력 전체, 양쪽 해석, 참고 유형과 사유를 보존한다. |
| `result.KisStockBasicInfoTypeResolutionReasonCode` | 대조 실패·보완·마스터 유지·동의·충돌·미확정을 구분한다. |

보완 버전은 `KIS_STOCK_BASIC_INFO_CURRENT_TYPE_RESOLUTION_V1`이다. 원천의 해석 버전·마스터 규격 revision·API 규격 참조와 SHA-256·파서 버전·입력 해시는 각 입력 안에 그대로 남는다. 보완 버전이 그 근거를 대체하지 않는다.

Spring 빈이 아닌 무상태 일반 Java 정책이다. 파일·HTTP·DB 접근과 배치 순회 실행을 추가하지 않는다. 대조 입력이 전체 배치를 보존하더라도 유형 정책의 호출 대상은 비교한 단건뿐이다. null 의존성·대조 입력·호출된 정책의 null 반환값은 `NullPointerException`으로 거절한다.

## 해석 호출과 참고 유형

요청 마스터 행이 있으면 기존 마스터 정책을 호출한다. 대조 실패에도 해당 해석은 진단 정보로 보존하지만 참고 유형으로 사용하지 않는다. 요청 행이 없으면 마스터 해석도 null이다.

API 정책은 대조 사유가 정확히 `STANDARD_CODE_AND_MARKET_MATCH`일 때만 호출한다. 대조 실패 시 API 해석은 null이며 API 원문은 대조 입력에 그대로 남는다. 성공 후 API 해석 객체가 존재하지만 `securityType`이 null인 경우는 대조 실패와 구분한다.

| 사유 | 조건 | 참고 유형 |
| --- | --- | --- |
| `MATCH_NOT_CONFIRMED` | 표준코드·시장 대조 실패. 마스터 유형이 있어도 적용한다. | null |
| `BASIC_INFO_TYPE_SUPPLEMENTED` | 대조 성공, 마스터 미확정·API 유형 확인 | API 유형 |
| `MASTER_TYPE_ONLY` | 대조 성공, 마스터 유형 확인·API 미확정 | 마스터 유형 |
| `SOURCES_AGREE` | 대조 성공, 양쪽 유형 확인·동일 | 공통 유형 |
| `TYPE_CONFLICT` | 대조 성공, 양쪽 유형 확인·상이 | null |
| `TYPE_UNVERIFIED` | 대조 성공, 양쪽 모두 미확정 | null |

예를 들어 마스터의 ETP 공백 때문에 삼성전자 유형이 미확정이고, 대조 성공 후 API 조합이 보통주로 해석되면 참고 유형은 `COMMON_STOCK`이다. 마스터의 공백·null 유형·`ETP_VALUE_UNVERIFIED` 사유는 바뀌지 않는다.

마스터가 ETF로 해석한 행에 API가 보통주를 반환하면 `TYPE_CONFLICT`로 보류한다. API 유형이 미확정이면 마스터 ETF를 참고값으로 보존하되 그 API 원문과 비지원 사유도 남긴다. 어느 경우도 해당 종목의 매매 허가가 아니다.

현재 실제 마스터 정책은 제한적인 ETF 조합만, API 정책은 보통주·일부 우선주만 해석한다. 지원 유형이 겹치지 않으므로 `SOURCES_AGREE`는 합성 마스터 해석으로 검증했다. 실제 원천 정책의 지원 범위를 확장하거나 실측 동의를 확인한 것이 아니다.

## 결과 객체의 정합성

결과 생성자는 null 대조 입력·사유와 공백 보완 버전을 거절하고 다음 관계를 검사한다.

- 요청 행이 있을 때만 마스터 해석이 존재해야 한다. 시장과 원본 행 전체도 대조 결과와 같아야 한다.
- 대조 성공일 때만 API 해석이 존재해야 한다. 유형이 미확정이어도 해석 객체와 원천 사유는 보존한다.
- API 해석의 파싱 입력 전체가 대조 입력과 같아야 한다. 원본 행이 같아도 입력 해시·파서 버전·응답 메시지가 바뀌면 거절한다.
- 대조 상태·양쪽 유형·참고 유형·보완 사유가 위 판단 표와 일치해야 한다.

값이 같은 JSON 복원은 허용하며 객체 인스턴스 동일성에 의존하지 않는다. 검사는 전달된 값의 정합성이지 원문 진위·HTTP 요청·원천 규칙의 실제 실행·해시 진위·버전 진위의 인증이 아니다.

SPAC·관리종목·거래정지·정리매매·투자주의환기·미제공 필드·상장 및 폐지일·NXT 관련 원문도 입력에 남는다. 이 정책에서 해당 값의 의미를 보충하거나 정상 상태로 변환하지 않는다. 보통주 참고 유형이 있어도 투자 후보 자격을 별도로 확인해야 한다.

## 관련 테스트

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.classification.kis.*' --tests 'com.stock.market.stock.master.matching.kisbasicinfo.*' --tests 'com.stock.market.stock.basicinfo.provider.kis.parsing.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **701개가 모두 통과**했다. 신규 보완 정책 23개·결과 객체 30개, 기존 마스터 유형 해석 66개·API 유형 해석 182개·대조 75개·API 파서 325개를 포함한 11개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 클래스 목록과 XML suite 이름으로 집계했다.

신규 정책 테스트는 양 시장과 영문 포함 코드의 보완, 우선주 두 종류, 마스터 ETF 유지, 원천 충돌·미확정, 모든 대조 실패 시 API 정책 미호출, 단건 호출 인자와 횟수, 원문·근거 보존과 무상태성을 확인한다. 대조 실패 시 확인된 마스터 ETF로 fallback하지 않는 것도 검증한다.

결과 테스트는 여섯 사유의 유형·사유 조합, 다른 행·시장·응답의 주입, 입력 해시·버전·메시지 변경, 필요한 해석 제거 또는 금지된 해석 추가를 거절하는지 확인한다. 모든 사유와 요청 행이 없는 결과의 전체 JSON 왕복을 포함한다. 양 시장의 우선주 조합과 동의 분기의 합성 테스트가 실측 성공이나 원천 인증을 뜻하지는 않는다.

테스트와 `support/KisStockBasicInfoTypeResolutionFixture.java`는 동일한 패키지 구조의 `src/test/java/` 아래에 있다. 기존 마스터·API Fixture와 실제 파서를 재사용하고 수집 메타데이터는 합성한다. Spring·DB·네트워크·보존 실측 파일이 필요하지 않다.

## 보존 응답 대조

기존 보존 응답 10건과 마스터 4,403행을 다시 읽었다. 전체 마스터 배치·단건 대조 결과 10건이 직전 대조 보고서와 같았고, API 해석 전체 10건도 기존 유형 해석 보고서와 같았다. 새 보완 결과는 사례별로 고정한 기대 참고 유형·사유와 전체 입력·해석·버전이 일치했다.

| 보존 요청 종목 | 참고 유형 | 보완 사유 |
| --- | --- | --- |
| `005930`, `000250`, `0004Y0`, `000040`, `007330`, `000300` | `COMMON_STOCK` | `BASIC_INFO_TYPE_SUPPLEMENTED` 6건 |
| `005935`, `000087` | `PREFERRED_STOCK` | `BASIC_INFO_TYPE_SUPPLEMENTED` 2건 |
| `069500` | `ETF` | `MASTER_TYPE_ONLY` 1건 |
| `067770` | null | `TYPE_UNVERIFIED` 1건 |

참고 유형은 보통주 6건·우선주 2건·ETF 1건·미확정 1건이다. 원천별 해석은 마스터 ETF 1건·미확정 9건, API 보통주 6건·우선주 2건·미확정 2건으로 유지됐다. 마스터 원문이나 API의 미확정 결과를 새 참고값으로 덮어쓰지 않았다.

전체 배치를 포함한 결과 10건의 JSON 왕복과 반복 호출 결과가 같았다. 최초·재현 보고서는 `verifiedAt`만 제외한 전체 JSON 값과 배열 순서가 같았으며, 정수·소수의 정밀도를 유지해 JSON 숫자 표현을 통일했다. 기존 원문·수집 증적·규격·파서·정책·도구·보고서 68개의 SHA-256도 검사 전후 같았다. 외부 API 요청·토큰 발급은 각각 0회다.

이 10건은 보존 관측의 재현 검증이지 전시장·최신 상태·과거 모집단·동시점 상태 검증이 아니다. 보통주 6건에는 SPAC·관리종목·투자주의환기·거래정지 사례도 포함되므로 투자 가능한 일반 종목 수로 사용하지 않는다. ETF와 우선주도 참고 유형일 뿐 전략 허용 대상이 아니다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-type-resolution-observation-01/`에 `VerifyKisStockBasicInfoTypeResolution.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 보고서는 공통 마스터 배치를 루트에 한 번 저장하며 각 행의 `resolutionResultWithoutSharedMasterBatch.matchingResult`에 배치를 결합해 전체 결과를 복원할 수 있다. 실제 JSON 왕복 검사는 배치를 포함한 전체 결과로 수행했다.

- 최초 보고서 SHA-256: `6b29f5a4c68591e58402982925104f36cc1f43dbf74b1d90565f37b7a1ccbaab`
- 재현 보고서 SHA-256: `29ff89f537f84827c6f75fa23a328cdb7d7c370a7b8e11c49f599fdb3d9efca3`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-type-resolution-observation-01/observation.init.gradle verifyKisStockBasicInfoTypeResolution --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-type-resolution-observation-01/observation.init.gradle verifyKisStockBasicInfoTypeResolution '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료된 최초·재현 실행의 기록이다. 두 보고서는 `CREATE_NEW`로 덮어쓰기를 거절한다. 기존 원문·보고서·검증 도구를 변경하지 않았고 새 구현과 도구의 해시를 별도로 남긴다. 임시 도구는 운영 빌드에 등록하지 않았으며 일반 Git 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

기존 파서·대조·원천별 유형 정책·KIS·KRX 보완·제한 관측·`StockEligibilityPolicy`는 변경하지 않았다. 참고 유형을 후보 목록·백테스트·주문에 연결하거나 `StockEligibilityInput`, `AS_OF_VERIFIED`, `informationAvailableAt`, 상장 상태와 거래 허가를 생성하지 않는다.

`runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. 현재는 유형 보완 계약과 재현 검증이며 순수익 개선이나 모의투자 운용 승인이 아니다.

HTTP Client·Provider·인증·DB·스키마·Spring 빈·라이브러리·스케줄·설정·`.env`·Docker와 서버 상태 변경은 없다. 계좌·주문·OpenAI 호출 및 커밋·Push도 수행하지 않았다.
