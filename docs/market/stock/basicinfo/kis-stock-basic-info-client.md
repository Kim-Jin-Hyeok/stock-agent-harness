# KIS 주식기본조회 원문 Client

## 목적과 범위

종목 하나와 이미 발급된 액세스 토큰을 받아 주식기본조회 GET 요청을 수행하고 원문 바이트와 최소 수집 메타데이터를 반환한다. **응답을 필드 DTO나 문자열로 다시 조립하지 않으며, 파싱·식별 대조·유형·제한·후보 판정은 기존 후속 정책의 책임으로 유지한다.**

요청 계약은 [기존 실전 응답 검증](../master/validation/kis-stock-basic-info-observation-01.md)에서 고정한 실전 주소·경로·TR ID·인자와 검증 도구의 헤더를 따른다. Client 구현과 검증에 실제 API 요청·토큰 발급을 수행하지 않았으며, 기존 모의 주문 연결이나 실전 주문 권한을 변경하지 않았다.

## 패키지와 호출 계약

기준 패키지는 `com.stock.market.stock.basicinfo.provider.kis`다. 파일은 동일한 `src/main/java/` 경로에 두고 HTTP 조회와 기존 `parsing`의 원문 해석을 구분한다.

| 클래스 | 책임 |
| --- | --- |
| `KisStockBasicInfoClient` | `RestClient`, 조회용 키·secret과 `Clock`을 생성자로 받는다. `getStockBasicInfo(String symbol, String accessToken)`으로 단건을 조회한다. |
| `dto.KisStockBasicInfoRawResponse` | 요청 종목코드, 요청 시작·응답 수신 시각, HTTP 상태와 원문 바이트를 보존한다. |

Spring 어노테이션과 빈 등록은 없다. Client는 토큰 Provider를 의존하거나 인증 요청을 수행하지 않으며 호출자가 같은 실전 조회 환경에서 발급한 토큰을 전달해야 한다. 요청마다 새 토큰을 발급하는 구조가 아니다.

기존 `KisConfiguration`의 모의투자 `RestClient`와 토큰을 연결하지 않는다. 향후 실전 조회 전용 구성에서 인증 환경과 토큰 재사용을 명시적으로 분리해야 한다. 여기서 읽기 전용은 이 Client가 허용한 경로와 메서드의 용도이며 증권사가 키에 별도의 읽기 전용 권한을 부여했다는 뜻이 아니다.

## 요청과 입력 검증

| 항목 | 계약 |
| --- | --- |
| 주소 | `https://openapi.koreainvestment.com:9443` |
| 메서드·경로 | `GET /uapi/domestic-stock/v1/quotations/search-stock-info` |
| 인자 | `PRDT_TYPE_CD=300`, 전달된 `PDNO` |
| 헤더 | Bearer 토큰, `appkey`, `appsecret`, `tr_id=CTPF1002R`, `custtype=P` |
| 표현 | JSON Content-Type·Accept, `Accept-Encoding: identity` |

종목코드는 정확히 여섯 자리의 대문자 영숫자만 허용한다. `005930`과 `0004Y0`를 그대로 전송하고 앞자리 0 제거·trim·대소문자 변환을 하지 않는다. 이는 현재 Client가 지원하는 요청 형식이며 실제 종목 존재·자격이나 모든 미래 코드 형식을 인증하지 않는다.

키·secret·토큰은 공백 없는 visible ASCII 문자열을 요구한다. null·빈 문자열·공백·제어문자와 CR/LF를 요청 전에 거절하며 오류에 입력값을 넣지 않는다. `RestClient`와 `Clock`은 nonnull이어야 한다.

URI를 만든 뒤 인증 헤더를 지정하기 전에 HTTPS·실전 호스트·9443 포트·정확한 경로와 두 인자의 일치를 검사한다. 모의 서버·다른 호스트·HTTP·사용자 정보·fragment·추가 경로·추가 인자가 있으면 요청 전에 실패한다. 이 검사는 최초 요청 URI의 제한이며 주입된 HTTP 구현의 후속 리다이렉트 정책을 검사하는 기능은 아니다.

## 원문 수신과 실패 처리

HTTP 200만 원문 응답 객체로 반환한다. 다른 상태는 오류 본문을 읽지 않고 안전한 숫자 상태 코드만 오류에 남긴다. Client 자체의 재시도·토큰 재발급·페이지 순회·전 종목 조회는 없다.

