# SWING_V1 Strategy Contract

## 1. Purpose

이 문서는 `SWING_V1` deterministic Baseline의 입력, 판단 시점, 진입·청산, 주문 수량, 비용과 검증 규칙을 고정한다.

같은 계약을 운영, 백테스트와 모의투자에서 사용하여 구현마다 전략 의미가 달라지는 것을 막는 것이 목적이다. 이 계약은 수익을 보장하지 않으며, 전략의 가설을 재현 가능하게 검증하기 위한 기준이다.

```text
strategyId: SWING_V1
strategyVersion: 1
horizon: SWING
decisionMode: DETERMINISTIC_BASELINE
contractStatus: ACTIVE_BASELINE
```

AI Provider는 이 Baseline의 진입·청산 방향을 변경하지 않는다. 이후 AI Overlay는 별도 식별자와 버전으로 같은 데이터 및 비용 조건에서 비교한다.

## 2. Current Scope

`SWING_V1`은 다음 범위만 포함한다.

- 완전히 확정된 일봉을 사용하는 추세 추종
- 20일·60일 단순이동평균 교차
- Wilder 방식 14일 ATR
- 평균 매수가 기준 초기 ATR 손절
- 위험 예산과 기존 Risk Guard 상한을 함께 적용한 주문 수량
- 미보유 상태의 신규 진입과 전량 청산

다음 항목은 `SWING_V1`에 포함하지 않는다.

- 보유 수량 추가 매수
- 부분 청산
- 고점 기준 ATR 추적 손절
- 거래량·거래대금 필터
- 최근 고가 돌파 필터
- 시장 지수 추세 필터
- AI가 제안한 방향으로 Baseline 신호 변경

이 기능이 추가되면 기존 결과와 섞지 않고 새로운 `strategyVersion`으로 검증한다.

## 3. Evaluation Time

거래일 `T`의 판단은 `T`일 `09:10 Asia/Seoul` 이후 첫 번째 허용된 Run에서 한 번만 수행한다.

판단에 사용하는 일봉은 직전 거래일 `T-1`까지 완전히 확정되어 로컬 DB에 저장된 데이터로 제한한다. `T`일 장중에 만들어지고 있는 미완성 일봉은 사용하지 않는다.

```text
T-1 20:10 이후
-> T-1 확정 일봉 수집 완료

T 09:10
-> T-1까지의 일봉으로 신호 계산
-> T의 현재가 조회
-> Risk Guard 검증
-> 허용된 경우 지정가 주문 제출
```

최신 저장 일봉의 거래일이 직전 거래일보다 과거라면 신규 BUY를 생성하지 않는다. 현재가가 없거나 신선도 검증에 실패하면 주문하지 않고 Run 실패로 기록하여 운영 장애가 단순 HOLD로 숨겨지지 않게 한다.

현재 `application.yml`의 스윙 실행 구간은 `09:10`부터 `09:20`까지다. 운영 Agent는 판단 전에 최신 저장 일봉이 직전 거래일에 확정된 데이터인지 확인한다. 기준일이 다르거나 일봉이 비어 있으면 주문 판단을 진행하지 않고 Run 실패로 기록한다.

## 4. Required Inputs

판단에는 다음 입력이 모두 필요하다.

- `InvestmentStrategyIdentity`
- 직전 거래일까지의 `DailyPriceHistory`
- 현재가로 평가된 현재 전략의 `PortfolioSnapshot`
- 판단 시점의 검증된 `CurrentPriceSnapshot`
- 20일·60일 단순이동평균 분석 결과
- Wilder 14일 ATR 분석 결과
- 기존 활성 Broker 주문 존재 여부
- 적용할 거래비용 모델 식별자와 값

이동평균 분석은 이전 거래일과 현재 기준일의 교차를 판정해야 하므로 최소 61개의 확정 일봉이 필요하다. ATR 14일 계산에는 최소 15개의 일봉이 필요하다. 따라서 통합 분석의 최소 요구 수량은 61개다.

