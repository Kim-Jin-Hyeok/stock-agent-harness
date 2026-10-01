# SWING_V1 다종목 백테스트 관측 결과 04

## 판정

2026-10-01 후보 종목 설정을 운영 Universe에서 분리한 기존 백테스트 경로로 네 종목을 검증했다. 부족한 세 종목의 일봉만 KIS 모의투자 API에서 수집했고, 동일한 조건으로 서로 다른 JVM 및 Spring ApplicationContext에서 두 번 실행했다.

종목별 청산비용 반영 누적 수익률은 삼성전자 **31.02649%**, SK하이닉스 **12.11111%**, 현대차 **5.74663%**, NAVER **-1.63397%**다. 네 종목의 중앙값은 **8.92887%**, 완료 거래는 총 19개였다. 손실 종목을 제외하지 않았다.

**다종목 실행, 데이터 보존, 재현성과 계산 정합성은 확인했다. 반복 가능한 양의 기대수익 또는 실제투자 승격을 입증한 결과는 아니다.** 모든 종목의 이익이 소수 거래에 집중되고, 동일 종목 10% 매수 후 보유 대비 수익률 우위도 네 종목 중 두 종목에만 관측됐다.

[확대 관측 결과 02](swing-v1-baseline-observation-02.md)와 [매수 후 보유 관측 결과 03](swing-v1-buy-and-hold-observation-03.md)은 원본 그대로 보존했다. 전략, 비용 모델, Risk Rule, 운영 후보와 스케줄은 변경하지 않았다.

## 사전 확정 조건

| 항목 | 값 |
| --- | --- |
| 조건 확정 시각 | 2026-10-01 15:29:48.7695549 KST, 신규 수집 및 결과 평가 전 |
| 백테스트 코드 기준 | `fcab5f9`, 후보 종목 설정 분리 구현 |
| 검증 종료 시 HEAD | `2392db7`, `fcab5f9` 대비 애플리케이션·테스트·빌드 코드 차이 없음 |
| 전략 / 판단 방식 | `SWING_V1`, version 1, SWING / deterministic Baseline |
| 요청 후보 및 순서 | `005930`, `000660`, `005380`, `035420` |
| 벤치마크 | KOSPI 하나, 저장된 동일 평가일의 지수 종가 |
| 신호 기간 | 2024-03-25 ~ 2026-09-23, 양 끝 포함 |
| 실제 평가 기간 | 2024-03-26 ~ 2026-09-28, 종목마다 609개 평가일 |
| 입력 일봉 구간 | 2023-09-25 ~ 2026-09-28, 종목마다 729개 |
| 초기 자금 / 보유 | 종목마다 독립 현금 10,000,000원 / 없음 |
| 판단 이력 / 지표 | 최근 120개 확정 일봉 / SMA 20·60, Wilder ATR 14 |
| 위험 예산 / ATR 배수 | 총자산의 0.5% / 2.0 |
| 수량 상한 | `max-order-ratio=0.1`, `max-position-ratio=0.3` |
| 체결 모델 | `DAILY_OPEN_FILL_APPROXIMATION` |
| 런타임 | Java 21.0.12, Spring Boot 3.5.16, MySQL 8.4.11 |

후보는 기존 대조 종목 하나와 신규 종목 세 개를 포함하는 고정된 소규모 실행 검증 표본이다. 결과나 교차 신호를 보고 선택하지 않았지만, 과거 시점의 전체 상장 종목·업종 비중·유동성 기준으로 구성한 Universe도 아니다. 현재 알려진 종목을 선택한 편향과 반도체 종목 간 상관을 포함한다. 투자 추천 또는 시장 대표 표본으로 해석하지 않는다.

기간과 초기 자금은 기존 관측 02의 비교 조건을 유지했다. 이 초기 자금은 과거 백테스트 조건이며 실제 Broker 잔액을 대체하지 않는다. 네 독립 포트폴리오의 요약을 공유 현금 다종목 계좌의 성과로 해석하지 않는다.

