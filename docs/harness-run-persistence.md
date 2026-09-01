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
RUN_INVESTMENT_AGENT
VALIDATE_DECISION
EXECUTE_TRADE
LOAD_FINAL_PORTFOLIO
CHECK_STEP_LIMIT
```

`LOAD_PORTFOLIO`는 Agent 판단에 사용될 초기 포트폴리오 상태를 조회한다.

`LOAD_FINAL_PORTFOLIO`는 거래 실행 이후 Run 결과로 저장할 최종 포트폴리오 상태를 조회한다.

두 포트폴리오 조회는 의미가 다르므로 별도 Step으로 기록한다. 이후 포트폴리오 조회가 Broker API 또는 Cache/Adapter 계층으로 이동하더라도, Harness는 어떤 시점의 상태를 읽었는지 Step 이력으로 추적할 수 있다.

`CHECK_STEP_LIMIT`는 외부 작업 실행이라기보다 Harness 내부 판정 Step이다. 따라서 `Supplier` 기반 실행 블록으로 감싸지 않고, 별도 helper에서 계산한 뒤 `completed` 또는 `failed` Step으로 기록한다.

Step limit 판정은 `CHECK_STEP_LIMIT` Step 자신까지 포함한 최종 Step 수를 기준으로 한다.

```text
executableStepCount = CHECK_STEP_LIMIT 기록 전 Step 수
finalStepCount = executableStepCount + 1
stepLimitExceeded = finalStepCount > maxSteps
```

예를 들어 현재 정상 실행 흐름은 `CHECK_STEP_LIMIT` 이전에 6개 Step을 기록하고, 최종 Step 수는 7개가 된다.

```text
Executable steps: 6, final steps: 7, max steps: 4
```

## Run Limits

`HarnessRunLimits`는 Run 하나에 적용되는 실행 제한을 표현한다.

```text
src/main/java/com/stock/harness/HarnessRunLimits.java
```

현재는 다음 값만 가진다.

```text
maxSteps
```

`HarnessProperties.maxSteps`는 애플리케이션의 기본 설정값이다.

`HarnessRunLimits.maxSteps`는 Run 시작 시점에 확정되어 해당 Run에 적용되는 제한값이다.

현재 흐름은 다음과 같다.

```text
application.yml
-> HarnessProperties(maxSteps)
-> InvestmentHarness.createContext()
-> HarnessRunLimits(maxSteps)
-> HarnessRunContext(limits)
```

`HarnessRunContext`는 `maxSteps`를 직접 들지 않고 `HarnessRunLimits`를 가진다.

```text
HarnessRunContext
-> runId
-> limits
-> portfolioSnapshot
-> marketSnapshot
```

이 구조를 통해 나중에 `apiCallLimit`, `toolCallLimit` 같은 Run 단위 실행 제한이 생기더라도 `HarnessRunContext`에 개별 필드를 계속 추가하지 않고 `HarnessRunLimits` 안에서 관리할 수 있다.

단, 현재 `HarnessRunLimits`는 DB에 저장하지 않는다.

아직 Run별 override, Tool 호출 제한, Broker API 호출 제한이 없기 때문에 저장 컬럼을 먼저 만들면 과한 구조가 될 수 있다.

현재는 Step limit 판정 메시지에 실행 당시의 `maxSteps`가 남는다.

## Harness Tool Types

`HarnessToolType`은 Harness가 Agent에게 허용할 수 있는 Tool 종류를 표현한다.

```text
src/main/java/com/stock/harness/tool/HarnessToolType.java
```

현재 단계에서는 실제 Tool Calling을 구현하지 않는다.

먼저 Agent에게 열 수 있는 Tool의 경계를 코드로 정의한다.

현재 허용 후보는 읽기 전용 Tool만 둔다.

```text
GET_PORTFOLIO
GET_MARKET
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
```

이렇게 두는 이유는 `InvestmentHarness`가 구체적인 Tool enum 목록을 직접 만들지 않도록 하기 위해서다.

`InvestmentHarness`는 "기본 읽기 전용 Tool을 허용한다"는 의도만 표현하고, 실제 기본 목록은 `HarnessAllowedTools`가 관리한다.

현재 `HarnessRunContext`는 Run 실행 제한과 허용 Tool 목록을 함께 가진다.

```text
HarnessRunContext
-> runId
-> limits
-> allowedTools
-> portfolioSnapshot
-> marketSnapshot
```

`InvestmentAgent`는 아직 Tool을 직접 호출하지 않는다.

현재는 `context.allowedTools().types()`를 읽어 판단 reason에 남기는 수준이다.

```text
allowedTools=[GET_PORTFOLIO, GET_MARKET]
```

이는 실제 Tool Calling 구현 전, Harness가 정한 Tool 권한 목록이 Agent까지 전달되는지 확인하기 위한 연결 단계다.

## Recommended Next Step

`HarnessAllowedTools`는 생성 시 전달받은 List를 `List.copyOf(types)`로 복사한다.

이를 통해 생성 이후 원본 List가 변경되어도 Run에 허용된 Tool 목록은 바뀌지 않는다.

`types()`로 반환되는 List도 수정 불가능한 List다.

따라서 `HarnessAllowedTools`는 현재 Tool 목록 값 객체로서 필요한 최소 불변성을 가진다.

다만 현재는 중복 Tool이나 빈 Tool 목록은 별도로 막지 않는다.

아직 Tool 실행기가 없고, 기본 생성 흐름은 `HarnessAllowedTools.readOnly()`로 고정되어 있기 때문이다.

## Recommended Next Step

다음 단계는 Agent에게 허용 Tool 목록을 어떻게 활용하게 할지 판단하는 것이다.

현재 Harness는 `maxSteps`를 통해 Run의 전체 Step 수를 제한한다. 장기 목표에서는 Step 수뿐 아니라 Tool 호출 수, Broker API 호출 수, Cache 사용 여부, Rate Limit도 Harness가 관리해야 한다.

다음 작업에서는 아직 실제 Tool Calling이나 Broker API를 붙이지 말고, Agent가 허용 Tool 목록을 보고 판단 흐름을 바꾸는 최소 연결을 검토하는 것이 좋다.

판단해야 할 질문은 다음과 같다.

```text
1. Agent가 allowedTools를 보고 어떤 판단을 달리할 수 있는가?
2. 허용되지 않은 Tool이 필요하면 Agent는 HOLD를 해야 하는가?
3. Tool 호출 없이 reason에만 남기는 현재 수준으로 충분한가?
4. 다음 단계에서 Tool 실행 요청 모델을 만들 필요가 있는가?
```

설정 책임은 현재 다음처럼 분리되어 있다.

`HarnessProperties`는 현재 다음 값을 가진다.

```text
maxSteps
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

