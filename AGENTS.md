# AGENTS.md

## 1. Project Purpose

이 프로젝트의 최우선 목적은 **거래비용 이후의 양의 기대수익을 반복적으로 검증하고, 정해진 손실 한도 안에서 모의투자와 실제 주문을 안전하게 수행하는 개인용 AI 투자 시스템을 구축하는 것**이다.

Harness Engineering은 더 이상 독립적인 학습 목표가 아니라 투자 Agent의 자율성을 통제하고, 잘못된 판단과 운영 장애가 실제 손실로 이어지지 않게 하는 핵심 수단이다.

AI Investment Agent는 시장과 포트폴리오 상태를 확인하고 필요한 Tool을 선택하여 데이터를 조회하며 매수, 매도 또는 보유를 제안한다. Harness와 deterministic Java Policy는 데이터 검증, 투자 한도, 주문 권한, 손실 제한과 실행 중단을 담당한다.

어떤 전략도 수익을 보장한다고 가정하지 않는다. 과거 데이터, 미사용 검증 구간, 모의투자와 제한된 실제 자금 운용을 순서대로 통과한 전략만 다음 단계로 승격한다.

---

## 2. Codex Agent Role

Codex Agent는 성과와 운영 안정성을 함께 개선하는 개발 협업 Agent다.

Codex의 기본 역할은 다음과 같다.

* 현재 프로젝트의 실제 코드 상태를 확인한다.
* 현재 구현 수준을 설명한다.
* 사용자와 다음 설계를 논의한다.
* 투자 성과 검증과 Harness 안전성 관점에서 개선할 부분을 찾는다.
* 다음에 구현할 작은 단계를 제안한다.
* 구현 방법과 코드 예시를 설명한다.
* 사용자가 작성한 코드를 검토한다.
* 사용자가 명시적으로 요청한 범위는 직접 구현하고 검증한다.
* 오류가 발생하면 코드와 실행 상태를 분석하여 원인을 설명한다.
* 여러 설계 선택지가 있다면 장단점을 비교한다.

즉 Codex는 기본적으로 다음 흐름으로 동작한다.

사용자 질문
→ 현재 코드 확인
→ 현재 상태 설명
→ 투자 성과와 운영 위험에 미치는 영향 분석
→ 설계 논의
→ 다음 작업 제안
→ 명시적인 요청이 있으면 구현 및 검증

Codex가 임의로 구현을 진행해서는 안 된다.

---

## 3. Code Modification Policy

사용자가 명시적으로 코드 수정을 요청하지 않는 한 Codex는 파일을 수정하지 않는다.

다음 표현은 코드 수정 요청으로 간주하지 않는다.

* "다음은 뭘 하면 돼?"
* "어떻게 구현하면 돼?"
* "이 구조 괜찮아?"
* "현재 코드 봐줘."
* "이 에러 왜 나는 거야?"
* "어떤 클래스를 만들면 좋을까?"
* "예제 코드 보여줘."

이 경우 Codex는 설명과 코드 예시만 제공한다.

실제 파일 수정은 사용자가 다음과 같이 명확하게 요청했을 때만 수행한다.

* "이 코드 수정해줘."
* "파일에 적용해줘."
* "직접 구현해줘."
* "이 클래스를 만들어줘."

명확한 요청이 없다면 **읽기 및 가이드 모드가 기본값**이다.

---

## 4. Current Technical Direction

기본 기술 스택은 다음과 같다.

* Java 21
* Spring Boot
* Gradle
* Spring Web
* Spring Data JPA
* Validation
* Lombok
* MySQL 8
* JUnit 5

현재는 Harness, AI Provider, KIS 모의투자와 주문 생명주기의 기본 골격이 갖춰진 상태로 본다.

앞으로는 기존 통제 기능을 유지하면서 다음 항목을 우선한다.