운영 설정은 계산 여유와 이후 검증을 위해 현재처럼 최근 120개 일봉을 조회할 수 있지만, 전략의 최소 데이터 계약은 61개로 본다.

## 5. Decision Priority

판단 순서는 다음과 같이 고정한다.

```text
1. 입력 데이터 및 신선도 검증
2. 보유 포지션 확인
3. 보유 중이면 청산 조건 평가
4. 미보유 중이면 진입 조건 평가
5. 주문 수량과 Risk Guard 상한 계산
6. BUY, SELL 또는 HOLD 확정
```

청산 판단은 신규 진입 판단보다 항상 우선한다. 같은 Run에서 동일 종목을 매도한 뒤 다시 매수하지 않는다.

## 6. Entry Rule

다음 조건을 모두 만족할 때만 BUY 후보가 된다.

- 해당 symbol을 현재 보유하지 않는다.
- 해당 symbol의 활성 Broker 주문이 없다.
- 이동평균 분석과 ATR 분석 상태가 모두 `ANALYZED`다.
- 직전 추세가 `UPTREND`가 아니고 현재 추세가 `UPTREND`다.
- 이동평균 교차 신호가 `GOLDEN_CROSS`다.
- 위험 기반 주문 가능 수량이 1주 이상이다.
- Harness와 Risk Guard의 모든 검증을 통과한다.

이미 `UPTREND`가 지속 중이지만 이번 기준일에 골든크로스가 새로 발생하지 않았다면 추격 매수하지 않고 HOLD한다.

`SWING_V1`은 보유 중인 symbol에 대한 추가 매수를 허용하지 않는다.

## 7. Exit Rule

보유 포지션이 있을 때 다음 순서로 청산 조건을 평가한다.

```text
1. ATR_INITIAL_STOP
2. DEAD_CROSS_EXIT
3. HOLD_POSITION
```

### ATR Initial Stop

초기 ATR 손절 가격은 다음과 같이 계산한다.

```text
stopDistanceKrw = 2.0 * ATR(14)
stopPriceKrw = averageEntryPriceKrw - stopDistanceKrw
```

현재가가 `stopPriceKrw` 이하라면 보유 수량 전부를 SELL한다.

이 기준은 평균 매수가에서 계산하는 고정 초기 손절이다. 보유 후 고점에 따라 상승하는 추적 손절은 고점 상태를 별도로 저장한 이후 새로운 전략 버전에서 추가한다.

계산된 손절 가격이 0원 이하라면 0원으로 보정해서 주문에 사용하지 않고, 현재가와의 비교 기준으로만 사용한다.

### Dead Cross Exit

ATR 손절 조건에 해당하지 않더라도 이동평균 교차 신호가 `DEAD_CROSS`이면 보유 수량 전부를 SELL한다.

ATR 손절과 데드크로스가 동시에 발생하면 `ATR_INITIAL_STOP`을 최종 판단 사유로 기록한다.

## 8. Position Sizing

신규 BUY의 주문당 위험 예산은 전략 총자산의 `0.5%`로 제한한다.

```text
riskBudgetKrw = floor(totalAssetAmountKrw * 0.005)
riskPerShareKrw = 2.0 * ATR(14)
riskBasedQuantity = floor(riskBudgetKrw / riskPerShareKrw)
```

여기서 `totalAssetAmountKrw`는 현금과 보유 포지션의 현재가 평가액을 합한 값이어야 한다. 매입 원가 또는 마지막 체결 금액으로 계산된 값은 위험 예산 기준으로 사용하지 않는다. 현재가 평가를 완료할 수 없으면 신규 BUY를 생성하지 않는다.

최종 BUY 수량은 다음 상한 중 가장 작은 값이다.

- `riskBasedQuantity`
- 현재 현금으로 구매 가능한 수량
- `max-order-ratio`가 허용하는 수량
- `max-position-ratio`가 허용하는 수량
- Broker가 허용하는 수량

```text
finalQuantity = min(all quantity limits)
```

`finalQuantity`가 1보다 작으면 BUY 대신 HOLD한다.

