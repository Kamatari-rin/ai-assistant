
# Chat Server-Sent Events Protocol

Contract version: 0.2.0

Status: Frozen for MVP

## 1. Overview

The AI Service exposes conversational AI responses as a
Server-Sent Events (SSE) stream.

Endpoint:

    POST /api/v1/chat/stream

Request content type:

    application/json

Successful response content type:

    text/event-stream

All event payloads use UTF-8 JSON with camelCase property names.

The HTTP request schema and pre-stream HTTP errors are defined in
[ai-service.openapi.yaml](./ai-service.openapi.yaml).

This document is authoritative for SSE event payloads, event ordering,
and streaming lifecycle semantics.

## 2. Request

Example:

```json
{
  "conversationId": null,
  "message": "Почему заказ 12345 задерживается?",
  "requestId": "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
}
```

- A null conversationId starts a new conversation.
- A non-null conversationId must identify an existing conversation.
- requestId is an optional correlation UUID.
- The server generates requestId when it is omitted.
- Each invocation attempt has its own requestId.
- requestId is not a business idempotency key.

The client consumes the POST response using a streaming HTTP client,
such as fetch with ReadableStream.

Native browser EventSource is not used because the chat request
requires a POST method and a JSON request body.

## 3. SSE framing

Each application event uses the following structure:

```text
id: 1
event: started
data: {"sequence":1,"requestId":"f90fd699-154c-4ddb-9b9a-316ea31b2b07","conversationId":"09b98c5b-37a5-4fa3-97c7-a016d7517760"}

```

An empty line terminates an SSE event.

Each application event contains:

| Field | Description |
|---|---|
| id | SSE event identifier, equal to sequence |
| event | Application event type |
| data | UTF-8 JSON payload |

SSE id is the decimal string representation of the JSON sequence
integer.

The server emits JSON payloads containing the fields defined in
this document.

Keepalive SSE comments are permitted:

```text
: keepalive

```

Keepalive comments are not application events and do not consume
sequence numbers.

## 4. Ordering and lifecycle

The following rules apply to each chat invocation:

1. started is the first application event.
2. sequence starts at 1.
3. sequence increases by exactly 1 for each application event.
4. SSE id equals the payload sequence.
5. Event emission is serialized.
6. Intermediate events may be omitted or repeated as needed.
7. A completed stream has exactly one terminal event:
   done or error.
8. A terminal event is the last application event.
9. The server closes the stream after the terminal event.

Sequence numbers are scoped to a single invocation.

They are not persistent conversation message identifiers.

The MVP does not support automatic replay, stream resumption,
or reconnection using Last-Event-ID.

Example successful sequences:

    started -> delta -> done

    started -> status -> source -> delta -> proposal -> done

Example failed sequence:

    started -> status -> delta -> error

A transport interruption or client cancellation may prevent delivery
of a terminal event. Such a stream is incomplete and must not be
treated as a successful invocation.

## 5. Common event fields and types

Every application event contains:

| Field | Type | Required |
|---|---|---|
| sequence | Integer, minimum 1 | Yes |

Identifier conventions:

- requestId: UUID string.
- conversationId: UUID string.
- documentId: UUID string.
- sourceId: UUID string.
- proposalId: UUID string.
- traceId: OpenTelemetry trace identifier, represented as a
  32-character hexadecimal string.
- orderNumber: Human-readable string identifier, not a numeric ID.

Timestamps use ISO-8601 UTC representation.

All fields listed in the individual event schemas are required.

## 6. Event types

### 6.1. started

Signals that the chat invocation has started and identifies the
associated conversation.

Exactly one started event is emitted for an opened stream.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Always 1 |
| requestId | UUID string | Invocation correlation ID |
| conversationId | UUID string | Conversation ID |

Example:

```json
{
  "sequence": 1,
  "requestId": "f90fd699-154c-4ddb-9b9a-316ea31b2b07",
  "conversationId": "09b98c5b-37a5-4fa3-97c7-a016d7517760"
}
```

