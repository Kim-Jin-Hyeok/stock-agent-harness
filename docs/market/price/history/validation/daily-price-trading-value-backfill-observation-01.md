# 일봉 거래대금 보충 사전 점검 결과 01

## 판정

2026-10-03 로컬 MySQL의 스키마와 저장 일봉을 읽기 전용으로 확인했다. **V3까지만 적용되어 있고 V4의 두 컬럼이 없어 실제 보충은 실행하지 않았다.** 상태는 `PREFLIGHT_BLOCKED_SCHEMA_V4_MISSING`이다.

보충 실행과 재실행의 성공, KIS 응답과 DB 값의 일치, 거래대금 단위 또는 시간외 포함 범위를 확인한 보고서가 아니다. 스키마 변경 승인을 요청했으며, 이 관측에서는 자동 마이그레이션을 실행하거나 승인을 받은 것으로 간주하지 않았다.

## 확인한 환경

| 항목 | 관측 값 |
| --- | --- |
| 코드 기준 | `63a6c64358d9add6f6c3bdda007929ab8e4c4f06` |
| 원본 보존 시각 | 2026-10-03 11:10:07 KST |
| 사후 비교 시각 | 2026-10-03 11:11:18 KST |
| DB / 버전 | 로컬 `stock_agent_harness` / MySQL 8.4.11 |
| Flyway 이력 | V1, V2, V3 모두 `success=1`; V4 이력 없음 |
| 일봉 스키마 | ID·종목·날짜·OHLCV 존재; `trading_value_krw`, `trading_venue_scope` 없음 |
| 잠금 대기 설정 | 조회 세션의 `innodb_lock_wait_timeout=50`초; 실제 잠금 충돌 시험은 미실행 |
| 저장 일봉 | 4개 종목 × 729행 = 2,916행 |
| 저장 기간 | 각 종목 2023-09-25 ~ 2026-09-28 |

조회한 종목은 `000660`, `005380`, `005930`, `035420`이다. 이는 기존 저장 현황이지 새로운 Universe 선정이나 전체 시장 표본 검증이 아니다. 컬럼 자체가 없으므로 거래대금 누락 행 수를 `NULL` 조건으로 계산하지 않았다.

## 실행하려던 최소 범위

저장 데이터가 있는 다음 3행을 작은 보충 검증 대상으로 정했다. 성과를 보고 고른 종목이나 기간이 아니며, 이 범위에도 아직 보충을 수행하지 않았다.

| ID | 종목 | 날짜 | 시가 | 고가 | 저가 | 종가 | 거래량 |
| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |
| 726 | 005930 | 2026-09-21 | 263500 | 275000 | 259500 | 275000 | 33914657 |
| 727 | 005930 | 2026-09-22 | 279000 | 283500 | 271500 | 277500 | 31323214 |
| 728 | 005930 | 2026-09-23 | 282500 | 286500 | 280750 | 286500 | 32046681 |

계획은 KIS 모의투자, `INTEGRATED` 조회 범위, 포함 달력 기간 상한 3일, 차트 페이지 상한 1회, 요청 대기 1초다. 토큰 발급은 차트 페이지 예산과 별도다. 실제 외부 요청은 모두 0회다. 기존 행의 시장 범위는 스키마 부재로 확인할 수 없으며, 계획한 현재 설정으로 과거 원천 범위를 확정하지 않는다.

승인 후에는 [수동 실행 문서](../daily-price-trading-value-backfill.md)의 격리 설정을 사용한다. V4를 별도로 적용·확인한 뒤 보충 실행에서는 Flyway를 끄고, 웹 서버·하네스·다른 수집·주문 스케줄러·AI를 비활성화한다. 기존 OHLCV가 원천과 충돌하면 보충을 실패로 남기며, 통과시키기 위해 원본을 바꾸지 않는다.

## 수행 내역과 보존 확인

앱과 MySQL 컨테이너는 점검 시작 시 모두 정지 상태였다. 기존 MySQL만 임시 기동해 SELECT를 실행했고, 종료 시 다시 정지했다. 앱은 시작·재빌드·재생성하지 않았다.

- 전체 일봉과 선택한 3행의 ID·OHLCV를 날짜순으로 보존했다.
- 사전·사후 전체 일봉 행 수는 2,916행으로 같았고 SHA-256도 같았다.
- 일봉 INSERT·UPDATE·DELETE, DDL, Flyway 실행, 포트폴리오 초기화, 주문과 외부 API 호출을 하지 않았다.
- `DailyPriceTradingValueBackfillRunner`를 실행하지 않아 `BACKFILLED` 또는 `NO_TARGETS` 결과도 없다. 미실행을 서비스의 성공이나 롤백으로 해석하지 않는다.
- 운영 Java·설정·의존성은 변경하지 않았고, 자동 테스트는 이번 사전 점검에서 다시 실행하지 않았다.

