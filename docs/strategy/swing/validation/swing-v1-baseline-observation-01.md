# SWING_V1 저장 데이터 백테스트 기준 결과 01

## 판정

2026-10-01 로컬 MySQL에 저장된 `005930`과 KOSPI 일봉으로 백테스트를 실행했다. 서로 다른 Java 프로세스에서 같은 조건으로 두 번 평가했고, 입력과 전체 결과 JSON의 SHA-256이 각각 일치했다. 기존 수동 Runner 경로에서도 같은 요약과 진단 결과를 확인했다.

평가일 239개 모두 `NO_ENTRY_SIGNAL`로 HOLD했다. 매수, 매도와 종료 보유 수량은 모두 0이다. 기간 내 골든크로스가 없어서 진입하지 않았으며, 수량 제한이나 주문 적용 실패로 거래가 막힌 결과가 아니다.

**실행과 재현성은 확인했지만, 양의 기대수익이나 체결·비용 처리의 실데이터 검증을 통과한 결과는 아니다.** MDD 0%도 계속 현금만 보유한 결과로 해석한다. 이 구간을 확인한 뒤 전략을 수정한다면 같은 구간은 미사용 검증 구간으로 취급하지 않는다.

## 실행 식별 정보

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-10-01, Asia/Seoul |
| 코드 커밋 | `e3369926a21a165841f35bbd3eaea87cfc3474a5` |
| 전략 | `SWING_V1`, version `1`, horizon `SWING` |
| 판단 방식 | deterministic Baseline |
| 후보 종목 | `005930` 한 종목 |
| 벤치마크 | `KOSPI` |
| 신호 기간 | 2025-09-30 ~ 2026-09-23, 양 끝 포함 |
| 실제 평가 기간 | 2025-10-01 ~ 2026-09-28, 239개 평가일 |
| 초기 현금 | 종목당 10,000,000원, 초기 보유 없음 |
| 판단 이력 | 각 신호일 이하의 최근 120개 일봉 |
| 지표 | SMA 20/60, Wilder ATR 14 |
| 주문 위험 예산 | 총자산의 0.5%, ATR 배수 2.0 |
| 수량 상한 설정 | `max-order-ratio=0.1`, `max-position-ratio=0.3` |
| 체결 모델 | `DAILY_OPEN_FILL_APPROXIMATION` |
| 런타임 | Java 21.0.12, Spring Boot 3.5.16, MySQL 8.4.11 |

초기 현금은 과거 실험의 비교 조건이다. 모의투자·실제 운용 계좌의 잔액을 설정값으로 대체한다는 뜻이 아니다.

2026-09-23 신호의 다음 저장 거래일은 2026-09-28이었다. 마지막 저장 일봉인 2026-09-28을 신호 종료일로 쓰지 않아 `NO_NEXT_DAILY_BAR` 없이 구간을 평가했다.

## 데이터 사전 확인

| 데이터 | 저장 시작일 | 저장 종료일 | 저장 건수 |
| --- | --- | --- | ---: |
| `daily_price_bar`, `005930` | 2023-09-25 | 2026-09-28 | 729 |
| `market_index_daily_observation`, `KOSPI` | 2025-09-30 | 2026-09-30 | 242 |

- 첫 신호일 2025-09-30 이하에 종목 일봉 490개가 있었다. 최소 요구량 61개와 설정된 120개 이력을 확보했다.
- 실제 평가 기간의 종목 일봉과 지수 관측일은 양방향으로 일치했다. 종목에만 있는 날짜와 지수에만 있는 날짜는 각각 0개였다.
- 저장된 종목 일봉 전체에서 시가·종가·저가의 비양수, 고가·저가의 시가/종가 범위 위반, 음수 거래량은 0개였다.
- Flyway V1, V2, V3가 이미 성공 상태였다. 검증 실행에서는 Flyway를 비활성화하고 기존 스키마를 validate했다.

날짜 일치는 두 데이터가 함께 누락된 거래일이 없다는 증거까지 제공하지 않는다. 이번 검사는 저장 데이터의 내부 정합성을 확인한 것이며 외부 원본, 기업행위와 과거 시점 데이터 버전을 대조한 검증은 아니다.

## 비용 가정

기존 실행 문서의 예시 모델 `EXAMPLE_UNVERIFIED_V1`, version `1`을 그대로 고정했다. 아래 값은 실제 계좌에 적용되는 수수료·세금으로 검증한 값이 아니다.

| 비용 | 비율 값 | 백분율 |
| --- | ---: | ---: |
| 매수 수수료 | 0.00015 | 0.015% |
| 매도 수수료 | 0.00015 | 0.015% |
| 매도 세금 | 0.0018 | 0.18% |
| 매수 슬리피지 | 0.001 | 0.1% |
| 매도 슬리피지 | 0.001 | 0.1% |

실제 거래와 보유가 없어 이번 결과에 발생 비용과 종료 청산 비용은 모두 0원이었다. 따라서 이 실행만으로 비용 계산, BUY/SELL 포트폴리오 전이 또는 청산의 실데이터 동작을 검증했다고 표시하지 않는다.

