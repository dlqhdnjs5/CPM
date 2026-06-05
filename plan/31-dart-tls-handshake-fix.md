## Understanding

`POST /api/dart/corp-codes/sync` reaches the controller, but the internal OpenDART request fails with `SSLHandshakeException: handshake_failure`.
Plain `curl.exe` can complete TLS to `https://opendart.fss.or.kr`, so this is likely a Java/Reactor Netty TLS negotiation issue.

## Implementation Plan

Configure only the DART WebClient to use HTTP/1.1 and TLSv1.2 for OpenDART requests.
Keep the shared connector unchanged for KIS, Naver, and OpenAI.

## Files / Changes

- `src/main/java/com/bowon/cpm/common/config/WebClientConfig.java`
  - Add a DART-only connector.
  - Force HTTP/1.1.
  - Force TLSv1.2.

## Test Steps

- Run Gradle compile/test if possible.
- Retry `curl.exe -i -X POST http://localhost:8080/api/dart/corp-codes/sync`.

## Risks / Assumptions

- This does not change DB schema.
- If OpenDART rejects Java TLS for another reason, a Java `HttpClient` or `RestClient` fallback may be needed.
