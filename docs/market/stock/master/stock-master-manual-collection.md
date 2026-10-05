# KIS 종목 마스터 수동 수집과 원본 보존

## 목적과 경계

KOSPI·KOSDAQ 마스터를 명시적으로 한 번 수집하고 원본 ZIP, 추출한 파일과 출처·시각·해시를 보존한다. **수집 성공은 자료 확보와 ZIP 구조 검증의 완료일 뿐, 종목 유형·상장 상태·과거 모집단 검증의 완료가 아니다.** 후속 파서는 보존한 동일 원본으로 검증하며, 파서 수정 때마다 외부 파일을 다시 받지 않는다.

이번 단계는 [종목 자격 원천 검증](../../../strategy/swing/validation/stock-eligibility-source-validation-01.md)의 첫 수집 범위 중 다운로드·안전한 추출·원본 보존을 구현한다. 인코딩·행 길이·필드 구성·코드 조합의 해석, 자격 입력 생성, DB 저장, 운영 Universe 교체, 백테스트·주문 연결과 스케줄러는 추가하지 않는다. `DESIGN_ONLY`와 `runtimeSelectionImplemented=false`를 유지한다.

`StockMasterCollectionRunner.main`은 수집 설정만 로드하는 별도 진입점이다. 메인 애플리케이션의 Component Scan·자동 설정·스케줄링을 사용하지 않으므로 DB·Broker·OpenAI 빈을 시작하지 않는다. `broker.kis.enabled`와 KIS Key·Secret·계좌 번호도 필요하지 않다. 기본 비활성화 상태에서는 HTTP Client와 수집 빈도 만들지 않는다.

## 코드와 설정

기준 패키지는 `com.stock.market.stock.master`다.

| 하위 경로와 클래스 | 역할 |
| --- | --- |
| `provider.kis.KisStockMasterMarket` | 두 시장의 공식 URL과 예상 파일 이름을 고정한다. 사용자 입력 URL을 받지 않는다. |
| `provider.kis.KisStockMasterClient` | 인증 없는 GET, 리다이렉트 차단, HTTP 상태·실제 본문 크기·전체 다운로드 대기를 제한한다. |
| `collection.StockMasterCollectionService` | 새 수집 디렉터리에 원본을 보존하고 ZIP 구조·실제 추출 크기·CRC를 확인한다. |
| `collection.result.StockMasterCollectionResult` | 두 시장의 완료 관측과 SHA-256, 상대 파일 경로를 불변 기록으로 보존한다. |
| `collection.runner.StockMasterCollectionRunner` | 수동 호출 한 번과 성공 로그, DB 없는 별도 main 진입점이다. |
| `collection.runner.config` | `StockMasterCollectionProperties`와 `StockMasterCollectionConfiguration`이 명시한 한도를 바인딩하고 수집 빈을 구성한다. |

접두어는 `market.stock.master.collection.manual`이다. 비활성화 시 입력을 요구하지 않고, 활성화 시 아래 값을 모두 명시해야 한다.

| 설정 | 계약 |
| --- | --- |
| `enabled` | 기본 `false`. 수동 명령에서만 `true`로 지정한다. |
| `output-directory` | 비어 있지 않은 저장 디렉터리. 상대 경로는 프로세스 작업 디렉터리 기준이다. 기존 디렉터리를 삭제하지 않는다. |
| `connect-timeout` | 양의 시간이며 `download-timeout` 이하여야 한다. |
| `download-timeout` | 양의 시간이며 최대 5분이다. 한 시장의 전체 HTTP 본문 완료 대기를 제한하며 초과 시 요청을 취소한다. |
| `max-archive-bytes` | 양의 정수이며 최대 64 MiB다. 광고한 `Content-Length`뿐 아니라 실제 수신한 본문에도 적용한다. |
| `max-extracted-bytes` | 양의 정수이며 최대 256 MiB다. ZIP 메타데이터와 실제 압축 해제 결과에 모두 적용한다. |

위 최대치는 프로젝트의 자원 보호 상한이며 KIS 파일 크기 규격이 아니다. 실제 사용하는 더 작은 한도도 명시해야 한다. 한 시장이 실패하면 다음 시장을 계속 수집하거나 자동 재시도하지 않는다. 두 시장 사이에도 기본 지연·재조회 루프를 추가하지 않는다.

