# KRX 종목기본정보 현재 유형 해석 정책

## 목적과 적용 범위

[KRX 실응답 관측](../../../strategy/swing/validation/stock-eligibility-krx-source-validation-03.md)의 원문 값에 제한적인 Java 해석 규칙을 적용한다. **KOSPI·KOSDAQ의 정확한 `주권 + 보통주`를 `COMMON_STOCK`, `주권 + 구형우선주 또는 신형우선주`를 `PREFERRED_STOCK`으로 해석한다.** 그 외에는 유형을 null로 보존하고 enum 사유를 반환한다.

원문 파서의 구조 검사, 주식 유형 해석, 특정 시점의 종목 자격 판정은 서로 다른 단계다. 이 정책은 유형만 해석하며 `StockEligibilityInput`, `ELIGIBLE`, `AS_OF_VERIFIED`를 생성하지 않는다. 상장 상태·거래정지·정보 가용 시각·과거 모집단·현재 주문 가능성도 검증하지 않는다.

이번 규칙은 프로젝트의 지원 범위다. 관측된 값 목록을 공식 API의 전체 허용 집합으로 선언하지 않는다. 두 시장에 같은 명칭의 해석 규칙을 적용하지만, 예를 들어 KOSDAQ의 구형우선주는 이번 실제 응답에서 0행이었다. 해당 조합을 지원하는 코드가 그 조합의 실제 제공을 입증하는 것은 아니다.

## 파일과 호출 계약

패키지는 `com.stock.strategy.universe.eligibility.classification.krx`다. KRX 원문 파서와 분리하며 기존 `StockSecurityType`을 재사용한다. Spring 빈이 아닌 일반 클래스이고 변경 가능한 실행 상태가 없다.

| 파일 | 책임 |
| --- | --- |
| `src/main/java/com/stock/strategy/universe/eligibility/classification/krx/KrxStockBasicInfoTypeClassificationPolicy.java` | `classify(KrxStockBasicInfoRawRecord)`로 원문 한 행의 시장·증권구분·주식종류를 해석한다. |
| 같은 경로의 `result/KrxStockBasicInfoTypeClassificationReasonCode.java` | 해석 성공과 첫 보류 사유를 표현한다. 자격 상태 enum이 아니다. |
| 같은 경로의 `result/KrxStockBasicInfoTypeClassificationResult.java` | 원문, nullable 유형, 사유, 규칙 버전과 근거 참조·SHA-256을 보존한다. |

반환 결과의 `rawRecord`는 전달받은 불변 record 그대로다. 시장·이름·식별자·행 번호·공백·소속부·날짜·숫자 문자열을 다시 만들지 않는다. trim, 대소문자 변경, 숫자 코드 변환과 이름 기반 추정을 하지 않는다. null 인자는 `NullPointerException`으로 거절한다.

결과 생성자는 원문·사유의 non-null, 규칙 버전·근거 참조의 nonblank, 근거 SHA-256의 소문자 64자리 형식을 검사한다. `TYPE_INTERPRETED`일 때만 유형이 non-null이고 보류 사유에는 반드시 null이도록 요구한다. 직접 생성된 결과가 정책 실행이나 근거 파일의 진위를 인증하는 것은 아니다.

## 판단 순서와 보류 사유

첫 사유 하나를 반환하되 다른 값도 원문에 유지한다.

| 순서 | 사유 또는 결과 | 규칙 |
| --- | --- | --- |
| 1 | `MARKET_VALUE_UNVERIFIED` | 원문 시장이 정확한 `KOSPI`·`KOSDAQ`가 아니다. |
| 2 | `SECURITY_GROUP_VALUE_UNVERIFIED` | 이번 관측에서 확인한 증권구분 문자열이 아니다. 공백·새 값·표기 변형을 포함한다. |
| 3 | `SECURITY_GROUP_UNSUPPORTED` | 부동산투자회사·사회간접자본투융자회사·외국주권·주식예탁증권·투자회사 등 관측된 비지원 증권구분이다. |
| 4 | `STOCK_KIND_VALUE_UNVERIFIED` | 주권의 주식종류가 이번 관측에서 확인한 네 문자열 중 하나가 아니다. |
| 5 | `TYPE_INTERPRETED` | 주권의 보통주는 보통주 유형, 구형우선주·신형우선주는 우선주 유형으로 해석한다. |
| 6 | `TYPE_COMBINATION_UNSUPPORTED` | 주권의 `종류주권`은 관측됐지만 기존 유형 enum으로 임의 통합하지 않는다. |