파서의 `MAX_CONTENT_BYTES`와 같은 1 MiB를 사용한다. 이 값은 프로젝트의 자원 보호 기준이지 KIS 공식 API 제한이 아니다. 선언된 `Content-Length`가 한도를 넘으면 본문을 읽지 않는다. 길이가 없거나 작게 선언돼도 실제 스트림에서는 최대 1 MiB+1바이트만 읽어 초과 여부를 확인한다. 빈 본문과 초과 본문은 거절하고, 정확히 1 MiB는 허용한다. 결과를 잘라 정상 원문으로 반환하지 않는다.

`Content-Encoding`은 미제공 또는 정확한 identity 표현만 허용하며 대소문자는 구분하지 않는다. 다른 인코딩은 읽기 전에 거절한다. 본문 스트림과 응답은 종료 경로에서 닫는다.

HTTP·URI 제한·크기·인코딩·빈 본문 오류는 고정한 안전한 메시지로 전달한다. 통신·스트림 읽기·잘못된 응답 헤더 등의 예외는 원문 메시지와 원인을 포함하지 않는 일반 실패로 바꾼다. 스트림 닫기 오류가 suppressed exception으로 붙어도 전달하지 않는다. 오류 본문·요청 헤더·키·secret·토큰을 기록하는 로깅은 추가하지 않았다.

**HTTP 200은 API 업무 성공이 아니다.** `rt_cd=1`이나 잘못된 JSON도 이 단계에서는 원문으로 반환될 수 있다. 호출자가 [기존 원문 파서](kis-stock-basic-info-raw-parsing.md)에 `response.content()`를 전달하면 기존 성공 코드·구조 검증과 입력 해시 계산이 적용된다. 응답 상품번호를 요청 코드로 바꾸거나 Client 단계에서 다른 종목 응답을 자동 연결하지 않는다.

## 응답 객체와 수집 시각

`KisStockBasicInfoRawResponse`는 `requestedSymbol`, `requestStartedAt`, `responseReceivedAt`, `httpStatus`, `content`를 갖는 record다. 바이트는 생성 시 복사하고 `content()` 접근 시에도 복사한다. `equals`와 `hashCode`는 배열 참조가 아닌 바이트 값과 메타데이터를 사용한다. `toString()`은 메타데이터와 바이트 길이만 보여 주며 본문을 출력하지 않는다.

시각은 nonnull이고 수신 시각이 시작 시각보다 앞서면 거절한다. 같은 시각은 허용한다. Client는 요청을 시작하기 직전에 첫 시각을 읽고 본문 읽기가 끝난 뒤 수신 시각을 읽는다. 두 값은 로컬 수집 구간의 기록이며 거래소의 상태 관측 시각·제한 효력 시각·과거 정보 가용성을 생성하지 않는다. `Clock`은 경과 시간 측정용 monotonic clock이나 호출 시간 제한 장치가 아니다.

생성자는 HTTP 200과 nonempty·최대 1 MiB를 검사하지만 JSON·API 업무 성공·원천 진위는 검사하지 않는다. 방어적 복사와 `toString` 제한은 원문 암호화·비밀정보 필터나 안전한 외부 공개 계약이 아니다. 본문 자체는 그대로 유지하므로 향후 저장·공개·로깅 계층의 보호가 별도로 필요하다.

## 관련 테스트

테스트는 동일한 `src/test/java/.../provider/kis/`의 Client 테스트와 `dto/`의 응답 객체 테스트에 둔다. 기존 파서 Fixture와 `MockRestServiceServer`를 재사용하며 Spring ApplicationContext나 실제 서버·인증정보가 필요하지 않다.

```powershell
.\gradlew.bat test --tests 'com.stock.market.stock.basicinfo.provider.kis.*' --offline --no-daemon
```

2026-10-06 관련 테스트 **400개가 모두 통과**했다. 새 Client 54개·응답 객체 21개와 기존 파서 325개를 포함한 5개 클래스다. 실패·오류·건너뜀은 0개이며 전체 프로젝트 테스트는 실행하지 않았다. 최신 HTML 클래스 목록과 XML suite 이름으로 집계했다.

정확한 GET·인자·헤더·단건 요청, 숫자·영문 포함 코드, 형식과 공백을 포함한 원문 보존, 기존 파싱 결과·SHA-256 일치와 수집 시각 순서를 확인했다. HTTP 오류·통신·읽기·닫기 오류의 민감 메시지와 cause·suppressed 제거, 미지원 주소의 요청 전 차단도 검증했다.

