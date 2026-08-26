# Harness Run Persistence Design

## Purpose

Harness Run 이력 저장의 목적은 Agent 실행을 운영 관점에서 다시 추적할 수 있게 만드는 것이다.

초기 목표는 `HarnessRunResult` 전체를 그대로 영속화하는 것이 아니다. 먼저 실행이 언제 발생했고, 어떤 상태로 끝났으며, 어떤 단계를 거쳤고, Agent 판단과 Risk Guard 결과가 무엇이었는지 확인 가능한 이력을 저장한다.

이를 통해 이후 다음 기능으로 확장할 수 있다.

- Run 목록 조회
- Run 상세 이력 조회
- Agent 판단 내용 추적
- Risk Guard 승인/거절 사유 추적
- 실패 Run 분석
- 전략별 실행 비교
- Trade 이력과 Run 연결

## Current State

현재 Harness 이력은 JPA 기반 저장 이력을 기준으로 관리한다.

```text
HarnessRunHistoryService.record(HarnessRunResult)
-> HarnessRunRepository에 HarnessRunEntity 저장
-> HarnessStepRepository에 HarnessStepEntity 목록 저장
```

`HarnessRunEntity`는 Run 메타데이터와 함께 다음 스냅샷을 JSON 문자열로 저장한다.

```text
HarnessRunResult.decision
-> HarnessDecisionSnapshot
-> decisionSnapshotJson

HarnessRunResult.riskCheckResult
-> HarnessRiskCheckSnapshot
-> riskCheckSnapshotJson

HarnessRunResult.portfolioSnapshot
-> HarnessPortfolioSnapshot
-> portfolioSnapshotJson

HarnessRunResult.marketSnapshot
-> HarnessMarketSnapshot
-> marketSnapshotJson
```

현재 조회 기준은 기능별로 다르다.

```text
getRunSummaries()
-> DB 기반 HarnessRunSummary 목록 조회

GET /api/harness/runs/{runId}
-> DB에 저장된 Run 메타데이터, 판단 스냅샷, 리스크 스냅샷,
   포트폴리오 스냅샷, 시장 스냅샷, Step 이력, Trade 이력을 조합한 HarnessRunDetail 조회

getStepsByRunId(runId)
-> DB 기반 HarnessStepResult 목록 조회
```

즉 Run 목록, Run 단건 상세, Step 이력, Trade 이력은 DB 기준으로 이동했다.

`HarnessRunResult`는 `POST /api/harness/run`으로 방금 실행한 결과를 응답할 때 사용한다. 저장된 Run 이력 조회의 기준은 `HarnessRunDetail`이다.

## Persisted Models

### HarnessRunEntity

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessRunEntity.java
```

현재 저장 필드:

```text
id
runId
status
startedAt
finishedAt
decisionSnapshotJson
riskCheckSnapshotJson
portfolioSnapshotJson
marketSnapshotJson
```

이 Entity는 Run 메타데이터와 Harness 관점에서 중요한 실행 당시 스냅샷을 저장한다.

`decisionSnapshotJson`은 Agent가 어떤 투자 판단을 했는지 저장한다.

`riskCheckSnapshotJson`은 Harness/Risk Guard가 그 판단을 승인했는지, 거절했다면 어떤 `reasonCode`로 거절했는지 저장한다.

`portfolioSnapshotJson`은 Agent가 판단할 때 참조한 포트폴리오 상태를 저장한다.

`marketSnapshotJson`은 Agent가 판단할 때 참조한 시장 상태를 저장한다.

이 값들은 현재 단계에서 조건 검색이나 통계 집계보다 Run 상세 확인 목적이 강하다. 그래서 별도 Entity로 분리하지 않고 JSON 문자열로 저장한다.

### HarnessDecisionSnapshot

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessDecisionSnapshot.java
```

현재 필드:

```text
action
symbol
quantity
expectedPriceKrw
estimatedOrderAmountKrw
reason
```

이 모델은 `InvestmentDecision` 전체를 그대로 저장하지 않고, 저장 이력 조회에 필요한 판단 요약만 담는다.