The client stores conversationId for subsequent chat requests.

### 6.2. status

Provides a progress update for the current invocation.

Status events do not contribute text to the final assistant response.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Event sequence |
| phase | String | Machine-readable processing phase |
| label | String | Human-readable progress label |

Example:

```json
{
  "sequence": 2,
  "phase": "TOOL_CALL",
  "label": "Получение информации о заказе"
}
```

TOOL_CALL is an initial documented phase.

The phase field is extensible and is not a closed enumeration.

Clients must support unknown phase values and may display the label
without interpreting the phase.

### 6.3. source

Describes a bounded source excerpt retrieved from the knowledge base.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Event sequence |
| sourceId | UUID string | Source identifier |
| documentId | UUID string | Associated document identifier |
| filename | String | Document filename |
| chunkIndex | Integer, minimum 0 | Document chunk index |
| snippet | String | Bounded source excerpt |

Example:

```json
{
  "sequence": 3,
  "sourceId": "27b9dfca-a98c-49a9-b49d-28d266481492",
  "documentId": "9e807f42-8af3-4a03-b0d4-e6fac010d525",
  "filename": "shipping-policy.pdf",
  "chunkIndex": 2,
  "snippet": "Escalation requests may be created for eligible orders."
}
```

Source events identify retrieved source candidates.

A source event must be emitted before the client can display a link
referencing that source.

The final set of cited sources is determined by done.sources.

A retrieved source is not necessarily cited in the final response.

Source excerpts are bounded. Full document contents are not streamed
through source events.

Retrieved text is untrusted data.

Clients must render source text safely and must not interpret it
as executable markup or instructions.

### 6.4. delta

Provides the next fragment of the assistant response.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Event sequence |
| text | String | Assistant response fragment |

Example:

```json
{
  "sequence": 4,
  "text": "Заказ 12345 задерживается"
}
```

Subsequent example:

```json
{
  "sequence": 5,
  "text": " из-за отсутствия товара."
}
```

The client appends text fragments in sequence order.

The resulting assistant response is the concatenation of delta.text
values.

Clients must not insert additional whitespace or separators between
fragments.

Delta boundaries are not guaranteed to correspond to words,
sentences, or model tokens.

### 6.5. proposal

Represents a proposed business action that requires explicit
user approval.

A proposal event does not execute the proposed action.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Event sequence |
| proposalId | UUID string | Persisted proposal identifier |
| type | String | Proposed action type |
| orderNumber | String | Target order display number |
| reason | String | Escalation reason |
| priority | String | Escalation priority |
| expiresAt | Date-time string | Proposal expiry in UTC |

Initial supported values:

| Field | Value |
|---|---|
| type | CREATE_ESCALATION |
| reason | OUT_OF_STOCK |
| priority | HIGH |

Example:

```json
{
  "sequence": 6,
  "proposalId": "127319be-d806-4a7a-b88b-8861f34e2e04",
  "type": "CREATE_ESCALATION",
  "orderNumber": "12345",
  "reason": "OUT_OF_STOCK",
  "priority": "HIGH",
  "expiresAt": "2026-11-11T19:00:00Z"
}
```

The client may display proposal details while the invocation
is streaming.

Confirmation controls must remain disabled until the client
receives a valid terminal done event for that invocation.

If the invocation ends with error, is cancelled, or terminates
without done, the client must not offer confirmation for a
proposal received from that incomplete stream.

The backend independently verifies that the originating
invocation has successfully completed and that its completion
state has been persisted.

A proposal from an active, failed or cancelled invocation
must not initiate a business mutation.

The backend must not trust the client to enforce this rule.

A proposal retains its own lifecycle. An unused PENDING proposal
may subsequently expire.

Explicit confirmation uses:

    POST /api/v1/actions/{proposalId}/confirm

Rejection uses:

    POST /api/v1/actions/{proposalId}/reject

Proposal state is retrieved using:

    GET /api/v1/actions/{proposalId}