## 성과와 거래 진단

| 항목 | 결과 |
| --- | ---: |
| 초기 자산 | 10,000,000원 |
| 최종 시가평가 자산 | 10,000,000원 |
| 종료 청산 비용 추정 | 0원 |
| 종료 청산 비용 반영 자산 | 10,000,000원 |
| 순손익 | 0원 |
| 비용 반영 수익률 | 0% |
| MDD | 0원 / 0% |
| KOSPI 시작 종가 | 3,455.83 |
| KOSPI 종료 종가 | 6,889.74 |
| KOSPI 수익률 | +99.36570954% |
| 벤치마크 대비 초과수익률 | -99.36570954%p |
| KOSPI MDD | 38.63043156% |
| 매수 / 매도 / 완료 거래 | 0 / 0 / 0 |
| 종료 보유 수량 | 0주 |
| 승률 / 거래당 평균 순손익 | 산정 불가, 완료 거래 없음 |
| Profit Factor | `NO_COMPLETED_TRADES`, 값 없음 |

종목 포트폴리오는 다음 저장 거래일 시가로 평가하고 벤치마크는 같은 날짜의 지수 종가로 평가한다. 위 초과수익률은 이 평가 시각 차이를 포함한 근사 비교다. 여러 종목을 동시에 운용하는 계좌의 성과나 위험 조정 알파로 해석하지 않는다.

```text
stepStatusCounts={HOLD=239}
actionReasonCounts={NO_ENTRY_SIGNAL=239}
blockedOrderReasonCounts={}
rejectedTransitionReasonCounts={}
executedBuyCount=0
executedSellCount=0
finalPositionQuantity=0
```

기술 분석은 239개 판단 모두 `ANALYZED`였다. 추세는 `UPTREND=194`, `DOWNTREND=45`였고, 교차 신호는 `NONE=238`, `DEAD_CROSS=1`, `GOLDEN_CROSS=0`이었다.

첫 신호일의 SMA 20은 77,880원, SMA 60은 71,545원이었다. 직전 신호일에도 이미 상승 추세였으므로 최초 판단에서 신규 골든크로스로 보지 않았다. `SWING_V1`은 지속 중인 상승 추세에 추격 진입하지 않는다.

유일한 데드크로스 신호일은 2026-07-22, 판단일은 2026-07-23이었다. 초기 보유가 없고 앞선 진입도 없었으므로 매도할 수량이 없었다. 마지막 신호일의 SMA 20은 261,750원, SMA 60은 262,325원이었다.

이 결과는 이번 종목·기간·현금 시작 조건에서 전략이 진입하지 않았다는 증거다. 전략 전체가 거래할 수 없다거나 수익을 낼 수 없다는 결론으로 확대하지 않는다.

## 재현성과 별도 검산

기존 `SwingV1BacktestExperimentEvaluationService`를 서로 다른 두 Spring ApplicationContext에서 실행했다. 각 결과에는 요청, 모든 Step의 판단 근거, 포트폴리오 상태, 체결 결과, 성과, 벤치마크와 진단을 포함했다. 실행 시각과 소요 시간은 비교 대상에서 분리했다.

| 항목 | 1차 | 2차 |
| --- | --- | --- |
| 시작 시각, KST | 12:53:14 | 12:53:24 |
| 평가 서비스 소요 시간 | 1,716ms | 1,635ms |
| 입력 SHA-256 | 아래 입력 해시 | 동일 |
| 전체 결과 SHA-256 | 아래 결과 해시 | 동일 |

```text
inputSha256=d304994fe1eb436c6b2d37927dade9f050ecc5733238d86753fae6840110c037
resultSha256=d4ac9fa5c0122013d85a83abb3341f4dda9fe3e1be31fc146886679aa3a202a6
```

입력 해시는 저장 종목 일봉 729개와 평가 기간 KOSPI 관측 239개를 정렬해 직렬화한 스냅샷을 대상으로 한다. 결과 JSON은 두 실행에서 바이트 단위로 같았다.

저장 종가를 이용해 PowerShell의 decimal 계산으로 현재·이전 SMA 20/60을 별도 검산했다. 소수 둘째 자리 반올림과 추세 교차 규칙을 적용한 239개 결과가 Java 판단 근거와 모두 일치했다. 각 Step의 지표 기준일이 신호일과 같고, 현재가 대용값이 다음 저장 거래일의 시가인지도 확인했다. 이 검산 범위에서 불일치는 0개였다.

기존 `SwingV1BacktestManualRunner`도 같은 요청으로 12:55:35~12:55:37에 실행했다. 완료 로그의 평가 기간, 239개 관측, 수익률과 진단이 위 결과와 일치했다. 두 재현성 실행과 별개로 수행한 실행 경로 확인이며, 추가 성과 표본으로 세지 않는다.

## DB와 실행 상태

관측 시작 시 Docker 앱과 MySQL은 중지 상태였다. MySQL만 임시로 시작했으며 관측 종료 후 다시 중지했다. 앱 컨테이너는 실행하지 않았고 데이터 볼륨은 유지했다.

