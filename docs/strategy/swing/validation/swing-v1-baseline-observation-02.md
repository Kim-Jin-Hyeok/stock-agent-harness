# SWING_V1 지수 보충 및 확대 백테스트 결과 02

## 판정

2026-10-01 로컬 MySQL에 KOSPI 과거 관측값 489개를 추가한 뒤, 기존 `SWING_V1` 규칙과 비용 가정으로 관측 기간을 확대했다. 평가일 609개에서 매수 4회와 매도 4회가 발생했고, 독립된 두 Java 프로세스의 입력·전체 결과 SHA-256이 각각 일치했다.

예시 비용 반영 순수익은 3,102,649원, 누적 수익률은 **31.02649%**, MDD는 **14.38969185%**였다. 같은 평가일의 KOSPI 누적 수익률은 **149.89173368%**여서 단순 초과수익률은 **-118.86524368%p**다. 완료 거래 4개 중 1개만 이익이었으며 그 한 거래가 전체 순수익을 만들었다.

**보충 수집, 데이터 보존, 실데이터의 체결 근사·비용·포트폴리오 전이와 결과 재현성은 확인했다. 양의 기대수익이나 실제투자 승격 기준을 통과한 결과는 아니다.** 단일 종목, 거래 4개, 검증되지 않은 예시 비용과 시가 체결 근사를 이용한 관측이다. 결과를 본 이후 이 기간은 미사용 검증 구간으로 취급하지 않는다.

[기준 결과 01](swing-v1-baseline-observation-01.md)은 그대로 보존한다. 이번에는 전략을 수정한 것이 아니라 지수 데이터와 관측 기간만 확장했다.

## 사전 확정 조건

| 항목 | 값 |
| --- | --- |
| 실행일 / 시간대 | 2026-10-01 / Asia/Seoul |
| 코드 커밋 | `55a7a549009a6fe8fc4318a597b264755f34ee25` |
| 전략 | `SWING_V1`, version `1`, horizon `SWING` |
| 판단 방식 / 후보 / 벤치마크 | deterministic Baseline / `005930` 한 종목 / KOSPI |
| 조건 확정 시각 | 13:53:38 KST, 결과 평가 전 |
| 신호 기간 | 2024-03-25 ~ 2026-09-23, 양 끝 포함 |
| 실제 평가 기간 | 2024-03-26 ~ 2026-09-28, 609개 평가일 |
| 초기 현금 / 보유 | 10,000,000원 / 없음 |
| 판단 이력 | 신호일 이하의 최근 120개 종목 일봉 |
| 지표 | SMA 20/60, Wilder ATR 14 |
| 주문 위험 예산 / ATR 배수 | 총자산의 0.5% / 2.0 |
| 수량 상한 설정 | `max-order-ratio=0.1`, `max-position-ratio=0.3` |
| 체결 모델 | `DAILY_OPEN_FILL_APPROXIMATION` |
| 런타임 | Java 21.0.12, Spring Boot 3.5.16, MySQL 8.4.11 |

신호 시작일은 저장된 종목 일봉 120개가 처음 확보되는 날짜로 계산했다. 신호일 자체를 포함한 120번째 일봉은 `2024-03-25`, 다음 저장 거래일은 `2024-03-26`이었다. 전략의 최소 요구량은 61개이지만 이번 관측에서는 설정된 120개 판단 이력을 확보하고 시작했다. 종료 신호일은 결과를 보기 전에 기준 결과 01과 같은 `2026-09-23`으로 고정했다. 마지막 판단은 다음 저장 거래일 `2026-09-28`에 수행했다.

기간은 진입 신호나 수익률을 보고 고른 것이 아니다. 초기 현금은 과거 실험의 비교 조건이며 실제 Broker 잔액을 설정값으로 대체한다는 뜻이 아니다.

## KOSPI 보충 수집

