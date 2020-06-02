---
layout: developer-doc
title: Enso Debugger Protocol Message Specification
category: repl
tags: [repl, protocol, specification]
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
type ObjectRepr = String;
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

#### Parameters
```idl
namespace org.enso.runner.protocol.binary;

table ReplEvaluationRequest {
  expression: String;
}
```

#### Result
```idl
namespace org.enso.runner.protocol.binary;

table ReplEvaluationResult {
  result: ObjectRepr;
}
```

### `repl/listBindings`
Lists all the bindings available in the current execution scope.

#### Parameters
```idl
namespace org.enso.runner.protocol.binary;

table ReplListBindingsRequest {}
```

#### Result
```idl
namespace org.enso.runner.protocol.binary;

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
namespace org.enso.runner.protocol.binary;

table ReplExitRequest {}
```

#### Result
```idl
namespace org.enso.runner.protocol.binary;

//Indicates an operation has succeeded.
table Success {}
```