# Harness Run Persistence Design

## Purpose

Harness Run 이력 저장의 목적은 Agent 실행을 운영 관점에서 다시 추적할 수 있게 만드는 것이다.

초기 목표는 `HarnessRunResult` 전체를 완전하게 영속화하는 것이 아니다. 먼저 실행이 언제 발생했고, 어떤 상태로 끝났으며, 어떤 단계들을 거쳤고, Agent 판단과 Risk Guard 결과가 무엇이었는지 확인 가능한 이력을 저장한다.

이를 통해 이후 다음 기능으로 확장할 수 있다.

- Run 목록 조회
- Run 단계 이력 조회
- Agent 판단 내용 추적
- Risk Guard 승인/거절 사유 추적
- 실패 Run 분석
- 전략별 실행 비교
- Trade 이력과 Run 연결

## Current State

현재 Harness 이력은 메모리 상세 이력과 JPA 기반 저장 이력이 함께 존재하는 전환 단계다.

```text
HarnessRunHistoryService.record(HarnessRunResult)
-> 메모리 runs에 HarnessRunResult 저장
-> HarnessRunRepository에 HarnessRunEntity 저장
-> HarnessStepRepository에 HarnessStepEntity 목록 저장
```

`HarnessRunEntity`는 Run 메타데이터와 함께 Agent 판단 스냅샷, Risk Guard 검증 스냅샷을 JSON 문자열로 저장한다.

```text
HarnessRunResult.decision
-> HarnessDecisionSnapshot
-> decisionSnapshotJson

HarnessRunResult.riskCheckResult
-> HarnessRiskCheckSnapshot
-> riskCheckSnapshotJson
```

현재 조회 기준은 기능별로 다르다.

```text
getRunSummaries()
-> DB 기준 HarnessRunSummary 목록 조회

getRunById(runId)
-> 메모리 기준 HarnessRunResult 상세 조회

getStepsByRunId(runId)
-> DB 기준 HarnessStepResult 목록 조회

GET /api/harness/runs/{runId}/detail
-> DB에 저장된 Run 메타데이터, 판단 스냅샷, 리스크 스냅샷, Step 이력, Trade 이력을 조합한 HarnessRunDetail 조회
```

즉 Run 목록, Step 이력, Trade 이력, 저장 데이터 기반 상세 조회는 DB 기준으로 이동했다.

다만 `GET /api/harness/runs/{runId}`는 아직 메모리 기준 `HarnessRunResult`를 반환한다. 이 API는 방금 실행한 런타임 상세를 확인하는 용도에 가깝고, 저장된 이력 조회 기준은 `/detail` API가 담당한다.

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
```

이 Entity는 Run 메타데이터와 Harness 관점에서 중요한 판단 스냅샷을 저장한다.

`decisionSnapshotJson`은 Agent가 어떤 투자 판단을 했는지 저장한다.

`riskCheckSnapshotJson`은 Harness/Risk Guard가 그 판단을 승인했는지, 거절했다면 어떤 reasonCode로 거절했는지 저장한다.

이 두 값은 별도 Entity로 분리하지 않고 JSON 문자열로 저장한다. 현재 단계에서는 이 값들을 조건 검색하기보다 Run 상세에서 당시 판단 맥락을 확인하는 목적이 강하기 때문이다.

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
```

Entity가 `ObjectMapper`나 converter를 직접 알지 않도록 변환 책임을 별도 컴포넌트로 둔다.

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
```

Step은 Run Entity 내부에 포함하지 않고 별도 테이블로 분리한다.

이유는 다음과 같다.

- 하나의 Run은 여러 Step을 가진다.
- Step은 실행 순서가 중요하므로 `stepOrder`가 필요하다.
- 실패 분석에서는 Run 전체보다 어느 Step에서 실패했는지가 더 중요하다.
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
steps
tradeRecords
```

`decisionSnapshot`과 `riskCheckSnapshot`은 DB에 저장된 JSON 문자열을 다시 객체로 복원한 값이다.

`steps`는 `HarnessStepEntity` 목록에서 복원한 값이다.

`tradeRecords`는 `TradeRecordEntity` 기준의 거래 이력 조회 결과다.

## Current APIs

### Run 실행

```text
POST /api/harness/run
```

새 Harness Run을 실행하고, 실행 결과와 해당 Run의 거래 이력을 함께 반환한다.