추적 가능한 스트림으로 빈 본문·정확한 한도·한도 초과와 길이 미제공·잘못된 작은 길이를 확인했다. 초과 본문은 1 MiB+1바이트 뒤에 499바이트가 남아 있어 전체 버퍼링을 하지 않았고 스트림도 닫혔다. 헤더에서 거절한 응답은 본문 읽기 0바이트였다. 업무 실패·잘못된 JSON이 HTTP 성공으로 승인되지 않고 원문과 파서 책임이 분리되는 것도 확인한다.

## 보존 응답 재현 검증

보존 응답 10건을 새 Client의 Mock HTTP 응답으로 한 번씩 전달했다. 요청코드와 헤더를 검증했고 결과의 원문 바이트·전체 응답 메타데이터가 고정한 기대값과 같았다. 기존 파서의 전체 결과와 SHA-256도 직전 보존 보고서와 일치했다.

Mock 수집 시각은 고정한 `2026-10-06T01:00:00Z`이며 시작·수신이 같다. 이것은 이번 오프라인 시뮬레이션 값이지 실제 수집 시각이나 응답 시간 측정값이 아니다. 과거 실제 요청·응답 메타데이터는 별도의 captured 필드로 유지했다. 실제 HTTP 요청·토큰 발급은 0회이며 Mock 요청만 10회다.

기존 원문·수집 증적·규격·코드·도구·보고서 91개의 SHA-256이 실행 전후 같았다. 최초·재현 보고서는 `verifiedAt`만 제외한 전체 JSON 값과 배열 순서가 같다. 이 검증은 전송 경계의 원문 보존과 기존 파서 재현이며 최신 상태·전 시장 성능·실제 네트워크 동작·후보 자격이나 과거 모집단 검증이 아니다.

## 재현 증적

Git 제외 경로 `build/kis-stock-basic-info-client-observation-01/`에 `VerifyKisStockBasicInfoClient.java`, `observation.init.gradle`, `verification.json`, `verification-replay.json`을 보관한다. 테스트 runtime classpath의 Mock HTTP 도구를 사용하며 운영 빌드와 서버에는 등록하지 않는다.

- 최초 보고서 SHA-256: `d2f9f576a263f9750b1b3a4c2ba2efa2b9b1edb67f82acd75d674f4a721981aa`
- 재현 보고서 SHA-256: `330d7de252095062c2fd68597f364636305938183e97c111a35cc1f2dd857116`

```powershell
.\gradlew.bat -I build/kis-stock-basic-info-client-observation-01/observation.init.gradle verifyKisStockBasicInfoClient --offline --no-daemon
.\gradlew.bat -I build/kis-stock-basic-info-client-observation-01/observation.init.gradle verifyKisStockBasicInfoClient '-PobservationReport=verification-replay.json' --offline --no-daemon
```

위 명령은 완료된 최초·재현 실행의 기록이다. 보고서는 `CREATE_NEW`로 덮어쓰기를 거절하며 기존 증적을 수정하지 않았다. 새 Client·응답 객체·검증 도구의 해시를 별도로 보존한다. 도구와 보고서는 일반 Git 커밋 대상이 아니며 증적이 필요한 동안 `gradlew clean`을 실행하지 않는다.

## 운영 연결 경계

Client를 빈으로 등록하거나 Provider·수집 서비스·저장·캐시·스케줄에 연결하지 않았다. 주입할 `RestClient`의 request factory·interceptor가 본문을 미리 버퍼링하거나 자동 재시도·리다이렉트를 수행하는지, 연결·읽기 타임아웃이 있는지는 이번 Mock 검증으로 보장하지 않는다. 실제 연결 구성 전에 이 통제를 확인해야 하며 Client 자체의 한 번 실행을 실제 네트워크 요청 횟수 보장으로 확대하지 않는다.

기존 파서·대조·유형·제한·사전 점검·자격·후보·백테스트·주문은 변경하지 않았다. 실전 조회 전용 키·토큰 구성과 호출 예산·Rate Limit·캐시·영속적 원문 저장은 별도 책임으로 남는다. 코스피 투자주의환기 미제공도 해결하지 않는다.

DB·스키마·빈·라이브러리·설정·`.env`·Docker와 서버 상태 변경은 없다. 계좌·주문·OpenAI 호출과 커밋·Push도 수행하지 않았다. `runtimeSelectionImplemented=false`, `eligibilityOrHistoricalPopulationVerified=false`를 유지하며 `AS_OF_VERIFIED`, `informationAvailableAt`·상장 상태·거래 허가를 생성하지 않는다.