* 운영 코드와 동일한 전략 규칙을 사용하는 백테스트
* 거래비용, 세금과 슬리피지를 포함한 성과 측정
* Stock Universe와 Screener를 통한 다종목 검증
* 전략별 벤치마크와 미사용 기간 비교
* 모의투자 운영 안정성 및 Broker 계좌 정합성
* 실제투자 전 Kill Switch와 자금 확대 조건

---

## 5. Target System

장기적으로 다음 구조를 목표로 한다.

Scheduler
→ Investment Harness
→ Investment Agent
→ Tool 선택
→ 시장/포트폴리오 데이터 조회
→ 투자 판단
→ Risk Guard
→ 모의 주문 또는 Broker API
→ 실행 결과 저장

Investment Agent는 고정된 순서대로 동작하는 것이 아니라 상황에 따라 필요한 Tool을 선택할 수 있어야 한다.

예:

getPortfolio()
→ searchStocks(...)
→ getChart(...)
→ getCurrentPrice(...)
→ buy(...)

또는:

getPortfolio()
→ getCurrentPrice(...)
→ HOLD

처럼 각 실행마다 다른 흐름이 가능해야 한다.

---

## 6. Harness Responsibilities

Harness는 Agent보다 상위의 실행 통제 계층이다.

장기적으로 다음 책임을 가진다.

* Agent 실행 시작 및 종료
* Agent Run 상태 관리
* 최대 Agent Step 제한
* Tool 호출 권한 관리
* Tool 호출 결과 검증
* Broker API 호출 Budget
* API Rate Limit
* Cache
* Agent Context 관리
* 실행 기록
* 실패 처리
* 재시도 정책
* 완료 조건 판정
* Risk Guard 연결

Agent가 모든 것을 자유롭게 결정하도록 만들지 않는다.

Agent에게 자율성을 제공하되 Harness가 실행 범위와 안전장치를 결정한다.

---

## 7. Investment Agent Responsibilities

Investment Agent는 투자 판단을 담당한다.

Agent는 Harness가 허용한 Tool만 사용할 수 있다.

장기적으로 다음과 같은 Tool이 제공될 수 있다.

* searchStocks
* getCurrentPrice
* getChart
* getFinancials
* getPortfolio
* buy
* sell

Agent는 어떤 Tool을 어떤 순서로 호출할지 스스로 판단할 수 있다.

하지만 다음 사항은 Agent가 변경해서는 안 된다.

* 투자 한도
* API 호출 한도
* Risk Rule
* Agent 최대 Step
* 허용 Tool 목록
* 시스템 설정

이 값들은 Harness가 관리한다.

---

## 8. Stock Discovery

전체 주식 데이터를 LLM Context에 넣지 않는다.

기본 방향은 다음과 같다.

전체 시장
→ Stock Universe
→ Screener
→ 후보 종목
→ Investment Agent
→ 필요한 종목 상세조회

Stock Screener는 대량 데이터를 좁히는 역할을 한다.

AI는 후보 종목 가운데 필요한 종목을 더 깊게 조사한다.

searchStocks와 같은 Tool은 가능하면 우리 시스템의 DB 또는 Cache 데이터를 검색하고, 종목 하나하나에 대해 외부 Broker API를 호출하지 않는다.

---

## 9. Strategy Experiment

여러 투자 전략을 동일한 조건에서 비교하고, 검증 기준을 통과한 전략에만 자금을 배분한다.

모의투자 실험은 다음 세 가지 투자 관점을 기본 축으로 사용한다.

* 단타(DAY_TRADING)
* 스윙(SWING)
* 장기(LONG_TERM)

세 관점은 하나의 Agent Run 안에서 섞지 않는다. Harness는 Run을 시작할 때 적용할 전략과 버전을 확정하고, Agent는 해당 전략의 후보 종목, 데이터 범위, Tool, Risk Rule 안에서만 판단한다.

기본 실행 주기는 다음 방향으로 운영한다.

* 단타: 장중 5분 주기
* 스윙: 거래일 기준 하루 1회
* 장기: 1주일에 1회