## 수동 실행

다음 명령은 **실제 외부 파일 다운로드와 로컬 파일 쓰기**를 수행한다. 수집 구현 당시의 안내 예시이며 합성 테스트의 실측 기록은 아니다. 아래와 같은 한도로 실행한 후속 실제 관측은 [수집 실측과 원본 형식 검증](validation/stock-master-collection-observation-01.md)에 따로 기록한다. 한 번의 관측으로 향후 모든 파일에 충분한 시간·크기 한도라고 보장하지 않는다.

프로젝트 루트의 별도 PowerShell에서 실행한다.

```powershell
$collectionArgs = @(
    '--market.stock.master.collection.manual.enabled=true'
    '--market.stock.master.collection.manual.output-directory=data/stock-master-observations'
    '--market.stock.master.collection.manual.connect-timeout=10s'
    '--market.stock.master.collection.manual.download-timeout=60s'
    '--market.stock.master.collection.manual.max-archive-bytes=10485760'
    '--market.stock.master.collection.manual.max-extracted-bytes=33554432'
)
.\gradlew.bat collectStockMaster --args="$($collectionArgs -join ' ')" --no-daemon
```

`collectStockMaster`는 일반 서버의 `bootRun`이 아니다. `.env`를 읽거나 수정할 필요 없이 수집 설정만 전달하며, 작업이 끝나면 컨텍스트를 닫고 종료한다. 일반 서버도 명시적으로 이 수집 설정을 활성화하면 Runner를 등록할 수 있으므로, 정상 서버의 YAML·환경변수·실행 설정에 활성화 값을 계속 남기지 않는다.

수집 없는 진입점 확인은 아래 명령으로 할 수 있다. 완료 수집 로그나 관측 파일이 생성되는 명령이 아니다.

```powershell
.\gradlew.bat collectStockMaster --args="--market.stock.master.collection.manual.enabled=false" --no-daemon
```

## 파일과 완료 판정

실행마다 새 UUID 디렉터리를 생성한다. 성공한 배치는 다음 구조다.

```text
data/stock-master-observations/<collectionId>/
  manifest.json
  KOSPI/
    kospi_code.mst.zip
    kospi_code.mst
    observation.json
  KOSDAQ/
    kosdaq_code.mst.zip
    kosdaq_code.mst
    observation.json
```

`manifest.json`에는 형식 버전 1, 수집 ID, `CURRENT_OBSERVATION` 범위, 전체 시작·완료 시각과 두 시장의 관측이 들어간다. 시장별 관측은 출처 URL, 다운로드·보존·검증을 포함한 시작·완료 시각, 두 파일의 상대 경로·실제 바이트 수·SHA-256을 담는다. 형식 버전은 **우리 JSON 기록의 버전**이지 KIS 마스터 규격 버전이 아니다.

파일은 새 디렉터리에 `CREATE_NEW`로 작성하며 원본 ZIP은 삭제하지 않는다. 검증한 추출 파일과 JSON은 `.partial` 파일에서 같은 디렉터리 내 원자적 이동으로 공개한다. 해당 파일시스템이 원자적 이동을 지원하지 않으면 실패시키며 조용히 일반 이동으로 바꾸지 않는다. 두 시장이 모두 성공하고 manifest를 기록한 뒤에만 `Stock master collection completed.` 로그를 남긴다.

시장 수집 실패 시 새 배치와 이미 받은 원본은 진단용으로 남기고 예외에 배치 경로와 실패 시장을 표시한다. 첫 시장이 성공했으면 해당 시장의 `observation.json`도 남지만 **전체 `manifest.json`은 없다.** manifest 기록 실패도 완료 로그 없이 호출자에게 전파한다. `.partial`은 완성 자료로 사용하지 않는다. 완료 로그가 없거나 프로세스가 중단된 경우 먼저 디렉터리를 확인하며, 해시·파일을 대조하지 않고 단순히 파일이 있다는 이유로 성공으로 간주하지 않는다.

관측 디렉터리는 Git과 Docker 빌드 컨텍스트에서 제외한다. `build/` 밖에 두므로 Gradle `clean`으로 지워지지 않지만, Git 제외가 백업을 대신하지는 않는다. 별도 백업·복원 및 운영 영속 스토리지는 이번 범위가 아니다.

## 안전 조건과 미확인 항목

