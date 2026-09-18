# Strategy Experiment Design

## Purpose

이 문서는 AI Investment Agent가 단타, 스윙, 장기 관점으로 각각 투자 판단을 수행하고, 전략별 모의투자 데이터를 독립적으로 축적하기 위한 기준을 정의한다.

목표는 한 Agent가 세 관점을 한 번에 섞어서 판단하게 하는 것이 아니다. 동일한 시장 조건에서 서로 다른 투자 관점이 어떤 Tool을 사용하고 어떤 판단을 내리며 어떤 성과를 만드는지 비교 가능하게 저장하는 것이다.

## Strategy Horizons

모의투자 실험의 기본 전략 관점은 다음 세 가지다.

```text
DAY_TRADING
SWING
LONG_TERM
```

단타, 스윙, 장기는 투자 시간 관점이다. Momentum, Mean Reversion, Value와 같은 구체적인 종목 선정 방식은 각 시간 관점 안에서 적용할 수 있다.

예를 들어 `SWING_MOMENTUM_V1`과 `LONG_TERM_VALUE_V1`은 서로 다른 전략으로 관리한다.

### Day Trading

단타 전략의 기본 방향은 다음과 같다.

```text
strategyId: DAY_TRADING_V1
실행 주기: 장중 5분
주요 데이터: 현재가, 분봉, 거래량, 단기 변동성
기본 보유 관점: 당일
주요 Risk: 일일 손실, 주문 횟수, 중복 주문, 장 마감 전 포지션
```

단타 전략은 짧은 시간에 반복 실행되므로 Broker API 호출량과 주문 중복 방지가 특히 중요하다.

### Swing

스윙 전략의 기본 방향은 다음과 같다.

```text
strategyId: SWING_V1
실행 주기: 거래일 기준 하루 1회
주요 데이터: 일봉, 거래량, 추세, 이동평균
기본 보유 관점: 수일에서 수주
주요 Risk: 종목 비중, 손절 기준, 최대 보유 기간
```

정확한 실행 시각은 시장 데이터 수집 방식과 매매 가능 시간을 확인한 뒤 결정한다.

### Long Term

장기 전략의 기본 방향은 다음과 같다.

```text
strategyId: LONG_TERM_V1
실행 주기: 1주일에 1회
주요 데이터: 재무정보, 실적, 밸류에이션, 장기 가격 추세
기본 보유 관점: 수개월 이상
주요 Risk: 장기 종목 비중, 분산, 투자 가설 훼손
```

주 1회 실행은 장기 포트폴리오를 매주 재검토한다는 뜻이다. 보유 기간을 1주로 제한하거나 매주 반드시 매도하는 정책은 아니다.

정확한 실행 요일과 시각은 운영 Scheduler 설정 단계에서 결정한다.

## Run Isolation

하나의 Agent Run에는 하나의 전략만 적용한다.

```text
Harness Run
-> strategyId
-> strategyVersion
-> strategyProfile
-> strategyPortfolio
-> candidateStocks
-> allowedTools
-> riskRules
```

다음과 같은 방식은 사용하지 않는다.

```text
하나의 Run
-> 단타 관점 검토
-> 스윙 관점 검토
-> 장기 관점 검토
-> 세 결과를 섞어 하나의 결정 생성
```

서로 다른 보유 기간과 매도 기준이 한 판단에 섞이면 결정 사유와 성과를 특정 전략에 귀속할 수 없기 때문이다.

## Agent and Harness Responsibilities

같은 `InvestmentAgent` 실행 엔진을 전략별로 재사용한다. 전략별 Agent 클래스를 복제하지 않는다.

```text
Harness
-> 이번 Run의 strategyId와 strategyVersion 확정
-> 전략별 후보 종목과 허용 Tool 제공
-> 전략별 Step, Tool, Provider Budget 적용
-> 전략별 Risk Rule 적용
-> Run과 거래 이력에 전략 식별자 기록

Investment Agent
-> 전달받은 전략 관점으로 후보 종목 조사
-> 허용된 Tool 중 필요한 Tool 선택
-> 전략 조건에 맞는 BUY, SELL, HOLD 판단
```

Agent는 실행 중에 자신의 전략을 변경할 수 없다. 단타 Run에서 장기 보유를 전제로 판단하거나 장기 Run에서 단기 가격 변동만으로 전략을 바꾸지 않도록 Harness가 전략 Context를 고정한다.

## Stock Discovery

전략별 후보 종목은 전체 시장을 Broker 현재가 API로 하나씩 조회해서 만들지 않는다.

```text
Stock Universe
-> 전략별 Screener
-> 전략별 후보 종목
-> Investment Agent
-> 필요한 종목만 상세 Tool 조회
```

각 전략의 Screener 조건은 달라질 수 있다.

```text
DAY_TRADING
-> 거래량, 거래대금, 단기 변동성 중심

SWING
-> 일봉 추세, 거래량 변화, 모멘텀 중심

LONG_TERM
-> 재무정보, 실적, 밸류에이션, 장기 추세 중심
```

초기 구현은 제한된 관심종목 목록으로 시작할 수 있다. 이후 Stock Universe와 로컬 DB 기반 Screener로 확장한다.

## Schedule Policy

