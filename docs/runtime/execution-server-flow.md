# Execution Server Flow
This document describes the API and workflow of the internal execution server.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Creating a connection](#creating-a-connection)
- [API](#api)
- [Internal architecture](#internal-architecture)
   - [Job Queue](#job-queue)
   - [Job types](#job-types)
   - [API Methods to Jobs Translation](#api-methods-to-jobs-translation)

<!-- /MarkdownTOC -->

## Creating a connection
> The actionables for this section are:
> describe the `org.graalvm.polyglot.Context.Builder.serverTransport` workflow
> of connecting to the server.

## API
> The actionables for this section are:
> Document the server's API.

## Internal architecture
This section describes certain implementation details of the execution server,
allowing it to perform its operations safely and interactively.

### Job Queue
The execution server uses a job queue containing requests to be performed.
All jobs should be performed sequentially. An API method may queue multiple
jobs. Moreover, a running job may queue additional jobs to be performed after
it is finished.

> The actionables for this section are:
> Rethink non-sequential executions in the future.

### Job types
There are a number of job types used internally for handling different
scenarios:

#### `EnsureCompiled`
Takes a context ID and ensures that the corresponding modules are compiled in
the newest version.
This operation is not currently interruptible, but may become in the future.

#### `Execute`
Takes a context ID (and possibly additional params, such as the minimal set
of expressions to be computed) and executes the Enso code corresponding to the
context's stack. Updates caches and sends updates to the users.
This operation is interruptible through `Thread.interrupt()`.

#### `RunVisualization`
Takes an expression value and a visualization function. Runs the function
on the value, sending an update to the user.
This operation is interruptible through `Thread.interrupt()`.

### API Methods to Jobs Translation
The following describes handling of API messages through job queue
modifications.

1. Code Edits:
   1. Abort and/or dequeue all pending and running messages (if possible).
   2. Synchronously perform all code updates and cache invalidations.
   3. Enqueue a pair of `EnsureCompiled` and `Execute` for each context.
2. Stack Modifications and Recomputes:
   1. Abort and/or dequeue all pending and running requests relevant to the
      affected context.
   2. Synchronously perform all state updates and cache updates.
   3. Respond to the user.
   4. Enqueue EnsureCompiled and Execute for the affected context.
3. Visualization modifications:
   1. Synchronously perform all state updates.
   2. Respond to the user.
   3. Enqueue `EnsureCompiled` and `Execute` for the affected context.
      Set the minimal set of expressions required to compute to
      `Set(visualizedExpression)` in the `Execute` command.
