## Understanding

The DART-specific Reactor Netty settings still fail with `SSLHandshakeException: handshake_failure`.
A direct Java 21 `java.net.http.HttpClient` test succeeds against OpenDART, so the issue is specific to Reactor Netty rather than Java 21 itself.

## Implementation Plan

Use Java's built-in `HttpClient` only for `corpCode.xml` download.
Keep the existing ZIP/XML parsing and DB save flow unchanged.

## Files / Changes

- `src/main/java/com/bowon/cpm/dart/client/DartCorpCodeClient.java`
  - Replace WebClient download call with `java.net.http.HttpClient`.
  - Use configured `external.dart.base-url` and `external.dart.api-key`.
  - Throw `ExternalApiException` for non-2xx responses or download failures.

## Test Steps

- Run `compileJava`.
- Restart the Spring app.
- Retry `POST /api/dart/corp-codes/sync`.

## Risks / Assumptions

- This change targets only corp code sync.
- Other DART clients still use WebClient and may need the same pattern if they show the same TLS failure.