원본과 기계 판독 가능한 결과는 Git에서 무시되는 `build/trading-value-backfill-observation-01/`에 보존했다. `capture-preflight.ps1`과 `verify-preflight.ps1`은 SELECT 및 증거 저장만 하는 이번 관측용 보조 스크립트이며 운영 패키지에 추가하지 않았다. Key·Secret·토큰·계좌 접속 정보는 증거에 포함하지 않았다.

| 파일 | SHA-256 |
| --- | --- |
| `schema-before.tsv` | `801C30C13D8AE9BE4D156B92BAFD7DACF058F3B3CE59D2E326445E721122B057` |
| `all-ohlcv-before.tsv` | `CED446351AAEBD341CB98D2C5B52EDDE0DB883631C683BC356D1ACDE57822424` |
| `selected-ohlcv-before.tsv` | `34F3CB4AE465518BE3105B86803E5DC75759BC955E63DEB6D0F4C7BFCA5B5D60` |
| `symbol-counts-before.tsv` | `570CF8CFD006E8323E1603EDAF2064C62568F93D92F4E86AB450BC5A16527A1C` |
| `all-ohlcv-after-preflight.tsv` | `CED446351AAEBD341CB98D2C5B52EDDE0DB883631C683BC356D1ACDE57822424` |

추가 결과 파일은 `preflight-summary.json`, `preflight-verification.json`이다. 전체 해시는 종목·날짜 정렬의 탭 구분, UTF-8 BOM 없음, LF 줄바꿈 기준이다. 이 해시는 보충 전후 메타데이터 일치가 아니라 **사전 점검 동안 기존 ID·OHLCV가 변경되지 않았음**을 확인한다.

## 원천 의미의 확인 범위

2026-10-03 확인한 [KIS 공식 조회 예제](https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/domestic_stock/inquire_daily_itemchartprice/inquire_daily_itemchartprice.py)는 `J`·`NX`·`UN`을 각각 KRX·NXT·통합으로 설명하고, 가격 조정 인자 `0`을 수정주가로 구분한다. 실제 보충 시 해당 요청 인자도 증거로 남겨야 한다.

[KIS 공식 응답 필드 예제](https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/domestic_stock/inquire_daily_itemchartprice/chk_inquire_daily_itemchartprice.py)는 `acml_tr_pbmn`을 누적 거래대금으로 표시한다. 하지만 이번에 확인한 예제만으로 금액 배율이나 시간외 포함 범위를 확정하지 않았다. 같은 종목·날짜·시장 범위의 응답 값과 독립적인 공식 자료의 숫자 비교도 아직 하지 않았다.

따라서 [후보 Universe 계약](../../../../strategy/swing/validation/swing-v1-candidate-universe-contract.md)의 단위·세션·과거 시점 정보 미검증 조건을 유지한다. 평균 거래대금 계산, 유동성 후보 확대와 실제투자 승격의 근거로 사용하지 않는다.

## 남은 완료 조건

1. 로컬 DB에 기존 `V4__add_daily_price_trading_value.sql`을 적용할 명시적인 승인을 받은 뒤 Flyway 이력과 두 컬럼을 확인한다.
2. 같은 3행의 메타데이터 포함 원본을 새로 보존하고, 격리된 Runner로 승인한 범위만 보충한다. KIS 원문 시세 필드·호출 수·결과 로그를 비밀값 없이 남긴다.
3. 전체 행 수·ID·OHLCV·이미 알려진 값의 유지, 선택한 행의 누락 필드만 변경, 응답 정수와 저장값의 일치를 비교한다. 실패하면 실제 DB 상태를 확인하고 실패 원인을 기록한다.
4. 성공한 경우 같은 요청을 다시 실행해 `NO_TARGETS`, 추가 시세 조회 0회, DB 변경 없음 여부를 확인한다. 성공하지 않았는데 재실행 성공을 가정하지 않는다.
5. 거래대금 배율과 시장·시간외 범위를 별도 공식 근거로 확인한다. 근거가 부족하면 미검증 상태로 남긴다.

이번 결과는 준비 부족을 안전하게 발견한 사전 점검이다. 보충 기능의 실 DB 검증 완료나 수익성 검증 완료가 아니다.
