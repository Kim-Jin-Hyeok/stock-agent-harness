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

HarnessRunResult.toolResults
-> List<HarnessToolExecutionSnapshot>
-> toolExecutionSnapshotsJson
```

현재 조회 기준은 기능별로 다르다.

```text
getRunSummaries()
-> DB 기반 HarnessRunSummary 목록 조회

GET /api/harness/runs/{runId}
-> DB에 저장된 Run 메타데이터, 판단 스냅샷, 리스크 스냅샷,
   포트폴리오 스냅샷, 시장 스냅샷, Tool 실행 스냅샷,
   Step 이력, Trade 이력을 조합한 HarnessRunDetail 조회

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
toolExecutionSnapshotsJson
```

이 Entity는 Run 메타데이터와 Harness 관점에서 중요한 실행 당시 스냅샷을 저장한다.

`decisionSnapshotJson`은 Agent가 어떤 투자 판단을 했는지 저장한다.

`riskCheckSnapshotJson`은 Harness/Risk Guard가 그 판단을 승인했는지, 거절했다면 어떤 `reasonCode`로 거절했는지 저장한다.

`portfolioSnapshotJson`은 Agent가 판단할 때 참조한 포트폴리오 상태를 저장한다.

`marketSnapshotJson`은 Agent가 판단할 때 참조한 시장 상태를 저장한다.

`toolExecutionSnapshotsJson`은 Run에서 발생한 Tool 실행 결과를 호출 순서대로 저장한다. 각 실행 결과에는 원본 Tool 요청도 포함하므로 출력이 없는 실패나 차단 결과에서도 어떤 입력으로 실행을 시도했는지 확인할 수 있다. 신규 Run에서 Tool 호출이 없으면 빈 JSON 배열을 저장하고, 저장 기능 추가 전 데이터처럼 값이 `null`이면 조회할 때 빈 목록으로 복원한다. Tool 출력은 커질 수 있으므로 이 필드는 `@Lob`으로 관리한다.

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

### HarnessToolExecutionSnapshot

패키지 경로:

```text
src/main/java/com/stock/harness/persistence/HarnessToolExecutionSnapshot.java
src/main/java/com/stock/harness/persistence/HarnessToolRequestSnapshot.java
```

현재 필드:

```text
status
type
reasonCode
reason
portfolioSnapshot
marketSnapshot
currentPriceSnapshot
request
currentPriceSource
```

이 객체는 JPA Entity가 아니라 `HarnessToolExecutionResult`를 JSON으로 저장하기 위한 영속화 스냅샷이다.

Tool 출력은 타입에 따라 `HarnessPortfolioSnapshot`, `HarnessMarketSnapshot`, `HarnessCurrentPriceSnapshot`으로 변환한다. 권한 거절이나 실행 실패처럼 출력이 없는 결과도 상태와 사유를 보존할 수 있도록 출력 필드는 null을 허용한다.

`request`는 런타임의 `HarnessToolRequest`를 `HarnessToolRequestSnapshot`으로 변환한 값이다. 현재는 `type`과 `symbol`을 저장하며, 실행 성공 여부와 관계없이 원본 요청을 남긴다. 따라서 `GET_CURRENT_PRICE` 실행이 실패해 `currentPriceSnapshot`이 null이어도 요청한 symbol을 확인할 수 있다.

기존 최상위 `type`은 API와 저장 데이터 계약의 호환성을 위해 유지한다. 신규 실행 결과에서는 `type`과 `request.type`이 같도록 팩토리 메서드가 생성한다. 원본 요청 저장 기능 추가 전에 생성된 JSON에는 `request` 필드가 없으므로 역직렬화 결과가 null일 수 있다.

`currentPriceSource`는 현재가가 이번 조회에서 캐시로 해결됐는지 Provider를 호출해 가져왔는지 저장한다. 현재가 조회가 아닌 Tool 또는 출력이 없는 실패 결과에서는 null이다. 조회 출처 저장 기능 추가 전 JSON에도 이 필드가 없으므로 역직렬화 결과가 null일 수 있다.

`HarnessCurrentPriceSnapshot`은 현재가 조회 당시의 종목 코드, 가격, Provider가 가격을 관측한 시각을 저장한다.

```text
src/main/java/com/stock/harness/persistence/HarnessCurrentPriceSnapshot.java

symbol
priceKrw
observedAt
```

`observedAt`은 `Instant`로 저장한다. 서버와 시장의 시간대가 달라도 동일한 절대 시각으로 비교하기 위한 선택이다. 이 필드는 런타임의 `CurrentPriceSnapshot.observedAt`에서 복사되며 실행 API와 Run 상세 조회 API의 `currentPriceSnapshot.observedAt`에도 포함된다.

신규 Tool 실행 결과에서는 `observedAt`이 필수다. 관측 시각 저장 기능 추가 전에 생성된 JSON에는 이 필드가 없으므로, 과거 Run을 역직렬화할 때는 `observedAt`이 null일 수 있다. 과거 저장 데이터 조회의 호환성을 위해 영속화 스냅샷 자체에서 null을 거부하지 않고, 신규 실행 결과의 필수 계약은 `HarnessToolResultValidator`가 검증한다.

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

List<HarnessToolExecutionSnapshot> -> JSON
JSON -> List<HarnessToolExecutionSnapshot>
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
toolExecutionSnapshots
steps
tradeRecords
```

`decisionSnapshot`, `riskCheckSnapshot`, `portfolioSnapshot`, `marketSnapshot`, `toolExecutionSnapshots`는 DB에 저장된 JSON 문자열을 다시 객체로 복원한 값이다.

`steps`는 `HarnessStepEntity` 목록에서 복원한 값이다.

`tradeRecords`는 `TradeRecordEntity` 기준의 거래 이력 조회 결과다.

## Current APIs

### Run 실행

```text
POST /api/harness/run
```

새 Harness Run을 실행하고, 방금 실행한 결과와 해당 Run의 거래 이력을 함께 반환한다. 런타임 Tool 실행 결과는 `HarnessRunResponse.toolResults`에 포함된다.

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

DB에 저장된 Run 메타데이터, 판단 스냅샷, 리스크 스냅샷, 포트폴리오 스냅샷, 시장 스냅샷, Tool 실행 스냅샷, Step 이력, Trade 이력을 조합해 `HarnessRunDetail`을 반환한다.

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
toolExecutionSnapshots
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

현재는 메타데이터, Step, Trade, 판단 스냅샷, 리스크 스냅샷, 포트폴리오 스냅샷, 시장 스냅샷, Tool 실행 스냅샷처럼 저장 책임과 조회 목적을 설명할 수 있는 값부터 분리한다.

### Snapshot JSON으로 저장하는 이유

`decision`, `riskCheckResult`, `portfolioSnapshot`, `marketSnapshot`, `toolResults`는 Run 당시 Agent와 Harness가 어떤 상태를 보고 판단했는지 보여준다.

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

Tool 결과도 같은 원칙을 따른다.

```text
POST HarnessRunResponse.toolResults
-> 런타임 HarnessToolExecutionResult
-> output.currentPriceSource에서 CACHE 또는 PROVIDER 확인

GET HarnessRunDetail.toolExecutionSnapshots
-> DB에서 복원한 HarnessToolExecutionSnapshot
-> currentPriceSource에서 CACHE 또는 PROVIDER 확인
```

## Open Questions

다음 설계 단계에서 결정해야 할 질문은 다음과 같다.

- 개발용 `reset()` API를 운영에서도 유지할 것인가?
- Snapshot JSON 값 중 나중에 검색이나 집계가 필요한 필드는 별도 컬럼 또는 Entity로 분리할 것인가?

## Current Step Recording Flow

현재 `HarnessStepRecorder`는 Harness Run의 주요 실행 블록을 감싸며 `startedAt`, `finishedAt`을 기록한다.

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

현재 Run의 Step 기록 순서는 다음과 같다.

```text
LOAD_PORTFOLIO
LOAD_MARKET
CHECK_STEP_LIMIT
RUN_INVESTMENT_AGENT
VALIDATE_AGENT_ACTION
VALIDATE_DECISION
EXECUTE_TRADE
LOAD_FINAL_PORTFOLIO
```

