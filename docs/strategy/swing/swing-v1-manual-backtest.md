# SWING_V1 수동 백테스트 실행

## 범위와 안전 조건

`SwingV1BacktestManualRunner`는 `backtest.swing-v1.experiment.manual.enabled=true`인 애플리케이션 시작마다 한 번 실행한다. 기본값은 `false`다. 저장된 종목·지수 일봉을 조회하여 백테스트하고 원본 결과로 요약을 계산한다. 이 Runner는 Broker 주문, 일봉 수집, 결과 DB 저장을 하지 않는다.

실험에 지정한 모든 후보 종목의 일봉과 벤치마크 지수 일봉이 로컬 MySQL에 먼저 있어야 한다. 아래 실행 예시는 `005930`과 KOSPI를 사용한다. 신호 시작일 이전의 전략 판단 이력과 종료 신호일 다음 거래일 일봉도 필요할 수 있다. 데이터가 부족하거나 종목 평가일과 지수 관측일이 다르면 성공한 성과 표본으로 취급하지 않는다.

로컬 전용 프로세스에서 `broker.kis.enabled=false` 및 다른 Scheduler 비활성 상태로 실행한다. 같은 설정을 켜둔 채 프로세스를 재시작하면 다시 실행된다. 한 번 확인한 뒤 `enabled=false`로 되돌린다.

`local` 프로필은 시작할 때 Flyway 마이그레이션을 적용할 수 있다. 운영 DB에 연결하지 말고 로컬 DB 연결 대상과 적용 예정 마이그레이션을 먼저 확인한다.

## 실험 후보 종목

백테스트 후보는 `backtest.swing-v1.experiment.manual.candidate-symbols`로 직접 지정한다. 운영 Harness의 `strategy.universes`와 별개이며, 실험 서비스는 `StrategyStockUniverseRegistry`를 조회하지 않는다. 실험 종목을 늘리기 위해 운영 후보나 주문 대상 목록을 바꿀 필요가 없다.

- 수동 실행이 활성화되어 있으면 후보 목록은 필수다. 누락하거나 비어 있으면 시작에 실패하며 운영 Universe로 대체하지 않는다.
- 비활성 상태에서는 후보 목록 없이도 기동할 수 있다. 기본 설정은 계속 비활성이다.
- null 원소, 공백 종목과 중복 종목은 허용하지 않는다. 종목 코드는 앞자리 0을 포함한 문자열이다.
- 요청의 `candidateSymbols`에는 지정 순서대로 불변 목록이 담긴다. 결과 보고서는 같은 종목과 순서여야 하며 누락, 추가, 대체 또는 순서 변경을 허용하지 않는다.
- 여러 종목은 각각 동일한 초기 현금을 가진 독립 포트폴리오로 실행한다. 현금을 공유하는 다종목 계좌 시뮬레이션이 아니다.

예를 들어 `--backtest.swing-v1.experiment.manual.candidate-symbols=005930,000660`으로 두 종목을 지정할 수 있다. 두 종목의 저장 데이터와 평가일 조건이 모두 충족되어야 하며, 일부 종목이 실패했다고 제외한 채 성공한 결과만 반환하지 않는다. 후보 목록은 결과를 보기 전에 고정하고, 목록이 달라진 실행은 별도의 실험으로 기록한다.

한 실험에는 `benchmark-id` 하나만 적용한다. 시장별 벤치마크 매핑은 아직 자동으로 하지 않으므로 KOSPI와 KOSDAQ 비교는 시장에 맞는 후보 및 벤치마크를 지정한 별도 실험으로 구분한다. 후보 설정은 데이터 수집을 실행하지 않는다.

기존 임시 실행 도구에서 `SwingV1BacktestExperimentRequest`를 직접 생성한다면 생성자의 두 번째 인수에 당시 후보 목록을 추가해야 한다. 이전 스냅샷에 `candidateSymbols`가 없다고 현재 운영 Universe로 보완하거나 원본 관측 결과를 덮어쓰지 않는다. 이번 설정 분리 자체는 새로운 실데이터 성과 관측을 의미하지 않는다.

## 데이터 확인

`docker compose up -d mysql`로 로컬 DB를 시작한 뒤 최소 범위를 확인한다.

```sql
SELECT MIN(trading_date), MAX(trading_date), COUNT(*)
FROM daily_price_bar
WHERE symbol = '005930';

SELECT MIN(observation_date), MAX(observation_date), COUNT(*)
FROM market_index_daily_observation
WHERE benchmark_id = 'KOSPI';
```