## 데이터 확보와 보존

수집 전 로컬 DB에는 삼성전자 일봉 729개와 KOSPI 관측 731개가 있었다. 앱과 MySQL 컨테이너는 중지 상태였고 MySQL만 임시로 시작했다. 읽기 전용 사전 점검 후 조건을 고정했으며, 신규 수집만 별도 쓰기 가능 프로세스에서 실행했다.

| 종목 | 수집 전 | 신규 저장 | 수집 후 | 일봉 조회 호출 |
| --- | ---: | ---: | ---: | ---: |
| `005930` | 729 | 0 | 729 | 0 |
| `000660` | 0 | 729 | 729 | 8 |
| `005380` | 0 | 729 | 729 | 8 |
| `035420` | 0 | 729 | 729 | 8 |
| 합계 | 729 | 2,187 | 2,916 | 24 |

기존 `DailyPriceHistoryCollectionService`와 KIS Provider를 그대로 사용했다. 삼성전자는 `ALREADY_UP_TO_DATE`로 조회 없이 건너뛰었다. 신규 종목은 각각 앞 일곱 페이지 100개씩, 마지막 페이지 29개를 반환했다. 24개 일봉 조회 모두 `SUCCESS`, `MCA00000`이며 HTTP 소요 시간 합은 1,169ms였다. 인증, 요청 전 대기와 DB 저장 시간은 이 합에서 제외한다.

요청은 모의투자 주소의 `inquire-daily-itemchartprice`, 시장 코드 `UN`, `FID_ORG_ADJ_PRC=0`을 사용했다. 종목당 최대 10페이지와 요청 전 1초 대기를 유지했고 한도를 확대하거나 자동 재시도하지 않았다. 전체 네 종목의 같은 요청을 재실행하면 모두 `ALREADY_UP_TO_DATE`, 조회·저장 0개로 끝났다. 최신 일자만 보고 과거 결측을 놓칠 수 있으므로 날짜 검증도 별도로 수행했다.

각 종목의 729개 날짜를 같은 구간의 KOSPI 저장 날짜와 양방향 비교했다. 누락·추가 날짜, 비양수 OHLC, 잘못된 고가·저가와 음수 거래량은 모두 0개였다. 첫 신호일까지 120개 판단 이력을 확보했고, 모든 종목의 실제 평가일 609개와 벤치마크 날짜가 일치했다.

수집 전 모든 테이블을 PK 순서로 조회해 내용 해시를 저장했다. 수집 후 원래 삼성전자 729개 행의 ID와 모든 필드는 동일했고, 나머지 여덟 테이블의 내용 해시도 같았다. 두 백테스트 실행 각각의 전후 전체 테이블 내용 해시가 같았다.

최종 건수는 `daily_price_bar=2916`, `market_index_daily_observation=731`, `broker_order=0`, `current_price_observation=80`, `harness_run_entity=82`, `harness_step_entity=2075`, `strategy_portfolio=3`, `trade_record_entity=81`, `flyway_schema_history=3`이다. 일봉 외 건수와 내용은 사전 점검과 같다.

이 검증은 저장 데이터의 내부 정합성과 보존 검사다. 종목·지수에 함께 빠진 날짜, 원본 시세의 정확성, 수정주가·배당·분할 등 기업행위와 과거에 실제 제공된 데이터 버전까지 독립적으로 검증한 것은 아니다.

## 비용과 종목별 결과

비용은 기존 `EXAMPLE_UNVERIFIED_V1`, version 1을 유지했다. 매수·매도 수수료 각각 0.00015, 매도 세금 0.0018, 매수·매도 슬리피지 각각 0.001이다. **실제 계좌 비용으로 검증된 값이 아니다.**