Agent가 최종 판단이 아니라 Tool 요청을 반환하면 Step 흐름은 다음처럼 달라진다.

```text
LOAD_PORTFOLIO
LOAD_MARKET
CHECK_STEP_LIMIT
RUN_INVESTMENT_AGENT
VALIDATE_AGENT_ACTION
VALIDATE_TOOL_REQUEST
AUTHORIZE_TOOL_REQUEST
CHECK_DUPLICATE_TOOL_REQUEST
CHECK_TOOL_CALL_LIMIT
EXECUTE_TOOL_REQUEST
VALIDATE_TOOL_RESULT
CHECK_STEP_LIMIT
RUN_INVESTMENT_AGENT
VALIDATE_AGENT_ACTION
VALIDATE_DECISION
EXECUTE_TRADE
LOAD_FINAL_PORTFOLIO
```

현재 Tool 실행기는 `GET_PORTFOLIO`, `GET_MARKET`, `GET_CURRENT_PRICE` 조회 Tool을 실행할 수 있다. 실행에 성공한 결과는 계약 검증을 통과한 뒤 `HarnessRunContext.toolResults`에 추가되고, Harness는 갱신된 Context로 Agent를 다시 실행한다. Agent가 `FINAL_DECISION`을 반환하면 기존 Risk Guard와 Trade Executor 흐름으로 진행한다.

Run 이력용 Tool 결과 목록은 Agent Context와 목적이 다르다. Agent Context에는 검증을 통과한 성공 결과만 들어가지만, Run 이력에는 성공 결과뿐 아니라 실행 실패, 권한 거절, 중복으로 건너뛴 결과도 남는다. 따라서 Run이 중간에 실패해도 실패 전에 수집된 Tool 결과를 저장하고 API에서 확인할 수 있다.

Agent가 다시 `REQUEST_TOOL`을 반환하면 같은 흐름을 반복한다. 반복 횟수는 `HarnessAgentStepBudget`이 제한하며, Budget을 모두 사용하면 다음 Agent를 호출하지 않고 Run을 실패로 종료한다.

`CHECK_TOOL_CALL_LIMIT`는 권한 검사와 중복 검사를 통과한 Tool을 실제로 실행하기 직전에 Tool Call Budget을 확인한 결과다. 권한이 거절되거나 중복으로 차단된 요청은 Tool 실행으로 이어지지 않으므로 Budget을 소비하지 않는다.

Tool 실행을 시도하면 성공 여부와 관계없이 Budget을 소비한다. 재시도도 실제 Tool 실행이므로 시도할 때마다 Budget을 추가로 소비한다. 외부 API를 호출한 뒤 실패할 수도 있으므로 성공한 결과만 계산하면 실제 호출 비용을 제한할 수 없기 때문이다.

```text
Tool call allowed. used={usedCalls}, max={maxCalls}
Tool call limit exceeded. used={usedCalls}, max={maxCalls}
```

`LOAD_PORTFOLIO`는 Agent 판단에 사용될 초기 포트폴리오 상태를 조회한다.

`LOAD_FINAL_PORTFOLIO`는 거래 실행 이후 Run 결과로 저장할 최종 포트폴리오 상태를 조회한다.

두 포트폴리오 조회는 의미가 다르므로 별도 Step으로 기록한다. 이후 포트폴리오 조회가 Broker API 또는 Cache/Adapter 계층으로 이동하더라도, Harness는 어떤 시점의 상태를 읽었는지 Step 이력으로 추적할 수 있다.

`CHECK_STEP_LIMIT`는 Agent를 실행하기 직전에 Agent Step Budget을 확인한 결과다. 기록된 전체 Harness Step 수가 아니라 `investmentAgent.next(context)` 호출 횟수를 제한한다.

Budget에 남은 횟수가 있으면 사용 횟수를 하나 증가시키고 `COMPLETED`로 기록한 뒤 Agent를 실행한다.

```text
Agent step allowed. used={usedSteps}, max={maxSteps}
```

Budget을 모두 사용했다면 `CHECK_STEP_LIMIT`를 `FAILED`로 기록하고 Agent를 추가로 실행하지 않는다. 따라서 제한 실패 이후에는 투자 판단 검증이나 거래 실행으로 넘어가지 않는다.

```text
Agent step limit exceeded. used={usedSteps}, max={maxSteps}
```

## Run Limits

`HarnessRunLimits`는 Run 하나에 적용되는 실행 제한을 표현한다.

```text
src/main/java/com/stock/harness/HarnessRunLimits.java
```

현재는 다음 값을 가진다.

```text
maxSteps
maxToolCalls
maxToolRetries
```

현재 `maxSteps`는 전체 `HarnessStepResult` 개수가 아니라 한 Run에서 Agent가 판단할 수 있는 최대 횟수다.

```text
maxSteps = 1
-> 최초 Agent 판단만 허용

maxSteps = 2
-> 최초 Agent 판단과 Tool 결과를 받은 재판단 허용
```

`HarnessProperties.maxSteps`는 애플리케이션의 기본 설정값이다.

`HarnessProperties.maxToolCalls`는 한 Run에서 실행을 시도할 수 있는 최대 Tool 호출 횟수다. `0`이면 Agent 판단은 허용하지만 Tool 실행은 허용하지 않는다.

`HarnessProperties.maxToolRetries`는 최초 Tool 실행이 `TOOL_EXECUTION_FAILED`로 끝났을 때 추가로 허용할 재시도 횟수다. `0`이면 재시도하지 않고, `1`이면 최초 실행을 포함해 최대 두 번 실행할 수 있다.

현재 기본 설정은 추가 재시도 1회다.

```yaml
harness:
  max-tool-retries: 1
```

현재가 Provider 호출 한도는 한 Run에서 캐시 미스 이후 실제 Provider를 호출할 수 있는 최대 횟수다.

```yaml
harness:
  max-provider-calls: 5
```

`HarnessRunLimits.maxSteps`, `HarnessRunLimits.maxToolCalls`, `HarnessRunLimits.maxToolRetries`, `HarnessRunLimits.maxProviderCalls`는 Run 시작 시점에 확정되어 해당 Run에 적용되는 제한값이다.

현재 흐름은 다음과 같다.

```text
application.yml
-> HarnessProperties(maxSteps, maxToolCalls, maxToolRetries, maxProviderCalls)
-> InvestmentHarness.createContext()
-> HarnessRunLimits(maxSteps, maxToolCalls, maxToolRetries, maxProviderCalls)
-> HarnessRunContext(limits)
-> HarnessAgentStepBudget(maxSteps)
-> HarnessToolCallBudget(maxToolCalls)
-> HarnessProviderCallBudget(maxProviderCalls)
-> HarnessToolRetryPolicy(maxToolRetries)
```

Budget 및 재시도 관련 클래스의 패키지 경로는 다음과 같다.

```text
src/main/java/com/stock/harness/execution/limit/HarnessAgentStepBudget.java
src/main/java/com/stock/harness/execution/limit/HarnessToolCallBudget.java
src/main/java/com/stock/harness/execution/limit/HarnessProviderCallBudget.java
src/main/java/com/stock/harness/execution/limit/HarnessProviderCallLimitExceededException.java
src/main/java/com/stock/harness/execution/retry/HarnessToolRetryPolicy.java
```

Budget은 사용 횟수를 가지는 Run 전용 객체다. Spring Bean으로 등록하지 않고 `InvestmentHarness.run()`에서 매번 새로 생성하므로 서로 다른 Run이 상태를 공유하지 않는다.

`HarnessToolRetryPolicy`도 Spring Bean으로 등록하지 않는다. Run 제한으로 생성되는 상태 없는 정책 객체이며, 실행 결과와 이미 사용한 재시도 횟수를 받아 재시도 여부만 판단한다.

```text
result.status == FAILED
result.reasonCode == TOOL_EXECUTION_FAILED
usedRetries < maxRetries
-> 재시도 허용
```

`TOOL_NOT_SUPPORTED`, 권한 거절, 중복 요청, Provider 호출 한도 초과, 요청 및 결과 계약 위반은 같은 호출을 반복해도 해결되지 않으므로 재시도하지 않는다.

`HarnessRunContext`는 `maxSteps`를 직접 들지 않고 `HarnessRunLimits`를 가진다.