이 결과는 양 끝과 건수만 보여주므로 중간 결측이 없다는 증거는 아니다. 실행 중 전체 평가일 목록과 지수 관측일 목록을 비교하는 검증이 별도로 수행된다. 처음에는 선행 일봉이 충분한 짧은 기간을 선택한다. 백테스트는 평가일마다 과거 종목 이력을 조회하므로 장기간 실행은 DB 조회가 많아질 수 있다.

지수 이력이 종목 이력보다 늦게 시작하면 [지수 일봉 과거 구간 보충 수집](../../market/index/history/market-index-daily-history-backfill.md)으로 최초 저장일 앞의 이력을 별도로 확보한다. 기존 최신 구간 수집은 `initial-lookback-years`를 늘려도 과거 이력을 보충하지 않는다. 보충 수집과 백테스트는 별도 프로세스로 실행한다.

## 실행

아래 비용률은 **설정 형식을 보여주는 예시**이며 실제 KIS 계좌의 수수료·세금·슬리피지로 검증된 값이 아니다. 실제 성과 평가 전에는 계좌와 적용 시점에 맞는 값을 확인하고 모델 ID·버전을 고정한다. 날짜 역시 DB에 저장된 구간에 맞게 바꾼다.

PowerShell에서 로컬 DB 접속 환경변수를 설정한 뒤 프로젝트 루트에서 실행한다. 기본 로컬 DB 접속값은 [Local MySQL Development](../../local-mysql-development.md)에 정리되어 있다.

```powershell
$runArgs = @(
    '--spring.profiles.active=local'
    '--spring.main.web-application-type=none'
    '--broker.kis.enabled=false'
    '--harness.scheduler.enabled=false'
    '--broker.order.reconciliation.scheduler.enabled=false'
    '--broker.order.cancellation.scheduler.enabled=false'
    '--market.price.history.collection.bootstrap.enabled=false'
    '--market.price.history.collection.scheduler.enabled=false'
    '--market.index.history.collection.bootstrap.enabled=false'
    '--market.index.history.collection.scheduler.enabled=false'
    '--market.index.history.collection.backfill.enabled=false'
    '--backtest.swing-v1.experiment.manual.enabled=true'
    '--backtest.swing-v1.experiment.manual.candidate-symbols=005930'
    '--backtest.swing-v1.experiment.manual.from-signal-date=2026-08-03'
    '--backtest.swing-v1.experiment.manual.to-signal-date=2026-08-28'
    '--backtest.swing-v1.experiment.manual.benchmark-id=KOSPI'
    '--backtest.swing-v1.experiment.manual.initial-cash-amount-krw-per-symbol=10000000'
    '--backtest.swing-v1.experiment.manual.cost-model.model-id=EXAMPLE_UNVERIFIED_V1'
    '--backtest.swing-v1.experiment.manual.cost-model.model-version=1'
    '--backtest.swing-v1.experiment.manual.cost-model.buy-commission-rate=0.00015'
    '--backtest.swing-v1.experiment.manual.cost-model.sell-commission-rate=0.00015'
    '--backtest.swing-v1.experiment.manual.cost-model.sell-tax-rate=0.0018'
    '--backtest.swing-v1.experiment.manual.cost-model.buy-slippage-rate=0.001'
    '--backtest.swing-v1.experiment.manual.cost-model.sell-slippage-rate=0.001'
)
.\gradlew.bat bootRun --args="$($runArgs -join ' ')"
```

시작 로그의 `candidateSymbols`, 신호 기간·초기 현금·비용 모델 전체 값을 확인한다. 완료 로그에서 실제 지수 관측 시작일·종료일·건수, 종목 수, 거래 수, 지수 수익률, 청산비용 반영 중앙값, 중앙값 초과수익률과 초과 종목 수를 확인한다. 결과 스냅샷을 남길 때도 요청의 후보 목록과 순서를 보존한다. 실패하면 원인을 수정하기 전까지 그 실행은 성과 검증에 포함하지 않는다.

## 슬리피지 민감도 비교

`backtest.swing-v1.experiment.manual.slippage-sensitivity-enabled`의 기본값은 `false`다. 기본 수동 실행은 기존처럼 지정한 비용 모델로 한 번만 평가한다. 같은 종목·기간을 기준, 2배, 3배 슬리피지로 비교하려면 위 실행 예시의 `$runArgs`에 다음 인수를 추가한다. 수동 Runner 자체의 `enabled=true`도 필요하다.

```powershell
'--backtest.swing-v1.experiment.manual.slippage-sensitivity-enabled=true'
```

