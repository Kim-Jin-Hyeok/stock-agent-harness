# 일봉 거래대금 보충 실측 검증 결과 02

## 판정

2026-10-03 승인된 로컬 MySQL에 기존 V4를 적용하고, 삼성전자 `005930`의 `2026-09-21~23` 3행만 KIS 모의투자 API로 보충했다. **첫 실행은 `BACKFILLED`, 별도 JVM의 같은 요청은 `NO_TARGETS`였다.** 재실행의 KIS 요청 시도는 0회이고 DB 데이터도 그대로였다.

확인한 것은 제한된 범위의 실제 응답 저장, 기존 데이터 보존과 재실행 조회 생략이다. 거래대금 금액 배율·시간외 포함 범위·과거 시점 Universe·전략 수익성을 검증한 결과는 아니다. [사전 점검 01](daily-price-trading-value-backfill-observation-01.md)의 V4 미적용 기록은 당시 상태로 유지한다.

첫 관측 보조 코드에는 로그 수집 오류가 있었다. 보충이 이미 커밋된 뒤 검증용 assertion이 실패했으며, 이를 Broker 또는 보충 실패·롤백으로 해석하지 않았다. 실패 기록을 보존하고 원본 값을 되돌리지 않은 상태에서 별도 프로세스 재실행 및 파일 기반 비교를 완료했다.

## 실행 조건

| 항목 | 값 |
| --- | --- |
| 코드 기준 | `1a019bb728b50b468269478d3fbc9250b1457ef5` |
| DB / 버전 | 로컬 `stock_agent_harness` / MySQL 8.4.11 |
| 마이그레이션 | Flyway, 목표 버전 4; V3에서 기존 V4 한 개만 적용 |
| 기존 일봉 | 4개 종목 × 729행 = 2,916행 |
| 대상 / 기간 | `005930` / 2026-09-21 ~ 2026-09-23, 양 끝 포함 |
| 조회 시장 / 가격 인자 | `INTEGRATED`, 요청 코드 `UN` / `FID_ORG_ADJ_PRC=0` |
| 일봉 인자 | `FID_PERIOD_DIV_CODE=D` |
| 기간 / 페이지 상한 | 최대 달력 3일 / 차트 최대 1페이지 |
| 요청 대기 | 1초 |
| Runner가 계산한 최근 확정일 | 2026-10-02 |
| KIS 환경 | 모의투자 `openapivts.koreainvestment.com:29443` |
| 최초 Runner 시작 / 종료 | 2026-10-03 11:31:50.126 / 11:31:51.640 KST |
| 재실행 Runner 시작 / 종료 | 2026-10-03 11:35:24.859 / 11:35:24.943 KST |
| 최종 파일 비교 시각 | 2026-10-03 11:42:36 KST |

Runner 소요 시간은 첫 실행 1,514ms, 재실행 84ms다. JVM 시작·Spring 초기화·증거 저장·종료 시간은 이 값에 포함하지 않는다. 한 종목의 짧은 요청 한 번을 전체 시장 수집 성능으로 일반화하지 않는다.

KIS 자격 증명은 `.env`의 모의투자 값만 프로세스 환경에 전달했다. DB 접속 값은 기존 로컬 MySQL 컨테이너에서 내부적으로 읽었으며 인자·문서에 비밀값을 넣지 않았다. `.env`와 정상 서버의 설정은 수정하지 않았다.

## 마이그레이션과 격리

별도 Flyway 실행으로 `V4__add_daily_price_trading_value.sql`을 적용했다. `trading_value_krw BIGINT NULL`, `trading_venue_scope VARCHAR(30) NULL`이 생성되었고 V4 이력은 `success=1`이었다. 기존 일봉 ID·OHLCV·고유 인덱스 및 다른 테이블의 데이터 해시는 유지됐다. 추가된 두 필드는 보충 전 모두 `null`이었다.

이후 보충에서는 `local` 프로필만 사용하고 Flyway를 끈 상태로 Hibernate `validate`를 통과했다. 웹 서버, 하네스, 주문 조회·취소 스케줄러, 다른 종목·지수 수집 경로, 수동 백테스트와 AI는 비활성화했다. 실제 주문·계좌 조회·포트폴리오 초기화는 실행하지 않았다.