```text
HarnessRunContext
-> runId
-> limits
-> allowedTools
-> portfolioSnapshot
-> marketSnapshot
-> toolResults
```

이 구조를 통해 새로운 Run 단위 실행 제한이 생기더라도 `HarnessRunContext`에 개별 필드를 계속 추가하지 않고 `HarnessRunLimits` 안에서 관리할 수 있다.

단, 현재 `HarnessRunLimits`는 DB에 저장하지 않는다.

현재 제한값은 애플리케이션 설정에서 모든 Run에 동일하게 적용하며, Run별 override와 제한값 조회 요구가 없기 때문에 저장 컬럼을 먼저 만들지 않는다.

현재는 `CHECK_STEP_LIMIT`에 Agent Step 사용량이, `CHECK_TOOL_CALL_LIMIT`에 Tool 호출 사용량이 남는다.

## Harness Tool Types

`HarnessToolType`은 Harness가 Agent에게 허용할 수 있는 Tool 종류를 표현한다.

```text
src/main/java/com/stock/harness/tool/HarnessToolType.java
```

현재 Harness에는 조회 Tool 요청을 권한 검사하고 실행하는 Tool Calling 흐름이 구현되어 있다. `HarnessToolType`은 이 흐름에서 Agent에게 열 수 있는 Tool의 경계를 정의한다.

현재 허용 후보는 읽기 전용 Tool만 둔다.

```text
GET_PORTFOLIO
GET_MARKET
GET_CURRENT_PRICE
```

`BUY`, `SELL`, `EXECUTE_TRADE`는 아직 Tool 타입에 넣지 않는다.

현재 거래 실행은 Agent가 직접 호출하는 Tool이 아니라, Harness가 Risk Guard 검증 이후 `TradeExecutor`를 통해 통제하는 흐름이다.

주문 실행 Tool을 너무 일찍 열면 Agent 권한, Risk Guard, Step 기록, Trade 이력 저장의 책임 경계가 섞일 수 있다.

따라서 현재 단계의 `HarnessToolType`은 "Agent에게 어떤 조회 Tool을 열 수 있는가"를 표현하는 최소 모델로 본다.

`HarnessAllowedTools`는 이번 Run에서 Agent에게 허용된 Tool 목록을 표현한다.

```text
src/main/java/com/stock/harness/tool/HarnessAllowedTools.java
```

현재는 다음 값을 가진다.

```text
types
```

`allows(type)`는 특정 Tool이 이번 Run에서 허용되는지 확인한다.

기본 읽기 전용 Tool 목록은 `HarnessAllowedTools.readOnly()`에서 생성한다.

```text
HarnessAllowedTools.readOnly()
-> GET_PORTFOLIO
-> GET_MARKET
-> GET_CURRENT_PRICE
```

이렇게 두는 이유는 `InvestmentHarness`가 구체적인 Tool enum 목록을 직접 만들지 않도록 하기 위해서다.

`InvestmentHarness`는 "기본 읽기 전용 Tool을 허용한다"는 의도만 표현하고, 실제 기본 목록은 `HarnessAllowedTools`가 관리한다.

현재 `HarnessRunContext`는 Run 실행 제한, 허용 Tool 목록, 검증된 Tool 실행 결과를 함께 가진다.

```text
HarnessRunContext
-> runId
-> limits
-> allowedTools
-> portfolioSnapshot
-> marketSnapshot
-> toolResults
```

기본 `InvestmentAgent` 구현은 아직 Tool 요청을 만들지 않는다.

현재는 `context.allowedTools().types()`를 읽어 판단 reason에 남기는 수준이다.

```text
allowedTools=[GET_PORTFOLIO, GET_MARKET, GET_CURRENT_PRICE]
```

다만 Harness의 Tool Calling 실행 구조는 이미 마련되어 있으므로, 테스트용 Agent가 `REQUEST_TOOL`을 반환하면 권한 검사, Budget 검사, Tool 실행, 결과 검증, 재판단 흐름이 동작한다.

## HarnessAllowedTools Immutability

`HarnessAllowedTools`는 생성 시 전달받은 List를 `List.copyOf(types)`로 복사한다.

이를 통해 생성 이후 원본 List가 변경되어도 Run에 허용된 Tool 목록은 바뀌지 않는다.

`types()`로 반환되는 List도 수정 불가능한 List다.

따라서 `HarnessAllowedTools`는 현재 Tool 목록 값 객체로서 필요한 최소 불변성을 가진다.

다만 현재는 중복 Tool이나 빈 Tool 목록은 별도로 막지 않는다.

현재 기본 생성 흐름은 `HarnessAllowedTools.readOnly()`로 고정되어 있고, Tool 목록도 읽기 전용 Tool로만 구성되기 때문이다.

## Harness Tool Request

`HarnessToolRequest`는 Agent 또는 Agent Loop가 호출하고 싶은 Tool을 표현한다.

```text
src/main/java/com/stock/harness/tool/HarnessToolRequest.java
```

현재는 다음 값을 가진다.

```text
type
symbol
```

`HarnessAllowedTools`와 `HarnessToolRequest`는 서로 다른 의미를 가진다.

```text
HarnessAllowedTools
-> 이번 Run에서 무엇이 허용되어 있는가

HarnessToolRequest
-> 이번에 무엇을 호출하고 싶은가
```

현재 `HarnessToolRequest`는 Tool 실행 입력값으로 사용된다. `symbol`은 `GET_CURRENT_PRICE`처럼 종목을 지정해야 하는 Tool의 인자다. `GET_PORTFOLIO`와 `GET_MARKET`은 symbol을 사용하지 않는다.

요청 의도를 명확하게 표현하기 위해 다음 정적 팩토리를 제공한다.

```text
HarnessToolRequest.portfolio()
HarnessToolRequest.market()
HarnessToolRequest.currentPrice(symbol)
```

기존 1인자 생성자는 symbol을 사용하지 않던 호출부와의 호환성을 위해 남아 있다. 새 요청 코드는 정적 팩토리를 기본으로 사용한다.

이 요청은 계약 검증을 통과한 뒤 `HarnessToolAuthorizer`에서 `HarnessAllowedTools.allows(request.type())`로 판정한다.

```text
HarnessToolRequest(type, symbol)
-> HarnessToolRequestValidator.validate(...)
-> HarnessToolAuthorizer.authorize(...)
-> HarnessToolAuthorizationResult
-> HarnessToolExecutor.execute(...)
-> HarnessToolExecutionResult
```

## Harness Tool Request Validation

Agent가 `HarnessToolRequest`를 반환했다고 해서 Harness가 요청 내용을 바로 권한 검사나 실행에 사용하지 않는다. 먼저 요청 객체와 Tool 타입이 실행 가능한 형태인지 검증한다.

패키지 경로:

```text
src/main/java/com/stock/harness/tool/validation/HarnessToolRequestValidator.java
src/main/java/com/stock/harness/tool/validation/HarnessToolRequestValidationResult.java
src/main/java/com/stock/harness/tool/validation/HarnessToolRequestValidationStatus.java
src/main/java/com/stock/harness/tool/validation/HarnessToolRequestValidationReasonCode.java
```

현재 검증 규칙은 다음과 같다.

```text
HarnessToolRequest != null
HarnessToolRequest.type != null
GET_CURRENT_PRICE -> symbol != null && !symbol.isBlank()
```

검증 상태는 다음 두 가지다.

```text
VALID
INVALID
```

검증 사유 코드는 다음과 같다.

```text
TOOL_REQUEST_VALID
REQUEST_MISSING
TOOL_TYPE_MISSING
SYMBOL_MISSING
```

`InvestmentHarness`는 유효한 `REQUEST_TOOL` Action을 받은 뒤 `VALIDATE_TOOL_REQUEST` Step을 기록한다. 검증에 실패하면 `HarnessToolAuthorizer`, 중복 검사, Tool Call Budget, `HarnessToolExecutor`로 진행하지 않고 Run을 실패로 종료한다.

Agent Action 검증과 Tool 요청 검증의 책임은 다르다.