- 기준 모델은 `cost-model` 설정 전체를 그대로 사용한다. 두 스트레스 모델은 매수·매도 슬리피지만 각각 2배와 3배로 곱하며 수수료·세금·모델 버전은 유지한다. 예시의 각각 0.001은 0.002와 0.003이 된다. 실제 계좌 비용 또는 예상 체결 품질로 검증된 값은 아니다.
- 스트레스 모델 ID는 기준 ID 뒤에 `_SLIPPAGE_X2`, `_SLIPPAGE_X3`를 붙인다. 적어도 한쪽 기준 슬리피지가 양수여야 하고 곱한 비율도 1 미만이어야 한다. 잘못된 조건은 평가 전에 거부한다.
- 후보 목록과 순서, 신호 기간, 종목별 초기 현금, 벤치마크 및 전략·Risk 설정은 고정한다. 각 시나리오가 초기 현금과 보유 없음에서 전체 전략을 다시 실행한다. 기존 거래에서 추가 비용만 차감하거나 앞 시나리오의 포트폴리오를 이어받지 않는다.
- 기존 평가 서비스가 SWING, 최초 자금 100%·10% 매수 후 보유, 최종 가정 청산에 해당 시나리오 비용 모델을 적용한다. 세 시나리오는 순서대로 실행하며 기준 평가를 별도로 중복 실행하지 않는다.
- `SwingV1CostSensitivityResult.evaluations`는 요청 순서대로 전체 평가 결과를 보존한다. 성공한 종목이나 시나리오만 골라 반환하지 않는다. 하나라도 실패하면 예외를 전파하며 Runner는 부분 성과 표나 비교 완료 로그를 출력하지 않는다.
- 결과를 묶을 때 요청 조건과 벤치마크 성과, 종목별 평가 시각, 신호·판단 날짜, 판단 근거의 현재가·출처·기술 분석을 대조한다. 비용에 따라 달라질 수 있는 행동, 수량, 현금, 보유, 수익률과 노출은 같을 필요가 없다. 이 검증은 원본 종목·지수 일봉 전체의 해시 검증을 대체하지 않으므로 실행 중 데이터 수집·수정과 전략 설정 변경을 하지 않는다.

각 `manual backtest completed`, `manual backtest diagnostic`, `manual backtest buy-and-hold comparison` 로그의 `costModel`로 시나리오를 식별한다. 보유 비교 로그에는 수익률·MDD·노출 외에 `swingCompletedTradeCount`와 `swingFinalPositionQuantity`도 포함된다. 모든 평가가 성공해야 `slippage sensitivity completed. scenarioCount=3`이 출력된다.

결과를 보기 전에 세 가정을 고정하고 손실·무거래를 포함해 기록한다. 비용 증가로 최종 주문 수량과 이후 경로가 달라질 수 있으므로 전체 전략 수익률이 항상 단조 감소한다고 가정하지 않는다. 무거래의 0% 수익률이나 스트레스 가정의 양수 결과만으로 전략 검증 게이트를 통과시키지 않는다. 실제 KIS 비용 확인, 미사용 구간 및 모의투자 검증은 별도다.

관측 05의 고정 A·B·C 기간과 네 종목을 기준·2배·3배 슬리피지로 두 번 재실행한 결과는 [SWING_V1 슬리피지 관측 결과 07](validation/swing-v1-slippage-observation-07.md)에 기록한다. 기준 결과 유지, 전체 JSON 값의 재현성, 독립 비용·노출 검산과 DB 보존을 확인했으며 기존 관측 문서와 스냅샷은 그대로 보존했다. A SK하이닉스의 작은 순이익과 A NAVER의 10% 보유 비교 우위가 스트레스 비용에서 사라졌지만, 이미 관측한 소규모 표본의 진단이므로 미사용 구간 검증이나 실제투자 승격을 의미하지 않는다. 슬리피지 비교도 저장된 일봉을 반복 조회하므로 기본 실행보다 DB 조회량이 늘어난다. Broker·OpenAI 호출, Scheduler 활성화나 결과 DB 저장 기능을 추가하지 않는다.

## 실행 결과 해석

종목별 `manual backtest diagnostic` 로그에서는 `stepStatusCounts`와 `actionReasonCounts`로 판단 결과를 구분한다. 진입·청산 신호가 있었지만 수량을 산정하지 못해 최종 `HOLD`가 된 경우는 `blockedOrderReasonCounts`에, 주문 근사 후 포트폴리오 적용이 거절된 경우는 `rejectedTransitionReasonCounts`에 기록된다. `executedBuyCount`와 `executedSellCount`는 각각 적용된 매수·매도 횟수이고, `finalPositionQuantity`는 구간 종료 시 미청산 보유 수량이다. `NO_NEXT_DAILY_BAR`에는 판단 근거가 없으므로 행동 사유 건수에 포함하지 않는다.