기존 `MarketIndexDailyHistoryBackfillRunner`를 별도 프로세스로 실행했다. KIS 모의투자 주소와 모의투자 인증정보를 사용했으며 Broker 주문, AI, 모든 실행·수집 스케줄러와 백테스트 Runner는 비활성화했다. 비밀값은 프로세스 환경변수로 전달했고 문서나 실행 인자에 넣지 않았다.

| 항목 | 결과 |
| --- | --- |
| 요청 / 실제 조회 구간 | 2023-09-25 ~ 2025-09-29 |
| API | 국내주식 업종 기간별 시세, `inquire-daily-indexchartprice` |
| 페이지 한도 / 요청 지연 | 10페이지 / 요청 전 1초 |
| 실제 지수 조회 호출 | 10회, 모두 `SUCCESS`, `MCA00000` |
| 반환 / 신규 저장 | 489개 / 489개, 앞 9페이지 각 50개와 마지막 39개 |
| 실제 반환 시작 / 종료 | 2023-09-25 / 2025-09-29 |
| 수집 결과 | `BACKFILLED` |
| Runner 수집 시작 / 종료 | 13:54:14.798 / 13:54:31.812 KST |
| 지수 조회 HTTP 소요 시간 합 | 4,485ms, 인증·대기·저장 시간 제외 |
| 같은 요청 재실행 | `NO_EARLIER_RANGE`, 반환·저장 0개, 지수 조회 호출 없음 |

수집 절차와 부분 응답의 한계는 [지수 일봉 보충 수집 문서](../../../market/index/history/market-index-daily-history-backfill.md)를 따른다. 이번 반환 구간과 건수가 맞았다는 사실만으로 모든 거래일이 빠짐없이 저장되었다고 단정하지 않는다.

## 데이터와 보존 검증

| 데이터 | 수집 전 | 수집 후 |
| --- | --- | --- |
| `005930` 일봉 | 2023-09-25 ~ 2026-09-28, 729개 | 동일 |
| KOSPI 지수 일봉 | 2025-09-30 ~ 2026-09-30, 242개 | 2023-09-25 ~ 2026-09-30, 731개 |

수집 전 모든 테이블을 PK 순서로 조회해 내용 해시를 기록했고, 기존 지수 행도 따로 저장했다. 수집·재실행·백테스트·최종 확인 후 기존 지수 242개 행의 ID와 모든 필드가 동일했고 다른 8개 테이블의 내용 해시도 수집 전과 같았다. 백테스트 전후에는 지수 테이블까지 포함한 모든 테이블의 내용 해시가 같았다.

| 테이블 | 수집 전 | 최종 확인 |
| --- | ---: | ---: |
| `broker_order` | 0 | 0 |
| `current_price_observation` | 80 | 80 |
| `daily_price_bar` | 729 | 729 |
| `flyway_schema_history` | 3 | 3 |
| `harness_run_entity` | 82 | 82 |
| `harness_step_entity` | 2,075 | 2,075 |
| `market_index_daily_observation` | 242 | 731 |
| `strategy_portfolio` | 3 | 3 |
| `trade_record_entity` | 81 | 81 |

평가 기간의 종목 거래일과 지수 관측일을 양방향 비교했다. 두 목록은 모두 609개이며 한쪽에만 있는 날짜는 0개였다. 종목 일봉 전체에서 비양수 시가·종가·저가, 시가·종가·저가보다 작은 고가, 시가·종가보다 큰 저가와 음수 거래량은 0개였다.

이는 저장 데이터의 내부 정합성과 기존 값 보존 검사다. 두 데이터가 함께 누락된 거래일, 기업행위·수정주가의 정확성 또는 과거 시점에 실제로 제공되던 데이터 버전까지 검증한 것은 아니다.

## 비용 가정과 성과

기준 결과 01과 같은 `EXAMPLE_UNVERIFIED_V1`, version `1`을 유지했다. **실제 계좌에 적용되는 비용으로 검증된 값이 아니다.**

| 비용 | 비율 값 |
| --- | ---: |
| 매수 / 매도 수수료 | 각각 0.00015 |
| 매도 세금 | 0.0018 |
| 매수 / 매도 슬리피지 | 각각 0.001 |

