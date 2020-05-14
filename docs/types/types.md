
<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Dynamic Dispatch](#dynamic-dispatch)
  - [Specificity](#specificity)
  - [Multiple Dispatch](#multiple-dispatch)
- [Modules](#modules)
  - [Scoping and Imports](#scoping-and-imports)
- [Monadic Contexts](#monadic-contexts)
  - [Context Definitions](#context-definitions)
  - [Context Lifting](#context-lifting)
- [Strictness and Suspension](#strictness-and-suspension)
- [Analysing Parallelism](#analysing-parallelism)
- [Typed Holes](#typed-holes)
- [Errors](#errors)
  - [Async Exceptions](#async-exceptions)
  - [Broken Values](#broken-values)
- [Type Checking and Inference](#type-checking-and-inference)
  - [Maximal Inference Power](#maximal-inference-power)
- [Dependency and Enso](#dependency-and-enso)
  - [Proving Program Properties](#proving-program-properties)
  - [Automating the Proof Burden](#automating-the-proof-burden)

<!-- /MarkdownTOC -->

## Dynamic Dispatch
Enso is a language that supports pervasive dynamic dispatch. This is a big boon
for usability, as users can write very flexible code that still plays nicely
with the GUI.

The current implementation of Enso supports single dispatch (dispatch purely on
the type of `this`), but there are broader visions afoot for the final
implementation of dynamic dispatch in Enso.

> The actionables for this section include:
>
> - Determining whether we want to support proper multiple dispatch in the
>   future. This is important to know as it has implications for the type
>   system, and the design of the dispatch algorithm.

### Specificity
In order to determine which of the potential dispatch candidates is the correct
one to select, the compiler needs to have a notion of _specificity_, which is
effectively an algorithm for determining which candidate is more specific than
another.

- Always prefer a member function for both `x.f y` and `f y x` notations.
- Only member functions, current module's functions, and imported functions are
  considered to be in scope. Local variable `f` could not be used in the `x.f y`
  syntax.
- Selecting the matching function:
  1. Look up the member function. If it exists, select it.
  2. If not, find all functions with the matching name in the current module and
     all directly imported modules. These functions are the _candidates_.
  3. Eliminate any candidate `X` for which there is another candidate `Y` whose
     `this` argument type is strictly more specific. That is, `Y` this type is a
     substitution of `X` this type but not vice versa.
  4. If not all of the remaining candidates have the same this type, the search
     fails.
  5. Eliminate any candidate `X` for which there is another candidate `Y` which
     type signature is strictly more specific. That is, `Y` type signature is a
     substitution of `X` type signature.
  6. If exactly one candidate remains, select it. Otherwise, the search fails.

> The actionables for this section are as follows:
>
> - THE ABOVE VERSION IS OLD. NEEDS UPDATING.
> - The definition of specificity for dispatch candidates (including how it
>   interacts with the subsumption relationship on typesets and the ordering of
>   arguments).

### Multiple Dispatch
It is an open question as to whether we want to support proper multiple dispatch
in Enso. Multiple dispatch refers to the dynamic dispatch target being
determined based not only on the type of the `this` argument, but the types of
the other arguments to the function.

To do multiple dispatch properly, it is very important to get a rigorous
specification of the specificity algorithm. It must account for:

- The typeset subsumption relationship.
- The ordering of arguments.
- How to handle defaulted and lazy arguments.
- Constraints in types. This means that for two candidates `f` and `g`, being
  dispatched on a type `t` with constraint `c`, the more specific candidate is
  the one that explicitly matches the constraints. An example follows:

  ```ruby
  type HasName
    name : String

  greet : t -> Nothing in IO
  greet _ = print "I have no name!"

  greet : (t : HasName) -> Nothing in IO
  greet t = print 'Hi, my name is `t.name`!'

  type Person
    Pers (name : String)

  main =
    p1 = Person.Pers "Joe"
    greet p1 # Hi, my name is Joe!
    greet 7  # I have no name
  ```

  Here, because `Person` conforms to the `HasName` interface, the second `greet`
  implementation is chosen because the constraints make it more specific.

## Modules
With such a flexible type system in Enso, the need for making modules
first-class is obviated. Instead, a module is very much its own entity, being
simply a container for bindings (whether they be functions, methods, atoms, or
more generic typesets).

- Where the module name clashes with a member contained in the module, the
  member is preferred. If you need the module you must import it qualified under
  another name.
- We provide the alias `here` as a way to access the name of the current module.

> The actionables for this section are:
>
> - Characterise modules in more depth as we need them.

### Scoping and Imports
To use the contents of a module we need a way to bring them into scope. Like
most languages, Enso provides an _import_ mechanism for this. Enso has four
different kinds of imports that may be combined freely, all of which take a
module path as their first argument.

1. **Unqualified Imports:** These import all symbols from the module into the
   current scope (`import M`).
2. **Qualified Imports:** These import all symbols from the module into the
   current scope with symbols qualified under a name _different_ from the
   module name (`import M as T`).
3. **Restricted Imports:** These import only the specific symbols from the
   module into the current scope (`import M only sym1 sym2`).
4. **Hiding Imports:** These are the inverse of restricted imports, and import
   _all_ symbols other than the named ones into the current scope
   (`import M hiding sym1 sym2`),

Imports may introduce ambiguous symbols, but this is not an error until one of
the ambiguous symbols is used in user code.

When importing a module `X` into the current module `Y`, the bindings in `X`
become available in `Y` (modified by the import type). However, these bindings
are _not_ available in `Y` externally. This means that we need a re-export
mechanism. Similarly to imports, this has four kinds, all of which take a module
path as their first argument, and all of which _may_ introduce the module it
exports into scope (if it is not already imported).

1. **Unqualified Exports:** These export all symbols from the module as if they
   were defined in the exporting module (`export X`).
2. **Qualified Exports:** These export all symbols from the module as if they
   were defined in another module accessible in the exporting module
   (`export X as Y`).
3. **Restricted Exports:** These export only the specified symbols from the
   module as if they were defined in the exporting module (`export X only sym`)
4. **Hiding Exports:** These export all symbols from the module except those
   explicitly specified (`export X hiding sym1 sym2`).

Exports effectively act to 'paste' the contents of the exported module into the
module declaring the export. This means that exports that create name clashes
must be resolved at the source.

> The actionables for this section are:
>
> - Are we _really, really_ sure we want unqualified by default?
> - Think about how to handle imports properly in the type checker. What, if
>   they have any, are the impacts of imports on inference and checking?

## Monadic Contexts
Coming from a Haskell background, we have found that Monads provide a great
abstraction with which to reason about program behaviour, but they have some
severe usability issues. The main one of these is the lack of automatic lifting,
requiring users to explicitly lift computations through their monad transformer
stack.

For a language as focused on usability as Enso is this really isn't feasible. To
that end, we have created the notion of a 'Monadic Context', which is a monad
transformer based on Supermonads (see [references](#references)). These have
special support in the compiler, and hence can be automatically lifted to aid
usability. There are three main notes about the syntax of contexts:

1. Monadic contexts are defined using the `in` keyword (e.g. `Int in IO`).
2. We have a symbol `!`, which is short-hand for putting something into the
   `Exception` monadic context. This is related to broken values.
3. Contexts can be combined by using the standard typeset operators, or nested
   through repeated uses of `in`.

It is also important to note that Enso has no equivalent to `<-` in Haskell.
Instead, pure computations are implicitly placed in the `Pure` monadic context,
and `=` acts to 'peel off' the outermost layer of contexts. As such, this means
that `=` _always_ acts as `bind`, greatly simplifying how the type-checker has
to work.

> The actionables for this section are:
>
> - Think about subsumption for contexts.
> - Contexts (e.g. IO) are represented using `T in IO`. Multiple contexts are
>   combined as standard `(IO | State Int)`, and it is written the same in arg
>   position.

### Context Definitions
Contexts can be defined by users.

> The actionables for this section are:
>
> - How, what, when and why?

### Context Lifting
> The actionables for this section are:
>
> - Specify and explain how automated lifting of monadic contexts works.

## Strictness and Suspension
Enso is a language that has strict semantics by default, but it can still be
very useful to be able to opt-in to suspended computations (thunks) for the
design of certain APIs.

To that end, Enso provides a mechanism for this through the type system. The
standard library defines a `Suspend a` type which, when used in explicit type
signatures, will cause the corresponding expression to be suspended.

- The explicit calls to `Suspend` and `force` are inserted automatically by the
  compiler doing demand analysis.
- This demand analysis process will also ensure that there are not polynomial
  chains of suspend and force being inserted to ensure performance.

> The actionables for this section are as follows:
>
> - Specify this much better.

## Analysing Parallelism

> The actionables for this section are:
>
> - Work out how the type checker can support parallelism analysis.

## Typed Holes

> The actionables for this section are:
>
> - Determine how we want to support typed holes.
> - Determine the syntax for typed holes.

## Errors
Enso supports two notions of errors. One is the standard asynchronous exceptions
model, while the other is a theory of 'broken values' that propagate through
computations.

> The actionables for this section are:
>
> - Greatly expand on the reasoning and theory behind the two exception models.
> - Explain why broken values serve the GUI well.
> - Explain how this can all be typed.

### Async Exceptions

> The actionables for this section are:
>
> - Formalise the model of async exceptions as implemented.

### Broken Values
In Enso we have the notion of a 'broken' value: one which is in an invalid state
but not an asynchronous error. While these may initially seem a touch useless,
they are actually key for the display of errors in the GUI.

Broken values can be thought of like checked monadic exceptions in Haskell, but
with an automatic propagation mechanism:

- Broken values that aren't handled explicitly are automatically promoted
  through the parent scope. This is trivial inference as no evidence discharge
  will have occurred on the value.

  ```ruby
  open : String -> String in IO ! IO.Exception
  open = ...

  test =
    print 'Opening the gates!'
    txt = open 'gates.txt'
    print 'Gates were opened!'
    7
  ```

  In the above example, the type of test is inferred to
  `test : Int in IO ! IO.Exception`, because no evidence discharge has taken
  place as the potential broken value hasn't been handled.
- This allows for very natural error handling in the GUI.

> The actionables for this section are:
>
> - Determine what kinds of APIs we want to use async exceptions for, and which
>   broken values are more suited for.
> - Ensure that we are okay with initially designing everything around async
>   exceptions as broken values are very hard to support without a type checker.
> - Initially not supported for APIs.

## Type Checking and Inference
As a statically-typed language, Enso is built with a sophisticated type checker
capable of reasoning about a fully dependently-typed system. However, a type
checker on its own is quite useless. For Enso to truly be usable, it must also
have a powerful type inference engine.

> The actionables for this section are:
>
> - Work out how on earth we do inference and how we maximise inference power.
> - Do we want to provide a way to reason about the _runtime representation_ of
>   types? This is 'Levity Polymorphism' style.
> - We want error messages to be as informative as possible, and are willing to
>   retain significant extra algorithmic state in the typechecker to ensure that
>   they are. This means both _formatting_ and _useful information_.
> - It is going to be important to retain as much information as possible in
>   order to provide informative error messages. This means that the eventual
>   algorithm is likely to combine techniques from both W and M
>   (context-insensitive and context-sensitive respectively).

### Maximal Inference Power
In order to make Enso's type inference as helpful and friendly as possible to
our users, we want the ability to infer the _maximal subset_ of the types that
Enso can express.

> The actionables for this section are:
>
> - How do we do inference for higher-rank and impredicative instantiations.
> - How do we infer contexts, and how do we make that inference granular (e.g.
>   `IO.Read`, `IO.Write`, rather than just `IO`).
> - How do we propagate inference information as far as possible?
> - If it comes to a tension between typechecker speed and inference capability,
>   Enso will err on the side of inference capability in order to promote ease
>   of use. Speed will be increased by performing incremental type-checking
>   where possible on subsequent changes.
> - Where are we okay requiring annotations? Polymorphic recursion, higher rank
>   function parameters, constrained data and dependency?

## Dependency and Enso
Enso is a [dependently typed](https://en.wikipedia.org/wiki/Dependent_type)
programming language. This means that types are first-class values in the
language, and hence can be manipulated and computed upon just like any other
value. To the same end, there is no distinction between types and kinds, meaning
that Enso obeys the 'Type in Type' axiom of dependent types. While this does
make the language unsound as a logic, the type safety properties do not depend
on this fact.

In essence, values are types and types are values, and all kinds are also types.
This means that, in Enso, you can run _arbitrary_ code to compute with types.
All in all, dependency in Enso is not about being a proof system, but is instead
about enabling practical usage without hampering usability. To that end we
combine a powerful and non-traditional dependently-typed system with the ability
to automate much of the proof burden through SMT solvers.

> The actionables for this section are:
>
> - Do we want the ability to explicitly quantify type variables for visibility,
>   dependency, relevance and requiredness (forall, foreach, etc).
> - How do we infer as much of these above properties as possible?
> - Based on QTT and RAE's thesis.

### Proving Program Properties
Some notes:

- Dependent types as constructing proofs through combining types. Combining
  types provides evidence which can be discharged to create a proof. A value can
  then only be constructed by discharging a proof.
- To provide more predictable inference in dependent contexts, this system will
  draw on the notion of _matchability polymorphism_ as outlined in the
  higher-order type-level programming paper. The key recognition to make,
  however is that where that paper was required to deal with
  backwards-compatibility concerns, we are not, and hence can generalise all
  definitions to be matchability polymorphic where appropriate.
- Provide some keyword (`prove`) to tell the compiler that a certain property
  should hold when typechecking. It takes an unrestricted expression on types,
  and utilises this when typechecking. It may also take a string description of
  the property to prove, allowing for nicer error messages:

    ```
    append : (v1 : Vector a) -> (v2 : Vector a) -> (v3 : Vector a)
    append = vec1 -> vec2 ->
        prove (v3.size == v1.size + v2.size) "appending augments length"
        ...
    ````

    Sample error:

    ```
    [line, col] Unable to prove that "appending augments length":
        Required Property: v3.size == v1.size + v2.size
        Proof State:       <state>

        <caret diagnostics>
    ```

  This gives rise to the question as to how we determine which properties (or
  data) are able to be reasoned about statically.
- Dependent types in Enso will desugar to an application of Quantitative Type
  Theory.

> The actionables for this section are:
>
> - Specify how we want dependency to behave in a _far more rigorous_ fashion.

### Automating the Proof Burden
Even with as capable and simple a dependently-typed system as that provided by
Enso, there is still a burden of proof imposed on our users that want to use
these features. However, the language [F*](https://www.fstar-lang.org/) has
pioneered the combination of a dependently-typed system with an SMT solver to
allow the automation of many of the simpler proofs.

- The Enso typechecker will integrate an aggressive proof rewrite engine to
  automate as much of the proof burden as possible.

> The actionables for this section are:
>
> - What is the impact of wanting this on our underlying type theory?
> - Where and how can we draw the boundary between manual and automated proof?
> - How much re-writing can we do (as aggressive as possible) to assist the SMT
>   solver and remove as much proof burden as possible.