`maxSteps`는 Harness 실행 통제 값이고, `maxOrderRatio`, `maxPositionRatio`는 Risk Guard 정책 값이다.

`enabled`는 Scheduler가 주기적으로 `InvestmentHarness`를 실행할지 결정하는 Scheduler 실행 설정 값이다.

이 분리를 통해 `InvestmentHarness`는 실행 제약만 알고, `RiskGuard`는 주문 위험 정책만 알게 된다.

`HarnessScheduler`는 `HarnessSchedulerProperties.enabled()`를 통해 실행 여부를 판단한다. `enabled=false`이면 Scheduler는 `InvestmentHarness.run()`을 호출하지 않는다.

`harness.scheduler.fixed-delay-ms`는 현재 `@Scheduled(fixedDelayString = "${harness.scheduler.fixed-delay-ms}")` 속성에서 직접 참조한다. `@Scheduled`는 어노테이션 속성으로 스케줄 간격을 받아야 하므로, 이 단계에서는 `fixed-delay-ms`를 별도 record 필드로 옮기지 않는다.

현재 추천 방향은 바로 Tool 실행기를 만들지 않는 것이다.

아직 Agent가 Tool을 선택하거나 호출하지 않는다. 따라서 다음 구현 단계는 실제 Tool 실행보다, 허용 Tool 목록 모델이 값 객체로서 안전한지 먼저 정리하는 것이 더 자연스럽다.