SELL 수량은 현재 보유 수량 전체다. 실제 체결 수량은 주문 체결 조회 결과를 기준으로 포트폴리오에 반영한다.

Risk Guard는 이 계약이 계산한 수량을 늘릴 수 없다. 추가 제한을 적용하거나 주문을 거절하는 것만 허용한다.

## 9. Order Price And Fill

운영 주문은 `09:10` 이후 조회하여 신선도 검증을 통과한 현재가를 `expectedPriceKrw`와 지정가로 사용한다. 주문 체결 여부는 주문 접수 응답이 아니라 Broker 체결 조회 결과로 확정한다.

미체결 주문은 기존 주문 유효시간과 취소 정책을 따른다. 만료 또는 취소된 주문을 체결로 간주하지 않는다.

일봉만 사용하는 초기 백테스트에서는 다음 거래일 시가를 판단 시점 현재가의 대용값으로 사용하고, 매수에는 불리한 방향으로 슬리피지를 더하고 매도에는 불리한 방향으로 슬리피지를 뺀다.

이 방식은 `09:10`의 실제 호가와 미체결 가능성을 정확히 재현하지 못한다. 따라서 초기 백테스트 결과에는 `DAILY_OPEN_FILL_APPROXIMATION`을 기록하고, 해당 결과만으로 실제투자 단계로 승격하지 않는다. KIS 모의투자 Forward Test에서 실제 주문 접수, 미체결, 취소와 체결 가격 차이를 별도로 측정한다.

## 10. Cost Model

성과는 반드시 다음 비용을 적용하기 전과 적용한 후를 모두 기록한다.

- 매수 수수료
- 매도 수수료
- 매도 시 적용되는 세금
- 매수 슬리피지
- 매도 슬리피지

비용률은 코드 상수로 숨기지 않는다. 계좌와 적용 시점에 맞는 설정값으로 관리하고, 백테스트 및 Run 이력에 비용 모델 식별자와 실제 적용값을 스냅샷으로 남긴다.

비용 설정이 없거나 유효하지 않은 실행은 전략 수익 검증 표본으로 사용하지 않는다. 비용률이 바뀌면 과거 결과를 덮어쓰지 않고 새로운 비용 모델 버전으로 재계산한다.

## 11. Decision Reason Codes

최종 판단에는 다음 중 하나의 구조화된 사유 코드를 기록한다.

```text
INSUFFICIENT_DAILY_PRICE_HISTORY
STALE_DAILY_PRICE_HISTORY
CURRENT_PRICE_UNAVAILABLE
CURRENT_PRICE_STALE
ACTIVE_ORDER_EXISTS
GOLDEN_CROSS_ENTRY
POSITION_ALREADY_HELD
NO_ENTRY_SIGNAL
ATR_INITIAL_STOP
DEAD_CROSS_EXIT
HOLD_POSITION
ZERO_ORDER_CAPACITY
RISK_GUARD_REJECTED
```

사람이 읽는 `reason` 문자열만으로 분류하지 않는다. `reasonCode`는 성과 보고서에서 신호 수, 주문 제안 수, Risk 거절 수와 실제 체결 수를 집계하는 기준으로 사용한다.

## 12. Evidence To Persist

각 판단에는 최소한 다음 근거를 남긴다.

- `runId`
- `strategyId`, `strategyVersion`, `horizon`
- 판단 모드와 Provider 식별자
- 판단 시각과 일봉 데이터 기준 거래일
- symbol과 보유 수량
- 평균 매수가와 현재가
- 20일·60일 이동평균 값
- 이전·현재 이동평균 추세와 교차 신호
- ATR 기간과 ATR 값
- ATR 배수, 손절 거리와 손절 가격
- 전략 총자산과 주문당 위험 예산
- 위험 기반 수량, 각 상한 수량과 최종 제안 수량
- 최종 action, reasonCode와 reason
- Risk Guard 결과
- 주문 접수, 체결, 취소와 거절 결과
- 비용 모델 및 체결에 적용된 비용

이 값은 전략 결과가 좋거나 나쁠 때 동일한 규칙으로 저장한다.

