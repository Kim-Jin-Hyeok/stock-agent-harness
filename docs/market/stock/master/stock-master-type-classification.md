# KIS 종목 마스터 현재 유형 해석 정책

## 목적과 적용 범위

[유형 매핑 근거 재검증](validation/stock-master-type-mapping-validation-01.md)에서 확인한 범위를 Java 정책으로 고정했다. **KOSPI의 `EF / 2 / 0`만 `StockSecurityType.ETF`로 해석하고, 그 밖의 행은 유형을 null로 보존하며 enum 사유를 반환한다.** ETP 공백을 `0`으로 바꾸거나 미확인 행을 보통주·우선주·`OTHER`로 채우지 않는다.

이는 현재 원문 한 행의 제한적인 유형 해석이다. 파서의 구조 검증과 별개이며 종목 자격, 현재 주문 가능성, 과거 시장 소속·상장 상태·정보 가용성을 승인하지 않는다. `StockEligibilityInput`, `ELIGIBLE`, `AS_OF_VERIFIED`를 만들지 않으며 `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다.

프로젝트는 개별주 후보의 근거를 확보하려는 단계다. 이번에 해석한 ETF 1,159행을 투자 후보로 채택한다는 뜻은 아니며, 개별주 자동 분류의 미확인 항목은 여전히 남아 있다. 이 정책의 역할은 확인된 범위와 미확인을 구분하여 추정이 후속 자격 판정에 섞이지 않게 하는 것이다.

## 패키지와 호출 계약

기준 패키지는 `com.stock.strategy.universe.eligibility.classification.kis`다. 원문 형식을 읽는 `market.stock.master.provider.kis.parsing`과 분리하고, Provider별 유형 해석을 Universe 자격 검토 아래에 둔다. 기존 `StockSecurityType`을 재사용한다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/strategy/universe/eligibility/classification/kis/KisStockMasterTypeClassificationPolicy.java` | `classify(KisStockMasterMarket, KisStockMasterRawRecord)`로 원문 한 행을 해석한다. Spring 빈이 아닌 일반 클래스이며 변경 가능한 실행 상태가 없다. |
| 같은 경로의 `result/KisStockMasterTypeClassificationReasonCode.java` | 유형 해석 성공과 보류 사유만 표현한다. 종목 자격 상태 enum이 아니다. |
| 같은 경로의 `result/KisStockMasterTypeClassificationResult.java` | 시장, 원문 record, nullable 유형, 사유, 규칙 버전과 고정한 공식 자료의 revision을 보존한다. |

호출자는 기존 원문 파서 또는 [배치 해석 서비스](stock-master-batch-parsing.md)의 시장 결과에 속한 행을 그 시장과 함께 전달한다. 정책이 배치 파일을 다시 읽거나 rawLine을 재파싱하지는 않는다. 단독 호출에 전달된 시장과 record의 실제 소속, 출처·배치 무결성을 이 정책 자체가 증명하지 않는다.

반환 결과의 `rawRecord`는 전달받은 불변 record 그대로다. 식별자·이름·행 번호·rawLine·미해석 필드를 다시 만들지 않으며 숫자 코드 필터, trim, 대소문자 변경이나 이름 기반 추정을 하지 않는다. null 인자는 `NullPointerException`으로 거절한다.

규칙 버전은 `KIS_STOCK_MASTER_CURRENT_TYPE_V1`, 근거 revision은 `277ec0eb7a9b7f63b6807829286c80f36649dad2`다. 고정 revision은 어떤 규격을 검토했는지 나타낼 뿐 마스터의 적용일이나 제공자의 완전한 조합 인증을 뜻하지 않는다. 해석 범위를 변경할 때는 새 근거와 규칙 버전을 함께 검토해야 한다.

## 보류 사유와 판단 순서

다음 순서로 검사하며 첫 보류 사유 하나만 반환한다. 다른 문제는 버리지 않고 원문에 그대로 남긴다.

| 순서 | 사유 | 판단 |
| --- | --- | --- |
| 1 | `GROUP_VALUE_UNVERIFIED` | 그룹 값이 고정 규격에서 확인한 목록에 없다. 공백·`EN`·`PF` 등을 임의 해석하지 않는다. |
| 2 | `ETP_VALUE_UNVERIFIED` | 해당 시장의 확인된 ETP 값이 아니다. 한 칸 공백·`8`·`9`를 포함하며 KOSDAQ의 `5`도 보류한다. |
| 3 | `PREFERRED_VALUE_UNVERIFIED` | 우선주 값이 `0`·`1`·`2` 중 하나가 아니다. `9`를 기존 값에 합치지 않는다. |
| 4 | `TYPE_INTERPRETED` | 세 값이 정의된 뒤, KOSPI의 정확한 `EF/2/0`이면 ETF를 반환한다. |
| 5 | `TYPE_COMBINATION_UNSUPPORTED` | 각 값은 정의되었지만 프로젝트에서 확인한 조합이 아니다. 기본 유형을 넣지 않는다. |