```text
VALIDATE_AGENT_ACTION
-> REQUEST_TOOL Action에 toolRequest가 존재하는지 확인
-> Action 타입과 payload 조합을 확인

VALIDATE_TOOL_REQUEST
-> toolRequest 내부에 실행에 필요한 값이 있는지 확인
```

Tool 요청 검증과 권한 검사도 서로 다른 판단이다.

```text
Tool request validation
-> 요청 형식이 올바른가?

Tool authorization
-> 올바른 요청이 이번 Run에서 허용됐는가?
```

예를 들어 `type == null`은 허용되지 않은 Tool이 아니라 실행할 Tool 종류가 없는 잘못된 요청이므로 `TOOL_TYPE_MISSING`으로 실패한다.

## Harness Tool Authorization

`HarnessToolAuthorizer`는 Agent 또는 Agent Loop가 요청한 Tool이 이번 Run에서 허용된 Tool인지 판정한다.

패키지 경로:

```text
src/main/java/com/stock/harness/tool/HarnessToolAuthorizer.java
```

현재 판정 흐름은 다음과 같다.

```text
HarnessAllowedTools
HarnessToolRequest
-> HarnessToolAuthorizer.authorize(...)
-> HarnessToolAuthorizationResult
```

허용된 Tool이면 다음 결과를 반환한다.

```text
status = ALLOWED
reasonCode = TOOL_ALLOWED
```

허용되지 않은 Tool이면 다음 결과를 반환한다.

```text
status = DENIED
reasonCode = TOOL_NOT_ALLOWED
```

`HarnessToolAuthorizationResult`는 단순 boolean 대신 `status`, `type`, `reasonCode`, `reason`을 가진다.

패키지 경로:

```text
src/main/java/com/stock/harness/tool/HarnessToolAuthorizationResult.java
src/main/java/com/stock/harness/tool/HarnessToolAuthorizationStatus.java
src/main/java/com/stock/harness/tool/HarnessToolAuthorizationReasonCode.java
```

boolean만 반환하지 않는 이유는 Harness 관점에서 "거부됐다"보다 "왜 거부됐는지"가 더 중요하기 때문이다.

예를 들어 Agent가 허용되지 않은 Tool을 요청했다면 Harness는 다음 정보를 남길 수 있어야 한다.

```text
requestedTool = GET_PORTFOLIO
status = DENIED
reasonCode = TOOL_NOT_ALLOWED
reason = Harness tool is not allowed.
```

이 정보는 이후 Agent가 다음 판단에 활용하거나, Run 상세 이력에서 실패 원인을 추적할 때 사용할 수 있다.

현재는 권한 판정을 통과한 요청만 중복 검사와 Tool Call Budget 검사를 거쳐 실제 Tool 실행으로 이어진다.

권한 판정과 Tool 실행은 분리한다.

```text
Tool authorization
-> 이 Tool을 호출해도 되는가?

Tool execution
-> 허용된 Tool을 실제로 실행했는가?
```

## Harness Tool Authorization Step

Tool 권한 판정 결과를 Harness 실행 이력에서 표현하기 위해 `AUTHORIZE_TOOL_REQUEST` Step 타입을 정의한다.

패키지 경로:

```text
src/main/java/com/stock/harness/HarnessStepType.java
```

현재 Step 타입 목록에는 다음 값이 포함된다.

```text
VALIDATE_TOOL_REQUEST
AUTHORIZE_TOOL_REQUEST
CHECK_DUPLICATE_TOOL_REQUEST
CHECK_TOOL_CALL_LIMIT
EXECUTE_TOOL_REQUEST
```

이 Step은 Agent 또는 Agent Loop가 요청한 Tool에 대해 Harness가 권한 판정을 수행했다는 사실을 표현한다.

예상 흐름은 다음과 같다.

```text
HarnessToolRequest
-> HarnessToolAuthorizer.authorize(...)
-> HarnessToolAuthorizationResult
-> HarnessStepType.AUTHORIZE_TOOL_REQUEST
```

현재 `InvestmentHarness.run()`은 Agent가 `REQUEST_TOOL`을 반환한 경우 이 Step을 실제 실행 흐름에 기록한다.

권한 판정이 허용되면 `CHECK_DUPLICATE_TOOL_REQUEST`, `CHECK_TOOL_CALL_LIMIT`를 통과한 뒤 `EXECUTE_TOOL_REQUEST` Step을 기록한다.

```text
HarnessToolRequest
-> HarnessToolExecutor.execute(...)
-> HarnessToolExecutionResult
-> HarnessStepType.EXECUTE_TOOL_REQUEST
```

결과가 `TOOL_EXECUTION_FAILED`이고 재시도 횟수가 남았다면 `CHECK_TOOL_CALL_LIMIT`와 `EXECUTE_TOOL_REQUEST`를 다시 수행한다. 권한과 요청 내용은 이미 확인됐으므로 `VALIDATE_TOOL_REQUEST`, `AUTHORIZE_TOOL_REQUEST`, `CHECK_DUPLICATE_TOOL_REQUEST`는 반복하지 않는다.

권한 판정이 거절되면 Tool 실행기로 넘어가지 않는다. 이 경우 `AUTHORIZE_TOOL_REQUEST`는 `FAILED`로 기록되고, Run은 `RUN_FAILED`로 종료된다.

현재 단계에서 검증할 내용은 다음과 같다.

```text
1. Tool 요청 통제와 실행에 필요한 Step 타입을 코드에 정의한다.
2. HarnessStepEntity가 각 Step 타입을 저장할 수 있다.
3. HarnessStepRepository가 각 Step 타입을 조회할 수 있다.
4. Tool 요청 Run에서 권한, 중복, Budget 확인과 실행 시도가 순서대로 Step에 남는다.
```

## Harness Tool Request Deduplication

동일 Run에서 Agent가 완전히 같은 Tool 요청을 반복하면 Harness는 두 번째 요청을 실제로 실행하지 않는다. Agent Loop 오류가 외부 API 중복 호출로 이어지는 것을 막기 위한 Run 단위 실행 통제다.

패키지 경로:

```text
src/main/java/com/stock/harness/execution/tool/HarnessToolRequestTracker.java
```

`HarnessToolRequestTracker`는 처리된 요청을 `Set<HarnessToolRequest>`에 등록하고 `tryRegister(request)` 결과로 최초 요청과 중복 요청을 구분한다.

`HarnessToolRequestTracker`는 record의 값 동등성을 사용하므로 `type`과 `symbol`이 모두 같은 요청만 중복으로 판단한다.

```text
GET_PORTFOLIO -> 최초 요청, 허용
GET_MARKET    -> 다른 요청, 허용
GET_PORTFOLIO -> 중복 요청, 차단
GET_CURRENT_PRICE(005930) -> 최초 요청, 허용
GET_CURRENT_PRICE(000660) -> symbol이 다른 요청, 허용
GET_CURRENT_PRICE(005930) -> 중복 요청, 차단
```

나중에 `market`, `period` 같은 요청 필드가 더 추가되어도 같은 원칙에 따라 모든 필드가 같은 요청만 중복으로 판단한다.

Tracker는 Spring Bean으로 등록하지 않는다. 처리된 요청 목록은 애플리케이션 전체가 아니라 Run 하나에만 필요한 상태이므로 `InvestmentHarness.run()`이 실행될 때마다 새로 생성한다.

중복 검사는 다음 순서에 위치한다.

```text
VALIDATE_TOOL_REQUEST
AUTHORIZE_TOOL_REQUEST
CHECK_DUPLICATE_TOOL_REQUEST
CHECK_TOOL_CALL_LIMIT
EXECUTE_TOOL_REQUEST
```

Harness가 실패한 실행을 다시 시도하는 것은 Agent가 같은 요청을 새로 반환한 것이 아니다. 따라서 내부 재시도는 `HarnessToolRequestTracker`를 다시 거치지 않으며 중복 요청으로 차단되지 않는다.

권한이 없는 요청은 중복 여부와 관계없이 먼저 차단한다. 중복 요청은 실제 Tool 실행 전에 차단되므로 Tool Call Budget을 소비하지 않는다.

최초 요청이면 `CHECK_DUPLICATE_TOOL_REQUEST`를 `COMPLETED`로 기록한다.

```text
Tool request is not duplicate. type={type}
```