The REST request and response schemas are defined in
[ai-service.openapi.yaml](./ai-service.openapi.yaml).

The LLM does not execute the proposed mutation.

The backend validates the persisted proposal state, action allowlist,
expiry, and applicable business rules.

The proposalId is the stable downstream business idempotency key.

### 6.6. done

Indicates successful completion of the chat invocation.

This is a terminal event.

The server emits done only after:

1. Assistant response generation has completed.
2. Final source citations have been validated.
3. The completed conversation turn has been successfully persisted.
4. Successful invocation completion has been recorded.

The persisted conversation turn and successful completion state
must be consistent before done is emitted.

A failed or cancelled invocation must not be recorded as a
completed assistant response.

After successful persistence, a transport failure may prevent
delivery of done. Loss of the HTTP connection does not undo an
already committed conversation turn.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Event sequence |
| conversationId | UUID string | Conversation ID |
| requestId | UUID string | Invocation correlation ID |
| traceId | Hexadecimal string | Invocation trace ID |
| sources | Array of UUID strings | Validated cited source IDs |

Example:

```json
{
  "sequence": 7,
  "conversationId": "09b98c5b-37a5-4fa3-97c7-a016d7517760",
  "requestId": "f90fd699-154c-4ddb-9b9a-316ea31b2b07",
  "traceId": "aaaaaaaa11111111bbbbbbbb22222222",
  "sources": [
    "27b9dfca-a98c-49a9-b49d-28d266481492"
  ]
}
```

The conversationId and requestId must match the started event.

The sources array contains only validated source IDs cited in the
final assistant response.

Every ID in sources must refer to a source event already emitted
within the same invocation.

If no sources are cited, sources is an empty array.

The client marks the invocation as successfully completed.

Completed conversation messages can subsequently be retrieved using:

    GET /api/v1/conversations/{conversationId}/messages

### 6.7. error

Indicates controlled failure after the SSE stream has opened.

This is a terminal event.

Payload:

| Field | Type | Description |
|---|---|---|
| sequence | Integer | Event sequence |
| code | String | Machine-readable error code |
| message | String | Sanitized error description |
| requestId | UUID string | Invocation correlation ID |

Example:

```json
{
  "sequence": 3,
  "code": "LLM_UNAVAILABLE",
  "message": "AI service is temporarily unavailable.",
  "requestId": "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
}
```

The requestId must match the started event.

An error event terminates the invocation.

The server must not emit done after error.

Error messages must not expose raw exceptions, stack traces,
credentials, API keys, or internal prompts.

Controlled invocation errors include:

| Code | Meaning |
|---|---|
| AI_RATE_LIMITED | AI provider rate limit |
| LLM_UNAVAILABLE | Language model dependency unavailable |
| ORDER_SERVICE_UNAVAILABLE | Order Service dependency unavailable |
| TOOL_UNAVAILABLE | Required tool unavailable |
| AI_TIMEOUT | Invocation timeout |
| TOOL_LIMIT_EXCEEDED | Tool execution limit exceeded |

TOOL_LIMIT_EXCEEDED is a controlled terminal SSE error.

Clients must handle an unknown error code as a generic failed
invocation and may display the sanitized message.

## 7. HTTP errors and SSE errors

Before the SSE stream opens, request failures use the appropriate
HTTP status and application/problem+json response.

The REST error schema is defined in ai-service.openapi.yaml.

After the stream opens, an HTTP 200 response does not guarantee that
the invocation will complete successfully.

Application failures after that point are communicated through the
terminal error event when the connection remains writable.

The client must inspect terminal events rather than relying solely
on the initial HTTP response status.

A partial assistant response followed by error is not a completed
assistant response.

## 8. Client stream parsing

The HTTP response body is a byte stream.

Network chunks do not necessarily correspond to SSE events.

The client must support:

- UTF-8 characters split across network chunks.
- Multiple events within a single network chunk.
- A single event split across multiple network chunks.
- SSE line endings.
- Empty lines terminating events.
- SSE comments and keepalive messages.
- Multiple data lines, joined according to SSE framing rules.

The client uses incremental UTF-8 decoding and retains incomplete
event data until the complete event is available.

JSON parsing is performed after the complete SSE event has been
assembled.

The client validates event ordering and sequence values.

The client must not treat an incomplete or malformed stream as a
successful chat invocation.

## 9. Cancellation and timeouts

The client may abort an active chat request.

The server cancels ongoing orchestration where feasible and releases
associated resources.

Cancellation does not guarantee immediate cancellation of an
already-running downstream operation.

An invocation may end without a terminal event if the client
disconnects or the network connection fails.

If a timeout occurs while the stream is writable, the server emits
a controlled terminal error with code AI_TIMEOUT.

The MVP does not automatically retry a failed or cancelled
POST chat request.

A user-initiated retry is a new invocation attempt with a new
requestId.

## 10. Security and rendering

The client treats streamed content as untrusted data.

Assistant text and source excerpts must be rendered safely.

The client must not execute instructions embedded in source text,
assistant text, or progress labels.

An action proposal is structured data, not authorization to execute
a mutation.

Business actions require explicit user confirmation through the
approval API.

No arbitrary downstream URL or executable command may be obtained
from an SSE payload.

All examples use synthetic demonstration data.

## 11. Complete successful stream example

```text
id: 1
event: started
data: {"sequence":1,"requestId":"f90fd699-154c-4ddb-9b9a-316ea31b2b07","conversationId":"09b98c5b-37a5-4fa3-97c7-a016d7517760"}

id: 2
event: status
data: {"sequence":2,"phase":"TOOL_CALL","label":"Получение информации о заказе"}

id: 3
event: source
data: {"sequence":3,"sourceId":"27b9dfca-a98c-49a9-b49d-28d266481492","documentId":"9e807f42-8af3-4a03-b0d4-e6fac010d525","filename":"shipping-policy.pdf","chunkIndex":2,"snippet":"Escalation requests may be created for eligible orders."}

id: 4
event: delta
data: {"sequence":4,"text":"Заказ 12345 задерживается"}

id: 5
event: delta
data: {"sequence":5,"text":" из-за отсутствия товара."}

id: 6
event: proposal
data: {"sequence":6,"proposalId":"127319be-d806-4a7a-b88b-8861f34e2e04","type":"CREATE_ESCALATION","orderNumber":"12345","reason":"OUT_OF_STOCK","priority":"HIGH","expiresAt":"2026-11-11T19:00:00Z"}

id: 7
event: done
data: {"sequence":7,"conversationId":"09b98c5b-37a5-4fa3-97c7-a016d7517760","requestId":"f90fd699-154c-4ddb-9b9a-316ea31b2b07","traceId":"aaaaaaaa11111111bbbbbbbb22222222","sources":["27b9dfca-a98c-49a9-b49d-28d266481492"]}

```

## 12. Complete failed stream example

```text
id: 1
event: started
data: {"sequence":1,"requestId":"9b41f757-6915-48ba-adc9-82c36bb39c85","conversationId":"09b98c5b-37a5-4fa3-97c7-a016d7517760"}

id: 2
event: status
data: {"sequence":2,"phase":"TOOL_CALL","label":"Получение информации о заказе"}

id: 3
event: error
data: {"sequence":3,"code":"ORDER_SERVICE_UNAVAILABLE","message":"Order information is temporarily unavailable.","requestId":"9b41f757-6915-48ba-adc9-82c36bb39c85"}

```

The server closes the stream after the error event.

## 13. Contract compatibility

REST wire formats are defined by the OpenAPI specifications.

This document defines the SSE protocol.

Changes to event types, required payload fields, field types,
ordering guarantees, or terminal semantics require coordinated
updates to the affected backend and frontend implementations.

Internal Java classes and Spring AI implementation details are not
part of this public contract.
