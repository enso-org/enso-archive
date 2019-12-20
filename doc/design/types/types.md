# Enso: The Type System
On the spectrum of programming type systems ranging from dynamically typed to
statically typed, one likes to think that there is a happy medium between the
two. A language that _feels_ dynamic, with high levels of type inference, but
lets the users add more type information as they want more safety and
compile-time checking.

Enso aims to be that language, providing a statically-typed language with a type
system that makes it feel dynamic. It will infer sensible types in many cases,
but as users move from exploratory pipelines to production systems, they are
able to add more and more type information to their programs, proving more and
more properties using the type system. This is based on a novel type-inference
engine, and a fusion of nominal and structural typing, combined with dependent
types.

All in all, the type system should stay out of the users' ways unless they make
a mistake, but give more experienced users the tools to build the programs that
they require.

This document contains discussion and designs for the type-system's behaviour,
as well as formal specifications where necessary. It discusses the impact of
many syntactic language features upon inference and type checking, and is
instrumental for ensuring that we build the right language.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Goals for the Type System](#goals-for-the-type-system)
- [The Type Hierarchy](#the-type-hierarchy)
  - [Atoms](#atoms)
  - [Typesets](#typesets)
  - [Type Operators](#type-operators)
  - [Records and Structural Typing](#records-and-structural-typing)
  - [Interfaces](#interfaces)
  - [Projections](#projections)
- [Function Types](#function-types)
  - [Structural Type Shorthand](#structural-type-shorthand)
  - [Function Composition](#function-composition)
- [Access Modificatiom](#access-modificatiom)
- [Pattern Matching](#pattern-matching)
- [Dynamic Dispatch](#dynamic-dispatch)
  - [Multiple Dispatch](#multiple-dispatch)
- [Modules](#modules)
  - [Scoping and Imports](#scoping-and-imports)
- [Monadic Contexts](#monadic-contexts)
  - [Context Definitions](#context-definitions)
  - [Context Lifting](#context-lifting)
  - [Broken Values](#broken-values)
- [Dynamic](#dynamic)
  - [The Enso Boundary](#the-enso-boundary)
- [Type Checking and Inference](#type-checking-and-inference)
  - [Maximal Inference Power](#maximal-inference-power)
  - [Row Polymorphism and Inference](#row-polymorphism-and-inference)
- [Dependency and Enso](#dependency-and-enso)
  - [Proving Program Properties](#proving-program-properties)
  - [Automating the Proof Burden](#automating-the-proof-burden)
- [References](#references)

<!-- /MarkdownTOC -->

## Goals for the Type System
In our design for Enso, we firmly believe that the type system should be able to
aid the user in writing correct programs, far and above anything else. However,
with so much of our targeted user-base being significantly non-technical, it
needs to be as unobtrusive as possible.

- Inference should have maximal power. We want users to be _forced_ to write
  type annotations in as few situations as possible. This means that, ideally,
  we are able to infer higher-rank types and make impredicative instantiations
  without annotations.
- Error messages must be informative. This is usually down to the details of the
  implementation, but we'd rather not employ an algorithm that discards
  contextual information that would be useful for crafting useful errors.
- Dependent types are a big boon for safety in programming languages, allowing
  the users that _want to_ to express additional properties of their programs
  in their types. We would like to introduce dependent types in future, but
  would welcome insight on whether it is perhaps easier to do so from the get
  go. If doing so, we would prefer to go with `Type : Type`.
- Our aim is to create a powerful type system to support development, rather
  than turn Enso into a research language. We want users to be able to add
  safety gradually.


## The Type Hierarchy
Enso is a statically typed language

### Atoms
Atoms _as the values_
Can we unify this with records?

#### Unsafe Atom Field Mutation

### Typesets

### Type Operators
`| & \ , : ~ <:`

### Records and Structural Typing
Separate records and typesets.

#### Anonymous Records

### Interfaces
Structural matching

#### Interface Generality

#### Special Interfaces

##### Wrapper
##### Convertible
##### Destruct

### Projections
Record projections and lenses.

#### Special Fields



## Function Types

### Structural Type Shorthand

### Function Composition



## Access Modificatiom



## Pattern Matching
Atoms, records and typesets.



## Dynamic Dispatch

### Multiple Dispatch



## Modules

### Scoping and Imports



## Monadic Contexts

### Context Definitions

### Context Lifting

### Broken Values



## Dynamic

### The Enso Boundary



## Type Checking and Inference

### Maximal Inference Power

### Row Polymorphism and Inference



## Dependency and Enso
Enso is a [dependently typed](https://en.wikipedia.org/wiki/Dependent_type)
programming language. This means that types are first-class values in the 
language, and hence can be manipulated and computed upon just like any other
value. 

### Proving Program Properties

### Automating the Proof Burden
Hybrid proof approach a la fstar.

## References
The design of the type-system described in this document is based on prior work
by others in the PL design community. The (probably) complete list of references
is as below.

#### Rows
- [Abstracting Extensible Data Types](http://ittc.ku.edu/~garrett/pubs/morris-popl2019-rows.pdf)

#### Maximum Inference Power
- [A Theory of Qualified Types](https://github.com/sdiehl/papers/blob/master/A_Theory_Of_Qualified_Types.pdf)
- [Boxy Type-Inference for Higher-Rank Types and Impredicativity](https://www.microsoft.com/en-us/research/publication/boxy-type-inference-for-higher-rank-types-and-impredicativity/)
- [Complete and Easy Bidirectional Typechecking for Higher-Rank Polymorphism](https://www.cl.cam.ac.uk/~nk480/bidir.pdf)
- [Flexible Types: Robust Type Inference for First-class Polymorphism](https://www.microsoft.com/en-us/research/publication/flexible-types-robust-type-inference-for-first-class-polymorphism/)
- [FPH: First-Class Polymorphism for Haskell](https://www.microsoft.com/en-us/research/publication/fph-first-class-polymorphism-for-haskell/)
- [MLF: Raising ML to the Power of System-F](http://gallium.inria.fr/~remy/work/mlf/icfp.pdf)
- [Practical Type Inference for Arbitrary-Rank Types](https://www.microsoft.com/en-us/research/publication/practical-type-inference-for-arbitrary-rank-types/)
- [QML: Explicit, First-Class Polymorphism for ML](https://www.microsoft.com/en-us/research/wp-content/uploads/2009/09/QML-Explicit-First-Class-Polymorphism-for-ML.pdf)
- [Wobbly Types: Type Inference for GADTs](https://www.microsoft.com/en-us/research/publication/wobbly-types-type-inference-for-generalised-algebraic-data-types/)

#### Dependent Types
- [Dependent Types in Haskell: Theory and Practice](https://cs.brynmawr.edu/~rae/papers/2016/thesis/eisenberg-thesis.pdf)
- [Higher-Order Type-Level Programming in Haskell](https://www.microsoft.com/en-us/research/uploads/prod/2019/03/ho-haskell-5c8bb4918a4de.pdf)
- [Practical Erasure in Dependently-Typed Languages](https://eb.host.cs.st-andrews.ac.uk/drafts/dtp-erasure-draft.pdf)
- [Syntax and Semantics of Quantitative Type Theory](https://bentnib.org/quantitative-type-theory.pdf)

#### Monadic Contexts
- [Supermonads](http://eprints.nottingham.ac.uk/36156/1/paper.pdf)

#### Types and Performance
- [Levity Polymorphism](https://cs.brynmawr.edu/~rae/papers/2017/levity/levity-extended.pdf)
- [Partial Type-Constructors](https://cs.brynmawr.edu/~rae/papers/2019/partialdata/partialdata.pdf)