장기 전략의 주 1회 실행은 포트폴리오를 매주 재검토한다는 뜻이다. 보유 기간을 1주로 제한하거나 매주 반드시 매도한다는 뜻은 아니다.

각 전략 Run과 이력에는 최소한 `strategyId`와 `strategyVersion`을 남겨 전략 규칙이나 AI Prompt 변경 전후의 결과가 섞이지 않게 한다.

각 전략은 독립적인 포트폴리오와 성과 이력을 가져야 한다. 단타, 스윙, 장기가 같은 현금과 보유 수량을 직접 공유하면 전략별 성과와 Risk 사용량을 구분할 수 없기 때문이다.

예:

* Momentum
* Mean Reversion
* Value
* Fixed Screener + AI
* AI Autonomous

각 전략에는 독립적인 Virtual Portfolio를 부여할 수 있다.

동일한 초기 자금과 기간을 기준으로 비교한다.

단, 여기서 말하는 초기 자금은 사용자가 애플리케이션 설정값으로 임의 입력하는 값을 우선한다는 뜻이 아니다.

장기적으로 포트폴리오의 현금, 잔고, 보유 수량은 증권사 모의투자 API 또는 Broker Account 조회 결과를 기준으로 한다.

현재 인메모리 PortfolioService의 기본 현금값은 실제 잔액 조회 연동 전까지 사용하는 임시 모의 상태다.

비교 가능한 지표 예:

* 총 수익률
* MDD
* 승률
* 거래 횟수
* 평균 수익/손실
* Sharpe Ratio

각 전략에는 AI를 사용하지 않는 deterministic Baseline을 둔다. AI가 개입하는 전략은 같은 후보, 데이터, 기간과 비용 조건에서 Baseline보다 나은 순성과를 보이는지 별도로 비교한다.

AI 자율성은 기능 구현 여부가 아니라 검증된 성과로 확대한다. AI Overlay가 Baseline보다 나은 결과를 반복해서 만들지 못하면 실제 자금 운용에는 사용하지 않는다.

---

## 10. Risk Guard

Risk Guard는 가능한 한 LLM이 아니라 deterministic Java 코드로 구현한다.

예:

* 최대 투자금
* 종목별 최대 투자 비율
* 일일 최대 주문 금액
* 일일 최대 손실
* 중복 주문 방지
* 거래 가능 시간
* 최대 주문 횟수

Agent가 Risk Rule을 위반하는 요청을 하더라도 실행되어서는 안 된다.

예:

Agent
→ BUY 10,000,000원

Risk Guard
→ DENIED
→ MAX_POSITION_LIMIT_EXCEEDED

Agent는 이 결과를 받아 다음 판단을 할 수 있다.

---

## 11. Scheduler and Agent Loop

Scheduler와 Agent Loop는 다른 개념으로 본다.

Scheduler는 일정 시간마다 새로운 Agent Run을 시작한다.

예:

09:10 Agent Run
09:20 Agent Run
09:30 Agent Run

각 Agent Run 내부에서는 다음과 같은 Loop가 존재할 수 있다.

현재 상태 확인
→ 판단
→ Tool 호출
→ 결과 확인
→ 다시 판단
→ Tool 호출
→ 최종 결정
→ Run 종료

Agent 자체가 계속 살아있는 무한 루프로 동작하도록 설계하지 않는다.

---

## 12. API Usage Policy

외부 Broker API를 과도하게 호출하지 않는 구조를 지향한다.

장기적으로 Harness가 다음을 관리한다.

* Run별 최대 API 호출 수
* 시간별 API 호출 수
* Tool별 호출 제한
* Cache TTL
* 동일 요청 중복 제거

포트폴리오 잔액과 보유 종목 조회는 장기적으로 Broker API 또는 그 위의 Cache/Adapter 계층을 통해 가져온다.

Harness나 PortfolioService가 임의의 시드머니를 최종 진실로 관리하지 않는다.

예:

현재가: 짧은 TTL
일봉: 비교적 긴 TTL
종목 기본정보: 매우 긴 TTL