`totalCompletedTradeCount=0`만으로 매수 신호가 없었다고 단정하지 않는다. 매수 후 미청산, 수량 산정 실패, 포트폴리오 적용 거절 여부를 진단 로그와 함께 확인한다. 이 진단은 사유를 설명할 뿐 거래를 강제로 생성하거나 전략 성과를 보증하지 않는다.

완료 로그도 수익성 입증은 아니다. 현재는 독립적인 종목별 결과와 하나의 종가 기반 벤치마크를 근사 비교하며 결과가 DB에 영속화되지 않는다. 기준 관측은 `005930` 한 종목이며, 이후 소수 네 종목의 실행과 비교 및 고정 기간별 진단을 검증했다. 시장 대표 Universe·미사용 구간의 실제 검증은 별도 단계다.

저장된 데이터로 수행한 첫 실행의 조건, 재현성 검사와 무거래 원인은 [SWING_V1 기준 결과 01](validation/swing-v1-baseline-observation-01.md)에 기록한다.

지수 과거 이력을 실제로 보충한 뒤 동일한 전략과 비용 가정으로 기간을 확대한 결과는 [SWING_V1 확대 결과 02](validation/swing-v1-baseline-observation-02.md)에 기록한다. 기존 기간 대조, 거래별 검산과 데이터 보존 확인을 포함하며 실제투자 승격을 의미하지 않는다.

운영 Universe를 바꾸지 않고 고정한 네 종목으로 수행한 결과는 [SWING_V1 다종목 관측 결과 04](validation/swing-v1-multi-symbol-observation-04.md)에 기록한다. 신규 일봉 수집, 손실 종목과 이익 집중, 독립 실행 재현성 및 동일 종목 보유 비교를 포함한다. 소규모 표본의 검증이며 전략 수익성 입증으로 해석하지 않는다.

같은 네 종목과 전략·비용을 유지한 세 고정 기간의 결과는 [SWING_V1 기간별 관측 결과 05](validation/swing-v1-period-observation-05.md)에 기록한다. 기간마다 초기 현금과 보유를 초기화해 전체 전략을 다시 실행하고, 선행 일봉은 지표 준비에만 사용한다. 완료 거래·미청산 평가이익·무거래를 구분하며 기존 전체 기간의 거래를 잘라 붙이지 않는다. 이미 관측한 데이터의 진단이므로 미사용 구간 검증이나 Walk-Forward로 취급하지 않는다.

## 거래 이익 집중도 확인

종목별 반환 보고서의 `tradePerformanceSummary`에는 최대 이익 거래의 순이익, 전체 이익 거래 합계 대비 비중과 그 한 건을 제외한 순손익을 포함한다. 계산 계약은 [전략 계약](swing-v1-strategy-contract.md)의 Benchmark And Validation을 따른다. 현재 Runner 완료 로그에는 이 세 값이 출력되지 않으므로 반환 보고서나 별도 저장 스냅샷에서 확인한다.

비중은 0~1 값이며 화면이나 표에서만 100을 곱해 퍼센트로 표시한다. 전체 순손익이 음수여도 이익 거래가 있으면 비중을 계산한다. 이익 거래가 없다면 최대 이익은 0원, 비중은 `null`, 제외 후 순손익은 원래 값이다. 최대 이익이 동률이어도 한 건만 뺀다.

새 필드가 없는 과거 결과를 0 또는 `null`로 임의 보완하지 않는다. 기존 스냅샷은 보존하고 그 안의 `completedTrades`를 현재 `SwingV1TradePerformanceCalculator`에 전달해 새 요약을 별도로 계산한다. 기존 순손익·승률·거래당 평균·Profit Factor가 달라지면 계산 회귀로 보고 확인한다.

### 관측 04 저장 거래 검산

2026-10-01 관측 04의 두 원본 결과에 저장된 네 종목·19개 완료 거래를 별도 JVM에서 각각 검산했다. 전략 실행이나 신규 데이터 수집을 하지 않았고 DB·KIS·OpenAI에 연결하지 않았다. 원본 입력·결과는 수정하지 않았다.