| 지표 | 결과 |
| --- | ---: |
| 초기 / 최종 자산 | 10,000,000원 / 13,102,649원 |
| 비용 전 거래 손익 합 | 3,127,600원 |
| 수수료 / 세금 / 슬리피지 합 | 1,619원 / 12,500원 / 10,832원 |
| 전체 거래비용 / 순손익 | 24,951원 / 3,102,649원 |
| 비용 반영 누적 수익률 | 31.02649% |
| MDD | 2,152,000원 / 14.38969185% |
| MDD 고점 / 저점 | 2026-06-19 / 2026-07-20 |
| 매수 / 매도 / 완료 거래 | 4 / 4 / 4 |
| 승리 / 손실 거래 | 1 / 3 |
| 승률 / 거래당 평균 순손익 | 25% / 775,662.25원 |
| Profit Factor | 18.89848685, 완료 거래 4개에 한정 |
| 종료 보유 / 종료 청산 비용 | 0주 / 0원 |
| KOSPI 시작 / 종료 종가 | 2,757.09 / 6,889.74 |
| KOSPI 누적 수익률 / MDD | 149.89173368% / 38.63043156% |
| 단순 벤치마크 대비 초과수익률 | -118.86524368%p |

종목 포트폴리오는 다음 저장 거래일 시가로 평가하고 벤치마크는 같은 날짜의 지수 종가로 평가한다. 위 비교는 평가 시각, 현금 비중과 단일 종목 운용의 차이를 포함하며 위험 조정 알파나 다종목 계좌 성과로 해석하지 않는다. 수익률은 기간 전체 누적 값이며 연환산 값이 아니다.

## 거래와 판단 진단

아래 매수·매도일은 실제 Broker 체결일이 아니라 백테스트 시가 체결 근사일이다. 4개 진입 사유는 모두 `GOLDEN_CROSS_ENTRY`였다.

| 매수일 | 매도일 | 수량 | 청산 사유 | 비용 전 손익 | 거래비용 | 순손익 |
| --- | --- | ---: | --- | ---: | ---: | ---: |
| 2024-03-27 | 2024-05-27 | 12주 | `ATR_INITIAL_STOP` | -46,800원 | 3,776원 | -50,576원 |
| 2024-07-03 | 2024-08-05 | 12주 | `ATR_INITIAL_STOP` | -67,200원 | 3,863원 | -71,063원 |
| 2025-02-21 | 2025-02-28 | 16주 | `ATR_INITIAL_STOP` | -48,000원 | 3,708원 | -51,708원 |
| 2025-06-11 | 2026-07-23 | 16주 | `DEAD_CROSS_EXIT` | 3,289,600원 | 13,604원 | 3,275,996원 |

```text
stepStatusCounts={HOLD=601, EXECUTED=8}
actionReasonCounts={GOLDEN_CROSS_ENTRY=4, NO_ENTRY_SIGNAL=265,
                    ATR_INITIAL_STOP=3, DEAD_CROSS_EXIT=1, HOLD_POSITION=336}
blockedOrderReasonCounts={}
rejectedTransitionReasonCounts={}
executedBuyCount=4
executedSellCount=4
finalPositionQuantity=0
```

609개 판단에서 교차 신호는 `GOLDEN_CROSS=4`, `DEAD_CROSS=4`, `NONE=601`이었다. 골든크로스는 모두 진입으로 이어졌다. 데드크로스 중 3개는 이미 ATR 손절 후라 매도할 포지션이 없었으며 1개만 청산으로 이어졌다. 신호 수와 실제 거래 수를 같은 것으로 세지 않는다.

이익 거래 1개의 순손익 3,275,996원이 손실 거래 3개의 합 173,347원을 상쇄했다. 높은 Profit Factor만 보고 반복 가능한 수익 구조로 판단하지 않는다.

## 재현성과 독립 검산

