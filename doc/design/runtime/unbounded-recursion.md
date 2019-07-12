# Unbounded Recursion
The JVM (and hence, GraalVM) do not have support for segmented stacks, and hence
do not allow for computation of unbounded recursion - if you make too many
recursive function calls you can cause your stack to overflow. Quite obviously,
this is a big problem for a functional language where recursion is the primary
construct for looping.

There are two main categories of solution for working with unbounded recursion:

- **Segmented Stacks:** If you have the ability to allocate stacks on the heap
  you can allocate the stack in segments as it grows, meaning that the upper
  limit on the size of your stack is
- **Continuation Passing Style (CPS):** A program in CPS is one in which the
  flow of control is passed explicitly as a function of one argument (the
  continuation). The significant benefit of this is that it means that all calls
  are made in tail position, and hence no new stack frame needs to be allocated.

This document contains the details of designs and experiments for allowing the
use of unbounded recursion in Enso on GraalVM.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [A Baseline](#a-baseline)
- [Emulating Stack Segmentation with Threads](#emulating-stack-segmentation-with-threads)
    - [When to Spawn a Thread](#when-to-spawn-a-thread)
    - [Conservative Counting](#conservative-counting)
    - [Catching the Overflow](#catching-the-overflow)
    - [Thread Pools](#thread-pools)
    - [Project Loom](#project-loom)
- [Avoiding Stack Usage via a CPS Transform](#avoiding-stack-usage-via-a-cps-transform)
    - [The CPS Transform](#the-cps-transform)
- [Alternatives](#alternatives)
- [Open Questions](#open-questions)

<!-- /MarkdownTOC -->

## A Baseline
In an ideal world, we'd want our tail-called recursive loops to execute as
quickly as an optimised iterative loop. In order to get some idea of where the
tests below fall on the performance spectrum, the results below are for a simple
for loop.

All benchmarks in this section are written in pure Java rather than as part of
Enso itself in order to get an idea of the maximum theoretical performance
possible. They run using GraalVM as the JVM, and test summing integers up to a
given threshold.

The following results are for a simple loop:

```
Benchmark            (inputSize)  Mode  Cnt   Score    Error  Units
Main.basicLoopBench          100  avgt    5  ≈ 10⁻⁵           ms/op
Main.basicLoopBench         1000  avgt    5  ≈ 10⁻⁴           ms/op
Main.basicLoopBench        10000  avgt    5   0.001 ±  0.001  ms/op
Main.basicLoopBench        50000  avgt    5   0.006 ±  0.001  ms/op
Main.basicLoopBench       100000  avgt    5   0.013 ±  0.002  ms/op
Main.basicLoopBench      1000000  avgt    5   0.131 ±  0.050  ms/op
```

This is the theoretical maximum performance on the JVM for summing the numbers
up to each threshold. While this would be ideal, we recognise that we're not
going to achieve this performance. This is mainly for information.

## Emulating Stack Segmentation with Threads
As each new thread has its own stack, we can exploit this to emulate the notion
of split stacks as used in many functional programming languages. The basic idea
is to work out when you're about to run out of stack space,

### When to Spawn a Thread
One of the main problems with this approach is that you want to make as much use
out of the stack for a given thread as possible. However, it is very difficult
to get an accurate idea of when a stack may be _about_ to overflow. There are
two main approaches:

- **Conservative Counting:** You can explicitly maintain a counter that records
  the depth of your call stack.
- **Catching the Overflow:** When a thread on the JVM overflows, it throws a
  `StackOverflowError`, thus giving information as to when you've run out of
  stack space.

It may at first be apparent that you can rely on some other details of how JVM
stacks are implemented, but the JVM spec is very loose with regards to what it
permits as a valid stack implementation. This means that from a specification
perspective there is very little that could actually be relied upon.

### Conservative Counting
A naive and obvious solution is to maintain a counter that tracks the depth of
your call stack. This would allow you to make a conservative estimate of the
amount of stack you have remaining, and spawn a new thread at some threshold.

Of course, the main issue with this is that the stacks you have available become
significantly under-utilised as the threshold has to be set such that overflow
is impossible.

We did some brief testing to experiment with the 'depth limit' to find a rough
estimate for how much utilisation we could get out of the thread stacks before
they overflowed. In practice this seemed to be around 2000, though some runs
could have it set higher. Using this value gave the following results.

```
Benchmark                 (inputSize)  Mode  Cnt    Score    Error  Units
Main.testCountedExecutor          100  avgt    5    0.001 ±  0.001  ms/op
Main.testCountedExecutor         1000  avgt    5    0.008 ±  0.004  ms/op
Main.testCountedExecutor        10000  avgt    5    0.951 ±  0.095  ms/op
Main.testCountedExecutor        50000  avgt    5    7.279 ±  2.476  ms/op
Main.testCountedExecutor       100000  avgt    5   12.790 ±  1.101  ms/op
Main.testCountedExecutor      1000000  avgt    5  107.034 ±  2.076  ms/op
```

As is obvious, this is a significant slowdown from the pure loop case, with
roughly a three orders of magnitude growth. A significant amount

### Catching the Overflow
Though it is heavily recommended against by the Java documentation, it is indeed
possible to catch the `StackOverflowError`. While this provides accurate info
about when you run out of stack space, it has one major problem: you may not
unwind enough to have enough stack space to spawn a new thread.

The following is a potential algorithm that ignores this problem for the moment:

1. Each recursive call is wrapped in a `try {} catch (StackOverflowError e) {}`
   block in order to detect when the stack overflows.
2. All side-effecting operations must take place within a single Java frame.
3. When the stack overflows, a `StackOverflowError` is thrown at frame creation.
4. This can be caught, with control-flow entering the `catch` block.
5. A new thread is spawned to continue the computation.

This works because the `StackOverflowError` is thrown when the attempt to create
the new stack frame is made. This means that in the failure case none of the
function body has executed so we can safely resume on a new thread.

The main issue with this design is ensuring that there is enough stack space
after the unwind to the catch block. If there isn't enough, then it proves
impossible to spawn a new thread and this doesn't work.

The benchmarks listed here implement this algorithm without actually performing
any significant computation.

```
Benchmark            (inputSize)  Mode  Cnt    Score    Error  Units
Main.testSOExecutor          100  avgt    5   ≈ 10⁻⁴           ms/op
Main.testSOExecutor         1000  avgt    5    0.003 ±  0.001  ms/op
Main.testSOExecutor        10000  avgt    5    0.031 ±  0.001  ms/op
Main.testSOExecutor        50000  avgt    5    3.927 ±  0.477  ms/op
Main.testSOExecutor       100000  avgt    5    7.724 ±  0.239  ms/op
Main.testSOExecutor      1000000  avgt    5  104.719 ± 11.411  ms/op
```

This performs slightly better than the conservative option discussed above. As
we're guaranteed total utilisation of the stack of each thread we spawn less
threads and hence reduce the context switching overhead. Nevertheless, this is
still very slow compared to the pure loop case.

### Thread Pools
While a thread pool is conventionally seen as a way to amortise the cost of
spawning threads, this approach to recursion requires far more threads than is
really feasible to keep around in a pool, so we've not explored that approach.

### Project Loom
If project loom's coroutines and / or fibres were stable, these would likely
help somewhat by reducing the thread creation overhead that is primarily down to
OS-level context switches.

However, Loom doesn't currently seem like a viable solution to this approach as
it is not only far from stable, but also has no guarantee that it will actually
make it into the JVM.

## Avoiding Stack Usage via a CPS Transform
Transforming recursive calls into CPS allows us to avoid the _need_ for using
the stack instead of trying to augment it. This could be implemented as a global
transformation, or as a local one only for recursive calls.


```
Benchmark     (inputSize)  Mode  Cnt   Score   Error  Units
Main.testCPS          100  avgt    5   0.002 ± 0.001  ms/op
Main.testCPS         1000  avgt    5   0.018 ± 0.001  ms/op
Main.testCPS        10000  avgt    5   0.233 ± 0.037  ms/op
Main.testCPS        50000  avgt    5   1.240 ± 0.012  ms/op
Main.testCPS       100000  avgt    5   2.547 ± 0.055  ms/op
Main.testCPS      1000000  avgt    5  29.634 ± 6.873  ms/op
```

The CPS-based approach is very much a trade-off. The code that is actually being
executed is more complex, showing an order of magnitude slowdown in the cases
where the execution profile fits into a single stack. However, once the input
size grows to the point that additional stack segments are needed

### The CPS Transform
While it is tempting to perform the CPS transform globally for the whole
program, this has some major drawbacks:

- As shown above, the code becomes an order of magnitude slower within the space
  of a single stack.
- Continuation passing is a bad match for the Graal execution style as it
  requires the allocation of a new `CallTarget` for every continuation. This
  means that it gains no benefit from the inbuilt caching mechanisms, and can
  increase memory pressure significantly through all the allocations.
- It may be difficult to maintain a mapping from the original code to the CPS'd
  execution. This would greatly impact our ability to use the debugging and
  introspection tools which are necessary for implementing Enso Studio.

As a result, an ideal design would involve only performing the CPS
transformation on code which is _actually_ recursive. While you can detect this
statically via whole-program analysis, you can also track execution on the
program stack in a thread-safe manner and perform the transformation at runtime
(e.g. `private static ThreadLocal<Boolean> isExecuting;`).

## Alternatives
At the current time there are no apparent alternatives to the two approaches
discussed above. While it would be ideal for the JVM to have native support for
stack segmentation on the heap, this would likely be an in-depth and significant
amount of work to add, with no guarantee that it would be accepted into master.

## Open Questions
The following are questions for which we don't yet have answers:

- Are there any ways to instrument a JVM thread to detect when it's about to
  stack overflow?
- Based on our investigation, what would your recommendation be for us to
  proceed?
- Is there a better way to achieve the _effect_ of CPS without the need to
  allocate a new `CallTarget`? Being able to do so may allow us to utilise the
  cache again, as well as reduce memory pressure.