## 13. Benchmark And Validation

현재 `005930` 단일 후보 실험의 기본 벤치마크는 같은 기간의 KOSPI로 둔다. 향후 Universe가 KOSDAQ 종목까지 확장되면 시장별 벤치마크 매핑을 별도로 정의한다.

백테스트의 벤치마크 기간은 신호일 구간이 아니라 실제 포트폴리오 평가의 첫날과 마지막 날을 사용한다. 종목별 전체 평가일 목록과 지수 관측일 목록이 일치하지 않으면 하나의 벤치마크 성과로 비교하지 않는다. 현재 포트폴리오는 다음 거래일 시가에 평가하고 지수는 일봉 종가만 저장하므로, 이 벤치마크는 날짜를 맞춘 종가 기반 근사 비교다. 시가 기준의 정확한 비교에는 지수 시가 데이터가 추가로 필요하다.

실험 평가에는 [동일 종목 매수 후 보유 비교](swing-v1-buy-and-hold-comparison.md)를 별도로 포함한다. 최초 자금 100%와 10% 배분을 사전에 고정하고 SWING과 같은 전체 평가일·시가·비용을 사용한다. KOSPI 참고값을 대체하거나 운영 Risk 한도를 완화하지 않는다. 최종 청산비용 보정 수익률과 시가 평가곡선 MDD를 함께 비교하되 동일한 위험 조건이나 수익성 입증으로 해석하지 않는다.

실험 요약의 중앙값 초과수익률은 종목별 청산비용 반영 수익률의 중앙값에서 같은 평가 기간의 지수 수익률을 뺀 값이다. 초과 종목 수는 청산비용 반영 수익률이 지수 수익률보다 엄격히 큰 종목만 센다. 두 값은 위의 시가·종가 차이를 가진 근사 비교이며 위험 조정 알파가 아니다. 종목별 독립 포트폴리오를 요약한 값이므로 실제 다종목 계좌의 수익률로 해석하지 않는다.

`SwingV1BacktestExperimentEvaluationService`는 실험을 한 번 실행한 결과로 요약을 계산하고 두 결과를 함께 반환한다. 현재 이 내부 호출은 HTTP API나 스케줄러에 연결되지 않는다.

저장된 일봉으로 수동 검증하는 절차는 [SWING_V1 수동 백테스트 실행](swing-v1-manual-backtest.md)을 따른다. 수동 실행은 기본적으로 비활성화하며 결과를 DB에 저장하거나 주문하지 않는다.

`SWING_V1` 평가는 최소한 다음 값을 포함한다.

- 비용 차감 전 수익률
- 비용 차감 후 순수익률
- 벤치마크 대비 초과수익률
- MDD와 최대 연속 손실
- 거래당 기대값과 Profit Factor
- 평균 보유 기간과 회전율
- 주문 제안 대비 체결률
- 미체결·취소·Risk 거절 비율

과거 데이터 결과와 KIS 모의투자 Forward Test 결과를 분리해서 보여준다. 두 결과가 모두 사전에 정한 기준을 통과하기 전에는 실제 자금 운용 대상으로 승격하지 않는다.

## 14. Implementation Gap

현재 코드에는 이동평균과 ATR 계산 및 `SwingTechnicalAnalysisResult` 통합까지 구현되어 있다. 이 계약을 운영 흐름에 적용하려면 다음 작업이 추가로 필요하다.

```text
1. SwingActionPolicy와 구조화된 판단 결과
2. 포트폴리오 현재가 평가와 평가 시각 검증
3. ATR 위험 기반 주문 수량 계산
4. InvestmentAgent의 SWING 전략 분기
5. 스윙 Scheduler 실행 구간 변경
6. 판단 근거와 reasonCode 영속화
7. 운영과 백테스트가 공유하는 전략 정책 경계
8. 거래비용 및 일봉 시가 체결 근사 모델
```

각 항목은 별도 커밋으로 구현하고, 기존 이동평균 전용 판단을 한 번에 제거하지 않는다.
