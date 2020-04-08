# Scratchpad
private and public messages

## Binary Protocol
- Something existing (protobufs / capnproto / flatbuffers)

- If we want to support streaming data then we have to use capnp / flatbuffers.
- Flatbuffers seems like a good choice due to that ability to stream.
- All of the above have JVM and Rust bindings.

## Messages

### `executionContext/attachVisualisation`

```typescript
{
    executionContextId: UUID;
    expressionId: UUID;
    visualisationId: UUID;
    visualisationModule: QualifiedName;
    expression: VisualisationExpression;
}

type VisualisationExpression = String;
```

- Empty response

- User sends an `expression` that is evaluated in the context of 
  `visualisationModule`, passed data from somewhere else.
- Expression _must_ evaluate to a single-argument function that is passed the
  actual data.

### `executionContext/detachVisualisation`

```typescript
{
    executionContextId: UUID;
    visualisationId: UUID;
}
```

- Empty response

### `executionContext/modifyVisualisation`

```typescript
{
    executionContextId: UUID;
    visualisationId: UUID;
    visualisationModule: QualifiedName;
    expression: VisualisationExpression;
}
```

## Connection Stuff

### `session/initTextConnection`

```typescript
{
    clientId: UUID;
}

{
    contentRoots: [UUID];
}
```

- Request

### `session/initBinaryConnection`

```typescript
{
    clientId: UUID;
}
```

- Binary message for correlation

### `session/binaryConnectionInitialised`
- May not need to exist
- TBC

- 2 sockets per client 
- At spawn up:
    + Port/socket information passed in as part of the initialisation flow
    + From the project manager

- Initialisation flow:
    + Client generates UUID Client Identifier
    + Connect + Init to text protocol -> replies with content roots
    + Connect + Init to binary protocol -> ACK

## Permissioning
Currently gated under `executionContext/canModify`.
