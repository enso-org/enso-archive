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

TODO do we need any custom defs here?

## Messages

### `repl/evaluate`
Evaluates an arbitrary expression in the current execution context.

#### Parameters
```typescript
{
  expression: String;
}
```

#### Result
```typescript
{
  TODO Object
}
```

TODO - reply message ? should execution be synchronous or async (send a separate reply)

### `repl/listBindings`
Lists all the bindings available in the current execution scope.

#### Parameters
```typescript
null
```

#### Result
```typescript
{
  TODO Map<String, Object>
}
```

return a map, where keys are variable names and values are current values of 
variables.

### `repl/exit`
Terminates this REPL session (and resumes normal program execution).

The last result of #evaluate(String) (or Builtins#unit() if evaluate(String) was
not called before) will be returned from the instrumented node.

This function must always be called at the end of REPL session, as otherwise the
program will never resume. It's forbidden to use this object after exit has been
called.

#### Parameters
```typescript
null
```

#### Result
```typescript
null
```