# KIS KRX 종목 식별 연결 검증

## 목적과 범위

KIS 종목 마스터와 KRX 종목기본정보에서 **표준 코드·단축 코드·시장이 정확하고 유일하게 일치하는 원문 행만 연결한다.** [기존 실응답 관측](../../../strategy/swing/validation/stock-eligibility-krx-source-validation-03.md)의 식별 대조를 재사용 가능한 Java 정책으로 구현했다. 두 원천의 유형을 결합하거나 종목 자격 입력을 만드는 단계는 아니다.

단축 코드만 같거나 중복 식별자가 있는 자료에서 임의의 첫 행을 선택하면 다른 종목의 유형을 붙일 수 있다. 정확한 연결과 불일치·미연결을 구분하고 모든 원문을 유지한다. 미연결은 상장폐지·누락·거래 불가를 뜻하지 않는다.

## 패키지와 파일

기준 패키지는 `com.stock.market.stock.master.matching.kiskrx`다. 원천 간 식별 연결은 전략의 자격 판단과 별개이므로 `strategy.universe.eligibility` 아래에 두지 않는다. Spring 빈이 아닌 무상태 일반 클래스이며 새 인터페이스를 만들지 않는다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/market/stock/master/matching/kiskrx/KisKrxStockIdentityMatchingPolicy.java` | 기존 파싱 결과를 받아 전체 입력을 인덱싱하고 정확한 연결·보류를 판단한다. |
| 같은 경로의 `result/KisKrxStockIdentityMatchReasonCode.java` | 정확한 일치와 첫 연결 불가 사유를 표현한다. |
| 같은 경로의 `result/KisKrxStockIdentityMatchResult.java` | 요청 시장·KRX 원문·nullable 대응 KIS 원문·사유를 보존한다. |
| 같은 경로의 `result/KisKrxStockIdentityMatchingResult.java` | 규칙 버전·KIS 배치·시장별 KRX 파싱 입력·전체 KRX 행 결과·시장별 미연결 KIS 원문을 보존한다. |

버전은 `KIS_KRX_STOCK_IDENTITY_MATCH_V1`이다. 테스트는 `src/test/java/com/stock/market/stock/master/matching/kiskrx/KisKrxStockIdentityMatchingPolicyTest.java`에 두며 기존 KIS 고정폭·KRX JSON Fixture를 재사용한다.

## 호출 계약

`match(StockMasterBatchParseResult, Map<KisStockMasterMarket, KrxStockBasicInfoParseResult>)`로 호출한다. KIS는 [기존 배치 파싱](stock-master-batch-parsing.md) 결과를 재사용한다. KIS 파서는 시장 내 중복을, 배치 결과는 두 시장 간 표준 코드·단축 코드 충돌과 수집 해시 연결을 검사하므로 새 정책에서 그 검사를 다시 구현하지 않는다.

KRX Map의 키는 **호출자가 실제 요청 자료에서 전달하는 요청 시장**이다. 기존 `KisStockMasterMarket`의 두 시장 값을 재사용하고 새 시장 enum을 만들지 않는다. 원문 `rawMarket`에서 키를 자동 생성하면 요청 시장 검사가 무의미해지므로 그렇게 호출하지 않는다. 두 시장 키와 non-null 파싱 결과가 모두 필요하며 한 시장 누락·null 인자는 거절한다.

개별 KRX 응답은 빈 행 목록일 수 있다. 이때도 두 시장의 입력 해시는 보존하고 대응 KIS 행을 미연결로 남긴다. 빈 응답을 시장 전체에 종목이 없다는 증거로 사용하지 않는다.

정책은 파일·HTTP·DB를 읽지 않는다. 파싱 결과 안의 원문 SHA-256·파서 버전과 KIS 수집 ID·수집 시각을 그대로 유지한다. KRX 요청 기준일·수집 시각·인증·이용 범위는 파싱 결과만으로 증명되지 않으며 호출자가 실제 수집 증적과 별도로 연결해야 한다.

## 판단 순서

KRX 두 응답 전체의 식별자 건수를 먼저 계산한 뒤 행별로 첫 사유 하나를 반환한다.

| 순서 | 사유 | 조건 |
| --- | --- | --- |
| 1 | `KRX_IDENTIFIER_BLANK` | 표준 코드 또는 단축 코드가 빈 문자열·공백이다. |
| 2 | `KRX_IDENTIFIER_DUPLICATED` | 두 시장 전체에서 표준 코드 또는 단축 코드가 중복된다. |
| 3 | `REQUEST_MARKET_MISMATCH` | 원문 시장이 Map에 전달된 요청 시장과 정확히 같지 않다. 미확인 시장 값·표기 변형도 포함한다. |
| 4 | `SYMBOL_ONLY_MATCH` | 표준 코드 대응 행은 없지만 같은 단축 코드의 KIS 행은 있다. 진단 사유일 뿐 연결하지 않는다. |
| 4 | `STANDARD_CODE_NOT_FOUND` | 표준 코드와 단축 코드 어느 쪽으로도 KIS 대응 행을 찾지 못했다. |
| 5 | `SYMBOL_MISMATCH` | 표준 코드로 찾은 KIS 행과 단축 코드가 다르다. |
| 6 | `MARKET_MISMATCH` | 두 코드가 같지만 KIS 배치의 소속 시장이 KRX 요청 시장과 다르다. |
| 7 | `EXACT_IDENTITY_MATCH` | 중복 없이 두 코드·원문 시장·요청 시장·KIS 소속 시장이 모두 일치한다. |

중복은 처음 발견한 행도 포함해 모두 보류한다. 잘못된 시장이나 다른 문제가 있는 행도 중복 집계에서 조용히 제외하지 않는다. 빈 식별자 행은 첫 사유가 공백이지만 그 행의 다른 nonblank 식별자는 전체 중복 검사에 포함한다.

이름 비교, trim, 대소문자 변경, 숫자 변환과 이름 기반 추정은 하지 않는다. 앞자리 0·영문이 들어간 코드도 문자열 그대로 비교한다. 행별 결과는 모든 KRX 행을 KOSPI→KOSDAQ 순서로, 각 응답 안에서는 원문 행 순서대로 유지한다. 미연결 KIS 행도 시장별 원문 순서를 유지한다. Map의 객체 필드 순서에는 의존하지 않는다.

## 결과의 보존 조건

`matchedKisRecord`는 `EXACT_IDENTITY_MATCH`에서만 non-null이다. 불일치 진단의 대응 자료를 승인된 연결처럼 노출하지 않는다. 진단을 재확인할 수 있도록 전체 양쪽 파싱 입력도 결과 안에 남긴다.

결과 생성자는 행·사유 관계, 코드·원문 시장 일치, 입력의 KIS 소속 시장, KRX 행의 누락·추가·순서·원문 변화, 같은 KIS 행의 중복 연결, 미연결 KIS 행의 누락·추가·순서·원문 변화를 검사한다. 모든 Map·List와 중첩된 미연결 목록은 방어적으로 복사한다.

보존 검사는 불변 record의 전체 값으로 비교한다. 정책 호출 결과는 원래 입력 record 인스턴스를 반환하고, 값이 같은 JSON 복원도 허용한다. 생성자 검사는 입력·출력의 일관성을 확인할 뿐 정책 실행 여부, 요청 시장의 실제 출처, 원문 해시의 진위나 정보 가용 시점을 인증하지 않는다.

## 테스트 결과

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.master.matching.kiskrx.*' --tests 'com.stock.market.stock.master.parsing.*' --tests 'com.stock.market.stock.master.provider.kis.parsing.*' --tests 'com.stock.market.stock.master.provider.krx.parsing.*' --offline --no-daemon
```