| 종목 | 최대 이익 거래 순이익 | 전체 이익 거래 합계 대비 비중 | 최대 이익 한 건 제외 후 순손익 |
| --- | ---: | ---: | ---: |
| `005930` | 3,275,996원 | 100% | -173,347원 |
| `000660` | 1,147,176원 | 90.50177% | 63,935원 |
| `005380` | 625,442원 | 94.99481% | -50,779원 |
| `035420` | 146,491원 | 100% | -309,888원 |

기존 거래 요약의 모든 필드가 유지됐고 새 세 값은 완료 거래를 직접 합산한 결과와 같았다. 새 요약을 결합한 보고서의 검증도 통과했다. 요청·완료 거래·기존 성과 요약·가정 청산 추정·초기 및 최종 포트폴리오·자산 평가곡선이 유지되는 것을 확인했다. 판단 근거 객체의 JSON 역직렬화 재현성까지 검증한 것은 아니다.

검산용 Java 실행 파일과 두 결과는 Git 추적 대상이 아닌 `build/trade-concentration-observation/`에만 남겼다. `CompareTradeConcentration.java`는 저장 결과를 읽는 일회성 도구이며 운영 코드나 새 패키지에 포함하지 않는다. `run-01.json`과 `run-02.json`의 SHA-256은 모두 `7b515acc5ec6975d2e62dad924114e5e2a3deb3ca4a25bb109dbee10fca6fd38`로 같다.

관측 04 원본 입력 SHA-256은 두 실행 모두 `f2af22f56dcbf8f4bbb9652edaa94a1518f1bb7e961c8424400a5922891f5294`, 원본 결과는 `fd6aba1d7e7124b492ede824d4598f0ee2b42eeb42ec749b4d1a7df5b9de9358`로 보존됐다. [관측 결과 04](validation/swing-v1-multi-symbol-observation-04.md)의 기존 기록도 변경하지 않았다.

네 종목 모두 이익이 한 건에 크게 집중됐고, 그 한 건을 빼면 세 종목이 손실이다. 다만 이는 **기존 거래를 고정한 산술적 민감도 검사**이지 거래를 생략한 새 백테스트가 아니다. 이미 관측한 기간의 추가 진단이므로 미사용 기간 검증이나 반복 가능한 기대수익의 증거로 취급하지 않는다. 이 결과를 근거로 매매 규칙이나 Risk 한도를 조정하지 않았다.

## 동일 종목 매수 후 보유 비교

평가 서비스는 각 종목에 최초 자금 100%와 10%를 배분한 매수 후 보유 결과를 함께 계산한다. 같은 평가일·시각 메타데이터·시가와 비용 모델을 사용하며, 1주도 살 수 없으면 현금으로 유지한다. 두 배분 모두 전체 초기 현금을 수익률 분모로 사용한다.

`manual backtest buy-and-hold comparison` 로그에서 배분별 수량·잔여 현금, 평가금액 수익률, 최종 가정 청산비용, 보정 수익률, MDD와 SWING 대비 차이를 확인한다. 반환값의 `buyAndHoldResults`에는 전체 평가곡선도 포함된다. SWING 당시 판단 근거와 다시 읽은 저장소 시가가 달라도 실패한다.

반환 보고서와 보유 비교 결과의 `exposureSummary`, 같은 로그의 `swingExposureSummary`·`buyAndHoldExposureSummary`에서 전체 평가일 수, 보유 평가일 수·비율, 평균·최대 투자 비중도 확인한다. 평균에는 현금만 보유한 평가일이 포함되며 최초 배분 비율을 그대로 사용하지 않는다. 세부 계산은 [전략 계약](swing-v1-strategy-contract.md)의 Benchmark And Validation을 따른다.

관측 05의 저장 평가곡선만으로 새 계산을 검산한 결과는 [SWING_V1 노출 관측 결과 06](validation/swing-v1-exposure-observation-06.md)에 기록한다. 기존 스냅샷은 수정하지 않고 새 노출 요약을 별도 증거로 보존한다. 과거 JSON에 새 필드가 없다고 0으로 보완하거나 원본 전체 결과 해시를 덮어쓰지 않는다.

100% 배분은 백테스트 기회비용 비교이며 실제 주문 권한을 확대하지 않는다. 10% 배분 역시 SWING과 동일한 위험을 보장하지 않는다. 상세 계약은 [동일 종목 비교 문서](swing-v1-buy-and-hold-comparison.md), 원본 확대 스냅샷을 사용한 실제 결과는 [비교 관측 결과 03](validation/swing-v1-buy-and-hold-observation-03.md)에 기록한다.
