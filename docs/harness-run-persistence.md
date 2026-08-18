# Harness Run Persistence Design

## Purpose

Harness Run 이력 저장의 목적은 Agent 실행을 운영 관점에서 다시 추적할 수 있게 하는 것이다.

초기 목표는 모든 실행 상세를 완전하게 영속화하는 것이 아니라, Harness Run이 언제 실행되었고 어떤 상태로 끝났는지 먼저 저장하는 것이다.

이를 통해 이후 다음 기능으로 확장할 수 있다.

- Run 목록 조회
- Run 단건 조회
- 실패 Run 분석
- 전략별 실행 비교
- Trade 이력과 Run 연결

## Original State

초기 Run 이력은 인메모리로 관리되었다.

```text
src/main/java/com/stock/harness/HarnessRunHistoryService.java
```

초기 구조는 `List<HarnessRunResult>`에 실행 결과를 저장했다.

```text
HarnessRunHistoryService
-> record(HarnessRunResult)
-> getRuns()
-> getRunById(runId)
-> clear()
```

이 구조는 초기 학습과 테스트에는 충분하지만, 애플리케이션 재시작 시 이력이 사라진다.

## Current Transition State

현재 `HarnessRunHistoryService`는 인메모리 상세 이력과 JPA 기반 요약 이력이 함께 존재하는 과도기 구조다.

```text
record(HarnessRunResult)
-> 인메모리 runs에 HarnessRunResult 저장
-> HarnessRunRepository에 HarnessRunEntity 저장

getRunById(runId)
-> 인메모리 runs에서 HarnessRunResult 조회

getRuns()
-> getRunSummaries() 위임

getRunSummaries()
-> HarnessRunRepository.findAll()
-> HarnessRunEntity.toSummary()

clear()
-> 인메모리 runs 삭제
-> HarnessRunRepository.deleteAll()
```

이 구조는 의도적인 중간 단계다.

목록 조회는 DB에 저장한 메타데이터만으로 충분하므로 `HarnessRunSummary`를 사용한다.

단건 상세 조회는 아직 `HarnessRunResult`를 반환한다. 현재 `HarnessRunEntity`에는 `steps`, `decision`, `riskCheckResult`, `tradeResult`, `portfolioSnapshot`, `marketSnapshot`이 없기 때문에 DB에서 완전한 상세 결과를 복원할 수 없다.

따라서 단건 상세 조회를 JPA 기반으로 바꾸기 전에 별도 상세 저장 모델을 설계해야 한다.

## Design Decision

초기 JPA 전환에서는 `HarnessRunResult` 전체를 그대로 Entity로 옮기지 않는다.

`HarnessRunResult`는 다음처럼 중첩된 실행 결과를 포함한다.

```text
runId
status
startedAt
finishedAt
steps
decision
riskCheckResult
tradeResult
portfolioSnapshot
marketSnapshot
```

이 중 `steps`, `decision`, `riskCheckResult`, `tradeResult`, `portfolioSnapshot`, `marketSnapshot`은 구조가 깊고 변경 가능성이 높다.

따라서 초기 저장 모델은 실행 메타데이터만 저장한다.

## Initial Entity Scope

초기 `HarnessRunEntity`는 다음 필드만 가진다.

```text
id
runId
status
startedAt
finishedAt
```

추천 패키지 경로는 다음과 같다.

```text
src/main/java/com/stock/harness/persistence/HarnessRunEntity.java
src/main/java/com/stock/harness/persistence/HarnessRunRepository.java
```

## Deferred Fields

다음 값들은 첫 JPA 전환 단계에서는 저장하지 않는다.

```text
steps
decision
riskCheckResult
tradeResult
portfolioSnapshot
marketSnapshot
```

이 값들은 이후 필요성이 명확해졌을 때 별도 저장 전략을 선택한다.

가능한 확장 방향은 다음과 같다.

- `steps`: 별도 `HarnessStepEntity`로 분리
- `tradeResult`: Trade 이력 저장 모델과 runId로 연결
- `decision`, `riskCheckResult`: JSON 컬럼 또는 별도 테이블 검토
- `portfolioSnapshot`, `marketSnapshot`: Run 당시 스냅샷 저장 요구가 생긴 뒤 설계

## Relationship With Trade History

Trade 이력은 Harness Run Entity 안에 포함하지 않는다.

Trade 기록은 별도의 저장 모델로 관리하고, `runId`로 Harness Run과 연결한다.

이유는 다음과 같다.

- 하나의 Run에서 여러 거래 기록이 생길 수 있다.
- 거래 기록은 Run 이력과 다른 조회 축을 가진다.
- `/api/trades?runId=...` API가 이미 별도 책임으로 존재한다.

따라서 초기 방향은 다음과 같다.

```text
HarnessRunEntity
-> runId

TradeRecordEntity
-> runId
```

DB Foreign Key를 바로 강제할지는 나중에 결정한다.

초기에는 문자열 `runId`로 느슨하게 연결해도 충분하다.

## Service Direction

초기 구현에서는 `HarnessRunHistoryService`의 외부 메서드 계약을 가능하면 유지한다.

```text
record(HarnessRunResult result)
getRuns()
getRunById(String runId)
clear()
```

내부 저장소만 인메모리 `List`에서 JPA Repository로 교체하는 방향이 좋다.

이렇게 하면 Controller와 Harness 쪽 변경 범위를 줄일 수 있다.

## Open Questions

다음 질문은 이후 단계에서 다시 판단한다.

- `steps`를 별도 테이블로 저장할 것인가?
- `decision`과 `riskCheckResult`는 JSON으로 저장할 것인가, 컬럼으로 펼칠 것인가?
- Run 상세 조회에서 DB Entity를 그대로 반환할 것인가, 별도 Response DTO로 조립할 것인가?
- Trade 이력 저장을 Harness Run 저장보다 먼저 할 것인가, 나중에 할 것인가?
- `clear()`는 운영 API로 유지할 것인가, 개발용 기능으로 제한할 것인가?

## Recommended Next Step

다음 구현 단계는 단건 상세 조회를 어떻게 저장하고 복원할지 결정하는 것이다.

바로 전체 `HarnessRunResult`를 하나의 Entity로 영속화하지 않는다.

먼저 다음 중 어떤 방식이 현재 단계에 맞는지 판단한다.

```text
1. 단건 상세 조회는 당분간 인메모리 유지
2. HarnessRunDetail 모델을 별도로 설계
3. steps만 먼저 별도 테이블로 저장
4. decision/risk/trade/portfolio/market snapshot을 JSON으로 저장
```

현재 추천은 1번이다.

상세 저장 요구가 명확해지기 전까지는 목록 조회만 JPA 기반으로 운영하고, 단건 상세 조회는 인메모리 상세 결과를 유지한다.