중복 요청이면 해당 Step을 `FAILED`로 기록하고 Run을 종료한다. 실행되지 않은 요청도 시도 이력에서 확인할 수 있도록 다음 `HarnessToolExecutionResult`를 저장한다.

```text
status = SKIPPED
reasonCode = DUPLICATE_TOOL_REQUEST
reason = Duplicate tool request was skipped.
output = null
```

## Agent Next Action

`AgentNextAction`은 Agent가 다음에 수행하려는 행동을 표현한다.

패키지 경로:

```text
src/main/java/com/stock/agent/AgentNextAction.java
src/main/java/com/stock/agent/AgentNextActionType.java
```

현재 타입은 다음 두 가지다.

```text
REQUEST_TOOL
FINAL_DECISION
```

`REQUEST_TOOL`은 Agent가 최종 투자 판단을 내리기 전에 추가 정보를 얻기 위해 Tool 호출을 요청하는 상태를 표현한다.

```text
AgentNextAction.requestTool(HarnessToolRequest)
-> type = REQUEST_TOOL
-> toolRequest = HarnessToolRequest
-> investmentDecision = null
```

`FINAL_DECISION`은 Agent가 더 이상 Tool을 요청하지 않고 최종 투자 판단을 반환하는 상태를 표현한다.

```text
AgentNextAction.finalDecision(InvestmentDecision)
-> type = FINAL_DECISION
-> toolRequest = null
-> investmentDecision = InvestmentDecision
```

## Harness Agent Action Validation

Agent가 `AgentNextAction`을 반환했다고 해서 Harness가 그 구조를 바로 신뢰하지 않는다. Action 타입과 실제 payload 조합이 계약에 맞는지 확인한 뒤 Tool 요청 또는 최종 판단 흐름으로 분기한다.

패키지 경로:

```text
src/main/java/com/stock/harness/agent/validation/HarnessAgentActionValidator.java
src/main/java/com/stock/harness/agent/validation/HarnessAgentActionValidationResult.java
src/main/java/com/stock/harness/agent/validation/HarnessAgentActionValidationStatus.java
src/main/java/com/stock/harness/agent/validation/HarnessAgentActionValidationReasonCode.java
```

현재 검증 규칙은 다음과 같다.

```text
AgentNextAction != null
AgentNextAction.type != null

REQUEST_TOOL
-> toolRequest != null
-> investmentDecision == null

FINAL_DECISION
-> investmentDecision != null
-> toolRequest == null
```

검증 상태는 다음 두 가지다.

```text
VALID
INVALID
```

검증 사유 코드는 다음과 같다.

```text
AGENT_ACTION_VALID
ACTION_MISSING
ACTION_TYPE_MISSING
TOOL_REQUEST_MISSING
UNEXPECTED_INVESTMENT_DECISION
INVESTMENT_DECISION_MISSING
UNEXPECTED_TOOL_REQUEST
```

`InvestmentHarness`는 `RUN_INVESTMENT_AGENT` 직후 `VALIDATE_AGENT_ACTION` Step을 기록한다. 검증이 실패하면 Tool 권한 검사, Tool 실행, Risk Guard 검증으로 진행하지 않고 Run을 실패로 종료한다.

`VALIDATE_AGENT_ACTION`과 `VALIDATE_DECISION`의 책임은 다르다.

```text
VALIDATE_AGENT_ACTION
-> Agent가 반환한 다음 행동의 구조가 올바른지 검증

VALIDATE_DECISION
-> FINAL_DECISION에 포함된 투자 판단을 Risk Guard가 검증
```

현재 `InvestmentAgent`는 `next(context)` 메서드를 제공한다.

패키지 경로:

```text
src/main/java/com/stock/agent/InvestmentAgent.java
```

현재 구현은 아직 Tool 요청을 만들지 않는다.

```text
InvestmentAgent.next(context)
-> AgentNextAction.finalDecision(decide(context))
```

즉 기존 `decide(context)`의 HOLD 판단을 `FINAL_DECISION`으로 감싸서 반환한다.

이 구조를 둔 이유는 기존 Harness 실행 흐름을 바로 깨지 않고, Agent가 나중에 Tool 요청과 최종 판단 중 하나를 반환할 수 있는 형태로 점진적으로 이동하기 위해서다.

현재 상태는 다음과 같다.

```text
AgentNextAction 모델 있음
InvestmentAgent.next(context) 있음
InvestmentHarness는 investmentAgent.next(context)를 호출함
HarnessAgentActionValidator가 AgentNextAction 계약을 검증함
VALIDATE_AGENT_ACTION Step을 Agent 실행 직후 기록함
FINAL_DECISION은 기존 Risk Guard / Trade 흐름으로 연결됨
REQUEST_TOOL이면 HarnessToolRequestValidator가 요청 계약을 검증하고 VALIDATE_TOOL_REQUEST Step을 기록함
요청 검증을 통과하면 HarnessToolAuthorizer로 권한을 판정하고 AUTHORIZE_TOOL_REQUEST Step을 기록함
권한이 허용되면 HarnessToolRequestTracker로 중복 여부를 확인하고 CHECK_DUPLICATE_TOOL_REQUEST Step을 기록함
중복 검사를 통과하면 HarnessToolCallBudget을 확인하고 CHECK_TOOL_CALL_LIMIT Step을 기록함
Tool Call Budget을 통과하면 HarnessToolExecutor로 실행을 시도하고 EXECUTE_TOOL_REQUEST Step을 기록함
TOOL_EXECUTION_FAILED이고 재시도 횟수가 남으면 Budget 확인과 Tool 실행을 반복함
HarnessToolExecutor는 GET_PORTFOLIO, GET_MARKET, GET_CURRENT_PRICE 조회 Tool을 실행함
HarnessToolExecutionResult 모델 있음
HarnessToolResultValidator가 실행 결과 계약을 검증하고 VALIDATE_TOOL_RESULT Step을 기록함
검증을 통과한 성공 결과만 HarnessRunContext에 추가함
성공, 실행 실패, 권한 거절, 중복 차단 결과는 HarnessRunResult와 Run 상세 이력에 저장함
POST 응답은 HarnessRunResponse.toolResults로 런타임 결과를 제공함
GET 상세 응답은 HarnessRunDetail.toolExecutionSnapshots로 저장 결과를 제공함
Tool Calling 기반 Agent Loop 있음
```

## Harness Tool Execution Result

`HarnessToolExecutionResult`는 Tool 실행이 어떻게 끝났는지 표현한다.

패키지 경로:

```text
src/main/java/com/stock/harness/tool/HarnessToolExecutionResult.java
src/main/java/com/stock/harness/tool/HarnessToolExecutionStatus.java
src/main/java/com/stock/harness/tool/HarnessToolExecutionReasonCode.java
src/main/java/com/stock/harness/tool/HarnessToolRequest.java
```

현재 필드는 다음과 같다.

```text
status
type
request
reasonCode
reason
output
```

`status`는 Tool 실행의 최종 상태를 표현한다.

```text
EXECUTED
FAILED
SKIPPED
```

`reasonCode`는 Tool 실행이 어떤 이유로 끝났는지 코드로 분류한다.

```text
TOOL_EXECUTED
TOOL_EXECUTION_FAILED
TOOL_NOT_SUPPORTED
TOOL_AUTHORIZATION_DENIED
DUPLICATE_TOOL_REQUEST
```

현재 생성 흐름은 다음과 같다.

```text
HarnessToolExecutionResult.executed(request, output)
-> status = EXECUTED
-> type = request.type
-> request = 원본 Tool 요청
-> reasonCode = TOOL_EXECUTED
-> reason = Harness tool execution completed.
-> output = Tool 조회 결과

HarnessToolExecutionResult.executionFailed(request, cause)
-> status = FAILED
-> type = request.type
-> request = 원본 Tool 요청
-> reasonCode = TOOL_EXECUTION_FAILED
-> reason = Tool execution failed. cause={예외 메시지}
-> output = null

HarnessToolExecutionResult.notSupported(request)
-> status = FAILED
-> request = 원본 Tool 요청
-> reasonCode = TOOL_NOT_SUPPORTED
-> reason = Tool execution is not supported yet.

HarnessToolExecutionResult.authorizationDenied(request)
-> status = FAILED
-> request = 원본 Tool 요청
-> reasonCode = TOOL_AUTHORIZATION_DENIED
-> reason = Tool authorization denied.

HarnessToolExecutionResult.duplicateRequest(request)
-> status = SKIPPED
-> request = 원본 Tool 요청
-> reasonCode = DUPLICATE_TOOL_REQUEST
-> reason = Duplicate tool request was skipped.
```