Agent는 Tool을 호출하지만 실제 외부 API 호출 여부는 Tool/Harness 계층이 결정할 수 있다.

---

## 13. Development Philosophy

이 프로젝트에서는 기능 수보다 **검증 가능한 순성과와 운영 안정성**을 우선한다.

새로운 지표나 AI Prompt를 계속 추가하기 전에 기존 전략의 가설, 입력 데이터, 비용과 평가 기준을 고정한다. 성과가 좋지 않은 전략을 숨기거나 같은 검증 구간을 반복해서 조정하지 않는다.

변경은 원인과 효과를 구분할 수 있는 작은 단위로 진행한다.

기본 흐름:

현재 상태 확인
→ 성과 또는 운영 문제 정의
→ 검증할 가설과 기준 정의
→ 작은 구현
→ 테스트 및 백테스트
→ 미사용 기간 또는 모의투자 검증
→ 문제 분석
→ 유지, 폐기 또는 다음 단계 결정

새로운 클래스나 계층을 만들기 전에 왜 필요한지 설명할 수 있어야 한다.

미래에 필요할 것이라는 이유만으로 미리 추상화하지 않는다.

백테스트 결과는 실제 수익의 보장이 아니다. 수수료, 세금, 슬리피지, 데이터 편향과 주문 실패를 반영하고 실제 운용 결과와 지속적으로 비교한다.

---

## 14. Codex Guidance Rules

사용자가 다음 작업을 물어보면 먼저 현재 Repository 상태를 확인한다.

가능하면 다음 순서로 답한다.

1. 현재 코드 상태
2. 현재 전략 또는 운영상 병목
3. 수익, 손실 위험과 운영 안정성에 미치는 영향
4. 검증할 가설과 비교 기준
5. 추천 설계
6. 구현할 최소 범위
7. 구현 후 확인할 방법
8. 추천 커밋 메시지

현재 코드와 관계없는 일반적인 템플릿을 무조건 제시하지 않는다.

현재 Repository에 이미 존재하는 코드와 구조를 최대한 활용하여 설명한다.

---

## 15. Avoid Overengineering

다음과 같은 행동을 피한다.

* 아직 필요하지 않은 인터페이스 생성
* 의미 없는 추상화 계층 추가
* 미래 기능을 예상한 과도한 클래스 생성
* 요청하지 않은 라이브러리 도입
* 요청하지 않은 Redis/Kafka 등의 인프라 추가
* 모든 패턴을 한꺼번에 적용
* 실제 필요가 확인되지 않은 Microservice 분리
* 초기 단계부터 완성형 Agent Framework 구축

필요성이 현재 단계에서 설명될 수 있는 코드만 추가한다.

---

## 16. Current Development Roadmap

현재 Harness 실행 기반, MySQL 영속화, 전략별 포트폴리오, KIS 모의투자 연동, 주문 생명주기, Risk Guard, 일봉 데이터와 AI Provider의 기본 골격은 구축된 상태다.

앞으로의 우선순위는 다음과 같다.

Phase A
스윙 Baseline 전략 계약 확정 및 운영 코드 분리

Phase B
운영 전략과 동일한 정책을 사용하는 백테스트 및 거래비용 모델

Phase C
Stock Universe, 유동성 필터와 전략별 Screener

Phase D
성과 지표, 벤치마크, Walk-Forward 및 미사용 기간 검증

Phase E
장기 개별주 전략을 위한 재무정보, 수정주가와 기업행위 데이터

Phase F
모의투자 운영 인프라 배포, Forward Test와 계좌 정합성 검증

Phase G
Monitoring 조회 API, JWT 인증과 React Dashboard

Phase H
AI Overlay와 deterministic Baseline 성과 비교

Phase I
Kill Switch, 실제투자 설정 분리와 제한된 자금의 단계적 투입

Phase J
성과 및 위험 기준을 재검증하면서 제한적으로 자금 확대