| 종목 | 청산비용 반영 순손익 | 누적 수익률 | 시가 평가 MDD | 완료 거래 | 이익 / 손실 거래 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 삼성전자 `005930` | 3,102,649원 | 31.02649% | 14.38969% | 4 | 1 / 3 |
| SK하이닉스 `000660` | 1,211,111원 | 12.11111% | 13.38533% | 4 | 3 / 1 |
| 현대차 `005380` | 574,663원 | 5.74663% | 3.73858% | 4 | 2 / 2 |
| NAVER `035420` | -163,397원 | -1.63397% | 3.93212% | 7 | 1 / 6 |

모든 종목은 종료 시 보유 수량이 0이므로 최종 가정 청산비용도 0원이다. 실제 거래 중 발생한 수수료·세금·슬리피지 합은 각각 24,951원, 11,389원, 12,837원, 15,297원이다. MDD는 일중 고저가나 실제 호가가 아닌 시가 평가곡선 기준이다.

중앙값 수익률은 8.92887%, 이익 종목 3개, 손실 종목 1개, 무거래 종목 0개다. 같은 날짜 KOSPI 종가 수익률은 149.89173368%이며 이를 초과한 종목은 0개, 중앙값 초과수익률은 -140.96286368%p다. 현금 비중·종목·시가와 종가 시점의 차이가 있으므로 위험 조정 알파가 아니다.

## 동일 종목 매수 후 보유 비교

모든 비교는 SWING과 같은 종목, 평가일·시각, 시가와 비용 모델을 사용했다. 최초 100%와 10% 배분을 사전에 고정하고 전체 초기 현금을 수익률 분모로 유지했다. 비교 배분이 같다고 보유 중 비중이나 위험까지 같아지는 것은 아니다.

| 종목 | SWING 수익률 / MDD | 최초 100% 보유 수익률 / MDD | 최초 10% 보유 수익률 / MDD |
| --- | --- | --- | --- |
| `005930` | 31.02649% / 14.38969% | 253.58969% / 46.29045% | 24.34460% / 15.52656% |
| `000660` | 12.11111% / 13.38533% | 969.96200% / 60.66899% | 83.61739% / 37.55887% |
| `005380` | 5.74663% / 3.73858% | 47.83577% / 54.18205% | 4.66690% / 13.78182% |
| `035420` | -1.63397% / 3.93212% | 3.81914% / 35.94959% | 0.36028% / 4.97554% |

100% 보유 수익률을 넘은 종목은 없고, 10% 보유 수익률을 넘은 종목은 삼성전자와 현대차 두 개다. SWING의 시가 평가 MDD는 네 종목 모두 두 보유 비교보다 낮았다. 이 결과는 순수익과 손실 폭의 차이를 함께 보여줄 뿐, 어느 방식이 항상 낫다는 결론이 아니다. 100% 보유 비교는 운영 주문 권한을 확대하지 않는다.

## 판단 진단과 이익 집중

| 종목 | 골든크로스 | 실제 매수 / 매도 | 주문 차단 | 가장 큰 이익 거래 / 전체 이익 거래 합 |
| --- | ---: | ---: | --- | ---: |
| `005930` | 4 | 4 / 4 | 없음 | 100% |
| `000660` | 4 | 4 / 4 | 없음 | 90.50177% |
| `005380` | 5 | 4 / 4 | `ATR_RISK_BUDGET_INSUFFICIENT` 1회 | 94.99481% |
| `035420` | 7 | 7 / 7 | 없음 | 100% |

현대차의 골든크로스 한 번은 ATR 위험 예산으로 1주를 허용하지 못해 HOLD가 됐다. 주문을 강제로 만들거나 수량 상한을 완화하지 않았다. 포트폴리오 전이 거절과 미청산 보유는 네 종목 모두 없었다.

가장 큰 이익 거래의 순손익은 삼성전자 3,275,996원, SK하이닉스 1,147,176원, 현대차 625,442원, NAVER 146,491원이다. 위 집중도 분모는 손실을 차감한 최종 순손익이 아니라 이익 거래의 순손익 합이다. NAVER는 이익 거래 한 건이 있었지만 손실 여섯 건을 상쇄하지 못했다. 높은 이익 거래 집중과 적은 거래 수 때문에 반복 가능한 기대수익을 주장하지 않는다.