이 모델은 `HarnessToolExecutor`가 Tool 실행 결과를 Harness에 돌려줄 때 사용하는 값 객체다.

`type`과 `request.type`은 현재 중복되지만 역할이 다르다. 최상위 `type`은 기존 API 응답과 조회 코드의 호환성을 유지하고, `request`는 symbol을 포함한 실제 입력 전체를 보존한다. 기존 호출부를 위한 type 기반 오버로드도 남아 있지만, 실제 Harness 실행 경로는 원본 요청이 유실되지 않도록 request 기반 팩토리를 사용한다.

현재 `HarnessToolExecutor`는 `GET_PORTFOLIO` 요청에 `PortfolioSnapshot`을, `GET_MARKET` 요청에 `MarketSnapshot`을 담은 실행 결과를 반환한다. `GET_CURRENT_PRICE` 요청은 symbol을 `CurrentPriceService`에 전달하고 `CurrentPriceSnapshot`을 반환한다.

Tool Service 호출 중 `RuntimeException`이 발생하면 `HarnessToolExecutor`는 예외를 그대로 전파하지 않고 `TOOL_EXECUTION_FAILED` 결과로 변환한다. Tool별 Service가 각자 실패 결과를 만드는 대신 Tool 실행 경계에서 동일한 실패 계약을 적용하기 위한 구조다.

현재는 원래 예외 메시지를 실패 결과의 `reason`에 포함한다. 실제 Broker API 연동 후에는 인증정보나 외부 응답의 민감한 값이 이 메시지에 포함되지 않도록 저장 가능한 메시지로 정제하는 정책이 필요하다.

현재 `CurrentPriceService`는 요청한 symbol의 캐시를 먼저 조회한다. TTL 안의 값이 있으면 Provider를 호출하지 않고 캐시 값을 반환하고, 캐시가 없거나 만료됐으면 `CurrentPriceProvider`를 호출한 뒤 캐시에 저장할 수 있는 결과인지 확인한다.

```text
src/main/java/com/stock/market/price/CurrentPriceService.java
src/main/java/com/stock/market/price/CurrentPriceSnapshot.java
src/main/java/com/stock/market/price/cache/CurrentPriceCache.java
src/main/java/com/stock/market/price/cache/CurrentPriceCacheProperties.java
src/main/java/com/stock/market/price/config/CurrentPriceConfiguration.java
src/main/java/com/stock/market/price/lookup/CurrentPriceLookupResult.java
src/main/java/com/stock/market/price/lookup/CurrentPriceLookupSource.java
src/main/java/com/stock/market/price/provider/CurrentPriceProvider.java
src/main/java/com/stock/market/price/provider/CurrentPriceProviderCallGuard.java
src/main/java/com/stock/market/price/provider/FixedCurrentPriceProvider.java
```

현재 조회 흐름은 다음과 같다.

```text
HarnessToolExecutor
-> CurrentPriceService
-> CurrentPriceCache 조회
-> 캐시 적중이면 CurrentPriceLookupResult(snapshot, CACHE) 반환
-> 캐시 미스 또는 만료면 CurrentPriceProviderCallGuard 실행
-> HarnessProviderCallBudget 소비에 성공하면 CurrentPriceProvider 호출
-> Budget이 소진됐으면 Provider를 호출하지 않고 실패
-> Provider가 symbol, priceKrw, observedAt을 포함한 CurrentPriceSnapshot 생성
-> Provider 결과의 캐시 저장 가능 여부 확인
-> 저장 가능하면 캐시에 저장
-> CurrentPriceLookupResult(snapshot, PROVIDER) 반환
-> HarnessToolOutput에 currentPriceSnapshot과 currentPriceSource를 분리해 저장
```

`CurrentPriceLookupResult`는 가격 데이터인 `CurrentPriceSnapshot`과 이번 조회 경로인 `CurrentPriceLookupSource`를 함께 전달한다.

```text
CACHE
-> 유효한 인메모리 캐시 값을 반환함

PROVIDER
-> 캐시 미스 또는 만료 후 CurrentPriceProvider를 호출함
```

조회 출처는 가격 데이터 자체의 속성이 아니라 이번 조회 과정의 메타데이터다. 따라서 `CurrentPriceSnapshot`에 넣지 않고 별도 조회 결과로 전달한다. `HarnessToolOutput`은 기존 `currentPriceSnapshot` 필드를 유지하면서 `currentPriceSource`를 별도 필드로 제공해 기존 응답 구조 변경을 최소화한다.

`CurrentPriceSnapshot.observedAt`은 가격 데이터가 Provider에서 관측된 시각이다. 캐시에서 같은 가격을 반환하더라도 새로운 시장 관측이 발생한 것이 아니므로 원래 `observedAt`을 유지한다. 캐시 조회 시각으로 덮어쓰면 오래된 가격이 방금 관측된 값처럼 보일 수 있다.

`CurrentPriceCache`는 `ConcurrentHashMap`을 사용하는 인메모리 캐시다. 요청 symbol을 키로 사용하므로 종목별 현재가가 분리된다. 캐시 Entry에는 `CurrentPriceSnapshot`과 캐시 저장 시각인 `cachedAt`을 함께 보관한다.

현재가와 관련된 시각의 책임은 다음처럼 구분한다.

```text
observedAt
-> Provider가 가격을 관측한 시각
-> CurrentPriceSnapshot의 시장 데이터

cachedAt
-> 애플리케이션이 스냅샷을 캐시에 저장한 시각
-> TTL 만료 계산을 위한 캐시 내부 값

Harness Step recordedAt
-> Harness가 실행 단계를 기록한 시각
-> 실행 이력 추적 값
```

`CurrentPriceService`는 Provider 결과가 다음 조건을 모두 만족할 때만 캐시에 저장한다.

```text
CurrentPriceSnapshot이 null이 아님
요청 symbol과 응답 symbol이 같음
priceKrw가 0보다 큼
observedAt이 null이 아님
```

조건을 만족하지 않는 결과는 캐시에 저장하지 않지만 Service에서 예외로 바꾸지도 않는다. 결과 자체는 `HarnessToolExecutor`를 거쳐 `HarnessToolResultValidator`에 전달되며, Harness가 `OUTPUT_PAYLOAD_MISSING`, `OUTPUT_SYMBOL_MISMATCH`, `OUTPUT_PRICE_INVALID`, `OUTPUT_OBSERVED_AT_MISSING`과 같은 구체적인 계약 위반 사유를 결정한다.

Service와 Harness가 비슷한 조건을 확인하지만 목적은 다르다. Service의 검사는 잘못된 Provider 응답이 TTL 동안 반복 반환되는 캐시 오염을 막는다. Harness의 검사는 해당 Tool 결과를 Agent Context에 전달해도 되는지 판정하고 Run 이력에 결정적인 실패 사유를 남긴다.

유효하지 않은 결과를 캐시에 저장하지 않으므로 다음 조회는 다시 캐시 미스가 되고 Provider를 다시 호출할 수 있다.

TTL은 `market.current-price.cache.ttl`로 설정하며 현재 기본 실행 설정은 `30s`다. 저장 시각에 TTL을 더한 시각과 현재 시각이 같아지는 순간부터 만료로 처리한다. 만료된 Entry는 해당 symbol 조회 시 제거한다. 새로운 가격을 저장할 때도 전체 Entry를 확인해 더 이상 조회되지 않는 종목의 만료 데이터가 메모리에 계속 누적되지 않도록 정리한다.

```yaml
market:
  current-price:
    cache:
      ttl: 30s
```

`CurrentPriceCacheProperties`는 TTL이 null, 0 또는 음수이면 생성을 거부한다. 따라서 이 설정이 누락되거나 유효하지 않으면 Spring ApplicationContext 시작 단계에서 실패한다. 이는 캐시 정책 없이 애플리케이션이 실행되는 것을 막는 fail-fast 정책이다.

