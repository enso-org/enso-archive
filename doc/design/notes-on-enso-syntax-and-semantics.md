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
- [Atoms](#atoms)
  - [Unsafe Atom Field Mutation](#unsafe-atom-field-mutation)
- [Typesets](#typesets)
  - [The Type Hierarchy](#the-type-hierarchy)
  - [Typesets and Smart Constructors](#typesets-and-smart-constructors)
  - [Type and Interface Definitions](#type-and-interface-definitions)
  - [Anonymous Typesets](#anonymous-typesets)
  - [Typeset Projections \(Lenses\)](#typeset-projections-lenses)
- [Interfaces](#interfaces)
  - [Special Interfaces](#special-interfaces)
- [Pattern Matching](#pattern-matching)
- [Visibility and Access Modifiers](#visibility-and-access-modifiers)
- [Multiple Dispatch](#multiple-dispatch)
  - [Overlappable Functions](#overlappable-functions)
- [Modules](#modules)
  - [Thinking About Modules](#thinking-about-modules)
  - [Imports](#imports)
- [Dynamic](#dynamic)
  - [The Enso Boundary](#the-enso-boundary)
  - [An Insufficient Design For Dynamic](#an-insufficient-design-for-dynamic)

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

## Atoms
Atoms are the fundamental building blocks of types in Enso. Where broader types
are sets of values, Atoms are 'atomic' and have unique identity. They are the
nominally-typed subset of Enso's types, that can be built up into a broader
notion of structural, set-based typing. All kinds of types in Enso can be
defined in arbitrary scopes, and need not be defined on the top-level.
In Enso, Atoms are product types with named fields, where each field has a
distinct and un-constrained type. Atoms are defined by the `type` keyword,
which can be statically disambiguated from the standard usage of `type`.

Some examples of atoms are as follows, with a usage example:

```ruby
type Nothing
type Just value
atom Vec3 x y z
atom Vec2 x y

v = V3 1 2 3 : V3 1 2 3 : V3 Int Int Int : V3 Any Any Any : Any
```

The key notion of an atom is that it has _unique identity_. No atom can unify
with any other atom, even if they have the same fields with the same names. To
put this another way, an atom is _purely_ nominally typed.

### Unsafe Atom Field Mutation
Often for performance, we need the ability to mutate atom fields deep in the
implementation:

- `unsafe` blocks?
- `use unsafe`, `use private` (expression)
- In-place field mutation: `setField : Name -> Any -> Nothing`
- Affine/linear typing in the future to make these things not unsafe

## Typesets
More complex types in Enso are known as typesets. All of these types are
_structural_. This means that unification on these types takes place based upon
the _structure_ of the type (otherwise known as its 'shape').

Two typesets `A` and `B` can be defined to be equal as follows, where equality
means that the sets represent the same type.

1.  `A` and `B` contain the same set of _labels._ A label is a _name_ given to
    a type.
2.  For each label in `A` and `B`, the type of the label in `A` must be equal to
    the type of the same label in `B`:

    1.  Atoms are only equal to themselves, accounting for application.
    2.  Types are equal to themselves recursively by the above.

Two typesets `A` and `B` also have a _subsumption_ relationship `<:` defined
between them. `A` is said to be subsumed by `B` (`A <: B`) if the following
hold:

1.  `A` contains a subset of the labels in `B`.
2.  For each label in `A`, its type is a subset of the type of the same label in
    `B` (or equal to it):

    1.  An atom may not subsume another atom.
    2.  A type is subsumed by another time recursively by the above, accounting
        for defaults (e.g. `f : a -> b = x -> c` will unify with `f : a -> c`)
    3.  The subsumption judgement correctly accounts for covariance and
        contravariance with function terms.
    4.  The subsumption judgement correctly accounts for constraints on types.
    5.  A typeset that defines only fields may be subsumed by an atom unless it
        names a specific atom.

As typesets are matched structurally, a typeset definition serves as both a type
and an interface.

> The actionables for this section are:
>
> - Determine if we want to support multiple dispatch in the future, as this has
>   impacts on whether the underlying theory for typesets needs to support
>   overidden labels (e.g. two definitions for `foo` with different types).

### The Type Hierarchy
These typesets are organised into a _modular lattice_ of types, such that it is
clear which typesets are subsumed by a given typeset. There are a few key
things to note about this hierarchy:

- The 'top' type, that contains all typesets and atoms is `Any`.
- The 'bottom' type, that contains no typesets or atoms is `Nothing`.

> The actionables for this section are:
>
> - How do we fit atoms into this?
> - How do we represent IORefs/MVars as types?

### Typesets and Smart Constructors
Enso defines the following operations on typesets that can be used to combine
and manipulate them:

- **Union:** `|` (e.g. `Maybe a = Nothing | Just a`)
- **Intersection:** `&` (e.g. `Person = HasName & HasPhone`)
- **Subtraction:** `\` (e.g. `NegativeNumber = Int \ Nat \ 0`)
- **Concatenation:** `,` This creates sum types.

Bijective applications of these constructors are able to be used for pattern
matching. Initially we only plan to support simple bijection detection, but
this may expand in the future. An example that would not be supported initially
follows:

```ruby
type V3 x y z

zV2 x y = V3 x y 0

test = match _ of
  ZV2 x y  -> ...
  V3 x y z -> ...

```

> The actionables for this section are as follows:
> 
> - Work out how these combine with standard record theories, what do these mean
>   when dealing with a record with n fields?
> - How do we _really_ work with atoms given you need the concept of an
>   'anonymous atom'.

How does this combine with the potential need for a `,` record concat operator?

### Type and Interface Definitions
Typesets are defined using a unified `type` keyword / macro that works as
follows:

1.  If you provide the keyword with only a name and fields, it generates an
    atom:

    ```ruby
    type Just value
    ```

2.  If provided with a body containing atom definitions, it defines a smart
    constructor that defines the atoms and related functions by returning a
    typeset. For example:

    ```ruby
    type Maybe a
      use Nothing
      type Just (value : a)

      isJust = case self of
        Nothing -> False
        Just _ -> True

      nothing = not isJust
    ```

    Translates to:

    ```ruby
    maybe a =
      atom Just value
      { (Nothing | Just a)
        & isJust: IsJust = isJust
        & nothing : Nothing = nothing }

    isJust : Maybe a -> Bool
    isJust self = case self of
      Nothing -> False
      Just _ -> True

    nothing : Maybe a -> Bool
    nothing = not isJust
    ```

3.  Though all types are interfaces, interfaces that define specific atoms are
    often not particularly useful. To this end, you can use the `type` keyword
    _without_ defining any atoms in the body to create a more-useful interface
    definition.

    ```ruby
    type HasName
      name: String

    printName: t:HasName -> Nothing
    printName t = t.name

    type Human name
    name (self: Int) = "IntegerName"

    main =
        printName (Human "Zenek")
        printName 7
    ```

4.  Explicit constraints can be put on the `self` type in a typeset, should it
    exist. This uses standard type-ascription syntax.

    ```ruby
    type Semigroup
      <> : self -> self

    type Monoid
      self : Semigroup
      use Nothing
    ```

Under the hood, typesets are based on GADT theory, and typing evidence is
_always_ discharged through pattern matching. This feature will not, however,
be available until we have a type-checker.

`type Foo (a : t)` is syntax only allowable inside a type definition.

### Anonymous Typesets
Given that typesets are unified structurally, it can often be very useful to
define interfaces as typesets in an ad-hoc manner while defining a function. To
this end we provide the `{}` syntax.

This syntax declares the members of the typeset explicitly. Member definitions
are of the form `name : type = default`, where the following rules apply:

- If a default is explicitly requested it becomes part of the subsumption
  judgement.
- If the type of a name is omitted it is inferred from a default if present, and
  is otherwise inferred to be `Any`.
- If only the type is present, auto-generated labels are provided using the
  index of the member in the typeset (e.g `{ Int & String }.1` has type `Int`).

The reason that the version with only the type is useful is that it means that
anonymous typesets subsume the uses for tuples.

### Typeset Projections (Lenses)
In order to work efficiently with typesets, we need the ability to seamlessly
access and modify (immutably) their properties. In the context of our type
theory, this functionality is known as a _projection_, in that it projects a
value from (or into) a typeset.

Coming from Haskell, we are well-versed with the flexibility of lenses, and
more generally _optics_. To that end, we base our projection operations on
standard theories of optics. While we _do_ need to formalise this, for now we
provide examples of the expected basic usage. This only covers lenses, while in
the future we will likely want prisms and other more-advanced optics.

Lenses are generated for both atom fields and records.

> Actionables for this section:
>
> - Work out whether standard optics theory with custom types is sufficient for
>   us. We may want to support side effects.
> - Determine how much of the above we can support without a type-checker. There
>   are likely to be a lot of edge-cases, so it's important that we make sure we
>   know how to get as much of it working as possible.
> - How (if at all) do lenses differ for atoms and typesets?

#### Special Fields
We also define special projections from typesets:

- `index`: The expression `t.n`, where `n` is of type `Number` is translated to
  `t.index n`.
- `field`: The expression `t.s` where `s` is of type `Text` is translated to
  `t.fieldByName s`.

## Interfaces
Interface implementation in Enso is structural. Any type that subsumes a given
interface is considered to match the type.

As typesets are matched structurally, types need not _explicitly_ implement
interfaces (a form of static duck-typing). However, when defining a new type, we
may _want_ to explicitly say that it defines an interface. This has two main
benefits:

- We can include default implementations from the interface definition.
- We can provide better diagnostics in the compiler as we can point to the
  definition site instead of the use site.

```ruby
type HasName
    name: String
    name = "unnamed"

type Vector a
    self: HasName
    V2 x:a y:a
    V3 x:a y:a z:a

name (self:Int) = "IntName"

greet (t:HasName) = print 'Hi, my name is `t.name`'

main =
    greet (V3 1 2 3)
    greet 8
```

### Special Interfaces
In order to aid usability we include a few special interfaces in the standard
library that have special support in the compiler.

#### Wrapper
In a language where composition is queen and inheritance doesn't exist there
needs to be an easy way for users to compose typesets without having to define
wrappers for the contained types. This is a big usability bonus for Enso.

```ruby
type Wrapper
    wrapped   : (lens s t a b) self.unwrapped
    unwrapped : t
    unwrapped = t # Default implementation based on inferred type.
```

`Wrapper` is an interface implemented implicitly for all typesets, and boils
down to delegating to the contained members if a given label is not found on
the top typeset. This delegation only occurs on the self type.

A usage example is as follows:

```ruby
type HasName a
    self:Wrapper # The field 'unwrapped' uses default implementation.
    type Cons
        name    : String
        wrapped : a

test i:Int = i + 1

main =
    p1 = HasName.Cons "Zenek" 7
    p2 = p1 + 1     # OK, uses `wrapped` lens.
    print $ p2.name # OK
    print $ test p1 # OK, uses `wrapped` lens.
```

#### Convertible
Also useful in a language for data science is the ability to have the compiler
help you by automatically converting between types that have sensible coercions
between them. This interface is known as `Convertible`, and defines a one-way
conversion between a type `a` and a type `b`.

```ruby
Convertible t
  to : t -> t
```

There are a few key points of this design that must be considered carefully:

- This interface _only_ applies when implemented explicitly by the type. The
  compiler will not automatically generate implementations for `Convertible t`.
- It is very important for the conversions to be inserted automatically in the
  correct place. If a conversion is required in the body of a block, the point
  at which the conversion takes place should propagate outwards as far as
  possible. This is very important for proper definition of controls in the GUI.
- `Convertible t` can also be implemented by a function that accepts arguments
  _iff_ all of the arguments have default values associated with them. In such
  a case, the GUI should display conversion controls with a checkbox that, when
  checked, can be converted to an explicit conversion call.
- We will need some limited mechanism for doing this even without type inference
  as it forms the backbone of good API design for the graphical interface. This
  is because polymorphic functions are much harder to support with graphical
  controls.

An example use-case is as follows:

```ruby
type Vector a
    type V3 x:a y:a z:a

    self : Convertible String
    to = 'V3 `self.x` `self.y` `self.z`'

    self : Convertible (a: Semigroup)
    to = self.x + self.y + self.z

test: Int -> String
test i = print 'I got the magic value `i`!'

main =
    test 7    # OK!
    test 'hi' # FAIL: type mismatch, no definition `Convertible Int` for String.
    test (Vector.V3 1 2 3) # OK, prints 'I got the magic value 6'.
```

 WRITE THIS
In order to implement some version of this now, we have two potential solutions
before we have a typechecker:

- Only inserting conversions on the `this` argument.
- Need the ability to expose the conversion, so you can see the additional
  (defaulted) parameters in the GUI.
- The conversion types.

#### Destruct
While it is a common idiom in functional languages to implement the `bracket`
pattern for acquiring and releasing resources, but this isn't such a good fit
for a language where many users aren't going to be used to thinking about
resources.

Instead, we have the final of our special traits, called `Destruct`, defined as
follows:

```ruby
type Destruct
  destroy : This -> Nothing
```

For those coming from Rust, C++, or another language which uses the RAII
principle, this should be a familiar sight. It works as follows:

1.  All types automatically provide a trivial implementation for `destroy`. This
    will recursively call the destructors of the type's members.
2.  A type definition may provide a non-default implementation for `destroy`,
    such that it implements more complex behaviour.
3.  When a type goes out of scope, its `destroy` method is called, allowing it
    to clean up any resources that it owns.

Initially, going out of scope will be defined as the point at which the
instance is garbage collected, while later, once we are able to perform more
sophisticated analysis, it will instead be defined as the point at which the
instance's lexical lifetime ends.

It should be noted, however, that a type that implements an explicit `destroy`
method should still implement explicit methods for resource handling as lexical
lifetimes are not always sufficient (e.g. a socket that you may want to close
and re-open in the same block).

> The actionables for this section are:
>
> - Determine how this interacts with copying and moving.

## Pattern Matching
Pattern matching in Enso works similarly to as you would expect in various other
functional languages. Typing information is _always_ refined in the branches of
a case expression, which interacts well with dependent typing and type-term
unification. There are a few main ways you can pattern match:

1.  **Positional Matching:** Matching on the scrutinee by structure. This works
    both for atoms and typesets (for typesets it is a subsumption judgement).

    ```ruby
    type Vector a
      V2 x:a y:a
      V3 x:a y:a z:a

    v = Vector.V3 x y z

    case v of
      Vector.V3 x y z -> print x
    ```

2.  **Type Matching:** Matching purely by the types involved, and not matching
    on structure.

    ```ruby
    case v of
      Vector.V3 -> print v.x
    ```

3.  **Name Matching on Labels:** Matching on the labels defined within a type
    for both atoms and typesets, with renaming.

    ```ruby
    case v of
      Vector.V3 {x y} -> print x
      {x}             -> print x
    ```

4.  **Naming Scrutinees in Branches:** Ascribing a name of a scrutinee is done
    using the standard typing judgement. This works due to the type-term
    unification present in Enso.

    ```ruby
    case _ of
      v : Vector.V3 -> print v,x
    ```

> The actionables for this section :
>
> - How do we type pattern matching?

## Visibility and Access Modifiers
While we don't usually like making things private in a programming language, it
sometimes the case that it is necessary to indicate that certain fields should
not be touched (as this might break invariants and such like). To this end, we
propose an explicit mechanism for access modification that works as follows:

- We provide explicit access modifiers that, at the definition site, start an
  indented block. These are `private` and `unsafe`.
- All members in the block have the access modifier attributed to them.
- By default, accessing any member under an access modifier will be an error.
- To use members under an access modifier, you use the syntax `use <mod>`, where
  `<mod>` is a modifier. This syntax 'takes' an expression, including blocks,
  within which the user may access members qualified by the modifier `<mod>`.

While `private` works as you might expect, coming from other languages, the
`unsafe` annotation has additional restrictions:

- It must be explicitly imported from `Std.Unsafe`.
- When you use `unsafe`, you must write a documentation comment on its usage
  that contains a section `Safety` that describes why this usage of unsafe is
  valid.

> The actionables for this section are:
> 
> - How do we type this?

# Dynamic Dispatch
Enso is a language that supports pervasive dynamic dispatch. This is a big boon
for usability, as users can write very flexible code that still plays nicely
with the GUI.

The current implementation of Enso supports single dispatch (dispatch purely on
the type of `self`), but there are broader visions afoot for the final
implementation of dynamic dispatch in Enso.

- Account for multidispatch, but no decision has been made.
- No transitive import of dispatch candidates.

> The actionables for this section include:
>
> - Determining whether we want to support proper multiple dispatch in the
>   future. This is important to know as it has implications for the type
>   system, and the design of the dispatch algorithm.
> - The definition of specificity for dispatch candidates (including how it
>   interacts with the subsumption relationship on typesets and the ordering of
>   arguments).

## Multiple Dispatch
It is an open question as to whether we want to support proper multiple dispatch
in Enso. Multiple dispatch refers to the dynamic dispatch target being
determined based not only on the type of the `self` argument, but the types of
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

If we want to support equivalents to multi-parameter type-classes in Haskell,
then we need to support multiple dispatch globally, as our method dispatch is
not opt-in (unlike typeclasses in Haskell).

Leave this for later.

### Overlappable Functions
Overlappable functions is a proposal for obtaining multiple-dispatch-style
behaviour that dispatches functions based on a notion of specificity that is
a little more specific than general multiple-dispatch. It considers only local
bindings as candidates, and is hence not part of the global dispatch mechanism.

Consider the following example:

```ruby
foldFields: (f: field -> out) -> struct -> List out
foldFields f struct =
  go: List out -> t (a: field) -> List out
  go out (t a) = go (f a, out) t

  go: List out -> t -> List out
  go out _ = out

  go Nothing struct
```

It defines a set of inner functions that have types that are both _explicit_ and
_overlapping_. We then use a notion of specificity for these functions to
determine which to dispatch to.

Please note that the function `f` is not typed as `f : Any -> Any` because then
this would not work correctly. We are allowed to provide any valid sub-type
(see `<:` in the section on [typesets](#typesets) above) of a given type to a
function (while accounting for covariance and contravariance). In this example,
we want to provide a function that traverses all of the arguments and also want
the user to be able to pass an `f : Int -> String`, the type system needs to
verify that every field is of type `Int`.

Please note that there is a potential syntactic ambiguity here: `t a = v`. This
could either be interpreted as a function definition or structural pattern
matching. Fortunately, the latter is not needed often, and will only be needed
by advanced users. Instead, we require that structural pattern matching use
parentheses around the match `(t a) = v`.

> The actionables for this section are:
>
> - Do we really need this feature? Isn't it subsumed by the more useful notion
>   of multiple dispatch?
> - How does the above notion of structural pattern matching work in relation to
>   structural pattern matching for typesets?

## Modules
It is important in Enso for modules to be first-class language entities that can
be computed upon at runtime. However, we do not yet have a concrete design for
how to handle this. There are two main ways to do this:

- Unify the concept of modules with the concept of typesets, with some file
  scope magic to make this usable.
- Make modules their own entity (non-first-class as it's not needed - I think).

### Thinking About Modules

```ruby
## X.enso
Number.succ = x -> x + 1

type Maybe a
  type Just a
  Nothing

  is_just : Maybe a -> Bool

## Main.enso

main =
  t1 = X.maybe Int
  t2 = Maybe Int

  # t1 ~ t2

  a = X.succ 5
  b = succ X 5

  # a == b
```

- `here` as an alias for the current module

### Imports

Import takes a module path

```ruby
import Std.Maybe
import Std.Maybe as M
import Std.Maybe only is_just nothing
import Std.Maybe hiding is_just nothing if_then_else # space sep list
```

Re-exports and transitivity:

- Transitive imports no longer exist.
- So we need the ability to re-export things:

export may introduce the module it exports into scope

```ruby
export X # everything in X
export X as Y
export X only is_just nothing
export X hiding is_just nothing if_then_else
```

- Exports paste in place.
- Exports that create name clashes must be resolved.

Should we treat atoms as methods in this model???

- Atoms and methods obey the same qualification rules (capital vs lower).

Module/member naming conflicts:

- Calling a function with an uppercase letter instantiates all of its arguments
  to free type variables.
- Modules are not first class.
- Where the module name clashes with a member contained in the module, the
  module is preferred. If you need the module you must import it qualified under
  another name.

# Broken Values
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

# Function Composition
Enso introduces a function composition operator which composes functions after
all arguments have been applied. This operator is `>>` (and its backwards cousin
`<<`). It takes a function `f` with `n` arguments, and a function `g` with `m`
arguments, and the result consumes `n` arguments, applies them to `f`, and then
applies the result of that plus any additional arguments to `g`.

```ruby
computeCoeff = (+) >> (*5)

doThing = (+) >> (*)
```

In addition, we have the standard function composition operator `.`, and its
backwards chaining cousin `<|`.

> The actionables from this section are:
>
> - Examples for the more advanced use-cases of `>>` to decide if the type
>   complexity is worth it.

## Dynamic
As Enso can seamlessly interoperate with other programming languages, we need a
principled way of handling dynamic types that we don't really know anything
about. This mechanism needs:

- A way to record what properties we _expect_ from the dynamic.
- A way to turn a dynamic into a well-principled type-system member without
  having the dynamics pollute the whole type system. This may involve a 'trust
  me' function, and potentially dynamicness-polymorphic types.
- A way to understand as much as possible about what a dynamic _does_ provide.
- A way to try and refine information about dynamics where possible.

> The actionables for this section are:
>
> - Work out how to do dynamic properly, keeping in mind that in a dynamic value
>   could self-modify underneath us.

### The Enso Boundary
Fortunately, we can at least avoid foreign languages modifying memory owned by
the Enso interpreter. As part of the interop library, Graal lets us mark memory
as read-only. This means that the majority of data passed out (from a functional
language like Enso) is not at risk. However, if we _do_ allow data to be worked
with mutably,

### An Insufficient Design For Dynamic
The following text contains a design for dynamic that doesn't actually account
for all the necessary use-cases in the real world. It is recorded here so that
it may inform the eventual design.

- `Dynamic a b` is a type where the `a` is used to provide a specification of
  the structure expected from the dynamic type, and the `b` is a specification
  of the _verified_ properties of the dynamic type.

  ```
  x : Dynamic { foo : Int -> String & prop : Int } {}
  ```

  This structure _need not be complete. Indeed, it will be fairly common to get
  values of type `Dynamic` about which we know nothing: `Dynamic {} {}`. As
  dynamic values are used, we can refine information about these values via
  pattern matching (moving properties from `a` into `b`), or adding properties
  to both if they hold.

- `Dynamic` has a constraint on the types of `a` and `b` such that `b` <: `a`
  where `<:` is assumed to be a subsumption relationship.

- The key recognition is that if a property is not contained in the `b`
  structure of a dynamic, it will be a type error to use that property.

- A value of type `Dynamic a b` can be converted to a value of type
  `Dynamic a a` by calling a method
  `assertValid : Dynamic a b -> Maybe (Dynamic a a)` on it that verifies the
  expected properties of the dynamic.

- A value of type `Dynamic a b` can be converted to a value of type `b` by
  calling `valid : Dynamic a b -> b`. This allows you to take the _verified_
  properties of your dynamic value and hoist it all into the type system safely.

- Users should also be able to `unsafeAssertValid` or something equivalent,
  which is an unsafe operation that treats a value of type `Dynamic a b` as
  having type `Dynamic a a`. Unlike `assertValid` above, this method performs
  _no_ verification.

Now, it can sometimes be useful to treat normal types as `Dynamic` values. For
this we have the following:

- A method on all structural types `asDynamic`. For a structural type t, it
  produces a value of type `Dynamic t t`.

- `Dynamic` provides a method that allows for defining functions and properties
  on dynamic values. This method is _safe_ such that for a `Dynamic a b` it
  extends the structural types `a` and `b` with the new properties:

  ```
  define : Dynamic a b -> prop : t -> Dynamic {a & prop : t} {b & prop : t}
  ```

The idea here is to use the two type variables to allow the user to understand
both what they _think_ the dynamic should provide, but also allow the type
system to track what the dynamic _does_ provide at any given moment.

This doesn't work in the face of types that can self-modify, meaning that there
is no performant way to work with dynamics short of unsafe assumptions about
them.