확인한 그룹 값은 `ST`, `MF`, `RT`, `SC`, `IF`, `DR`, `EW`, `EF`, `SW`, `SR`, `BC`, `FE`, `FS`다. ETP 값은 KOSPI `0`~`5`, KOSDAQ `0`~`4`로 시장별 검사한다. 개별 값의 정의가 전체 조합의 해석 근거를 자동으로 제공하지 않으므로 `ST/0/0`, `FE/2/0` 등도 이번 규칙에서는 보류한다. 이는 KIS에서 불가능한 조합이라고 단정하는 오류 코드가 아니다.

KOSDAQ의 `EF/2/0`에도 KOSPI 규칙을 자동 적용하지 않는다. 날짜·거래정지·정리매매 필드는 그대로 남긴다. 예를 들어 유형 해석이 성공했어도 거래정지 원문이 `Y`인 종목에 주문을 승인할 수 있다는 뜻은 아니다.

결과 record는 `TYPE_INTERPRETED`일 때만 유형이 non-null이고 다른 사유일 때는 반드시 null이도록 검사한다. 버전은 nonblank, revision은 소문자 40자리 Git commit 해시 형식을 요구한다. 이 생성자 검사는 필드 간 일관성만 보장하며 직접 생성된 결과의 원천 신뢰성이나 정책 실행 여부를 인증하지 않는다.

## 관련 테스트

새 테스트의 경로는 `src/test/java/com/stock/strategy/universe/eligibility/classification/kis/`와 그 아래 `result/`다. 기존 고정폭 원문 Fixture와 모의 Client로 만드는 배치 Fixture를 재사용한다. 새 Fixture 계층이나 외부 HTTP 의존성을 추가하지 않았다.

```powershell
.\gradlew.bat test --tests "com.stock.strategy.universe.eligibility.classification.kis.*" --tests "com.stock.market.stock.master.parsing.*" --tests "com.stock.market.stock.master.provider.kis.parsing.*" --offline --no-daemon
```

2026-10-05 관련 테스트 146개 중 **145개 통과, 1개 건너뜀, 실패·오류 0개**로 Gradle 실행이 성공했다. 새 정책 46개·결과 record 20개는 전부 통과했고, 기존 배치·원문 파서 관련 80개 중 79개가 통과했다. 건너뛴 1개는 기존 배치 테스트의 실제 Windows 심볼릭 링크 생성 권한 부족 때문이며 이번 정책의 성공으로 대체하지 않는다. 전체 프로젝트 테스트는 실행하지 않았다.

확인한 항목은 정확한 지원 조합·시장 범위·공백 보존·미정의 값·정의되었지만 미지원인 조합·첫 사유 우선순위, 영숫자 식별자와 원문 보존, 날짜·거래 플래그 비해석, 규칙 메타데이터, 반복 호출과 배치 결과의 수집 계약 유지다. 결과 객체의 null 유형·사유 관계 및 잘못된 버전·revision도 거절되는지 확인했다.

## 실제 보존 배치 대조

대상은 기존 배치 `4ebd57c2-dd69-4b99-86c7-8efed3e531f7`다. 수집 시각은 `2026-10-05T09:32:35.795200200Z`부터 `2026-10-05T09:32:36.202168500Z`까지이며, 이번 정책 대조 시각은 `2026-10-05T11:59:13.426801700Z`다. 대조 시각을 새 수집 시각이나 적용일로 사용하지 않았다.

| 항목 | KOSPI | KOSDAQ | 합계 |
| --- | --- | --- | --- |
| 전체 행 | 2,578 | 1,825 | 4,403 |
| ETF 유형 해석 | 1,159 | 0 | 1,159 |
| 유형 보류 | 1,419 | 1,825 | 3,244 |
| 첫 사유 `GROUP_VALUE_UNVERIFIED` | 370 | 0 | 370 |
| 첫 사유 `ETP_VALUE_UNVERIFIED` | 1,049 | 1,825 | 2,874 |
| 첫 사유 `PREFERRED_VALUE_UNVERIFIED` | 0 | 0 | 0 |
| 첫 사유 `TYPE_COMBINATION_UNSUPPORTED` | 0 | 0 | 0 |

