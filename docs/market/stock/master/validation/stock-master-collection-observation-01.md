# KIS 종목 마스터 수집 실측과 원본 형식 검증

## 결론과 검증 범위

2026-10-05 기존 수동 수집기로 KOSPI·KOSDAQ 마스터를 각각 한 번 받았다. **두 파일의 보존·해시·ZIP 내용·CP949 디코딩·행 구조 검사는 통과했지만, 종목 유형 매핑과 과거 종목 자격 검증은 완료하지 않았다.** 총 4,403행을 조사했으며 현재 관측 자료를 운영 후보나 과거 백테스트 모집단으로 승격하지 않는다.

실제 원본에는 ETP 공백과 열람한 공개 헤더에 정의되지 않은 코드가 있었다. 앞서 작성한 [원천 검증의 유형 매핑 초안](../../../../strategy/swing/validation/stock-eligibility-source-validation-01.md)은 이 관측 없이 확정한 계약이 아니다. 빈 값을 `0`으로 바꾸거나 미정의 코드를 보통주·`OTHER`로 추정해서는 안 된다. `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지한다.

이번 변경은 실측 기록과 기존 문서의 연결이다. 생산 Java 코드·설정·DB 스키마·운영 파서·자격 입력·스케줄러는 변경하지 않았다. DB 연결·Docker 조작·계좌 및 주문 API·OpenAI API 호출·커밋·Push도 하지 않았다.

## 실행 조건과 보존 자료

| 항목 | 고정한 값 또는 결과 |
| --- | --- |
| 수집 구현 기준 | `8ed9efd51937b684fb0ebedaf1cb8530c255393c` |
| 수집 ID | `4ebd57c2-dd69-4b99-86c7-8efed3e531f7` |
| 명령 | `collectStockMaster --offline --no-daemon`에 아래 명시적 수집 인자를 전달 |
| 원본 디렉터리 | `data/stock-master-observations/4ebd57c2-dd69-4b99-86c7-8efed3e531f7/` |
| 실행 및 검증 기록 | `build/stock-master-collection-observation-01/` |
| 접속 및 다운로드 제한 | 연결 `10s`, 시장별 전체 다운로드 `60s` |
| 크기 제한 | ZIP `10,485,760` bytes, 추출 파일 `33,554,432` bytes |
| 수집 범위 | KOSPI·KOSDAQ의 고정 URL, 각각 한 요청. 자동 재시도 없음 |
| 완료 결과 | 프로세스 종료 코드 `0`, 완료 로그 존재, 배치 1개, 최종 manifest 존재 |
| 증거 범위 | manifest 형식 버전 `1`, `CURRENT_OBSERVATION` |

실행 인자는 `market.stock.master.collection.manual` 아래의 `enabled=true`, `output-directory=data/stock-master-observations`, `connect-timeout=10s`, `download-timeout=60s`, `max-archive-bytes=10485760`, `max-extracted-bytes=33554432`다. 로그 수준은 `logging.level.com.stock=INFO`로 명시했다. Gradle의 `--offline`은 의존성 다운로드 제한이며 수집기의 HTTPS 요청을 금지하는 옵션이 아니다.

실측 헬퍼는 기준 커밋과 최초 실행 여부를 확인하고, 외부 서비스·서버 설정 및 JVM 옵션 관련 환경변수를 자식 프로세스에서 제거했다. `.env`와 자격증명 값은 읽거나 기록하지 않았다. 수집은 [격리 진입점](../stock-master-manual-collection.md)을 사용했다. 수집 코드의 기본 TLS 검증·리다이렉트 차단·본문 크기 제한·ZIP 검증을 사용했으며, 인증서 체인이나 패킷을 별도로 캡처한 검증은 아니다.

보존된 파일은 `manifest.json`과 시장별 ZIP·MST·`observation.json`의 총 7개다. 원본과 관측 헬퍼는 Git 제외 대상이다. 원본은 `build/` 밖에 보존했지만, 이번에 별도 백업·복원까지 확인한 것은 아니다. `build/`의 로컬 검증 기록은 Gradle `clean`으로 삭제될 수 있으므로 이 실측에서는 `clean`을 실행하지 않았다.

| 자료 | 실제 bytes | SHA-256 |
| --- | --- | --- |
| KOSPI ZIP | 119,899 | `974bbcf81b7d53fe6222d45168f9e70e67aa9e9bfdd657676dc38d55f3c62fb1` |
| KOSPI MST | 745,042 | `630220921e86a7c8684a80afd6c8fc741924fe0214d60fecbc865cebe91d3547` |
| KOSDAQ ZIP | 103,885 | `5484391ad586628aedafb482447a5efda339f589eba0d36916abbf83f5960bce` |
| KOSDAQ MST | 516,475 | `3a9bcc3db9a5c6b7fd9857ced658b5aa16b16df9e9fde39ab6a62d8080f36b12` |

수집 출처는 [KOSPI ZIP](https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip)과 [KOSDAQ ZIP](https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip)이다. 두 ZIP 모두 예상 이름의 MST 엔트리 하나만 포함했다. 독립적인 로컬 ZIP 읽기 결과의 해시가 보존 MST·manifest와 일치했고, 시장별 JSON도 manifest의 해당 관측과 일치했다.

## 소요 시간

| 범위 | 시작 UTC | 완료 UTC | 경과 시간 |
| --- | --- | --- | --- |
| 전체 수집 구간 | `2026-10-05T09:32:35.795200200Z` | `2026-10-05T09:32:36.202168500Z` | 406.9683 ms |
| KOSPI 관측 | `2026-10-05T09:32:35.795200200Z` | `2026-10-05T09:32:36.137515600Z` | 342.3154 ms |
| KOSDAQ 관측 | `2026-10-05T09:32:36.177401500Z` | `2026-10-05T09:32:36.200168300Z` | 22.7668 ms |

전체 수집은 한국 시각 18:32:35.795부터 18:32:36.202까지다. 시장별 구간에는 다운로드뿐 아니라 보존·추출·해시 등의 작업이 들어간다. 전체 완료 시각은 최종 manifest 쓰기 전이므로 그 파일 쓰기까지의 시간은 아니다. Gradle·JVM 기동과 종료를 포함한 자식 프로세스 실측은 별도로 **14.101초**다. 어느 값도 순수 HTTP 응답 시간이나 장기 운영의 평균 지연으로 해석하지 않는다.

## 전체 행의 형식 검사

| 검사 항목 | KOSPI | KOSDAQ |
| --- | --- | --- |
| 전체 행 | 2,578 | 1,825 |
| 한 행의 bytes, LF 제외 | 모든 행 288 | 모든 행 282 |
| LF 포함 레코드 bytes | 289 | 283 |
| 관측한 앞부분 폭 | 61 bytes | 61 bytes |
| 대조한 뒷부분 폭과 필드 수 | 227 bytes, 70개 | 221 bytes, 64개 |
| CP949 디코딩 후 한 행의 문자 수, LF 제외 | 270~288 | 270~282 |
| LF 종료 행 | 2,578 | 1,825 |
| CRLF·단독 CR·빈 행·예상 길이 불일치 | 각각 0 | 각각 0 |
| CP949 엄격 디코딩 및 재인코딩 해시 일치 | 통과 | 통과 |
| 6자리 숫자 단축 코드 | 1,778 | 1,766 |
| 그 외 단축 코드 형식 | 800 | 59 |
| 앞자리 `0`인 단축 코드 | 1,050 | 811 |
| 빈 코드·시장 내 단축 코드 중복·표준 코드 중복 | 각각 0 | 각각 0 |
| 상장일의 공백 또는 `yyyyMMdd` 해석 실패 | 0 | 0 |

시장 간 단축 코드와 표준 코드의 충돌도 각각 0이었다. 모든 파일은 마지막 LF를 포함했고, 추출 파일 크기는 `행 수 × LF 포함 레코드 길이`와 일치했다. 이것은 이번 두 원본의 내부 구조 검사이며 전체 시장 모집단의 완전성이나 코드 의미를 외부 자료로 인증한 결과는 아니다.

[KOSPI 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py)와 [KOSDAQ 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kosdaq_code_mst.py)의 CP949·시장별 필드 폭을 대조했다. 예제의 뒤쪽 문자열 슬라이스 `228`·`222`는 정규화된 줄바꿈 한 문자를 포함한다. 그 숫자를 LF 제외 바이트 폭으로 복사하지 않는다. 이번 원본은 앞부분 `9 + 12 + 40` bytes와 뒤쪽 폭의 합에 모두 맞았으며, 앞부분 폭은 **이번 바이트 대조로 확인한 관측값**이다. 한글 이름 때문에 디코딩된 전체 문자 수는 고정 바이트 길이와 달랐다.

검증 헬퍼는 원천 필드의 위치를 확인하기 위한 로컬 관측 도구이며 생산 파서가 아니다. 대조할 폭과 일곱 필드의 인덱스는 `reviewed-layouts.json`에 기록했다. 해당 파일의 SHA-256은 `78b8f89b858a97618eb1634623bbd27cf0fe71bdea68b398574c7adb6adfa4f4`다. 이는 **우리 검증 설정의 해시**이며 KIS 규격 파일의 해시가 아니다. 공개 `main`을 열람했지만 upstream commit·원천 규격 파일 바이트를 고정하지 않아 `upstreamRevisionPinned=false`로 남겼다.

## 분류 초안과 실제 원문 값의 차이

아래의 공백은 빈 문자열이나 숫자 `0`이 아니라 원본 ETP 필드의 한 칸 공백이다. 건수는 로컬 원본의 집계이며, 의미가 확인되지 않은 값을 지우거나 기본값으로 치환하지 않았다.

| 원문 항목 | KOSPI | KOSDAQ |
| --- | --- | --- |
| 증권 그룹 | `BC` 84, `DR` 1, `EF` 1,175, `EN` 368, `FS` 1, `IF` 2, `MF` 1, `PF` 2, `RT` 23, `SR` 3, `ST` 914, `SW` 4 | `DR` 9, `FS` 11, `ST` 1,805 |
| ETP 구분 | 공백 1,033, `2` 1,159, `3` 365, `4` 1, `5` 2, `8` 16, `9` 2 | 공백 1,825 |
| 우선주 구분 | `0` 2,467, `1` 78, `2` 21, `9` 12 | `0` 1,822, `2` 2, `9` 1 |
| 거래정지 필드 `Y` | 41 | 85 |
| 정리매매 필드 `Y` | 3 | 7 |

**두 시장의 `ST` 2,719행은 모두 ETP 공백이고, 어느 행에도 ETP `0`은 없었다.** 따라서 초안의 `ST + ETP 0 + 우선주 0` 조합을 그대로 적용할 수 없다. 공백이 해당 없음이라는 의미인지, 규격 또는 필드 해석을 보완해야 하는지 확인하기 전까지 공백을 `0`으로 정규화하지 않는다.

열람한 [KOSPI 헤더](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/%EC%A2%85%EB%AA%A9%EB%A7%88%EC%8A%A4%ED%84%B0%EC%A0%95%EB%B3%B4%28%EC%BD%94%EC%8A%A4%ED%94%BC%29.h)와 [KOSDAQ 헤더](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/%EC%A2%85%EB%AA%A9%EB%A7%88%EC%8A%A4%ED%84%B0%EC%A0%95%EB%B3%B4%28%EC%BD%94%EC%8A%A4%EB%8B%A5%29.h)는 그룹 `ST`·`EF`, ETP `0`~`4`, 우선주 `0`~`2` 등을 설명하며 KOSPI는 ETP `5`도 설명한다. 그러나 이번에 관측한 그룹 `EN`·`PF`, ETP `8`·`9`, 우선주 `9`의 정의는 **이 두 헤더에서 확인하지 못했다.** 이는 열람한 자료의 한계이지 제공자의 다른 자료에도 정의가 없다는 주장은 아니다. 이름만으로 의미를 확정하거나 생산 enum 매핑을 추가하지 않았다.

| 시장과 행 | 보존한 단축 코드와 이름 | 그룹 / ETP / 우선주 원문 | 확인할 문제 |
| --- | --- | --- | --- |
| KOSPI 441 | `005930` 삼성전자 | `ST` / 공백 / `0` | 앞자리 0과 ETP 공백 보존 |
| KOSPI 442 | `005935` 삼성전자우 | `ST` / 공백 / `1` | 우선주 필드와 그룹을 별도로 보존 |
| KOSPI 1,049 | `069500` KODEX 200 | `EF` / `2` / `0` | 숫자 코드·우선주 `0`만으로 보통주 판정 불가 |
| KOSPI 615 | `0106J0` 대신 KOSPI200인덱스 X클래스 | `PF` / `5` / `0` | 미정의 그룹을 이름으로 추정하지 않음 |
| KOSPI 834 | `0192L0` RISE SK하이닉스단일종목레버리지 | `EF` / `8` / `0` | 공개 헤더에서 ETP `8` 의미 미확인 |
| KOSPI 2,280 | `Q520100` 미래에셋 레버리지 삼성전자 단일종목 ETN | `EN` / `9` / `0` | 7자리 코드, 미정의 그룹·ETP 조합 |
| KOSDAQ 8 | `0001A0` 덕양에너젠 | `ST` / 공백 / `0` | 영문자가 포함된 `ST` 코드도 실제 존재 |
| KOSDAQ 265 | `03481K` 해성산업1우 | `ST` / 공백 / `9` | 우선주 `9` 의미 미확인 |

샘플 이름은 보존 원본의 표시값이며 별도 분류 근거가 아니다. **`^[0-9]{6}$`만 통과시키면 `0001A0` 같은 `ST` 행도 잃는다.** 원천 코드를 정수로 바꾸거나 잘라내지 않는다. 향후 주문 가능 코드 검증과 마스터 원문 보존은 별도 계약으로 다뤄야 한다.

상장일은 전 행에서 날짜로 해석됐지만 상장 상태·유효 기간·과거 모집단이 확인된 것은 아니다. 삼성전자 행의 상장일은 `19750611`, `base_date`는 `20260630`이고, 삼성전자우의 `base_date`는 공백이다. 이 값을 전체 마스터의 적용일이나 공개 시각으로 사용하지 않는다. 거래정지·정리매매도 `NOT_LISTED`와 같지 않다. [KOSPI 헤더](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/%EC%A2%85%EB%AA%A9%EB%A7%88%EC%8A%A4%ED%84%B0%EC%A0%95%EB%B3%B4%28%EC%BD%94%EC%8A%A4%ED%94%BC%29.h)와 [KOSDAQ 헤더](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/%EC%A2%85%EB%AA%A9%EB%A7%88%EC%8A%A4%ED%84%B0%EC%A0%95%EB%B3%B4%28%EC%BD%94%EC%8A%A4%EB%8B%A5%29.h)의 필드 구분을 유지한다.

## 로컬 재검증과 한계

`verify-observation-01.ps1`은 네트워크 호출 없이 보존 파일을 읽어 `verification.json`을 생성했다. 첫 실행의 PowerShell 줄바꿈 문법 오류는 헬퍼에서 수정했으며, 그 때문에 수집기를 다시 실행하지 않았다. JSON 기록은 UTF-8로 읽어야 한다.

같은 헬퍼와 원본으로 `verification-replay.json`을 별도로 생성했다. 첫 검증 시각은 `2026-10-05T09:41:16.8998057Z`, 재검증은 `2026-10-05T09:46:26.2740101Z`다. 검증 시각을 제외한 행 집계·샘플·파일 해시·교차 시장 중복·검증 설정이 일치했다. 검사 전후 7개 보존 파일의 길이와 해시도 변하지 않았다. 이는 독립적인 다른 파서와의 교차 검증이 아니라 **동일 관측 도구의 재현성 검사**다.

| 판정 범위 | 이번 결과 |
| --- | --- |
| 두 원본의 확보와 manifest 대조 | 완료 |
| 전체 행 구조·인코딩·식별 중복·상장일 형식 | 완료, 해당 구조 오류 0행 |
| 원문 분류 코드와 조합의 관측 | 완료, 미정의 값은 원문으로 보존 |
| 생산 파서 및 전체 종목의 성공·미확인·오류 분류 | 미구현. 유형 승인 건수나 미확인 건수를 임의로 만들지 않음 |
| 원천 코드 의미·허용 조합·규격 버전 고정 | 미완료 |
| 과거 전체 시장 모집단·상장폐지·시장 이전·정보 가용 시각 | 미검증 |
| 종목 자격·운영 후보·전략 순성과 | 승인하지 않음 |

이번 실측을 위해 Java 테스트와 전체 테스트를 재실행하지 않았다. 기존 59개 합성 테스트의 기록은 [수집 구현 문서](../stock-master-manual-collection.md)에 남기며, 그 결과와 이번 실제 원본 검증을 구분한다. 공개 자료 대조를 위한 웹 열람은 마스터 재수집과 별개이며, 로컬 재검증의 외부 요청 수는 0이다.

`eligibilityOrHistoricalPopulationVerified=false`를 유지한다. `asOfDate`, `listingStatus`, `informationAvailableAt`, `AS_OF_VERIFIED`를 생성하지 않았다. 규격·코드 조합과 시점 근거가 추가로 확인되기 전에는 자료를 자동 평가나 주문으로 연결하지 않는다. 이 관측은 수익성 검증이나 실투자 허가를 대신하지 않는다.

## 후속 Java 원문 파서

원본 수집 이후 [Java 원문 파서와 로컬 대조](../stock-master-raw-parsing.md)를 별도로 구현했다. 관련 합성 테스트 37개와 동일 MST의 2,578행·1,825행 대조를 통과했다. 위 표의 생산 파서 미구현은 최초 원본 관측 시점의 상태이며, 후속 구현은 원문 분리·보존까지만 제공한다. 종목 유형·자격·과거 모집단·운영 후보의 미확인과 승인 차단은 그대로 유지한다.

## 후속 유형 매핑 재검증

[고정 규격과 원문 조합 재검증](stock-master-type-mapping-validation-01.md)에서 공식 파일 4개를 commit·바이트 해시로 보존하고 같은 원본의 모든 유형 조합과 건수를 대조했다. 최초 관측 당시의 `upstreamRevisionPinned=false`는 당시 기록으로 유지하며, 이번 고정을 그 시점의 증거로 소급하지 않는다. 공백·미정의 코드의 의미와 과거 자격은 여전히 미확인이고 재다운로드·원본 수정·Java 매핑·DB·운영 후보 연결은 하지 않았다.