캐시 만료 판정과 `FixedCurrentPriceProvider`의 관측 시각 생성은 직접 `Instant.now()`를 호출하지 않고 주입받은 `Clock`을 사용한다. 운영에서는 `CurrentPriceConfiguration`이 `Clock.systemUTC()`를 Bean으로 제공하고, 테스트에서는 고정하거나 제어할 수 있는 Clock을 사용해 실제 대기 없이 TTL 경계와 `observedAt`을 검증한다.

Provider가 예외를 던지면 `CurrentPriceService`는 캐시에 값을 저장하지 않고 예외를 상위로 전달한다. 만료된 이전 가격도 대신 반환하지 않는다. 이 예외는 `HarnessToolExecutor`에서 `TOOL_EXECUTION_FAILED`로 변환되며 Harness 재시도 정책의 대상이 된다.

현재 `FixedCurrentPriceProvider`는 Broker API 연동 전 Harness 흐름을 검증하기 위해 고정 로컬 가격을 반환한다. 따라서 이 결과를 실제 시장 가격으로 사용해서는 안 된다. 실제 연동 시에는 `CurrentPriceProvider` 구현을 Broker API Adapter로 교체하고 `CurrentPriceService`와 Harness Tool 계약은 유지한다.

캐시 적중 여부와 관계없이 `HarnessToolCallBudget`은 소비한다. 이 Budget은 Agent의 논리적인 Tool 실행 시도를 제한하기 때문이다.

`HarnessProviderCallBudget`은 실제 `CurrentPriceProvider` 호출만 제한한다. `InvestmentHarness.run()`에서 Run마다 새로 생성되며, `CurrentPriceService`가 캐시 미스를 확인한 뒤 Provider를 호출하기 직전에 `CurrentPriceProviderCallGuard`를 통해 소비한다. 캐시 적중 시에는 Guard를 실행하지 않으므로 Provider Budget도 소비하지 않는다.

Provider가 예외를 던져도 외부 요청 자체는 이미 시도됐으므로 소비한 횟수를 복구하지 않는다. `TOOL_EXECUTION_FAILED` 재시도에서 Provider를 다시 호출하려면 Budget을 다시 소비해야 한다. 남은 횟수가 없으면 실제 Provider를 호출하지 않고 `PROVIDER_CALL_LIMIT_EXCEEDED` 결과를 반환한다. 이 사유는 같은 Run에서 재시도해도 한도가 복구되지 않으므로 `HarnessToolRetryPolicy`의 재시도 대상이 아니다.

현재 구조에서는 `CurrentPriceService`가 캐시 미스 이후 Provider 호출 필요 여부를 판단하므로 별도 `CHECK_PROVIDER_CALL_LIMIT` Step을 선행 기록하지 않는다. 한도 초과는 `EXECUTE_TOOL_REQUEST` Step의 `FAILED` 상태와 `HarnessToolExecutionResult.reasonCode=PROVIDER_CALL_LIMIT_EXCEEDED`로 Run 이력에 남는다.

`currentPriceSource`는 이미 발생한 조회 경로를 관찰하기 위한 정보다. 호출 허용 여부는 이 결과값으로 사후 판단하지 않고 Provider 호출 직전의 Guard와 Budget이 통제한다.

현재 캐시에는 다음 기능이 없다.

```text
Redis를 통한 서버 간 캐시 공유
최대 Entry 수와 LRU 제거 정책
동시 캐시 미스 요청을 하나로 합치는 single-flight
만료된 가격 fallback
```

현재가 TTL이 짧고 단일 애플리케이션 인스턴스를 전제로 하므로 인메모리 캐시로 시작한다. 여러 서버나 Scheduler가 같은 Broker API 호출량을 공유해야 할 때 Redis 도입을 검토한다. 인메모리 캐시는 애플리케이션 재시작 시 모두 사라진다.

`notSupported` 결과 생성 메서드는 지원하지 않는 Tool 타입이 추가될 경우를 표현하기 위해 남아 있다.

`HarnessToolAuthorizationResult`와 `HarnessToolExecutionResult`는 비슷한 구조를 가지지만 책임이 다르다.

```text
HarnessToolAuthorizationResult
-> 이 Tool을 호출해도 되는가?

HarnessToolExecutionResult
-> Tool 실행이 어떻게 끝났는가?
```

## Harness Tool Result Validation

Tool 실행 성공만으로 Agent에게 결과를 전달하지 않는다. Harness는 실행 결과가 요청한 Tool의 계약을 지키는지 별도로 검증한다.

패키지 경로:

```text
src/main/java/com/stock/harness/tool/validation/HarnessToolResultValidator.java
src/main/java/com/stock/harness/tool/validation/HarnessToolResultValidationResult.java
src/main/java/com/stock/harness/tool/validation/HarnessToolResultValidationStatus.java
src/main/java/com/stock/harness/tool/validation/HarnessToolResultValidationReasonCode.java
```

현재 검증 순서는 다음과 같다.

```text
request.type == result.type
output != null
request.type == output.type
GET_PORTFOLIO -> portfolioSnapshot != null
GET_MARKET -> marketSnapshot != null
GET_CURRENT_PRICE -> currentPriceSnapshot != null
GET_CURRENT_PRICE -> request.symbol == currentPriceSnapshot.symbol
GET_CURRENT_PRICE -> currentPriceSnapshot.priceKrw > 0
GET_CURRENT_PRICE -> currentPriceSnapshot.observedAt != null
```

검증 사유 코드는 다음과 같다.

```text
TOOL_RESULT_VALID
RESULT_TYPE_MISMATCH
OUTPUT_MISSING
OUTPUT_TYPE_MISMATCH
OUTPUT_PAYLOAD_MISSING
OUTPUT_SYMBOL_MISMATCH
OUTPUT_PRICE_INVALID
OUTPUT_OBSERVED_AT_MISSING
```

현재가 결과는 payload 존재, symbol 일치, 가격 유효성, 관측 시각 존재 순서로 검증한다. 여러 오류가 동시에 있으면 먼저 발견된 계약 위반 하나를 반환해 실패 이유를 결정적으로 유지한다.

Tool 실행 상태가 `EXECUTED`일 때만 `VALIDATE_TOOL_RESULT` 단계로 진행한다. 실행 자체가 실패하면 실행 실패 결과를 `HarnessRunResult.toolResults`와 Run 상세 이력에 남기고 검증 전 Run을 종료한다.

계약 검증이 실패하면 `VALIDATE_TOOL_RESULT` Step을 `FAILED`로 기록하고 Run을 종료한다. 이때 원래 Tool 실행 결과는 Run 이력에 남지만 Agent Context에는 추가하지 않으므로, Agent가 잘못된 결과를 근거로 다시 판단하지 않는다.

## Agent Loop

현재 Harness는 Agent Step Budget 안에서 Tool 실행 결과를 Agent에게 반복해서 전달할 수 있다.

현재 Harness는 한 Run의 Agent 판단 횟수, 논리적인 Tool 호출 수, 현재가 Provider 호출 수를 각각 제한한다. Cache 사용 여부는 조회 출처로 기록하며, 여러 Run과 서버가 공유하는 시간 단위 Rate Limit은 아직 구현하지 않는다.

`HarnessRunContext.toolResults`는 현재 Run에서 완료된 Tool 실행 결과를 보관한다. `withToolResult`는 기존 Context를 변경하지 않고 실행 결과가 추가된 새 Context를 만든다.

`InvestmentHarness`는 `RUN_INVESTMENT_AGENT` Step에서 `investmentAgent.next(context)`를 호출한다.

반환된 Action은 `HarnessAgentActionValidator`로 검증하고 `VALIDATE_AGENT_ACTION` Step에 결과를 기록한다. 유효한 `FINAL_DECISION`이면 기존처럼 `InvestmentDecision`을 꺼내 Risk Guard와 Trade Executor 흐름으로 진행한다.

