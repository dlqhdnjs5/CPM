## Understanding

`DartCorpCodeClient` needed Java's built-in `HttpClient` because Reactor Netty fails TLS handshake with OpenDART.
`DartDisclosureClient` and `DartFinancialClient` still use WebClient and can hit the same failure.

## Implementation Plan

Replace only the OpenDART HTTP calls in `DartDisclosureClient` and `DartFinancialClient` with Java `HttpClient`.
Deserialize JSON responses with Jackson into the existing response DTO records.

## Files / Changes

- `DartDisclosureClient.java`
  - Remove WebClient/API-key field usage.
  - Use `DartProperties` for base URL and API key.
  - Use Java `HttpClient` + Jackson `ObjectMapper`.
- `DartFinancialClient.java`
  - Same pattern for `/api/fnlttSinglAcnt.json`.

## Test Steps

- Run `compileJava`.
- After app restart, run disclosure and financial fetch APIs for a stock code.

## Risks / Assumptions

- No DB schema changes.
- Existing service logic and response DTOs remain unchanged.