기존 `SwingV1BacktestExperimentEvaluationService`를 서로 다른 Spring ApplicationContext와 JVM에서 호출했다. 입력 스냅샷에는 요청·비용 모델, 종료 평가일까지의 종목 일봉 729개와 평가 구간 KOSPI 관측 609개를 포함했다. 결과에는 모든 Step, 판단 근거, 포트폴리오, 체결, 성과와 진단을 포함했으며 실행 시각·소요 시간은 비교 대상에서 분리했다.

| 항목 | 1차 | 2차 |
| --- | --- | --- |
| 평가 시작 시각, KST | 13:55:23.164 | 13:55:53.965 |
| 평가 서비스 소요 시간 | 4,995ms | 5,286ms |
| 입력 SHA-256 | 아래 입력 해시 | 동일 |
| 전체 결과 SHA-256 | 아래 결과 해시 | 동일 |

```text
inputSha256=a31ae2a86be9519fd05e7938fd29afe13f137f64c288b054933136bbb34829a8
resultSha256=26a0b8358e3caa6e492af2a64173ec1cb175795f9ee38f55978555cccbcfe4d3
```

PowerShell의 decimal 계산으로 Java 계산기를 호출하지 않고 다음 항목을 별도 검산했다. 불일치는 0개였다.

- 609개 판단의 현재·이전 SMA 20/60, 교차 신호와 Wilder ATR 14. Java의 계산·출력 반올림 규칙을 적용했다.
- 지표 기준일이 신호일이고 현재가 대용값은 다음 저장 거래일 시가인지 확인했다.
- 8개 체결의 매수 올림·매도 내림 슬리피지, 수수료·세금 올림, 정산금과 현금·수량 전이를 확인했다.
- 모든 Step의 이전 상태 연결, 평균 체결가 보존과 다음 거래일 시가평가 자산을 확인했다.
- 매수 수량이 기록된 위험 기반 수량과 Harness 수량 상한을 넘지 않는지 확인했다.
- 완료 거래 4개의 정산 기준 순손익, 최종 현금, MDD 금액·비율과 KOSPI 수익률·MDD를 검산했다. 비율 비교 허용 오차는 `1e-12`였다.

이 검산은 계산과 실행 연결을 확인한다. 실제 호가, 지정가 미체결, 09:10 가격과 일중 손절 체결의 정확성까지 검증하지 않는다.

## 기존 기간 대조

보충 수집 후 기준 결과 01과 같은 신호 기간 `2025-09-30~2026-09-23`, 초기 현금 1,000만원과 초기 보유 없음으로 대조 실행했다. 239개 판단 모두 `NO_ENTRY_SIGNAL`, 거래 0개와 수익률 0%가 다시 나왔다.

전체 결과를 Jackson `JsonNode.equals`로 비교했으며 객체 필드 순서를 제외한 모든 JSON 값과 배열 순서가 기준 결과 01과 같았다. JSON 객체 필드 출력 순서가 달라 바이트 해시는 아래처럼 다르므로, 이 대조를 바이트 단위 동일성으로 표시하지 않는다.

```text
oldResultSha256=d4ac9fa5c0122013d85a83abb3341f4dda9fe3e1be31fc146886679aa3a202a6
controlResultSha256=d0a981d263bc95da58c00e5a3819b8f1419c1837a3acf1097cb8f711a90f3d32
jsonValuesEquivalent=true
```

확대 실행에서는 2025-06-11 매수한 포지션을 기존 관측 기간까지 보유했다. 기존 기간을 현금만 가진 상태로 새로 시작한 기준 결과 01과 초기 보유 상태가 다르다. 두 누적 수익률의 차이를 전략 개선 효과로 해석하지 않는다.

## 실행 상태와 증거 파일

시작할 때 앱과 MySQL 컨테이너는 모두 중지 상태였다. MySQL만 임시로 시작했으며 최종 DB 보존 확인 후 다시 중지했다. 앱 컨테이너는 시작하지 않았고 이미지, `.env`, 운영 설정과 DB 스키마도 변경하지 않았다. 지수 신규 행 489개는 데이터 볼륨에 남아 있다.

