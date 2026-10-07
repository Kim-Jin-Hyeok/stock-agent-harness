# 시장별 투자주의환기 비적용 판정 검증

2026-10-07, [시장별 적용 근거](kis-investment-caution-applicability-validation-01.md)를 확인한 코스피 보통주에 한해 제한 점검 V2의 비적용 설명을 추가했다. **원문 미제공과 기존 진단을 유지하면서 이 한 항목만 차단 사유에서 분리한다.** V1 결과와 보존 보고서는 변경하지 않는다.

현재 기본 버전은 `KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V2`이며 명시한 V1 평가와 V1 JSON 복원도 지원한다. 세부 조건·고정 입력 버전·규격 근거는 [제한 사전 점검 계약](../kis-stock-basic-info-restriction-screening.md#v2-비적용-범위와-근거)에 기록한다. 규정·입력 버전의 변경을 자동 승인하거나 과거 자격과 현재 거래 가능성을 인증하지 않는다.

## 변경 범위

Java 변경은 `com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening`과 `screening.result`의 기존 네 클래스에 한정한다. 결과의 네 필드는 그대로 유지한다. V2는 `MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED` 뒤에 `MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE`을 기록하며, 생성자는 명시된 버전으로 전체 사유·상태를 다시 검사한다. 미지원 버전, 근거 없는 비적용 주입과 진단 삭제는 거절한다.

원천 파서·제한 관측·유형 판단, 분석 서비스·실행기 운영 코드와 호출 계약, 설정·스키마·DB·Docker는 변경하지 않는다. 저장된 분석 경로는 전달받은 현재 기본 정책을 사용하므로 새 분석 결과의 점검 버전은 V2다. 기존 V1 관측 분석 결과를 수정하거나 MySQL 분석 실행기를 재실행하지 않는다.

## 관련 테스트

20개 클래스·656개 테스트가 통과했고 실패·오류·건너뜀은 0개다. 최신 HTML 클래스 목록과 해당 실행의 XML suite 이름·시각을 대조했다. 이전 실행의 XML도 같은 폴더에 남아 있으므로 모든 파일의 단순 합계나 `TEST-*.xml`만의 합계를 사용하지 않는다. 전체 프로젝트 테스트는 실행하지 않았다.

신규 `KisStockBasicInfoRestrictionScreeningApplicabilityTest`의 30개 테스트는 원문 null·미제공 유지, 다른 여섯 필드의 Y·미확인 보존, 코스닥 해석 유지와 비적용 범위 밖의 유형을 확인한다. 11종의 버전·규격·근거 변경은 검토 필요로 남으며, 해당 관측에 이전 비적용 결과를 붙인 JSON은 거절한다. V1 원형 복원, 판정이 달라지는 결과의 버전명만 변경, 미지원 버전 거절과 저장된 분석·실행기 H2 회귀 검증도 포함한다.

첫 실행의 두 실패는 재사용한 ETF·유형 충돌 합성 입력의 기존 제한 Y를 무시하고 검토 필요를 기대한 새 테스트 때문이었다. V1의 제외 신호와 사유가 그대로 유지되는지 비교하도록 기대값을 수정했고, 같은 관련 테스트를 재실행해 모두 통과했다.

```powershell
.\gradlew.bat test --tests 'com.stock.strategy.universe.eligibility.restriction.*' --tests 'com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.*' --tests 'com.stock.market.stock.basicinfo.observation.analysis.*' --offline --no-daemon
```

## 보존 응답 비교

실제 보존된 마스터 4,403행과 API 응답 10건을 읽었다. V1 JSON의 전체 배치를 결합해 복원한 뒤 같은 입력을 V1으로 명시해 다시 평가했고, 10건 모두 당시 결과 전체와 같았다. V2는 종목별 기대 상태·사유·버전을 별도로 고정해 전체 결과를 비교했다. 원천 관측·해석·수집 메타데이터는 동일하다.

| 종목 | V1 | V2 | 비적용 설명과 다른 사유 |
| --- | --- | --- | --- |
| `005930` | 검토 필요 | 신호 없음 | 코스피 보통주 비적용, 나머지 여섯 제한 N |
| `000250` | 신호 없음 | 신호 없음 | 코스닥 일곱 제한 N, 비적용 없음 |
| `005935`, `000087` | 검토 필요 | 검토 필요 | 우선주는 이번 비적용 범위 밖 |
| `069500` | 검토 필요 | 검토 필요 | ETF는 이번 비적용 범위 밖 |
| `000040` | 제외 신호 | 제외 신호 | 미제공·비적용 설명과 마스터·API 관리종목 Y 모두 보존 |
| `000300` | 제외 신호 | 제외 신호 | 미제공·비적용 설명과 마스터·API 거래정지 Y 모두 보존 |
| `0004Y0` | 제외 신호 | 제외 신호 | 코스닥 마스터 SPAC Y |
| `007330` | 제외 신호 | 제외 신호 | 코스닥 투자주의환기 Y |
| `067770` | 제외 신호 | 제외 신호 | 코스닥 마스터 거래정지·정리매매·관리종목 Y와 API 거래정지·관리종목 Y |

V1은 제외 5·검토 4·신호 없음 1건, V2는 제외 5·검토 3·신호 없음 2건이다. 바뀐 상태는 `005930` 하나이며, 비적용 설명 추가는 `005930`·`000040`·`000300` 세 건이다. V1 및 V2 전체 결과 20건의 JSON 왕복과 반복 호출이 같았고, 최초·재현 보고서는 검증 시각만 제외한 전체 JSON 값·배열 순서가 같았다.

기존 원문·규격·수집 증적·이전 보고서 83개와 V1 보고서 두 개·V1 검증 도구 두 개, 총 87개의 해시는 실행 전후 같았다. 변경한 운영 코드의 당시 V1 구현 해시는 이전 보고서에 그대로 남겨 비교 대상과 구분했다. HTTP·토큰 요청·DB 연결은 각각 0회이며 Docker와 서버는 기동하지 않았다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-restriction-screening-observation-02/`에 검증 Java·init script·최초·재현 보고서를 보관한다. 보고서 루트의 공통 `masterBatch`를 각 `v1WithoutSharedMasterBatch`와 `v2WithoutSharedMasterBatch`의 `observation.typeResolution.matchingResult.masterBatch`에 결합하면 전체 결과를 복원할 수 있다. 실제 JSON 왕복은 배치를 포함한 전체 결과로 검사했다.

- 최초 보고서 SHA-256: `5e4bb2e868281c2afe52233af60579836b51f4477e533a776b41b7014648b272`
- 재현 보고서 SHA-256: `518c871f437b16a52bd2763c56c5046b272cc6e41aaa64251b259ce696a60371`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-restriction-screening-observation-02/observation.init.gradle verifyKisStockBasicInfoRestrictionScreeningV2 --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-restriction-screening-observation-02/observation.init.gradle verifyKisStockBasicInfoRestrictionScreeningV2 '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료된 실행 기록이다. `CREATE_NEW`로 기존 보고서 덮어쓰기를 거절한다. 도구는 운영 Gradle 작업에 등록하지 않으며 일반 커밋 대상이 아니다. 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 투자 판단 경계

이 비교는 보존 입력의 해석 차이와 재현성을 검증한다. `NO_EXCLUSION_SIGNAL_OBSERVED`는 보통주 자격·상장 상태·신선도·시장경보·NXT 권한·주문 Risk를 승인하지 않는다. 투자주의·투자경고·투자위험과 예고 필드의 새 추출·검증은 이번 범위에 없다.

원천 수집 시각 차이를 없애거나 `AS_OF_VERIFIED`, `informationAvailableAt`과 거래 허가를 생성하지 않는다. 운영 후보·Universe·백테스트·주문 경로에 연결하지 않았으며 계좌·주문·OpenAI API 호출과 커밋·Push도 수행하지 않았다.