2026-10-06 관련 테스트 317개 중 **316개 통과·1개 건너뜀·실패와 오류 0개**로 실행이 성공했다. 새 식별 연결·결과 검증 50개는 모두 통과했다. 기존 KIS 파서·배치 80개 중 79개, KRX 파서·원문·결과 187개가 통과했다. 건너뛴 1개는 기존 배치 테스트의 Windows 심볼릭 링크 생성 권한 부족이며 해당 조건의 검증 완료로 대체하지 않는다. 전체 프로젝트 테스트는 실행하지 않았다.

정상 연결, 영숫자·앞자리 0·공백 보존, 요청 시장·소속 시장 불일치, 코드 불일치, 응답 안·시장 간 중복, 첫 사유 우선순위, 빈 응답, 전체 원문·해시·시각·행 순서 유지, 방어적 복사·반복 호출, 손상 결과 거절과 값 보존 JSON 왕복을 확인했다. 테스트 입력은 기존 Fixture의 합성 자료이며 실제 원천 인증을 대신하지 않는다.

## 보존 실응답 대조

기존 KIS 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`와 KRX의 `2026-10-02` 요청분 두 원문을 재사용했다. **추가 데이터 API 호출 없이** 새 정책 결과를 기존 독립 관측의 행별 연결 및 미연결 목록과 대조했다.

| 항목 | KOSPI | KOSDAQ | 합계 |
| --- | --- | --- | --- |
| KIS 입력 행 | 2,578 | 1,825 | 4,403 |
| KRX 입력 행 | 942 | 1,824 | 2,766 |
| 정확한 식별 연결 | 942 | 1,824 | 2,766 |
| KIS 미연결 | 1,636 | 1 | 1,637 |

모든 KRX 행의 사유가 `EXACT_IDENTITY_MATCH`였고 양쪽 원문 전체가 기존 관측과 일치했다. 미연결 KIS 1,637행의 원문·시장·행 순서도 같았다. 연결과 미연결 건수의 합계는 KIS 전체 입력과 일치한다. 이는 현재 보존 자료의 식별 연결 건수이지 투자 가능 후보나 과거 모집단 검증 건수가 아니다.

KIS 수집 시각 `2026-10-05T09:32:35.795200200Z`~`2026-10-05T09:32:36.202168500Z`와 KRX 요청 기준일 `20261002`를 구분해 보존한다. 식별자가 같아도 두 자료의 같은 시점 상태가 입증되지 않으며 날짜를 맞춰 `informationAvailableAt`·`AS_OF_VERIFIED`를 만들지 않는다.

실행 전후 기존 KRX 관측 보고서와 그 증적 15개, 기존 KIS 증적 7개, 직전 KRX 유형 정책 대조·재현 보고서의 크기·해시를 확인했다. 별도로 기존 KIS·KRX 공개 자료 보존 검사도 수행했다. 기존 관측·정책 파일을 덮어쓰지 않았다.

수동 도구와 전체 결과는 Git 제외 경로 `build/kis-krx-stock-identity-matching-observation-01/`의 `VerifyKisKrxIdentityMatching.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`에 보존한다. 운영 빌드에 연결하지 않았고 이 도구를 실행해야 정책을 사용할 수 있는 구조도 아니다. `gradlew clean`으로 증적이 삭제될 수 있다.

- 최초 보고서 SHA-256: `784e6a25df9e6844fad459b7395b7b8e9270ff0860efc2eebd7d201744436842`
- 재현 보고서 SHA-256: `e44f14f8c8349036180c67cf42cb732d1cfb7c15c607f97f7a02dad288a1ba72`

재현 결과는 검증 시각만 제외하고 전체 JSON 구조가 같았다. 최초 재현 비교는 메모리 JSON의 Java long 노드와 파일에서 읽은 int 노드의 표현 차이로 실패했다. 양쪽을 저장되는 JSON으로 읽어 같은 형식으로 비교하도록 도구를 수정한 뒤 성공했다. 객체 필드 순서나 Java 숫자 노드 클래스가 아니라 실제 JSON 값과 배열 순서를 비교하며, 기존 보고서는 덮어쓰지 않았다.

```powershell
.\gradlew.bat -I build/kis-krx-stock-identity-matching-observation-01/observation.init.gradle verifyKisKrxIdentityMatching '-PobservationReport=verification-replay.json' --offline --no-daemon
```

보고서가 이미 있으면 덮어쓰기를 거절한다. PowerShell에서는 `-P` 인자 전체를 따옴표로 묶는다.

## 연결하지 않은 영역

기존 [KIS 유형 정책](stock-master-type-classification.md)과 [KRX 유형 정책](krx-stock-basic-info-type-classification.md)을 수정·호출하거나 결과를 자동 결합하지 않았다. `StockEligibilityInput`, 상장 상태·거래정지 판정, 후보 선정·백테스트·주문·스케줄에 연결하지 않는다. `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다.

HTTP Client·Provider·DB·스키마·Spring 빈·라이브러리·설정·`.env`·Docker 변경이 없다. Broker 계좌·주문·OpenAI도 호출하지 않았다. 커밋과 Push는 실행하지 않았다.

## 후속 현재 유형 보완

[KIS KRX 현재 종목 유형 보완 결과](kis-krx-stock-type-resolution.md)는 이 정책의 결과를 입력으로 받아 정확한 식별 연결에만 KRX 유형 근거를 붙인다. 식별 연결 정책과 원문·연결 실패·미연결 목록은 변경하지 않고 별도의 참고 유형을 반환한다. 종목 자격·과거 모집단·주문 승인으로 연결하지 않는다.