## 재현성과 독립 검산

기존 `SwingV1BacktestExperimentEvaluationService`를 읽기 전용 DB 연결로 호출했다. 입력에는 명시적 후보 목록·비용, 네 종목의 원본 OHLCV와 벤치마크 관측을 포함한다. 전체 결과에는 모든 Step·판단 근거·체결·포트폴리오·성과, 여덟 매수 후 보유 비교와 진단을 담았다.

| 항목 | 1차 | 2차 |
| --- | --- | --- |
| 평가 서비스 시작 시각 KST | 15:32:24.3565864 | 15:33:53.5760601 |
| 평가 서비스 소요 시간 | 12,939ms | 11,742ms |
| 입력 SHA-256 | 아래 입력 해시 | 동일 |
| 전체 결과 SHA-256 | 아래 결과 해시 | 동일 |

```text
inputSha256=f2af22f56dcbf8f4bbb9652edaa94a1518f1bb7e961c8424400a5922891f5294
resultSha256=fd6aba1d7e7124b492ede824d4598f0ee2b42eeb42ec749b4d1a7df5b9de9358
```

시작 시각과 소요 시간은 비교 대상에서 분리했다. 두 JVM의 입력·전체 결과 바이트 해시는 각각 일치했다. 원래 관측 02·03의 입력과 결과 해시도 유지됐고, 새 결과 중 삼성전자 전체 보고서와 두 보유 비교는 이전 결과와 `JsonNode.equals`로 동일했다.

PowerShell decimal 연산으로 Java 계산기를 호출하지 않고 70,119개 항목을 검산했으며 불일치는 0개였다. 검산 대상은 다음과 같다.

- 2,436개 Step의 현재·이전 SMA, 교차 신호와 Wilder ATR, 신호일 이하 120개 판단 이력과 다음 저장 거래일 시가 연결.
- 38개 체결의 매수 올림·매도 내림 슬리피지, 수수료·세금, 현금·수량 전이, 진입 수량이 기록된 위험 기반 수량 및 Harness 상한 이하인지 확인.
- 완료 거래 19개의 정산 손익, 평가 자산과 MDD, 최종 가정 청산비용.
- 여덟 보유 비교의 비용 포함 최대 정수 수량, 잔여 현금, 609개 평가일·시각별 시가 평가, MDD와 최종 청산비용.
- 요청 후보와 보고서 순서, 입력 및 결과 재현성.

금액은 정확히 일치해야 했고 비율 허용 오차는 `1e-12`였다. 재현성과 독립 검산은 계산 일관성을 확인한다. KIS 원본 데이터의 진실성이나 실제 09:10 체결 가능성을 증명하지 않는다.

`gradlew test --rerun-tasks --console=plain`으로 회귀 테스트 1,443개가 통과했고 실패·건너뜀은 0개였다. 테스트는 H2를 사용하며 로컬 MySQL 관측 데이터와 분리된다.

## 실행 상태와 증거

신규 수집에만 KIS 모의 인증정보와 DB 쓰기를 허용했다. 비밀값은 프로세스 환경변수로 전달했고 로그나 문서에 출력하지 않았다. 모든 프로세스에서 웹, Flyway, Harness·주문 조회·주문 취소·시세 수집 스케줄러, OpenAI와 자동 Runner를 비활성화했다. 수집은 기존 서비스를 직접 호출했고 평가는 기존 평가 서비스를 직접 호출했다. Broker 주문이나 Harness Run·거래 이력을 생성하지 않았다.

검증 후 MySQL을 다시 중지했다. 앱 컨테이너는 시작하지 않았고 이미지·Compose·`.env`·운영 설정·DB 스키마는 변경하지 않았다. 신규 일봉 2,187개는 로컬 데이터 볼륨에 남아 있다. Commit과 Push는 수행하지 않았다.

임시 도구와 전체 증거는 Git에서 제외되는 다음 경로에 있다. 이전 관측 파일을 덮어쓰지 않았다.

