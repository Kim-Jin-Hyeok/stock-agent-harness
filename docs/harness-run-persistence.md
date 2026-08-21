# Harness Run Persistence Design

## Purpose

Harness Run 이력 저장의 목적은 Agent 실행을 운영 관점에서 다시 추적할 수 있게 만드는 것이다.

초기 목표는 `HarnessRunResult` 전체를 완전하게 영속화하는 것이 아니다. 먼저 실행이 언제 발생했고, 어떤 상태로 끝났으며, 어떤 단계들을 거쳤는지 확인 가능한 최소 이력을 저장한다.

이를 통해 이후 다음 기능으로 확장할 수 있다.

- Run 목록 조회
- Run 단계 이력 조회
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

현재 조회 기준은 기능별로 다르다.

```text
getRunSummaries()
-> DB 기준 HarnessRunSummary 목록 조회

getRunById(runId)
-> 메모리 기준 HarnessRunResult 상세 조회

getStepsByRunId(runId)
-> DB 기준 HarnessStepResult 목록 조회

GET /api/harness/runs/{runId}/detail
-> DB에 저장된 Run 메타데이터, Step 이력, Trade 이력을 조합한 HarnessRunDetail 조회
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
```

이 Entity는 Run의 메타데이터만 저장한다. `decision`, `riskCheckResult`, `tradeResult`, `portfolioSnapshot`, `marketSnapshot`은 아직 저장하지 않는다.

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
steps
tradeRecords
```

이 모델은 현재 DB에 저장된 데이터만 포함한다. 따라서 아직 저장하지 않는 `decision`, `riskCheckResult`, `tradeResult`, `portfolioSnapshot`, `marketSnapshot`은 포함하지 않는다.

## Current APIs

### Run 실행

```text
POST /api/harness/run
```

새 Harness Run을 실행하고, 실행 결과와 해당 Run의 거래 이력을 함께 반환한다.

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
- DB에는 Run 메타데이터와 Step/Trade 이력만 남는다.
- 따라서 이 API는 아직 완전한 DB 기반 상세 조회가 아니다.

### 저장 이력 기반 Run 상세 조회

```text
GET /api/harness/runs/{runId}/detail
```

DB에 저장된 Run 메타데이터, Step 이력, Trade 이력을 조합해 `HarnessRunDetail`을 반환한다.

현재 포함하는 값:

```text
runId
status
startedAt
finishedAt
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

아직 DB에 저장하지 않는 값은 다음과 같다.

```text
HarnessRunResult.decision
HarnessRunResult.riskCheckResult
HarnessRunResult.tradeResult
HarnessRunResult.portfolioSnapshot
HarnessRunResult.marketSnapshot
```

이 값들은 구조가 깊고 변경 가능성이 높다. 따라서 초기에 모두 Entity 필드로 풀어 저장하지 않는다.

향후 선택지는 다음과 같다.

- JSON 컬럼으로 저장
- 별도 Entity로 분리
- 요약 정보만 컬럼으로 저장
- 상세 조회 요구가 명확해질 때까지 저장 보류

## Design Decisions

### HarnessRunResult 전체를 바로 Entity로 저장하지 않는다

`HarnessRunResult`는 실행 결과 전체를 담는 런타임 모델이다. 여기에는 판단 결과, 리스크 검증 결과, 거래 결과, 포트폴리오 스냅샷, 시장 스냅샷이 함께 들어 있다.

이 객체를 그대로 영속화하면 초기에는 빠르지만, 이후 구조 변경과 조회 요구가 생길 때 관리가 어려워진다.

현재는 메타데이터, Step, Trade처럼 저장 책임이 명확한 것부터 분리한다.

### Step은 별도 저장 모델로 관리한다

Step은 Harness Engineering 관점에서 핵심 관찰 데이터다.

Agent가 어떤 판단을 했는지보다 먼저 확인해야 할 것은 Harness가 어떤 단계를 실행했고, 어디에서 멈췄는지다.

따라서 Step은 별도 Entity로 분리하고, `runId`와 `stepOrder`로 조회한다.

### Trade는 Run 안에 포함하지 않는다

하나의 Run에서 여러 거래가 생길 수 있고, 거래 이력은 Run과 다른 조회 축을 가진다.

따라서 Trade는 `TradeRecordEntity`로 분리하고 `runId`로 연결한다.

### HarnessRunResponse와 HarnessRunDetail을 분리한다

`HarnessRunResponse`는 방금 실행한 런타임 결과를 표현한다.

`HarnessRunDetail`은 DB에 저장된 이력 데이터를 조합해서 표현한다.

두 모델을 분리하는 이유는 아직 `HarnessRunResult` 전체를 DB에서 복원하지 않기 때문이다. 런타임 결과와 저장 이력 조회 결과를 같은 모델로 표현하면 어떤 필드가 영속화된 값인지 구분하기 어렵다.

## Open Questions

다음 설계 단계에서 결정해야 할 질문은 다음과 같다.

- `decision`과 `riskCheckResult`는 JSON으로 저장할 것인가, 별도 Entity로 분리할 것인가?
- `portfolioSnapshot`과 `marketSnapshot`은 매 Run마다 저장할 가치가 있는가?
- `/api/harness/runs/{runId}`를 계속 메모리 런타임 상세 API로 유지할 것인가?
- `/api/harness/runs/{runId}/detail`을 최종 상세 조회 API로 삼을 것인가?
- `tradeResult`는 `TradeRecordEntity`와 별도로 저장할 필요가 있는가?
- 개발용 `reset()` API를 운영에서도 유지할 것인가?

## Recommended Next Step

다음 단계는 `decision`과 `riskCheckResult` 저장 방식을 결정하는 것이다.

이 두 값은 Agent 판단과 Harness 통제 결과를 나타내므로 실패 분석과 전략 비교에서 가치가 높다.

현재 `HarnessRunDetail`은 다음 값까지만 포함한다.

```text
HarnessRunDetail
-> runId
-> status
-> startedAt
-> finishedAt
-> steps
-> tradeRecords
```

다음 후보는 아래 값을 추가로 저장할지 결정하는 것이다.

```text
decision
riskCheckResult
```

가능한 방향은 두 가지다.

```text
1. JSON 컬럼으로 저장
2. 별도 Entity로 분리
```

현재 단계에서는 JSON 컬럼 저장을 먼저 검토하는 것이 좋다. 두 값은 조회 조건으로 자주 검색하기보다, Run 상세에서 당시 판단 내용을 확인하는 목적이 강하기 때문이다.