관측용 코드는 기존 `DailyPriceTradingValueBackfillRunner`, Service, KIS Provider와 Client를 그대로 사용했다. 첫 실행의 HTTP 관측 계층은 모의투자 토큰·승인한 차트 경로만 허용하고 토큰 1회·차트 1회 상한을 적용했다. 요청 헤더와 토큰 응답 본문은 저장하지 않았고 차트 응답은 허용한 시세 필드만 기록했다.

무한 대기를 막기 위해 관측 프로세스에만 연결 5초·응답 30초·전체 프로세스 180초 제한을 두었고, Spring DB 연결의 잠금 대기는 세션에만 5초로 지정했다. 실제 잠금 충돌은 발생시키지 않았다. 운영 Java·설정·의존성과 Docker 이미지는 변경하지 않았다.

## 실제 결과

| 실행 | 결과 | 대상 행 | 응답 행 | 변경 행 | 토큰 요청 | 차트 요청 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 최초 | `BACKFILLED` | 3 | 3 | 3 | 1 | 1 |
| 별도 JVM 재실행 | `NO_TARGETS` | 0 | 0 | 0 | 0 | 0 |

최초 응답은 HTTP 200, `rt_cd=0`, `msg_cd=MCA00000`이었다. 토큰 요청의 관측 시간은 248ms, 차트의 응답 헤더 수신까지는 16ms였다. 기존 KIS Client가 본문 해석까지 기록한 차트 시간은 32ms였다. 응답 3행은 요청한 날짜와 일치했고 OHLCV도 기존 저장값과 같았다.

다음 숫자는 `acml_tr_pbmn`의 원문 정수를 그대로 저장한 값이다. 컬럼명이 `trading_value_krw`인 것만으로 원 단위 배율의 독립 검증을 완료했다고 인정하지 않는다.

| ID | 날짜 | 원문 정수 / 저장값 | 저장 시장 범위 |
| ---: | --- | ---: | --- |
| 726 | 2026-09-21 | 9208918133058 | `INTEGRATED` |
| 727 | 2026-09-22 | 8743631121850 | `INTEGRATED` |
| 728 | 2026-09-23 | 9100552358490 | `INTEGRATED` |

첫 보충에서 변경된 필드는 이 3행의 기존 `null` 거래대금·시장 범위뿐이었다. 신규 일봉을 삽입하거나 OHLCV를 덮어쓰지 않았고, 나머지 2,913행은 메타데이터까지 그대로였다. 첫 실행 전 알려진 거래대금은 없었으므로 알려진 값·확인된 0의 충돌 보호를 이번 실측에서 직접 시험했다고 주장하지 않는다.

재실행은 새로운 JVM·ApplicationContext에서 수행했다. KIS RestClient의 외부 요청 시도를 관측하고 차단하는 계층을 두었으며 시도 자체가 0회였다. 전체 일봉과 다른 테이블의 해시가 첫 보충 직후와 같았고, 이미 채워진 값도 유지됐다.

## 원본 보존과 증거

이전 관측 01의 전체 ID·OHLCV와 이번 마이그레이션 전 입력을 행 단위로 비교해 일치함을 확인했다. 마이그레이션 전후, 보충 전후, 재실행 전후 및 MySQL 재기동 후에도 원본 필드의 canonical SHA-256은 다음 값으로 같았다.

`38aff53bdd24bfe001a63013321ac601bd6c07de452109a689a920ee24565af4`

이 해시는 종목·날짜순으로 정렬한 ID·종목·날짜·OHLCV 문자열 맵의 compact JSON 기준이다. 관측 01의 TSV 해시와 직렬화 방식이 다르므로 해시 문자열을 직접 비교하지 않고 원본 필드를 비교했다. 메타데이터를 포함한 전체 행 파일의 해시는 보충으로 달라지는 것이 정상이다.

원본·시세 응답·로그·관측 코드·테스트 XML은 Git에서 제외되는 `build/trading-value-backfill-observation-02/`에 보존했다. 주요 증거는 다음과 같다.