### HarnessRiskCheckSnapshot

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessRiskCheckSnapshot.java
```

현재 필드:

```text
status
action
symbol
quantity
expectedPriceKrw
estimatedOrderAmountKrw
reasonCode
reason
```

이 모델은 `RiskCheckResult` 전체를 그대로 저장하지 않고, Risk Guard 판단을 사후 분석하기 위한 요약 정보를 담는다.

### HarnessPortfolioSnapshot

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessPortfolioSnapshot.java
src/main/java/com/stock/harness/persistence/HarnessPortfolioPositionSnapshot.java
```

현재 필드:

```text
cashAmountKrw
totalAssetAmountKrw
positions
```

`positions`는 각 보유 종목을 `HarnessPortfolioPositionSnapshot`으로 변환해 저장한다.

이 스냅샷은 Run 당시 Agent가 어떤 현금, 총 평가금, 보유 종목 상태를 보고 판단했는지 확인하기 위한 값이다.

### HarnessMarketSnapshot

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessMarketSnapshot.java
```

현재 필드:

```text
market
marketOpen
description
```

이 스냅샷은 Run 당시 시장이 열려 있었는지, 어떤 시장 상태 설명을 Agent가 참고했는지 확인하기 위한 값이다.

### HarnessRunSnapshotJsonConverter

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessRunSnapshotJsonConverter.java
```

역할:

```text
HarnessDecisionSnapshot -> JSON
JSON -> HarnessDecisionSnapshot

HarnessRiskCheckSnapshot -> JSON
JSON -> HarnessRiskCheckSnapshot

HarnessPortfolioSnapshot -> JSON
JSON -> HarnessPortfolioSnapshot

HarnessMarketSnapshot -> JSON
JSON -> HarnessMarketSnapshot
```

Entity가 `ObjectMapper`나 converter를 직접 알지 않도록 JSON 변환 책임은 별도 컴포넌트로 둔다.

저장할 때는 `HarnessRunHistoryService`가 snapshot 객체를 JSON으로 변환한다. 조회할 때도 `HarnessRunHistoryService`가 JSON을 snapshot 객체로 복원한다.