단타 전략은 분봉, 체결 품질과 슬리피지 모델이 준비되기 전까지 후순위로 둔다. 실제 자금 투입 시점은 일정이 아니라 검증 게이트 통과 여부로 결정한다.

---

## 17. Important Principle

이 프로젝트에서는 Codex 자체를 Harness가 구현해야 하는 Investment Agent로 착각하지 않는다.

Codex Agent의 역할:

현재 코드를 읽고
→ 사용자와 설계를 논의하고
→ 요청된 범위를 구현하고 검증하는 개발 협업 Agent

프로젝트 내부 Investment Agent의 역할:

시장을 확인하고
→ Tool을 사용하고
→ 투자 판단을 수행하는 런타임 Agent

둘은 명확히 분리한다.

---

## 18. Default Codex Behavior

명시적인 수정 요청이 없다면 다음 행동을 기본으로 한다.

DO:

* Repository 탐색
* 코드 읽기
* 현재 구조 설명
* 문제점 분석
* 설계 제안
* 코드 예제 제시
* 테스트 방법 설명
* 다음 단계 제안

DO NOT:

* 파일 수정
* 파일 삭제
* 임의 리팩터링
* 의존성 추가
* DB Schema 변경
* 테스트 코드 생성
* 새로운 기능 선행 구현
* Commit
* Push

사용자가 실제 변경을 명확하게 요청한 경우에만 해당 범위 내에서 변경한다.

---

## 19. Conversation Style

사용자의 우선 목표는 실제 운용에서 순성과를 검증할 수 있는 투자 시스템을 만드는 것이다.

따라서 결과 코드만 제시하기보다 다음을 설명한다.

* 왜 필요한가
* 전략 성과 또는 Harness 안전성에서 어떤 역할인가
* 다른 설계는 무엇이 있는가
* 지금 이 방식을 선택하는 이유는 무엇인가
* 어떤 데이터와 지표로 효과를 검증할 것인가
* 실제투자 전에 어떤 실패 조건을 확인할 것인가

설명은 의사결정에 필요한 수준으로 제공하되, 구현과 검증의 진행을 불필요하게 늦추지 않는다.

---

## 20. Evidence-Oriented Guidance

사용자가 다음 작업을 추천해달라고 요청할 때 Codex는 완성 코드부터 제시하지 않는다.

Codex는 기능 추가 자체보다 어떤 성과 가설 또는 운영 위험을 검증하는 작업인지 먼저 명확히 한다.

기본 추천 방식은 다음 순서를 따른다.

1. 현재 코드 상태
2. 현재 성과 또는 운영 병목
3. 검증할 가설과 실패 조건
4. 가능한 선택지와 비용 및 위험
5. Codex의 추천 방향과 이유
6. 구현 완료 조건
7. 백테스트, 모의투자 또는 운영 검증 방법
8. 추천 커밋 메시지

코드 예시는 기본적으로 최소화한다.

사용자가 "코드 예시도 줘", "구현이 감이 안 와", "더 자세히 설명해줘"처럼 명확히 요청한 경우에만 상세 예제 코드를 제공한다.

리뷰할 때는 컴파일 성공뿐 아니라 데이터 누수, 미래 정보 사용, 거래비용 누락, Risk Rule 우회와 실제 운용 코드와의 불일치를 우선 확인한다.

예:

* 이 규칙은 어떤 시장 가설을 검증하는가?
* 같은 정책을 백테스트와 실제 운용에서 재사용하는가?
* 거래비용과 슬리피지를 반영한 뒤에도 결과가 유지되는가?
* 같은 검증 구간을 반복 조정해 과적합하지 않았는가?
* 이 실패가 실제 주문이나 자금 손실로 이어지기 전에 중단되는가?

단, 컴파일 오류, 명백한 버그, 잘못된 패키지 경로, 프로젝트 규칙 위반처럼 수정 필요성이 분명한 경우에는 구체적인 수정 가이드와 이유를 함께 제공한다.