이 사유는 제공자 자료의 오류 판정이 아니라 프로젝트 해석 범위의 제한이다. `OTHER`, 보통주 또는 우선주 기본값으로 미확인 값을 채우지 않는다. ETF·ETN 문자열이 새로 등장해도 이름만으로 기존 KIS 규칙을 적용하지 않는다.

소속부는 유형 해석에 사용하지 않는다. `SPAC(소속부없음)`, `관리종목(소속부없음)`, `투자주의환기종목(소속부없음)` 또는 미확인 소속부가 있어도 주식 유형 자체는 보통주로 해석될 수 있다. **이 결과를 종목 자격 승인으로 사용해서는 안 된다.** 소속부와 추가 자격·거래 조건은 후속 자격 판단에서 별도로 검증해야 한다. 빈 식별자와 중복 식별자도 이 단일 행 유형 정책이 인증하거나 해소하지 않는다.

## 규칙과 근거 메타데이터

- 규칙 버전: `KRX_STOCK_BASIC_INFO_CURRENT_TYPE_V1`
- 근거 참조: `build/stock-eligibility-krx-source-validation-03/final-verification.json`
- 근거 SHA-256: `df0658234036f57e88f297ef3cdf13eca804ea07ff0b944474ba3151a6448711`

이는 규칙을 검토할 때 사용한 고정 관측 근거다. KIS의 Git revision 형식과 구분한다. 정책은 해당 파일을 읽지 않으므로 실행 환경에 `build/` 증적이 없어도 파일 로딩이나 빈 생성 실패가 발생하지 않는다.

이 메타데이터는 전달받은 행의 원문 출처·요청일·해시·정보 가용 시각을 증명하지 않는다. 실제 입력의 연결과 해시는 호출자가 파서 결과·원본 스냅샷으로 별도 검증해야 한다. 수집 날짜를 `informationAvailableAt`이나 과거 `asOfDate`로 복사하지 않는다. 근거 보고서의 당시 `NOT_TYPE_APPROVED` 상태와 원본은 변경하지 않고 이번 정책 적용은 별도 증적으로 남긴다.