Client는 기본 TLS 검증을 사용하고 리다이렉트를 따르지 않는다. HTTP 200과 요청한 원천 URL이 아니면 거절한다. ZIP 응답 위에 추가 HTTP 압축을 허용하지 않고, 본문 전체를 제한된 크기의 메모리에 받은 뒤 보존한다. 다운로드 시간 제한은 ZIP 해제·디스크 쓰기·전체 프로세스 실행 시간 제한과는 다르다.

ZIP은 정확히 하나의 예상 이름을 가진 파일만 허용한다. 추가·중복 엔트리, 디렉터리, 다른 파일 이름과 경로 이탈 이름을 거절한다. 파일을 엔트리 경로에 직접 풀지 않으며, 실제 추출 크기와 CRC가 ZIP 기록과 맞는지 확인한 후에만 최종 파일을 공개한다. 원본 바이트는 문자 디코딩이나 줄바꿈 변환 없이 보존한다.

수집 시각은 원천의 적용일·당시 공개 시각이 아니다. manifest는 `asOfDate`, `listingStatus`, `informationAvailableAt` 또는 `AS_OF_VERIFIED`를 만들지 않는다. 수집기 자체는 종목 수·정상·미확인·오류 행 건수, 실제 인코딩·레이아웃과 유형 매핑을 계산하지 않으며 0이나 추정 기본값으로 채우지 않는다. 원본 대조와 생산 파서의 분류 검증은 별도 기록으로 관리한다.

## 검증 범위

관련 HTTP·서비스·결과·Runner·설정 테스트는 제한된 합성 ZIP과 모의 HTTP 응답을 사용한다. 정상 바이트·해시·JSON 복원, 새 배치와 이전 자료 보존, 부분 실패의 성공 차단, 전체 본문 대기 취소, 실제 크기 한도, ZIP 경로·추가 엔트리·손상·CRC 및 비활성화·격리 컨텍스트를 확인한다.

```powershell
.\gradlew.bat test --tests "com.stock.market.stock.master.*" bootJar --no-daemon
```

2026-10-05 관련 테스트 59개가 실패·오류 없이 통과했고 `bootJar` 빌드도 성공했다. 일반 서버 JAR의 `Start-Class`는 기존 `com.stock.StockAgentHarnessApplication`으로 유지됐다. 별도 Gradle 진입점의 `enabled=false` 실행이 정상 종료했으며 실제 관측 디렉터리는 생성하지 않았다. 전체 프로젝트 테스트는 재실행하지 않았다.

실제 KIS 다운로드·TLS 접속·마스터 행 해석·종목 자격·과거 모집단·전략 순성과는 이 테스트로 검증하지 않는다. 실제 두 파일 수집과 원본 형식 대조는 별도 실측이며, 그 결과가 없으면 후보 평가의 미확인 상태와 기존 검증 게이트를 유지한다.

## 후속 실제 관측

2026-10-05 [수집 실측과 원본 형식 검증](validation/stock-master-collection-observation-01.md)에서 두 시장을 각각 한 번 수집했다. KOSPI 2,578행·KOSDAQ 1,825행의 해시·ZIP 내용·CP949·고정 바이트 길이와 동일 원본 재검증을 확인했다. DB·Docker·계좌·주문 연결은 하지 않았다.

실제 원본의 ETP 공백과 공개 헤더에서 의미를 확인하지 못한 그룹·ETP·우선주 코드가 남아 있어 유형 매핑은 확정하지 않았다. 이 실측은 위 합성 테스트의 후속 증거이며, 생산 파서·종목 자격·과거 모집단·운영 후보 승인을 뜻하지 않는다.

후속 [KIS 종목 마스터 원문 파서](stock-master-raw-parsing.md)는 보존한 MST 바이트를 읽는 별도 Java 기능이다. 수집 Runner가 자동으로 호출하지 않으며, 실제 원본 대조를 통과해도 유형 분류·자격 입력·DB·주문으로 연결하지 않는다.

후속 [보존된 배치 해석 서비스](stock-master-batch-parsing.md)는 기존 완료 배치의 manifest·시장별 JSON·ZIP·MST를 검증하고 두 시장의 원문 해석 결과를 반환한다. 재다운로드·재추출·원본 수정 없이 동작하며 기존 수집 Runner의 자동 호출이나 수집 완료 판정은 변경하지 않는다.