- `before-migration-*`, `after-migration-*`: V4 적용 전후 스키마·원본·테이블 해시.
- `before-backfill-*`, `after-backfill-*`, `chart-response.json`, `http-audit.json`, `backfill.log`: 최초 보충·응답·호출 수.
- `before-replay-*`, `after-replay-*`, `replay-result.json`, `replay.log`: 독립 JVM 재실행과 무변경 확인.
- `failure-inspect-*`: 종료 코드 137 이후 MySQL 재기동 상태의 읽기 전용 점검.
- `backfill-failure.json`, `run-observation-02-first.ps1`: 첫 관측 보조 코드의 오류와 실행 당시 스크립트.
- `verification-summary-final.json`, `environment-final.json`: 최종 데이터·테스트 판정과 컨테이너 상태.
- `focused-test-results/`, `evidence-manifest-complete.json`: 자동 테스트 XML과 증거 파일 해시 목록.

중간 파일은 삭제하지 않았다. 최종 판정에는 `verification-summary-final.json`을 사용한다. 초기 테스트 집계는 긴 XML 파일명의 일부만 매칭해 31건으로 과소 집계했으며, 최종 집계는 XML의 클래스명으로 6개 클래스 103건을 확인했다.

| 파일 | SHA-256 |
| --- | --- |
| `chart-response.json` | `3FFF202DBE1B2F4B340B4B74367546713949A7820E7C3BEE3544AC200F8DF132` |
| `verification-summary-final.json` | `5E7444E508C4949A5F1A7284406B8A0483E1D261DB55FEE8D81B1BD82CBB5E2A` |
| `evidence-manifest-complete.json` | `683E04FDB563FB58F94B5038FBDFEA5B4E9AC93FF9EF0882E8160C7BF18BF4D3` |

증거의 Key·Secret·계좌번호 실제 값과 미마스킹 토큰 여부를 점검했다. 토큰 응답·요청 인증 헤더를 증거로 저장하지 않았다.

## 실행 중 발견한 사항

첫 관측 코드가 Spring 시작 전 등록한 로그 Appender는 로깅 초기화 중 해제되어 메모리의 Runner 로그 목록이 비었다. 실제 Runner 완료 로그와 DB 반영은 존재했지만 관측 assertion이 실패해 보조 프로세스는 종료 코드 1이었다. 실패 뒤 DB를 초기화하거나 같은 구간의 시세를 다시 조회하지 않았다. 파일의 완료 로그·원문 응답·전체 데이터 비교와 별도 JVM의 재실행을 기준으로 최종 판정했다.

MySQL의 첫 정지는 종료 대기 설정이 1초인 상태에서 종료 코드 137로 끝났고 `OOMKilled=false`였다. 다시 기동해 V4 이력, 보충값과 다른 테이블 해시의 유지 여부를 확인했다. 이후 `docker compose stop --timeout 60 mysql`로 정지해 종료 코드 0을 확인했다. 앱은 전 과정에서 정지 상태였고 최종 MySQL도 정지 상태다. Compose 파일이나 볼륨을 변경하지 않았다.

Flyway 로그에는 MySQL 8.4의 지원 검증 범위에 대한 경고가 있었다. 이번 V4 적용은 성공했지만 모든 마이그레이션의 운영 호환성을 증명한 것은 아니다. 라이브러리 업그레이드는 이번 범위에 포함하지 않았다.

## 자동 테스트와 한계

`.\gradlew.bat classes test --tests '*DailyPriceTradingValueBackfill*' --no-daemon`을 실행했다. Service 31, H2 통합 10, 결과 계약 15, Runner 15, 설정 19, Spring Context 13으로 총 103개 모두 통과했고 실패·오류·스킵은 0이었다. 전체 프로젝트 테스트는 이번에 다시 실행하지 않았다.

이번 실측은 한 종목·3일의 정상 경로와 완전한 행의 재실행만 검증했다. 실제 MySQL 동시 갱신·잠금 타임아웃·DB 쓰기 실패 롤백·Broker 오류 경로의 운영 검증을 대신하지 않는다. 관측용 HTTP/시간 제한도 운영 설정 변경이 아니다.

금액 배율·시간외 포함 범위의 독립적인 공식 자료 비교, 수정주가·기업행위·과거 시점 정보와 전체 기간 누락 검증은 남아 있다. [후보 Universe 계약](../../../../strategy/swing/validation/swing-v1-candidate-universe-contract.md)의 미검증 조건을 유지하며, 이번 결과만으로 평균 거래대금 필터·후보 확대·실제 주문을 활성화하지 않는다.