응답 모델은 `HarnessRunResponse`이며, 방금 실행한 런타임 결과를 표현한다.

### Run 목록 조회

```text
GET /api/harness/runs
```

DB에 저장된 `HarnessRunEntity`를 기준으로 `HarnessRunSummary` 목록을 반환한다.

### Run 상세 조회

```text
GET /api/harness/runs/{runId}
```

현재는 메모리에 남아 있는 `HarnessRunResult`를 기준으로 상세 결과를 반환한다.

주의할 점:

- 애플리케이션을 재시작하면 메모리 상세 이력은 사라진다.
- 저장 이력 기반 상세 조회는 `/api/harness/runs/{runId}/detail`이 담당한다.
- 따라서 이 API는 아직 완전한 DB 기반 상세 조회가 아니다.

### 저장 이력 기반 Run 상세 조회

```text
GET /api/harness/runs/{runId}/detail
```

DB에 저장된 Run 메타데이터, 판단 스냅샷, 리스크 스냅샷, Step 이력, Trade 이력을 조합해 `HarnessRunDetail`을 반환한다.

현재 포함하는 값:

```text
runId
status
startedAt
finishedAt
decisionSnapshot
riskCheckSnapshot
steps
tradeRecords
```

Run이 존재하지 않으면 `404 Not Found`를 반환한다.

이 API는 애플리케이션 재시작 이후에도 DB에 남아 있는 데이터 기준으로 조회할 수 있는 상세 조회의 출발점이다.

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

아직 `HarnessRunEntity`에 직접 저장하지 않는 값은 다음과 같다.

```text
HarnessRunResult.tradeResult
HarnessRunResult.portfolioSnapshot
HarnessRunResult.marketSnapshot
```

`decision`과 `riskCheckResult`는 더 이상 deferred field가 아니다. 현재는 각각 `HarnessDecisionSnapshot`, `HarnessRiskCheckSnapshot`으로 축약한 뒤 JSON 컬럼에 저장한다.

`tradeResult`는 별도 저장 필요성을 아직 낮게 본다. 실제 거래 이력은 이미 `TradeRecordEntity`로 저장되기 때문이다.

`portfolioSnapshot`과 `marketSnapshot`은 구조가 깊고 변경 가능성이 있다. 또한 매 Run마다 저장할 가치가 있는지 아직 명확하지 않다.

향후 선택지는 다음과 같다.

- JSON 컬럼으로 저장
- 별도 Entity로 분리
- 요약 정보만 컬럼으로 저장
- 상세 조회 요구가 명확해질 때까지 저장 보류

## Design Decisions

### HarnessRunResult 전체를 바로 Entity로 저장하지 않는다

`HarnessRunResult`는 실행 결과 전체를 담는 런타임 모델이다. 여기에는 판단 결과, 리스크 검증 결과, 거래 결과, 포트폴리오 스냅샷, 시장 스냅샷이 함께 들어 있다.

이 객체를 그대로 영속화하면 초기에는 빠르지만, 이후 구조 변경과 조회 요구가 생길 때 관리가 어려워진다.

현재는 메타데이터, Step, Trade, 판단 스냅샷처럼 저장 책임과 조회 목적이 설명 가능한 것부터 분리한다.

### decision과 riskCheckResult는 Snapshot JSON으로 저장한다

`decision`과 `riskCheckResult`는 Agent 판단과 Harness 통제 결과를 나타낸다.

이 두 값은 실패 분석과 전략 비교에서 가치가 높다.

하지만 현재 단계에서는 이 값들을 조건 검색하거나 통계 집계하는 요구보다, Run 상세에서 당시 판단 내용을 확인하는 요구가 더 강하다.

따라서 별도 Entity로 분리하지 않고 다음 흐름으로 저장한다.

```text
InvestmentDecision
-> HarnessDecisionSnapshot
-> decisionSnapshotJson

RiskCheckResult
-> HarnessRiskCheckSnapshot
-> riskCheckSnapshotJson
```

조회 시에는 반대 방향으로 복원한다.

```text
decisionSnapshotJson
-> HarnessDecisionSnapshot

riskCheckSnapshotJson
-> HarnessRiskCheckSnapshot
```

### Entity는 JSON 파싱을 하지 않는다