이 건수는 Java 정책을 실행한 결과이며 운영 Universe 선택이나 자격 승인 건수가 아니다. 우선주 원문 `9`인 KOSPI 12행·KOSDAQ 1행은 모두 ETP 공백도 있어 첫 사유가 ETP로 기록된다. 따라서 우선주 사유 0건을 해당 코드가 없거나 의미를 확인했다는 뜻으로 해석하면 안 된다. 사유별 건수는 중복되지 않는 첫 사유 집계다.

전체 행의 시장·원문 record가 유지되고 두 시장의 원문 조합별 건수가 최초 관측 JSON과 같았다. 규칙 버전·근거 revision과 해석 유형을 행마다 대조했다. 실행 전후 기존 7개 파일의 크기·SHA-256, 고정 규격·예제 4개 파일의 크기·SHA-256도 일치했다. 최초 관측 JSON의 SHA-256은 `08f2c7a40a738d99b05df6b0eef215cee109bed3d3307e3882b5ea1402fbea87`, 규격 보존 JSON은 `2261f134ab3c8b1604005d913cb37c86e2b8339bdbbe4867d386d18c2c444314`다.

도구와 결과는 Git 제외 경로 `build/stock-master-type-classification-observation-01/VerifyStockMasterTypeClassification.java` 및 `verification.json`에 남겼다. 기존 배치 해석 서비스를 호출하고 정책을 적용한 로컬 대조이며 자동 실행 기능은 아니다. 외부 요청 0회, 재다운로드·원본 덮어쓰기 없이 완료했다.

## 변경하지 않은 영역

원문 파서와 배치 해석 서비스의 기존 계약은 변경하지 않았다. 새 정책을 Spring 빈·Runner·스케줄러·운영 후보·백테스트·주문 흐름에 연결하지 않았다. 종목 자격 입력 생성, DB 저장·스키마·설정·라이브러리·Docker 변경, Broker 계좌·주문·OpenAI 호출도 없다. 커밋과 Push는 실행하지 않았다.

## 후속 개별주 유형 보완 검토

[KRX 종목기본정보 원천 검증](../../../strategy/swing/validation/stock-eligibility-krx-source-validation-01.md)은 공식 명세에 식별자·시장·증권구분·주식종류 항목이 있음을 확인했다. 실제 유형 값·시점 근거·이용 범위는 미확인으로 남겼고, 현재 정책·KIS 원문·보류 사유를 변경하지 않았다. KRX 보완은 별도 근거를 확보하는 방향이지 ETP 공백을 `0`으로 치환하거나 자격 승인을 우회하는 규칙이 아니다.

[KRX 최소 실측 사전점검](../../../strategy/swing/validation/stock-eligibility-krx-source-validation-02.md)에서도 인증키·승인·이용 범위·기준일이 확인되지 않아 실응답을 수집하지 않았다. 현재 유형 해석 정책과 기존 보류 사유는 유지되며 실측 완료나 유형 보완 승인으로 해석하지 않는다.

후속 [KRX 실응답 관측 및 KIS 식별 대조](../../../strategy/swing/validation/stock-eligibility-krx-source-validation-03.md)에서 `2026-10-02` 기준의 두 시장 응답을 각각 1회 조회했다. 기존 파서로 읽은 KRX 2,766행이 모두 KIS 식별자·시장과 일치했고 KIS 잔여 1,637행은 따로 보존했다. 주식종류·증권구분·소속부 값은 관측 분포로만 기록하며 현재 정책·유형 보완·종목 자격 승인에는 연결하지 않는다.

별도 [KRX 현재 유형 해석 정책](krx-stock-basic-info-type-classification.md)은 주권의 보통주·구형우선주·신형우선주를 제한적으로 해석한다. 기존 KIS 정책의 null 유형·원문·사유는 그대로 유지하며 두 원천의 결과를 자동 결합하지 않는다. 현재 유형 해석이 종목 자격·과거 정보 가용성·주문 승인을 의미하지 않는다.

후속 [KIS KRX 현재 종목 유형 보완 결과](kis-krx-stock-type-resolution.md)는 정확한 식별 연결과 기존 두 정책을 재사용해 별도의 참고 유형을 만든다. KIS 원문·null 유형·보류 사유는 변경하지 않는다. 충돌은 참고 유형을 null로 보류하며 운영 후보·종목 자격·주문 승인을 추가하지 않는다.