### HarnessStepEntity

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessStepEntity.java
```

현재 저장 필드:

```text
id
runId
stepOrder
type
status
message
startedAt
finishedAt
```

Step은 Run Entity 안에 포함하지 않고 별도 테이블로 분리한다.

이유는 다음과 같다.

- 하나의 Run은 여러 Step을 가진다.
- Step은 실행 순서가 중요하므로 `stepOrder`가 필요하다.
- 실패 분석에서는 Run 전체보다 어느 Step에서 실패했는지가 더 중요하다.
- `startedAt`, `finishedAt`은 Step이 기록된 시점을 저장한다. 현재는 실제 실행 구간 측정이 아니라 기록 시각을 양쪽에 동일하게 넣는 점진적 구조다.
- 이후 Step별 소요 시간, Tool 호출, 재시도 정보 등을 붙이기 쉽다.

### TradeRecordEntity

패키지 경로:

```text
src/main/java/com/stock/trade/persistence/TradeRecordEntity.java
```

Trade 이력은 Harness Run Entity 안에 포함하지 않고 별도 저장 모델로 관리한다.

현재 연결 기준은 `runId` 문자열이다.

```text
HarnessRunEntity.runId
TradeRecordEntity.runId
HarnessStepEntity.runId
```

초기 단계에서는 DB Foreign Key를 강제하지 않는다. 지금은 실행 이력을 유연하게 쌓고 조회하는 것이 우선이다.

### HarnessRunDetail

패키지 경로:

```text
src/main/java/com/stock/harness/HarnessRunDetail.java
```

`HarnessRunDetail`은 Entity가 아니라 저장된 데이터를 조합해서 반환하는 읽기 모델이다.

현재 필드:

```text
runId
status
startedAt
finishedAt
decisionSnapshot
riskCheckSnapshot
portfolioSnapshot
marketSnapshot
steps
tradeRecords
```

`decisionSnapshot`, `riskCheckSnapshot`, `portfolioSnapshot`, `marketSnapshot`은 DB에 저장된 JSON 문자열을 다시 객체로 복원한 값이다.

`steps`는 `HarnessStepEntity` 목록에서 복원한 값이다.

`tradeRecords`는 `TradeRecordEntity` 기준의 거래 이력 조회 결과다.

## Current APIs

### Run 실행

```text
POST /api/harness/run
```

새 Harness Run을 실행하고, 방금 실행한 결과와 해당 Run의 거래 이력을 함께 반환한다.

응답 모델은 `HarnessRunResponse`이며, 런타임 결과인 `HarnessRunResult`를 API 응답으로 표현한다.

### Run 목록 조회

```text
GET /api/harness/runs
```

DB에 저장된 `HarnessRunEntity`를 기준으로 `HarnessRunSummary` 목록을 반환한다.

### Run 상세 조회

```text
GET /api/harness/runs/{runId}
```

DB에 저장된 Run 메타데이터, 판단 스냅샷, 리스크 스냅샷, 포트폴리오 스냅샷, 시장 스냅샷, Step 이력, Trade 이력을 조합해 `HarnessRunDetail`을 반환한다.

주의사항:

- 이 API가 현재 Run 단건 상세 조회의 기본 경로다.
- 응답 모델은 런타임 결과인 `HarnessRunResponse`가 아니라 저장 이력 조회 모델인 `HarnessRunDetail`이다.
- Run이 존재하지 않으면 `404 Not Found`를 반환한다.

현재 포함하는 값:

```text
runId
status
startedAt
finishedAt
decisionSnapshot
riskCheckSnapshot
portfolioSnapshot
marketSnapshot
steps
tradeRecords
```

Run이 존재하지 않으면 `404 Not Found`를 반환한다.

### Run Step 조회

```text
GET /api/harness/runs/{runId}/steps
```

DB에 저장된 `HarnessStepEntity`를 기준으로 Step 목록을 반환한다.

Run이 존재하지 않으면 `404 Not Found`를 반환한다.

### Trade 이력 조회

```text
GET /api/trades
GET /api/trades?runId={runId}
```

DB에 저장된 `TradeRecordEntity`를 기준으로 거래 이력을 반환한다.

## Deferred Fields

현재 `HarnessRunEntity`에 중복 저장하지 않는 주요 값은 다음과 같다.

```text
HarnessRunResult.tradeResult
```

`tradeResult`를 `HarnessRunEntity`에 중복 저장하지 않는 이유는 실행 거래 이력이 이미 `TradeRecordEntity`에 기록되기 때문이다.

`portfolioSnapshot`과 `marketSnapshot`은 더 이상 보류 필드가 아니다. 현재는 각각 `HarnessPortfolioSnapshot`, `HarnessMarketSnapshot`으로 변환한 뒤 JSON 컬럼에 저장한다.

## Design Decisions

### HarnessRunResult 전체를 바로 Entity로 저장하지 않는 이유

`HarnessRunResult`는 실행 결과 전체를 담는 런타임 모델이다. 여기에는 판단 결과, 리스크 검증 결과, 거래 결과, 포트폴리오 스냅샷, 시장 스냅샷이 함께 들어 있다.

이 객체를 그대로 영속화하면 초기에는 빠르지만, 이후 구조 변경과 조회 요구가 생길 때 관리가 어려워진다.

현재는 메타데이터, Step, Trade, 판단 스냅샷, 리스크 스냅샷, 포트폴리오 스냅샷, 시장 스냅샷처럼 저장 책임과 조회 목적을 설명할 수 있는 값부터 분리한다.

### Snapshot JSON으로 저장하는 이유

`decision`, `riskCheckResult`, `portfolioSnapshot`, `marketSnapshot`은 Run 당시 Agent와 Harness가 어떤 상태를 보고 판단했는지 보여준다.

이 값들은 실패 분석과 전략 비교에서 가치가 높다.

하지만 현재 단계에서는 이 값들을 조건 검색하거나 통계 집계하는 요구보다 Run 상세에서 당시 맥락을 확인하는 요구가 더 강하다. 따라서 별도 Entity로 분리하지 않고 다음 흐름으로 저장한다.

```text
runtime model
-> persistence snapshot
-> JSON column
```

조회 시에는 반대 방향으로 복원한다.

```text
JSON column
-> persistence snapshot
-> HarnessRunDetail
```

### Entity가 JSON 파싱을 하지 않는 이유

`HarnessRunEntity`는 JSON 문자열을 저장하지만 JSON 파싱 책임은 갖지 않는다.

Entity가 `ObjectMapper`나 converter를 알게 되면 persistence 모델의 책임이 넓어진다.

따라서 JSON 변환은 `HarnessRunSnapshotJsonConverter`가 담당하고, `HarnessRunHistoryService`가 저장과 조회 흐름에서 converter를 사용한다.

`HarnessRunEntity.toDetail(...)`은 이미 복원된 snapshot 객체, steps, tradeRecords를 받아 `HarnessRunDetail`을 생성한다.

### Step을 별도 저장 모델로 관리하는 이유

Step은 Harness Engineering 관점에서 핵심 관찰 데이터다.

Agent가 어떤 판단을 했는지보다 먼저 확인해야 할 것은 Harness가 어떤 단계를 실행했고, 어디서 멈췄는지다.

따라서 Step은 별도 Entity로 분리하고, `runId`와 `stepOrder`로 조회한다.

### Trade를 Run 안에 포함하지 않는 이유

하나의 Run에서 여러 거래가 생길 수 있고, 거래 이력은 Run과 다른 조회 축을 가진다.

따라서 Trade는 `TradeRecordEntity`로 분리하고 `runId`로 연결한다.

`tradeResult`를 `HarnessRunEntity`에 중복 저장하지 않는 이유는 이미 실행 이력을 `TradeRecordEntity`에 기록하기 때문이다.

### HarnessRunResponse와 HarnessRunDetail을 분리하는 이유

`HarnessRunResponse`는 방금 실행한 런타임 결과를 표현한다.

`HarnessRunDetail`은 DB에 저장된 이력 데이터를 조합해서 표현한다.

두 모델을 분리하는 이유는 아직 `HarnessRunResult` 전체를 DB에서 복원하지 않기 때문이다. 런타임 결과와 저장 이력 조회 결과를 같은 모델로 표현하면 어떤 필드가 영속화된 값인지 구분하기 어렵다.

## Open Questions

다음 설계 단계에서 결정해야 할 질문은 다음과 같다.

- 개발용 `reset()` API를 운영에서도 유지할 것인가?
- Snapshot JSON 값 중 나중에 검색이나 집계가 필요한 필드는 별도 컬럼 또는 Entity로 분리할 것인가?

## Recommended Next Step

다음 단계는 `HarnessStepRecorder`의 실행 블록 계측 범위를 넓힐지 결정하는 것이다.

현재 기본 단건 조회 API는 DB 기반 `HarnessRunDetail`로 이동했고, 메모리 기반 Run 이력 보관은 제거됐다.

```text
GET /api/harness/runs/{runId}
-> DB 기반 HarnessRunDetail
```

이제 Run이 어떤 순서로 진행됐는지는 Step 목록으로 확인할 수 있다.

```text
HarnessStepResult
-> type
-> status
-> message
-> startedAt
-> finishedAt
```

현재 `LOAD_PORTFOLIO`, `LOAD_MARKET`, `RUN_INVESTMENT_AGENT`, `VALIDATE_DECISION`, `EXECUTE_TRADE` Step은 `HarnessStepRecorder`가 실제 작업을 감싸며 `startedAt`, `finishedAt`을 기록한다.

나머지 Step은 아직 결과를 보고 기록하는 방식이다. 이 경우 `HarnessStepRecorder`가 Step 기록 시점에 `recordedAt`을 한 번 생성하고, 그 값을 `startedAt`, `finishedAt`에 동일하게 넣는다.

다음 작업에서는 이 방식을 다른 Step까지 확장할지 판단하는 것이 좋다.

```text
1. CHECK_STEP_LIMIT도 Recorder가 감쌀 대상인가?
2. Step limit 판정은 실행 블록 계측보다 별도 helper가 더 적절한가?
3. Step limit 검사 시점이 현재 순서에서 맞는가?
```

현재 추천 방향은 `CHECK_STEP_LIMIT`를 무리하게 실행 블록으로 감싸기보다, Step limit 판정 책임을 더 명확히 분리할지 검토하는 것이다.

이유는 다음과 같다.

- 상태 조회, Agent 판단, Risk Guard 검증, 거래 실행에서 실행 블록 계측 방식이 먼저 검증됐다.
- `CHECK_STEP_LIMIT`는 외부 작업 실행이라기보다 Harness 내부 판정에 가깝다.
- 따라서 같은 Recorder API로 감싸기보다, Step limit 계산과 Step 기록의 책임을 분리하는 것이 더 자연스러울 수 있다.
- 이후 Tool 호출, Broker API 호출, Retry 정책을 붙일 때 이 구조가 필요해진다.