공통 실행은 `local`, 웹 비활성, Flyway 비활성, `VIRTUAL`, Rule-based Agent, OpenAI 비활성과 모든 실행·수집 스케줄러 비활성을 명시했다. 보충 수집 때만 KIS와 보충 Runner를 켜고 DB 쓰기를 허용했다. 사전·사후 확인과 백테스트에서는 KIS·보충 Runner를 끄고 Hikari 연결을 읽기 전용으로 설정했다.

전체 회귀 테스트는 `gradlew test --rerun-tasks`로 별도 수행했으며 1,271개가 통과했다. 실패·오류·건너뛴 테스트는 0개였다. 자동 테스트는 H2를 사용하므로 로컬 MySQL의 결과 데이터와 분리된다.

이번 실행용 임시 도구와 원본 JSON은 Git에서 제외되는 다음 경로에 있다. 기준 결과 01의 임시 파일은 덮어쓰지 않았다.

```text
build/backtest-observation-02/SwingV1Observation02.java
build/backtest-observation-02/run-observation-02.ps1
build/backtest-observation-02/plan.json
build/backtest-observation-02/preflight-database.json
build/backtest-observation-02/collect-01.log
build/backtest-observation-02/collect-01-database.json
build/backtest-observation-02/collect-02.log
build/backtest-observation-02/collect-02-database.json
build/backtest-observation-02/run-01/{input,result,metadata}.json
build/backtest-observation-02/run-02/{input,result,metadata}.json
build/backtest-observation-02/audit-observation-02.ps1
build/backtest-observation-02/audit.json
build/backtest-observation-02/control-01/{input,result,metadata}.json
build/backtest-observation-02/CompareControl.java
build/backtest-observation-02/control-comparison.json
build/backtest-observation-02/postflight-database.json
```

정규 실행 절차는 [수동 백테스트 문서](../swing-v1-manual-backtest.md)를 따른다. 위 신호 기간과 비용 모델을 명시하고 지수 보충 수집은 별도 프로세스로 분리한다. 임시 도구가 남아 있다면, 로컬 MySQL을 시작하고 클래스패스를 준비한 뒤 다음 명령으로 확대 평가와 검산을 다시 수행할 수 있다.

```powershell
.\gradlew.bat -I build\backtest-observation\classpath.init.gradle writeObservationClasspath --console=plain
powershell.exe -NoProfile -File build\backtest-observation-02\run-observation-02.ps1 -Mode evaluate -Label run-01
powershell.exe -NoProfile -File build\backtest-observation-02\run-observation-02.ps1 -Mode evaluate -Label run-02
powershell.exe -NoProfile -File build\backtest-observation-02\audit-observation-02.ps1
```

재실행은 `plan.json`에 고정된 조건과 당시 데이터가 유지된 경우에만 같은 관측으로 비교한다. 재실행 명령은 기존 `run-01`, `run-02` 파일을 덮어쓰므로 증거 보존이 필요하면 먼저 별도 보관한다. `build` 파일은 `gradlew clean`으로 삭제될 수 있으며 이 문서는 원본 데이터·전체 JSON의 장기 보관을 대체하지 않는다.

## 완료 범위와 한계

- KIS 모의투자 실제 조회, 신규 지수 행 저장과 재실행 시 조회 생략을 확인했다.
- 기존 데이터 보존, 609개 평가일 정합성, 독립 실행 재현성과 거래·비용·손익 검산을 완료했다.
- 전략·Risk Rule·비용 모델은 변경하지 않았고 신규 운영 클래스나 테이블을 추가하지 않았다.
- 미사용 기간·다종목·Walk-Forward, 실제 비용·기업행위 원본 검증과 실제 모의 주문·체결·취소 검증은 범위 밖이다.
- 누적 수익이 양수여도 거래 수가 적고 한 거래에 의존하며 단순 KOSPI 비교에서 뒤처졌으므로, 이번 결과만으로 실제 자금 운용을 허용하지 않는다.