## 테스트 결과

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.classification.krx.*' --tests 'com.stock.strategy.universe.eligibility.classification.kis.*' --tests 'com.stock.market.stock.master.provider.krx.parsing.*' --offline --no-daemon
```

2026-10-06 관련 테스트 336개가 모두 통과했다. 새 KRX 정책 51개·결과 record 32개, 기존 KIS 정책·결과 record 66개, 기존 KRX 파서·원문·결과 record 187개다. 실패·오류·건너뜀 0개이며 전체 프로젝트 테스트는 실행하지 않았다.

지원 조합, 정확한 문자열 비교, 비지원 그룹·종류, 첫 사유 우선순위, 앞자리 0·영문 코드·공백·소속부·날짜 보존, 파서 연결과 중복 행 유지, 반복 호출의 무상태성, 결과·사유 일관성과 근거 형식을 확인했다. 기존 원문 파싱 Fixture를 재사용하며 외부 HTTP 의존성을 추가하지 않았다.

## 보존 실응답 대조

2026-10-02를 기준일로 요청했던 기존 두 응답을 재사용했다. 이번 작업에서 외부 데이터 API를 호출하거나 원문을 다시 수집하지 않았다. Java 정책을 호출하지 않는 독립 원문 집계로 기대 건수를 먼저 생성하고 정책 결과와 비교했다.

| 분류 | KOSPI | KOSDAQ | 합계 |
| --- | --- | --- | --- |
| 전체 입력 | 942 | 1,824 | 2,766 |
| `COMMON_STOCK` | 803 | 1,801 | 2,604 |
| `PREFERRED_STOCK` | 99 | 2 | 101 |
| 유형 null | 40 | 21 | 61 |
| 사유 `SECURITY_GROUP_UNSUPPORTED` | 28 | 20 | 48 |
| 사유 `TYPE_COMBINATION_UNSUPPORTED` | 12 | 1 | 13 |

해석 성공은 2,705행, 보류는 61행이며 합계가 전체 입력과 같다. 이 건수는 투자 가능 후보 수가 아니다. 보류 행도 원문·사유·규칙 메타데이터와 함께 결과 목록에 유지했다. 합성 테스트에서 검사한 미관측 시장·그룹·종류 사유는 이번 고정 실응답에서 0행이다.

입력 SHA-256은 기존 KOSPI `7914a16e95eb7c1d5002b6e3acb3fa994ddd2f9766c7c59b1542853fad987a84`, KOSDAQ `9d84b2e52eb9d83392325e4d3319853fa18848043940bcead74998633d4389e0`와 일치한다. 기존 관측 보고서와 연결된 증적 15개도 검사 전후 그대로였다. 기존 KIS 7개·KRX 공개 자료 7개의 크기·해시도 확인했다.

같은 입력으로 두 번 실행했고 검증 시각을 제외한 전체 JSON 결과가 구조적으로 동일했다. JSON 객체의 필드 순서는 비교 기준으로 삼지 않는다. 기존 결과를 덮어쓰지 않고 재현 결과를 별도 파일로 생성했다.

Git 제외 경로 `build/krx-stock-basic-info-type-classification-observation-01/`에 수동 검증용 Java 도구·Gradle init 파일·`expected-counts.json`·`verification.json`·`verification-replay.json`을 보관한다. 이 도구는 운영 빌드 설정에 연결하지 않았으며 일반 Git 커밋에 포함되지 않는다. `gradlew clean`으로 삭제될 수 있으므로 관측 증적을 유지할 동안에는 실행하지 않는다.

- 최초 정책 대조 보고서 SHA-256: `83b3776423c8a881364952cc8b2e9f1479537d6099510589c4dd6d78e95c6206`
- 재현 보고서 SHA-256: `139f6f23a4f023e98d0060d1318428c7bdd25fa4d475872cfe32b293578bdf45`

수동 재현 실행은 다음과 같다. 보고서가 이미 있으면 덮어쓰기를 거절한다.

```powershell
.\gradlew.bat -I build/krx-stock-basic-info-type-classification-observation-01/observation.init.gradle verifyKrxTypeObservation '-PobservationReport=verification-replay.json' --offline --no-daemon
```

재현 검증의 최초 시도는 실행 환경의 Gradle 캐시 접근 제한, 다음 시도는 PowerShell의 인자 분리로 검증 시작 전에 실패했다. 기존 실행 환경과 명시적으로 묶은 인자로 재실행해 성공했다. 이 실패는 원문 파싱·정책 실행 실패가 아니며 데이터 API 요청이나 증적 덮어쓰기를 발생시키지 않았다.

## 변경하지 않은 영역

기존 KIS 유형 정책·원문·보류 사유를 덮어쓰거나 두 원천의 유형을 자동 결합하지 않았다. 새로운 KRX 원문 파서·HTTP Client·Provider·DB 스키마·라이브러리·설정·Spring 빈을 추가하지 않았다. Docker·Broker 계좌·주문·OpenAI를 호출하거나 `.env`를 변경하지 않았다.

유형 정책은 운영 후보·백테스트·스케줄·주문에 연결하지 않는다. `DESIGN_ONLY`, `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지한다. 이번 작업의 의미는 제한적인 현재 주식 유형 해석의 구현·검증이며 순수익 개선이나 과거 Universe 검증이 아니다. 커밋과 Push는 실행하지 않았다.

## 후속 식별 연결

[KIS KRX 종목 식별 연결 검증](kis-krx-stock-identity-matching.md)은 기존 파싱 결과의 코드·시장·중복을 검사해 연결과 미연결을 보존한다. 이 유형 정책의 규칙·원문·근거는 변경하지 않으며 유형 자동 결합이나 종목 자격 승인으로 사용하지 않는다.

## 후속 현재 유형 보완

[KIS KRX 현재 종목 유형 보완 결과](kis-krx-stock-type-resolution.md)는 정확히 연결된 KRX 행에만 이 정책을 적용하고 KIS 해석과 별도의 참고 유형을 만든다. 원천 해석·미확정 사유·근거 메타데이터를 보존하며 두 원천의 충돌은 보류한다. 소속부·거래 조건·시점 자격을 승인하거나 운영 후보·주문에 연결하는 단계는 아니다.

## 후속 소속부 표시 해석

[KRX 소속부 기반 현재 종목 제한 사유 해석](krx-stock-section-restriction.md)은 정확한 식별 연결과 기존 유형 보완 결과를 재사용해 KOSDAQ의 세 제한 검토 표시를 기록한다. 유형 정책에 소속부 규칙을 섞지 않으며 원문·유형·근거는 그대로 남긴다. 대상 표시가 관측되지 않아도 종목 자격·거래 가능성을 승인하지 않는다.
