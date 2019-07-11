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

- [Emulating Stack Segmentation with Threads](#emulating-stack-segmentation-with-threads)
    - [When to Spawn a Thread](#when-to-spawn-a-thread)
    - [Conservative Counting](#conservative-counting)
    - [Catching the Overflow](#catching-the-overflow)
    - [Thread Pools](#thread-pools)
    - [Project Loom](#project-loom)
- [Avoiding Stack Usage via a CPS Transform](#avoiding-stack-usage-via-a-cps-transform)
    - [CPS Performance](#cps-performance)
- [Alternatives](#alternatives)

<!-- /MarkdownTOC -->

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

We did some brief testing to experiment with the 'depth limit' (shown here as
'interruptions') to see the kind of times we were looking at.

```
Benchmark                 (interruptions)  Mode  Cnt   Score   Error  Units
Main.testCountedExecutor               10  avgt    5  61.822 ± 7.031  ms/op
Main.testCountedExecutor              100  avgt    5   6.471 ± 0.286  ms/op
Main.testCountedExecutor             1000  avgt    5   1.320 ± 0.253  ms/op
```

<analysis>

### Catching the Overflow
Though it is heavily recommended against by the Java documentation, it is indeed
possible to catch the `StackOverflowError`. While this provides accurate info
about when you run out of stack space, it has one major problem: unless you
unwind further than catching the error, you don't have enough stack space to
even spawn a new thread to continue execution.

The following is a potential algorithm that ignores this problem for the moment:

1. das

<benchmarks>

### Thread Pools
As this approach relies on the ability to spawn significant numbers of threads,


### Project Loom
If project loom's coroutines and / or fibres were stable, these would likely
help somewhat by reducing the thread creation overhead that is primarily down to
OS-level context switches.

However, Loom doesn't currently seem like a viable solution to this approach as
it is not only far from stable, but also has no guarantee that it will actually
make it into the JVM.

## Avoiding Stack Usage via a CPS Transform
If we can globally (or for specific instances) transform recursive calls into

### CPS Performance
One of the main issues with interpreting Enso in CPS is that the nature of
continuation passing is fairly mismatched with the GraalVM execution model.

## Alternatives

<!--
  - The details of how the JVM manages thread stacks and how they are implemented.
    - The specification makes no claims as to how the stacks are implemented, and we shouldn't rely
      on any HotSpot implementation details.
    - Most importantly, stack frames are of a variable size, meaning that tracking frames with our
      own counter wouldn't result in a reliable

  - Potential techniques for tracking stack usage.
    - The very slow method is to count the length of a stack trace, however this only works for
      stacks up to a certain depth as the resultant array is capped in length.

  - A potential design for thread-based stack segmentation:
    - Each recursive call creates a save point. This is as simple as running the next recursive call
      in a `try {} catch (StackOverflowError e) {}`.
    - Ensure that all side-effecting operations take place within a single Java frame, which does
      not necessarily correspond to an enso frame.
    - When the stack overflows, execution will enter the `catch` block. For this design to work,
      this overflow needs to be thrown at frame allocation.
    - Ensure that there is enough stack space after unwinding (how?) to spawn a new thread to let
      the execution continue.

  - What is the performance profile like for an application using this approach for recursion?

  - To what extent can we instrument a JVM thread to learn when it's gonna run out of memory?
    - JMX? Even if you can, you can't dynamically read thread maximum memory size and hence this is
      still brittle.
    - Can you get the maximum stack size set for a VM instance?

  - What is the impact of CPS on performance and debuggability/introspection.
  - Ask Chris for an intro to Duncan MacGregor regarding loom and Graal.
-->