Scheduler는 일정 간격으로 실행 가능 여부를 확인하고, 현재 시각에 실행 대상인 전략만 선택한다.

```text
5분 Scheduler Tick
-> DAY_TRADING 실행 대상 여부 확인
-> SWING 실행 대상 여부 확인
-> LONG_TERM 실행 대상 여부 확인
-> 실행 대상 전략별로 독립 Harness Run 시작
```

Scheduler가 5분마다 깨어나는 것과 모든 전략이 5분마다 실행되는 것은 다르다.

```text
DAY_TRADING
-> 장중 5분마다 실행 가능

SWING
-> 지정된 거래일 시각에 한 번 실행

LONG_TERM
-> 지정된 요일과 시각에 주 1회 실행
```

같은 전략이 같은 실행 구간에 중복 시작되지 않도록 실행 이력 또는 별도 실행 잠금으로 보호해야 한다.

## Portfolio Isolation

각 전략은 독립적인 포트폴리오와 성과 이력을 가져야 한다.

```text
DAY_TRADING_V1 Portfolio
SWING_V1 Portfolio
LONG_TERM_V1 Portfolio
```

하나의 포트폴리오를 공유하면 다음 문제가 생긴다.

- 한 전략이 사용한 현금 때문에 다른 전략이 주문하지 못한다.
- 같은 종목을 여러 전략이 보유할 때 수량의 소유 전략을 구분할 수 없다.
- 수익과 손실을 어느 전략의 결과로 볼지 판단할 수 없다.
- 전략별 Risk Limit 사용량을 계산할 수 없다.

초기 전략 비교 단계에서는 전략별 Virtual 또는 Shadow Portfolio를 사용한다. 각 전략은 같은 시장 데이터를 사용하되 주문과 포지션을 독립적으로 계산한다.

전략별 초기 자금을 애플리케이션의 임의 기본값으로 정하지 않는다. 별도 Broker 모의계좌를 사용할 수 있다면 각 계좌 잔액을 직접 기준으로 삼는다. 하나의 Broker 모의계좌를 공유한다면 조회한 전체 현금과 잔고를 기준으로 동일 비율 또는 명시적인 배분 규칙을 적용하고, 해당 배분 결과와 규칙을 실험 시작 이력에 저장한다.

한국투자증권 모의계좌를 전략별로 분리할 수 있는지는 계좌 발급 조건을 확인해야 한다. 하나의 Broker 모의계좌만 사용하는 경우 Broker 계좌는 전체 실행 결과의 외부 검증에 사용하고, 전략별 성과 귀속은 내부 전략 포트폴리오에서 관리하는 방안을 검토한다.

Broker 계좌와 내부 전략 포트폴리오의 정합성 규칙은 실제 모의 주문 연동 전에 별도로 확정한다.

## Persistence Requirements

전략별 비교를 위해 Run과 거래 이력에 최소한 다음 식별자가 필요하다.

```text
strategyId
strategyVersion
portfolioId
runId
agentVersion
```

`strategyVersion`은 전략 규칙, Screener 조건 또는 AI Prompt가 변경될 때 증가시킨다.

```text
SWING_V1
-> 기존 규칙으로 수집한 데이터

SWING_V2
-> 변경된 규칙으로 수집한 데이터
```

버전이 다른 데이터를 같은 전략 결과로 합치지 않는다.

Run에는 가능한 한 다음 입력과 결과를 함께 저장한다.

- 전략 식별자와 버전
- 후보 종목과 Screener 결과
- Agent가 확인한 Tool 결과
- 최종 투자 판단과 사유
- Risk Guard 결과
- 주문 및 체결 결과
- Run 시작과 종료 시점의 전략 포트폴리오

## Performance Evaluation

전략별 시간 관점이 다르므로 같은 단기 수익률만으로 비교하지 않는다.

```text
DAY_TRADING
-> 거래당 수익률
-> 일일 손실
-> 승률
-> 거래 횟수
-> 수수료와 슬리피지 영향

SWING
-> 보유 기간 수익률
-> 평균 손익비
-> MDD
-> 평균 보유 기간

LONG_TERM
-> 주간 및 월간 수익률
-> 벤치마크 대비 수익률
-> 장기 MDD
-> 포트폴리오 회전율
```

모든 전략에는 총 수익률, MDD, 거래 횟수와 같은 공통 지표도 함께 제공한다.

## Incremental Implementation Order

한 번에 세 전략의 완성형 Screener와 AI Prompt를 만들지 않는다.

```text
1. strategyId와 strategyVersion 모델 추가
2. Run과 이력에 전략 식별자 저장
3. 전략별 실행 주기 모델 추가
4. 전략별 독립 Portfolio 경계 추가
5. 제한된 관심종목 기반 후보 목록 추가
6. 단타, 스윙, 장기 전략 Profile 추가
7. 전략별 Screener 확장
8. 전략별 성과 지표 추가
9. 선택한 전략을 KIS 모의 주문과 연결
```

현재 단계에서는 실제투자 연결을 범위에 포함하지 않는다. 충분한 모의투자 데이터를 축적하고 전략별 성과와 운영 안정성을 검증한 후 별도의 Go/No-Go 결정으로 다룬다.
