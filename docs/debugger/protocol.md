---
layout: developer-doc
title: Enso Debugger Protocol Message Specification
category: debugger
tags: [repl, debugger, protocol, specification]
order: 1
---

# Enso Debugger Protocol Message Specification
Binary Protocol for the Debugger is used in communication between the runtime
and tools exploiting the REPL/debugger functionalities. It can be used to
implement a simple REPL or add debugging capabilities to the editor.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

TODO - TOC

<!-- /MarkdownTOC -->

## Types

There are some helper types used within the debugger's protocol. These are
specified here.

### `ObjectRepr`
External representation of arbitrary values returned by the REPL (internally
these are of type `Object`).

As these values are only used for presentation, they are represented by String.

```typescript
interface ObjectRepr {
  representation: String;
}
```

### `StackTraceElement`
Represents a line of the stack trace. Corresponds to
`java.lang.StackTraceElement`.

```typescript
interface StackTraceElement {
  declaringClass: String;
  methodName: String;
  fileName: String;
  lineNumber: Int;
}
```

### `Exception`
Represents an exception that may have been raised during requested execution.

```typescript
interface Exception {
  message: String;
  stackTrace: [StackTraceElement];
  cause: Exception;
}
```

### `Binding`
Represents a single binding in the current scope.

```typescript
interface Binding {
    name: String;
    value: ObjectRepr;
}
```

## Messages

### `repl/evaluate`
Evaluates an arbitrary expression in the current execution context.

Returns an union-type that contains either the value of successfully evaluated
expression or an exception that has been raised during evaluation.

#### Parameters
```idl
namespace org.enso.polyglot.protocol.debugger;

table ReplEvaluationRequest {
  expression: String (required);
}
```

#### Result
```idl
namespace org.enso.polyglot.protocol.debugger;

table ReplEvaluationSuccess {
  result: ObjectRepr (required);
}

table ReplEvaluationFailure {
  exception: Exception (required);
}

union ReplEvaluationResult {
  success: ReplEvaluationSuccess,
  failure: ReplEvaluationFailure
}
```

### `repl/listBindings`
Lists all the bindings available in the current execution scope.

#### Parameters
```idl
namespace org.enso.polyglot.protocol.debugger;

table ReplListBindingsRequest {}
```

#### Result
```idl
namespace org.enso.polyglot.protocol.debugger;

table ReplListBindingsResult {
  bindings: [Binding];
}
```

### `repl/exit`
Terminates this REPL session (and resumes normal program execution).

The last result of #evaluate(String) (or Builtins#unit() if evaluate(String) was
not called before) will be returned from the instrumented node.

This function must always be called at the end of REPL session, as otherwise the
program will never resume. It's forbidden to use this object after exit has been
called.

#### Parameters
```idl
namespace org.enso.polyglot.protocol.debugger;

table ReplExitRequest {}
```

#### Result
```idl
namespace org.enso.polyglot.protocol.debugger;

table ReplExitSuccess {}
```