`HarnessRunEntity`는 JSON 문자열을 저장하지만, JSON 파싱 책임은 갖지 않는다.

Entity가 `ObjectMapper`나 converter를 알게 되면 persistence 모델의 책임이 넓어진다.

따라서 JSON 변환은 `HarnessRunSnapshotJsonConverter`가 담당하고, 이 converter는 `HarnessRunHistoryService`에서 사용한다.

`HarnessRunEntity.toDetail(...)`은 이미 복원된 snapshot 객체, steps, tradeRecords를 받아 `HarnessRunDetail`을 생성한다.

### Step은 별도 저장 모델로 관리한다

Step은 Harness Engineering 관점에서 핵심 관찰 데이터다.

Agent가 어떤 판단을 했는지보다 먼저 확인해야 할 것은 Harness가 어떤 단계를 실행했고, 어디에서 멈췄는지다.

따라서 Step은 별도 Entity로 분리하고, `runId`와 `stepOrder`로 조회한다.

### Trade는 Run 안에 포함하지 않는다

하나의 Run에서 여러 거래가 생길 수 있고, 거래 이력은 Run과 다른 조회 축을 가진다.

따라서 Trade는 `TradeRecordEntity`로 분리하고 `runId`로 연결한다.

`tradeResult`를 `HarnessRunEntity`에 중복 저장하지 않는 이유는 이미 실행 이력이 `TradeRecordEntity`에 기록되기 때문이다.

### HarnessRunResponse와 HarnessRunDetail을 분리한다

`HarnessRunResponse`는 방금 실행한 런타임 결과를 표현한다.

`HarnessRunDetail`은 DB에 저장된 이력 데이터를 조합해서 표현한다.

두 모델을 분리하는 이유는 아직 `HarnessRunResult` 전체를 DB에서 복원하지 않기 때문이다. 런타임 결과와 저장 이력 조회 결과를 같은 모델로 표현하면 어떤 필드가 영속화된 값인지 구분하기 어렵다.

## Open Questions

다음 설계 단계에서 결정해야 할 질문은 다음과 같다.

- `portfolioSnapshot`은 매 Run마다 저장할 가치가 있는가?
- `marketSnapshot`은 매 Run마다 저장할 가치가 있는가?
- `portfolioSnapshot`과 `marketSnapshot`을 저장한다면 JSON 컬럼으로 둘 것인가, 별도 Entity로 분리할 것인가?
- `/api/harness/runs/{runId}`를 계속 메모리 런타임 상세 API로 유지할 것인가?
- `/api/harness/runs/{runId}/detail`을 최종 상세 조회 API로 삼을 것인가?
- 개발용 `reset()` API를 운영에서도 유지할 것인가?

## Recommended Next Step

다음 단계는 `portfolioSnapshot` 저장 여부를 결정하는 것이다.

`portfolioSnapshot`은 Run 당시의 보유 현금, 총 평가금, 포지션 목록을 담는다.

이 값은 다음 질문에 답할 때 필요하다.

```text
Agent가 어떤 포트폴리오 상태를 보고 판단했는가?
거래 실행 후 포트폴리오가 어떻게 바뀌었는가?
전략별 Run 결과를 나중에 비교할 수 있는가?
```

다만 포지션 목록은 구조가 깊고, 이후 Broker API 연동 시 실제 계좌 조회 결과와 연결될 수 있다.

따라서 바로 저장하기 전에 최소 설계 질문을 먼저 정해야 한다.

```text
1. Run 상세 확인 목적이면 JSON 컬럼으로 충분한가?
2. 포지션별 검색/집계가 필요하면 별도 Entity가 필요한가?
3. 현재 단계에서 저장할 값은 전체 portfolioSnapshot인가, 요약값만인가?
```

현재 단계에서는 `portfolioSnapshot` 전체를 바로 Entity로 분리하기보다, 먼저 JSON 저장 후보로 검토하는 것이 좋다.

추천하는 다음 구현 단위는 다음과 같다.

```text
HarnessPortfolioSnapshot
HarnessRunEntity.portfolioSnapshotJson
HarnessRunSnapshotJsonConverter portfolio 변환 메서드
HarnessRunDetail.portfolioSnapshot
```

단, 구현 전에 `PortfolioSnapshot`을 그대로 저장할지, 저장 전용 Snapshot 모델을 별도로 만들지 먼저 결정한다.