```text
build/backtest-observation-04/SwingV1Observation04.java
build/backtest-observation-04/run-observation-04.ps1
build/backtest-observation-04/plan.json
build/backtest-observation-04/inventory.json
build/backtest-observation-04/inspect-database.json
build/backtest-observation-04/collect-02{.log,-collections.json,-after-database.json,-coverage.json}
build/backtest-observation-04/collect-03{.log,-collections.json,-after-database.json,-coverage.json}
build/backtest-observation-04/run-01/{input,result,metadata}.json
build/backtest-observation-04/run-02/{input,result,metadata}.json
build/backtest-observation-04/audit-observation-04.ps1
build/backtest-observation-04/audit.json
build/backtest-observation-04/ComparePriorObservation.java
build/backtest-observation-04/prior-comparison.json
build/backtest-observation-04/postflight-database.json
```

`collect-01.log`은 임시 도구의 JSON 숫자 노드 표현 비교 때문에 외부 호출 전에 종료된 기록이다. 동일한 비용 값을 JSON으로 정규화해 비교하도록 도구만 수정했고 조건 파일은 다시 생성하지 않았다. 실제 신규 수집은 `collect-02`, 조회 생략 대조는 `collect-03`이다.

`build` 증거는 `gradlew clean`으로 삭제될 수 있다. 이 문서는 원본 입력·전체 결과의 장기 보관을 대신하지 않는다. 원본이 사라졌다면 같은 관측의 재현을 주장하지 않는다.

기존 증거와 동일한 DB 데이터가 남아 있다면 아래처럼 새 라벨로 읽기 전용 평가를 재실행한다. 같은 라벨의 로그가 있으면 도구가 거부하므로 기존 증거를 삭제하지 말고 다른 새 라벨을 사용한다. 조건을 다시 고정하는 `plan`과 원본 점검 `inspect`는 재실행하지 않는다.

```powershell
docker compose start mysql
.\gradlew.bat -I build/backtest-observation/classpath.init.gradle writeObservationClasspath --console=plain
powershell.exe -NoProfile -File build/backtest-observation-04/run-observation-04.ps1 -Mode evaluate -Label recheck-01
powershell.exe -NoProfile -File build/backtest-observation-04/run-observation-04.ps1 -Mode evaluate -Label recheck-02
Get-FileHash -Algorithm SHA256 build/backtest-observation-04/run-01/input.json,build/backtest-observation-04/recheck-01/input.json,build/backtest-observation-04/recheck-02/input.json
Get-FileHash -Algorithm SHA256 build/backtest-observation-04/run-01/result.json,build/backtest-observation-04/recheck-01/result.json,build/backtest-observation-04/recheck-02/result.json
powershell.exe -NoProfile -File build/backtest-observation-04/audit-observation-04.ps1
docker compose stop mysql
```

위 검산 스크립트는 보존된 `run-01`과 `run-02`를 검산한다. 새 라벨의 재실행은 위 해시 비교로 원본과 동일한지 별도로 확인한다. DB에 데이터가 추가되면 시세 구간이 같아도 보존 검사 조건을 먼저 검토해야 하며, 운영 중인 DB를 임의로 중지하지 않는다.

## 완료 범위와 한계

고정한 모든 후보를 포함한 다종목 실행과 동일 종목 비교를 완료했다. 손실 종목, 위험 예산으로 차단된 신호와 이익 집중도를 그대로 남겼다. 이 관측을 계기로 기존 전략의 규칙이나 비용을 결과에 맞춰 조정하지 않았다.

네 종목만 선택한 표본이며 이미 본 기간과 삼성전자 결과를 포함한다. 신규 종목도 이번 평가 이후에는 미사용 검증 표본으로 재사용하지 않는다. 거래 간 상관·생존 편향·기업행위·실제 계좌 비용·호가·미체결·일중 손절과 Forward Test는 여전히 별도 검증 대상이다. 현재 결과만으로 실제 자금 투입을 허용하지 않는다.