유효한 `REQUEST_TOOL`이면 Harness는 `HarnessToolRequestValidator`로 요청 계약을 검사하고 `VALIDATE_TOOL_REQUEST` Step을 기록한다. 요청 검증을 통과하면 `HarnessToolAuthorizer`로 권한을 판정하고 `AUTHORIZE_TOOL_REQUEST` Step을 기록한다. 권한이 허용되면 `CHECK_DUPLICATE_TOOL_REQUEST`, `CHECK_TOOL_CALL_LIMIT`를 순서대로 수행한 뒤 `HarnessToolExecutor`로 Tool을 실행하고 `EXECUTE_TOOL_REQUEST` Step을 기록한다. 실행 결과가 성공하면 `HarnessToolResultValidator`가 계약을 검사하고 `VALIDATE_TOOL_RESULT` Step을 기록한다. 검증까지 통과한 결과만 새 Context에 추가되어 Agent의 다음 판단 입력으로 전달된다.

권한 판정 결과는 Step status와 message로 남는다.

```text
ALLOWED
-> AUTHORIZE_TOOL_REQUEST Step COMPLETED
-> message = Harness tool authorization allowed.

DENIED
-> AUTHORIZE_TOOL_REQUEST Step FAILED
-> message = Harness tool is not allowed.
```

Tool 실행 결과도 Step status와 message로 남는다.

```text
EXECUTED
-> EXECUTE_TOOL_REQUEST Step COMPLETED
-> message = Harness tool execution completed.

FAILED
-> EXECUTE_TOOL_REQUEST Step FAILED
-> message = Tool 실행 실패 사유
-> 실패한 HarnessToolExecutionResult를 Run 이력에 저장

TOOL_EXECUTION_FAILED이고 재시도 가능
-> CHECK_TOOL_CALL_LIMIT와 EXECUTE_TOOL_REQUEST를 다시 수행
-> 실패한 시도와 후속 시도를 모두 Run 이력에 저장

PROVIDER_CALL_LIMIT_EXCEEDED
-> 실제 Provider를 호출하지 않음
-> EXECUTE_TOOL_REQUEST Step을 FAILED로 기록
-> 같은 Run에서 한도가 복구되지 않으므로 재시도하지 않음

재시도할 수 없거나 재시도까지 실패
-> VALIDATE_TOOL_RESULT를 실행하지 않음
-> Agent를 다시 호출하지 않고 Run 종료
```

현재 `HarnessToolExecutor`는 `GET_PORTFOLIO`, `GET_MARKET`, `GET_CURRENT_PRICE` 조회 Tool을 실행한다. Tool 실행에 성공하면 이력에는 다음처럼 남는다.

```text
RUN_INVESTMENT_AGENT
-> Agent가 Tool 요청을 반환함

VALIDATE_AGENT_ACTION
-> Harness agent action is valid.

VALIDATE_TOOL_REQUEST
-> Harness tool request is valid.

AUTHORIZE_TOOL_REQUEST
-> Harness tool authorization allowed.

CHECK_DUPLICATE_TOOL_REQUEST
-> Tool request is not duplicate. type={type}

CHECK_TOOL_CALL_LIMIT
-> Tool call allowed. used={usedCalls}, max={maxCalls}

EXECUTE_TOOL_REQUEST
-> Harness tool execution completed.

VALIDATE_TOOL_RESULT
-> Harness tool result is valid.

RUN_INVESTMENT_AGENT
-> Agent의 최종 판단 사유

VALIDATE_AGENT_ACTION
-> Harness agent action is valid.
```

Agent Loop는 `FINAL_DECISION`이 반환되거나 실행 Budget이 소진될 때까지 반복된다. `FINAL_DECISION`이 반환되면 Risk Guard와 Trade Executor 흐름으로 진행한다. Agent Step Budget이 소진되면 다음 Agent 호출 전에 중단하고, Tool Call Budget이 소진되면 다음 Tool 실행 전에 중단한다. 현재가 Provider Budget이 소진되면 캐시 적중 결과는 계속 사용할 수 있지만 새로운 Provider 호출은 실행하지 않는다.

`TOOL_EXECUTION_FAILED`는 `HarnessToolRetryPolicy`가 허용하는 동안 즉시 재시도한다. 최초 실패 후 재시도에 성공하면 실패한 실행 Step은 `FAILED`로 유지하지만 복구된 실패로 판단해 최종 Run은 `COMPLETED`가 될 수 있다. 실패한 시도와 성공한 시도는 모두 동일한 원본 `HarnessToolRequest`와 함께 Run 이력에 남고, 최종 성공 결과만 검증 후 Agent Context에 전달한다.

재시도 횟수가 소진되거나 Tool Call Budget 또는 Provider Call Budget이 부족하면 Run을 실패시킨다. 현재는 재시도 사이의 대기 시간과 Backoff를 적용하지 않는다. `TOOL_EXECUTION_FAILED`가 아닌 실행 실패와 결과 계약 검증 실패는 재시도하지 않는다.

이후 판단해야 할 질문은 다음과 같다.

```text
1. 재시도 사이에 고정 대기 또는 지수 Backoff를 적용할 것인가?
2. 여러 Run과 서버가 공유하는 분당 또는 시간당 Provider Rate Limit을 어디에서 관리할 것인가?
```

설정 책임은 현재 다음처럼 분리되어 있다.

`HarnessProperties`는 현재 다음 값을 가진다.

```text
maxSteps
maxToolCalls
maxToolRetries
maxProviderCalls
```

`RiskProperties`는 현재 다음 값을 가진다.

```text
maxOrderRatio
maxPositionRatio
```

`HarnessSchedulerProperties`는 현재 다음 값을 가진다.

```text
enabled
```

`CurrentPriceCacheProperties`는 현재 다음 값을 가진다.

```text
ttl
```

`maxSteps`, `maxToolCalls`, `maxToolRetries`, `maxProviderCalls`는 Harness 실행 통제 값이고, `maxOrderRatio`, `maxPositionRatio`는 Risk Guard 정책 값이다.

`ttl`은 현재가 캐시의 유효 시간을 결정하는 시장 데이터 조회 정책 값이다.

`enabled`는 Scheduler가 주기적으로 `InvestmentHarness`를 실행할지 결정하는 Scheduler 실행 설정 값이다.

이 분리를 통해 `InvestmentHarness`는 실행 제약만 알고, `RiskGuard`는 주문 위험 정책만 알게 된다.

`HarnessScheduler`는 `HarnessSchedulerProperties.enabled()`를 통해 실행 여부를 판단한다. `enabled=false`이면 Scheduler는 `InvestmentHarness.run()`을 호출하지 않는다.

`harness.scheduler.fixed-delay-ms`는 현재 `@Scheduled(fixedDelayString = "${harness.scheduler.fixed-delay-ms}")` 속성에서 직접 참조한다. `@Scheduled`는 어노테이션 속성으로 스케줄 간격을 받아야 하므로, 이 단계에서는 `fixed-delay-ms`를 별도 record 필드로 옮기지 않는다.

현재 Tool 실행 결과의 계약 검증, Run 결과 포함, JSON 저장, 상세 조회까지 구현되어 있다. 각 실행 결과는 원본 Tool 요청을 함께 보존하므로 출력이 없는 실패, 권한 거절, 중복 차단 결과에서도 요청 타입과 symbol을 확인할 수 있다. `GET_CURRENT_PRICE`는 요청 symbol 필수 검증과 응답 symbol·양수 가격·관측 시각 검증, Provider 경계, 인메모리 TTL 캐시, `CACHE`와 `PROVIDER` 조회 출처 기록, Run별 Provider 호출 한도까지 포함한다. 관측 시각은 런타임 결과, JSON 저장, 상세 조회 API까지 보존되며 캐시 적중 시에도 원래 값이 유지된다. Tool Service의 `RuntimeException`은 `TOOL_EXECUTION_FAILED` 결과로 변환되어 실행 이력에 저장되고, 설정된 횟수 안에서 같은 원본 요청으로 재시도된다.

동일 Run의 중복 Tool 요청 차단까지 구현되어 있다.

현재가 Cache 경계, Tool 실패 재시도 정책, 논리적인 Tool Call Budget과 실제 Provider Call Budget의 분리는 구현되어 있다. 실제 Broker API를 연결하기 전에는 인증과 응답 매핑, 외부 API 오류 분류, 여러 Run에 걸친 시간 단위 Rate Limit 정책을 결정해야 한다.