검증 프로세스에는 다음 설정을 명시했다.

```text
spring.profiles.active=local
spring.main.web-application-type=none
spring.flyway.enabled=false
spring.datasource.hikari.read-only=true
broker.kis.enabled=false
trade.execution.mode=VIRTUAL
agent.next-action.provider-type=RULE_BASED
agent.decision.moving-average.provider-type=RULE_BASED
agent.provider.ai.openai.enabled=false
harness.scheduler.enabled=false
broker.order.reconciliation.scheduler.enabled=false
broker.order.cancellation.scheduler.enabled=false
market.price.history.collection.bootstrap.enabled=false
market.price.history.collection.scheduler.enabled=false
market.index.history.collection.bootstrap.enabled=false
market.index.history.collection.scheduler.enabled=false
```

직접 서비스 평가 시 수동 Runner는 비활성화했다. 별도 Runner 확인 시에만 `backtest.swing-v1.experiment.manual.enabled=true`와 위 실험 조건을 명시했다. 기본 `application.yml`은 수정하지 않았다.

두 서비스 평가 전후와 수동 Runner 확인 후의 테이블 행 수는 아래와 같이 같았다.

| 테이블 | 실행 전 | 실행 후 |
| --- | ---: | ---: |
| `broker_order` | 0 | 0 |
| `current_price_observation` | 80 | 80 |
| `daily_price_bar` | 729 | 729 |
| `flyway_schema_history` | 3 | 3 |
| `harness_run_entity` | 82 | 82 |
| `harness_step_entity` | 2,075 | 2,075 |
| `market_index_daily_observation` | 242 | 242 |
| `strategy_portfolio` | 3 | 3 |
| `trade_record_entity` | 81 | 81 |

행 수 검사는 기존 행의 내용 변경을 검출하는 검사는 아니다. 별도로 입력 스냅샷 해시가 두 실행에서 같았고, 검증 연결은 읽기 전용으로 설정했다. Broker·AI 연동과 수집·주문 스케줄러는 비활성화했다.

## 재실행과 로컬 증거 파일

정규 실행 절차는 [수동 백테스트 실행 문서](../swing-v1-manual-backtest.md)를 따른다. 해당 명령에서 신호 기간을 `2025-09-30~2026-09-23`으로 바꾸고, 위 초기 현금·비용 모델을 사용한다. 이 실행과 동일하게 기존 DB를 읽으려면 `--spring.flyway.enabled=false`와 `--spring.datasource.hikari.read-only=true`도 지정한다.

이번 관측의 전체 결과를 얻기 위한 임시 도구는 기존 평가 서비스를 호출하며, 운영 코드에는 포함하지 않았다. 프로젝트 루트 기준 로컬 파일은 다음과 같다.

```text
build/backtest-observation/classpath.init.gradle
build/backtest-observation/SwingV1Observation.java
build/backtest-observation/run-observation.ps1
build/backtest-observation/run-01/input.json
build/backtest-observation/run-01/result.json
build/backtest-observation/run-01/metadata.json
build/backtest-observation/run-01.log
build/backtest-observation/run-02/input.json
build/backtest-observation/run-02/result.json
build/backtest-observation/run-02/metadata.json
build/backtest-observation/run-02.log
build/backtest-observation/manual-runner.log
```

임시 도구가 남아 있는 동안에는 다음 명령으로 두 실행의 전체 JSON을 다시 비교할 수 있다. 이 스크립트는 로컬 MySQL이 실행 중이어야 하며, 컨테이너의 DB 접속정보를 로그에 출력하지 않고 사용한다.

```powershell
.\gradlew.bat -I build\backtest-observation\classpath.init.gradle writeObservationClasspath --console=plain
powershell.exe -NoProfile -File build\backtest-observation\run-observation.ps1
powershell.exe -NoProfile -File build\backtest-observation\run-observation.ps1 -ManualRunnerOnly
```

`build` 아래 도구·스냅샷·로그는 Git에서 제외되며 `gradlew clean`으로 제거될 수 있다. 이 문서에 식별 정보와 핵심 수치를 남기지만, 원본 데이터와 전체 JSON의 장기 보관을 대체하지 않는다. 데이터가 갱신되면 기록된 입력 해시와 먼저 비교하고, 다를 경우 새 관측으로 구분한다.

## 완료 범위와 남은 검증

- 저장 데이터 실행, 날짜 정합성, 전체 결과 재현성과 무거래 원인 확인은 완료했다.
- 신규 Java 운영 클래스, DB 테이블과 전략 규칙은 변경하지 않았다.
- 거래가 없어 진입 이후 손절·청산, 비용 반영 체결과 손익 전이는 이번 실데이터 관측에서 검증되지 않았다.
- 실계좌 비용 확인, 여러 종목·시장 구간, 미사용 기간, 실제 09:10 체결 품질과 Forward Test는 이번 관측 범위 밖이다.
- 0% 수익률과 미진입 상태를 근거로 실제 자금 운용 단계로 승격하지 않는다.
