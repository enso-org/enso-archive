<!-- MarkdownTOC levels="1,2" autolink="true" -->

- [Atoms](#atoms)
- [Typesets](#typesets)
- [Interfaces](#interfaces)
- [Pattern Matching](#pattern-matching)
- [Visibility and Access Modifiers](#visibility-and-access-modifiers)
- [Dynamic Dispatch](#dynamic-dispatch)
- [Multiple Dispatch](#multiple-dispatch)
- [Modules](#modules)
- [Broken Values](#broken-values)
- [Function Composition](#function-composition)
- [Dynamic](#dynamic)
- [Principles for Enso's Type System](#principles-for-ensos-type-system)
- [Structural Type Shorthand](#structural-type-shorthand)
- [Interface Generality](#interface-generality)
- [Subtyping and User-Facing Type Definitions](#subtyping-and-user-facing-type-definitions)
- [Row Polymorphism and Inference](#row-polymorphism-and-inference)
- [Unresolved Questions](#unresolved-questions)
- [Dependency and Enso](#dependency-and-enso)

<!-- /MarkdownTOC -->

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

# Principles for Enso's Type System

- Types in Enso are functions on sets (constructors included), and are based on
  the theory of rows described in the "Abstracting Extensible Data Types" paper.

- All interface definitions must resolve to a 1:1 mapping between resolution
  type (e.g. `Text`) and result type (e.g. `Functor Char Char`), modulo type
  annotations.

- Scope lookup proceeds from function scope outwards, and the body is in scope
  for the signature. The signature is in scope for the body.

- There is no distinction between types and kinds. That means that all kinds
  (e.g. Constraint, Type, Representation) are Type (Type in Type).

- We want to provide the ability to explicitly quantify type variables, for all
  of: dependent, visible, required, and relevant (e.g. `forall`, `foreach`).

- It should be clear from a signature or pattern match in isolation which
  variables are implicitly quantified.

- Naming in Enso is case-insensitive, so to hoist a bare function to the type
  level, you are required to capitalise it. `foo` and `Foo` are the same thing
  in values, but when used in a type, the former will be inferred as a free var.

- Implicitly quantified variables are parsed as explicit but hidden.

- Applications of types are done via named arguments.

- All arguments are named.

- There is inbuilt support for inference of hole-fits.

- Default arguments are always applied when left unfilled. We provide a syntax
  `...` for preventing use of a default.

- Argument names at the type and term level must not shadow each other.

- There is no syntactic sugar for multiple argument functions.

- Contexts (e.g. IO) are represented using `T in IO`. Multiple contexts are
  combined as standard `(IO | State Int)`, and it is written the same in arg
  position. Monadic Contexts.

- Types define namespaces.

- A more generic type can be written than will be inferred.

- Contexts may be omitted when writing types.

- Laziness may be omitted when writing types, but has explicit syntax.

- Computation defaults to strict.

- Laziness and Strictness is controlled by the type.

- Type definitions are, by default, desugared to open polymorphic rows.

- Type definitions that do not include data members, do not have generated
  constructors.

- Type definitions that do include data members are given autogenerated
  constructors.

- The desugaring of all type definitions will include default implementations
  and values.

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

- Tuples are strictly a less powerful case of rows.

- The type system requires support for impredicative types and higher-rank
  types with inference.

- Passing a lazy type to a function expecting a strict one (i.e. one that is not
  strictness-polymorphic) should force the computation.

- The typechecker should support interleaving of metaprogramming and
  type-checking for elab-style scripts.

- Supermonadic theory, as a strictly more-powerful theory over standard monads,
  allows trivial definitions in terms of standard monads with inferred trivial
  contexts. Enso's Monadic Contexts are based on this theory.

- The context inference algorithm should support a high-granularity (e.g. read,
  write, FFI, print, etc).

- The base theory of rows that underlies the typechecker must support projection
  of values based on arbitrary types.

- All of the above concepts are represented as operations over row types.

- Row projections are first-class citizens in the language. They are based on
  the projection mechanism described in "Abstracting Extensible Data Types".

- When a function has defaulted arguments, these arguments should be treated as
  filled for the purposes of matching types. This point is somewhat subsumed by
  ones above, but bears making explicit. `f : A -> B` should match any function
  of that type (accounting for defaulted arguments).

- The type-system will support wired in `Convertible` and `Coercible` instances.
  The former deals with runtime conversions between types, while the latter
  deals with zero-cost conversions between types of the same runtime
  representation. Both can be inserted by the compiler where necessary.

- Enso will make use of whatever advanced type-system features are at its
  disposal to improve safety and performance.

- Complex errors will be explained simply, even if this requires additional
  annotation from programmers who use the advanced features.

- In the context of dependent types, relevance inference will be performed.

- The Enso typechecker will integrate an aggressive proof rewrite engine to
  automate as much of the proof burden as possible.

- Inference is employed heavily, letting types span from invisible to fancy,
  with each level providing as many guarantees as is practicable.

- Enso _is not a proof system_, and its implementation of dependent types will
  reflect said fact, being biased towards more practical usage.

- The future implementation of dependent types into Enso will be based on RAE's
  thesis about dependent types in Haskell (particularly PICO and BAKE).

- Inference will propagate as far as possible, but under some circumstances it
  will require users to write types.

- Value-level names become part of the function interface.

    ```
    replicate : (n : Nat) -> a -> Vector n a
    replicate = num -> val -> ...

    # Alternatively
    replicate : Nat -> a -> Vector sz a
    replicate = sz -> val -> ...
    ```

  The issue here is that after an `=`, you want a bare name to be a _name_, and
  after a `:`, you want it to represent a type. In the above example, `a` is a
  free type variable (`forall a`), while `n` is a name.

- If it comes to a tension between typechecker speed and inference capability,
  Enso will err on the side of inference capability in order to promote ease of
  use. Speed will be increased by performing incremental type-checking where
  possible on subsequent changes.

- 'Categorical Typing' (e.g. the `1 <: Int`) relationship, is supported by two
  key realisations:
  + Atoms can be represented as elements of a row.
  + Labels can be the atom they project (e.g. `1 : 1`) in the context of
    polymorphic labels.
  Thereby, when `Int : (1:1, 2:2, 3:3, ...)`, we trivially have `12:12 <: Int`.
  We just need to ensure that the 'subtyping' rules for the primitives are
  wired-in. This still supports `Vector3D 1 2 3 <: Vector3D Int Int Int`. A
  close examination of how this works with functions is required.

- We do not intend to support duplicate row labels, and will use appropriate
  constraints on combination and projection to achieve this.

- Deallocation of resources will be performed by 'drop', which can be explicitly
  implemented by users if need be (a wired-in interface).

- Record syntax is `{}`.

- We do not want to support invisible arguments.

- Enso must support nested type definitions. These nested types are
  automatically labelled with their name (so constructors are `mkFoo`, rather
  than `Foo`). The nested type is constructed as part of the containing type.

- When writing a method, the type on which the method is defined must be the
  last argument.

- Every name uses the following syntax `name : type = value`, where either
  `type` or `value` can be omitted. This has additional sugar that allows users
  to write it as follows:

    ```
    name : type
    name = value
    ```

  This sugar is most likely to be seen for function definitions, but it works in
  all cases.

- Rows in Enso are open, and have polymorphic projections. A projection without
  a given type is assumed to be a label, but `{ (Foo : Type) : Int }` lets users
  use other types as projections. A row `{ myLabel : Int }` is syntax sugar for
  an explicit projection based on a label: `{ (myLabel : Label) : Int }`.

- Enso features built-in first-class lenses and prisms based on row projections,
  as described in "Abstracting Extensible Datatypes".

- Rows where no explicit labels are given are assumed to be tuples. This means
  that the row `{Int, Int, Char}` is syntax sugar for a row with autogenerated
  labels for projection: `{ 1 : Int, 2 : Int, 3 : Char }`. Names of the labels
  are TBC. In doing this, you enforce the expected 'ordering' constraint of a
  tuple through the names.

- Bare types, such as `Int` or `a : Int` are assumed to be rows with a single
  member. In the case where no name is given, a it is treated as a 1-tuple.

- We want the theory to have possible future support for GADTs.

- The operation `foo a b` desugars to `b.foo a` so as to construct a
  transformation applied between two types.

- We want error messages to be as informative as possible, and are willing to
  retain significant extra algorithmic state in the typechecker to ensure that
  they are. This means both _formatting_ and _useful information_.

- There is a formalisation of how this system lowers to System-FC as implemented
  in GHC Core.

- Constructors are treated specially for the purposes of pattern matching.

- The implementation supports `Dynamic`, a type that allows typechecked
  interactions with external data. The properties expected of a `Dynamic` value
  become part of the `Dynamic` type signature, so if you need to access a
  property `foo` on a value `a` that is dynamic, `a` has type
  `Dynamic { foo : T }`, and thereby represents a contract expected of the
  external data. Dynamic is a strict subtype of `Type`.

- The typechecker will work efficiently in the presence of defaulted arguments
  by lazily generating the possible permutations of a function's signature at
  its use sites. This can be made to interact favourably with unification, much
  like for prolog.
- Users need to explicitly run their contexts to provide the lifting.

- It is going to be important to retain as much information as possible in order
  to provide informative error messages. This means that the eventual algorithm
  is likely to combine techniques from both W and M (context-insensitive and
  context-sensitive respectively).

- Type errors need to track possible fixes in the available context.

- There is explicit support for constraints appearing at any point in a
  polymorphic type.

- Type equality in Enso is represented by both representational and structural
  equality. Never nominal equality. There is no inbuilt notion of nominal
  equality.

- The type-system includes a mechanism for reasoning about the runtime
  representation of types. It will allow the programmer to constrain an API
  based upon runtime representations. While this is described as a type-level
  mechanism, it only is insofar as kinds are also types. The kind of a type is
  an expression that contains the following information:
  + Levity: Types that are represented by a pointer can be both lifted (those
    that may contain 'bottom') and unlifted (those that cannot). Thunks are
    lifted.
  + Boxiness: Whether the type is boxed (represented by a pointer) or unboxed.
  + Representation: A description of the machine-level representation of the
    type.

  This is effectively represented by a set of data declarations as follows:

    ```hs
    data Levity
        = Lifted   -- Can contain bottom (is a lazy computation)
        | Unlifted -- Cannot contain bottom (has been forced or is strict)

    data Size = UInt64

    data MachineRep
        = FlatRep [MachineRep] -- For unboxed rows
        | UInt32
        | UInt64
        | SInt32
        | SInt64
        | Float32
        | Float64
        | ...

    data RuntimeRep
        = Boxed   Levity
        | Unboxed MachineRep

    data TYPE where TYPE :: RuntimeRep -> ? -- Wired in

    -- Where does `Constraint` come into this?
    ```

  Doing this allows programs to abstract over the representation of their types,
  and is very similar to the implementation described in the Levity Polymorphism
  paper. The one change we make is that `FlatRep` is recursive; with most of
  Enso's types able to be represented flat in memory. This means that the list
  `[MachineRep]` is able to account for any row of unboxed types.

  The return type of `TYPE` is still an open question. What does it mean for a
  dependently typed language to deal in unboxed types at runtime? Reference to
  the Levity Polymorphism paper will be required. We don't want the usage of
  this to rely on the JIT for code-generation, as it should operate in a static
  context as well.

  An example of where this is useful is the implementation of unboxed arrays,
  for which we want a flat in-memory layout with no indirections. Being able to
  parametrise the array type over the kind `forall k. RuntimeRep (Unboxed k)`,
  means that the type will only accept unboxed types.

- We want to support contexts on types such that instantiation can be guarded.
  For more information see the Partial Type-Constructors paper. If you have a
  type `type Num a => Foo a = ...`, then it should be a type error to
  instantiate `Foo a` where `Num a` doesn't hold. This allows a treatment of
  partial data. However, this isn't easily extensible across interfaces. Could
  we propagate the constraint to the constructors in a GADT-alike? Nevertheless,
  they act as well-formedness constraints on the type definition. This means
  that the desugaring for type definitions involves GADTs. The construction of
  a constrained type creates evidence that is discharged at the type's use site
  (pattern match or similar). This should be based on the reasoning in the
  Partial Data paper, and so all types should automatically be generalised with
  the 'well-formed type' constraints, where possible.

- Type constructors are special entities. Not to be confused with value
  constructors `Vector Int` vs. `mkVector 1`.

- The syntax is as follows:
  + Row Alternation: `|`
  + Row Subtraction: `\`
  + Row Concatenation: `&` or `,`

- We should be able to infer variants and records, but this behaviour can be
  overridden by explicit signatures.

- With regards to Monadic Contexts, `in` allows nesting of contexts, as opposed
  to composition of transformers. `=` 'peels off' the last layer of contexts.
  When composing contexts, we have `&` for composing transformers, and `|` for
  alternating them, similarly to the dual with rows.

- To provide more predictable inference in dependent contexts, this system will
  draw on the notion of _matchability polymorphism_ as outlined in the
  higher-order type-level programming paper. The key recognition to make,
  however is that where that paper was required to deal with
  backwards-compatibility concerns, we are not, and hence can generalise all
  definitions to be matchability polymorphic where appropriate.

- Implicit parameters (e.g. `{f : Type -> Type} -> f a -> f a` in Idris) are
  able to be declared as part of the signature context.

- As of yet it is undecided as to what form of type-checking and inference will
  be used. The idea is to match our goals against existing theory to inform
  implementation.
  + Boxy: Provides greater inference power for higher-rank types and
    impredicative instantiation. Integrates well with wobbly inference for GADTs
    and has a relatively simple metatheory.
  + Complete Bidirectional: An interesting theory with particular power to infer
    higher-rank types, but with no support for impredicative instantiation. Has
    some additional complexity through use of subtyping relationships. This is
    fully decidable, and subsumes standard Damas-Milner style inference.
  + Flexible Types/HML: Provides a translation from MLF to System-F, which may
    be useful during translation to GHC Core. Requires annotations only on
    polymorphic function parameters (e.g. `fn f = (f 1, f True)` would require
    annotation of `f :: forall a . a -> a`). However, in the context of this
    requirement, all other instantiations (including impredicative and
    higher-rank) can be inferred automatically. Could this be combined with
    decidable rank-2 inference to increase expressiveness?
  + FPH: Lifts restrictions on polymorphic instantiation through use of an
    internal theory based on MLF. Focuses on impredicativity, but provides some
    commentary on how to extend the theory with approaches to higher-rank
    inference. This is based upon the Boxy work, but has trade-offs with regards
    to typeability in absence of annotations.
  + MLF: A highly complex type theory with the ability to represent strictly
    more types than System-F (and its variants). There are various theories that
    allow for translation of these types to System-F. It has theory supporting
    qualified types (a separate paper), which are necessary for our type system.
    I worry that choice of MLF will expose greater complexity to users.
  + Practical: Provides a local inference-based foundation for propagation of
    higher-rank type annotations at the top level to reduce the annotation
    burden. We can likely use this to inform our design, but it is somewhat
    subsumed by the HML approach.
  + QML: A simple system supporting impredicative instantiation, but with a much
    higher annotation burden than others. The underlying specification of
    checking and inference is very clear, however, so there are still potential
    lessons to be learned.
  + Wobbly: Provides a unified foundation for treating all types as GADTs, and
    hence allowing for bounded recursive type definitions. It precisely
    describes where type-signatures are required in the presence of GADTs. Has
    some interesting insight with regards to polymorphic recursion.

  It should be noted that none of these theories explicitly address extension to
  dependent type theories, so doing so would be entirely on our plate.
  Furthermore any theory chosen must have support for qualified types (e.g.
  those with constraints, existentials).

  To this end, I don't know if it is possible to always transparently support
  eta expansion of functions without annotation.

  My _current_ recommendation is to base things on HML. This is for the
  following reasons:
  + It maximises inference power for both higher-rank and impredicative type
    instantiations.
  + It has a simple rule for where annotations are _required_.
  + While it requires them in more places than MLF, the notion of where to put
    an annotation is defined by the function's external type, not its body.
  + The inference mechanism is far simpler than MLF as it only makes use of
    flexible types rather than constrained types.
  + There is existing work for adding qualified types to HML.

- There is an integration of constraints with interfaces. An implementation of
  an interface may be _more specific_ than the interface definition itself,
  through use of GADTs.

- Interfaces in Enso are inherently multi-parameter, by virtue of specifying a
  structure as a row.

- In an ideal world, we would like to only require programmer-provided type
  annotations in the following circumstances:
  1. Polymorphic Recursion (technically a case of #2)
  2. Higher-Rank Function Parameters
  3. Constrained Data-Types (GADTs)

  In order to achieve this, the final design will employ techniques from both
  unification-based Damas-Milner inference techniques, and annotation
  propagation inspired by local type-inference techniques.

# Structural Type Shorthand
In Enso, we want to be able to write a type-signature that represents types in
terms of the operations that take place on the input values. A classical example
is `add`:

```
add : a -> b -> b + a
add = a -> b -> b + a
```

There are a few things to note about this signature from a design standpoint:

- `a` and `b` are not the same type. This may, at first glance, seem like a
  signature that can't work, but the return type, in combination with our
  integrated `Convertible` mechanism gives us the tools to make it work.
- The return type is `a + b`. This is a shorthand expression for a detailed
  desugaring. The desugaring provided below is what the typechecker would infer
  based on such a signature.

```
add : forall a b c d. ({+ : Convertible b c => a -> c -> d} <: a) => a -> b -> d
```

This may look fairly strange at first, but we can work through the process as
follows:

1. The expression `b + a` is syntactic sugar for a method call on a: `a.+ b`.
2. This means that there must be a `+` method on a that takes both an `a` and a
   `b`, with return-type unspecified as of yet: `+ : a -> b -> ?`
3. However, as `Convertible` is built into the language, we have to consider
   that for `a.+ b` to work, the `+` method can actually take any type to which
   `b` converts. This introduces the constraint `Convertible b c`, and we get
   `+ : a -> c -> ?`
4. The return type from a function need not be determined by its arguments, so
   hence in the general case we have to default to an unrestricted type variable
   giving `+ a -> c -> d`.
5. This method must exist on the type `a`, resulting in the constraint that the
   row `{+ : a -> c -> d} <: a` must conform to that interface.
6. We now know the return type of `a + b`, and can rewrite it in the signature
   as `d`.

Please note that `a <: b` (which can be flipped as `:>`) means that `a` is a row
that is a sub-row contained within the row `b`. The containment relation allows
for the possibility that `a == b`.

The inferred type for any function should, in general, match the given type
signature. Cases where this break down should only exist where the type
signature acts to constrain the function type further than would be inferred.

## Interface Generality
One of the larger pain-points in Haskell comes from the fact that typeclass
structure places restrictions on what types can become an instance of the class.
Enso, instead, works from a foundation of rows. This means that we can trivially
make use of associated types in interfaces to compute more general signatures.

Consider the following `Iterable` interface, which expresses a map operation in
a way that is not easy in Haskell, and that is more general than `Functor`.

```
# The `=` is used here for consistency with unified definitions of the form
# (name : type = val).

interface Iterable : (a : Type) -> Type =
    elemType : Type
    map : (elemType -> elemType) -> a -> a

# Needs to account for internal type transformations (map toString) for example

instance Iterable Text =
    elemType = Char
    map = ...
```

Checking such a type is still able to be done through standard qualified
typechecking algorithms. There are, however, a few things to keep in mind:

- For a type to conform to an interface, it is sufficient for its implementation
  of the interface methods to be _callable_ with the signature given in the
  interface. This means that an implementation can add additional function
  parameters as long as they are defaulted.
- Of course, in the case where these function parameters are _used_, the type is
  no longer conforming with the interface. This is a bit nasty, but unavoidable.

Even nicer is the fact that such an approach can be combined with partial data
(restricted instantiation) to allow definition of `Iterable` across sets.
Consider the following:

```
instance Iterable (Set a) =
    elemType = a
    map = ...
```

# Subtyping and User-Facing Type Definitions
As complex as the internal type language of Enso may need to be, we ideally want
to only present a limited set of concepts to the user for use in writing their
own types. Internally, we have the following relations between types:

- `:` - This gives a type to an expression (this expression can be a name, a
  program fragment, or any valid expression). A program expression `a : b` means
  that the expression `a` has the type given by `b`.
- `~` - This relation asserts structural equality between types. If `a ~ b`,
  then the two types have exactly the same structure. This can be represented by
  the containment relation: `a ~ b = a <: b && b <: a`.
- `<:` - This relation asserts that one type is wholly contained in another, and
  can be thought of as a form of structural subtyping. If `a <: b`, then the
  type `a` is structurally equal to or a subtype of `b`. In essence, this means

Please note that this may initially be a little confusing. What, then, does it
mean to have an expression like `(name : type = value)`. Simply enough, this
universal definition format says that 'the identifier given by `name` has type
given by `type`, and has value given by `value`.' This works globally, including
for functions, and for type definitions.

```
type MyExample : (a : Type) -> (b : Type) -> Type =
    # The body of the type goes here, as it is the VALUE of the type constructor
    # In a truly dependently typed language, a type constructor is just a
    # function on types, and the `type` keyword isn't even needed other than to
    # indicate automatic constructor generation.
```

Externally, however, it is a different story. We want users to not have to think
at such depth about their types to express what they want. This means that we do
not intend to require users to write expressions involving structural equality
or containment (though we may still expose these relations to allow simpler
programming with types).

- In reality, we want users to really only make use of `:` when defining their
  types.
- This means that we need to be very clear about what it means, especially in
  the face of recursive types.

Let's examine the major cases:

1. **Polymorphic Function Arguments:**
2. **Partial Data Types:**
3. **Qualified Types in Functions:**
4. **Variable Definitions:**

Is variance always valid in the ways we need?

# Row Polymorphism and Inference
The foundation of Enso's usability is based on a _structural_ type system. This
means that there is no concept of nominal equality. In Enso types are equal if
they have the same structure. Under such a system, the only reason that users
should _need_ to write types is to provide a type that the inference engine
cannot infer (though this may be more general, more specific, or for a case that
cannot be inferred).

From a philosophical standpoint, we want Enso's type system to infer sensible
types for 99% of expressions that users write, and allow the users to _refine_
these inferred types. This refinement process can involve:

- Giving a more general (more permissive) type to an expression.
- Constrain an expression by giving a less permissive type to it.
- Add additional safety and checking by introducing more complex type-system
  usage (e.g. dependency, partial data, etc).

It should be noted that, in this sense, Enso's structural type system has no
concept of a 'principle' (or most-general) type for an expression, at least not
in the sense of traditional System-F.

# Unresolved Questions

1. How to integrate row polymorphism with the inference story?

2. How to do dependent types?

5. Representing ADTs as rows (variants and records).

6. Auto-injectivity for Generalised inductive types (GADTS)? Are our type
   constructors _matchable_ (injective and generative)?

Points that need to be accounted for in the 'wishlist' design:

- Basic types
- Constrained types
- Inference
- Dependent Types
- Containment/subtyping
- Dynamic
- Exception handling
- Monadic Contexts
- Complexity tradeoff between execution and implementation

```
read : String -> Ty? -> Ty
```

Named state items? Parametrise the state over a type and get via names, which
are projections.

- State parametrised over a record giving both name and type
- Do we want to look things up based on type?
- `State.get name` where `name` is a row projection.

- Monadic contexts desugared as transformers.


- Initially treat the value _in_ the state as a dynamic.

- Dependent types at runtime compared with dynamics.

- Encoding of strictness in the type system.

IO, State, Exception (not ! error)

- Dependent types as constructing proofs through combining types. Combining
  types provides evidence which can be discharged to create a proof. A value can
  then only be constructed by discharging a proof.

# Dependency and Enso
The ability to evaluate arbitrary functions on the type level inherently makes
Enso a dependently typed language, as arbitrary values can appear in types.

- The initial implementation will provide this facilities, but no system for
  automating the proof steps (c.f. f-star), or interactive theorem proving.
- While this allows people to express safety guarantees in the type system, it
  is a natural consequence of Enso's design.
- The ability to run arbitrary code at the type level.



<!-- ====================================================================== -->

Eager evaluation semantics.

<!-- ====================================================================== -->

Enso is a statically typed language. It means that every variable is tagged with
an information about its possible values. Enso's type system bases on the idea
that each type is denoted by a set of values, called `constructors`. Formally,
this makes the type system a
[Modular Lattice](https://en.wikipedia.org/wiki/Modular_lattice). For an
example, the type `Nat` contains constructors `1, 2, 3, ...`, and is hence
denotable by a set of the possible values.

As a result, type checking doesn't work via _unification_ as one might expect if
they are familiar with other functional programming languages, but instead
checks if a given set of values is a valid substitution for another. There is,
of course, the empty set `Void`, and a set of all possible values `Any`.

Each value forms a set with a single member, the value itself. This notion is
supported by an enforced equivalence between value-level and type-level syntax,
as the compiler makes no distinction between the two. This means that it is
perfectly valid to type `7 : 7`. Because we can describe infinite number of sets
containing a particular value, every value in Enso has infinite number of types.
Taking in consideration the lucky number `7`, it is a `Natural` number,
`Integer` number, a `Number`, and also `Any` value at the same time! This
relation could be expressed as follow:

```haskell
7 : 7 : Natural : Integer : Number : Any : Any : ...
```

<!-- ====================================================================== -->

Enso allows providing explicit type information by using the colon operator. The
compiler considers type signatures as hints and is free to discard them if they
do not provide any new information. However, if the provided hint is incorrect,
an error is reported.

For example, the following code contains an explicit type signature for the `a`
variable. Although the provided type tells that `a` is either an integer number
or a text, the compiler knows its exact value and is free to use it instead of
the more general type. Thus, no error is reported when the value is incremented
in the next line.

```haskell
a = 17 : Int | Text
b = a + 1
print b
```

However, if the provided type contains more information than the currently
inferred one, both are merged together. Consider the following example for
reference.

```haskell
test : Int -> Int -> Int
test = a -> b ->
    c = a + b
    print c
    c
```

Without the explicit type signature, the inferred type would be very generic,
allowing the arguments to be of any type as long as it allows for adding the
values and printing them to the screen. The provided type is more specific, so
Enso would allow to provide this function only with integer numbers now.
However, the provided type does not mention the context of the computations. The
compiler knows that `print` uses the `IO` context, so considering the provided
hint, the final inferred type would be
`Int in c1 -> Int in c2 -> Int in IO | c1 | c2`.

TODO: The above information about contexts could be removed from here as it is
pretty advanced. We should just mention that explicit type signatures are hints
everywhere BUT function definitions and new type definitions, where they are
constraining possible values.

It's worth to note that the type operator is just a regular operator with a very
low precedence and it is defined in the standard library.

<!-- ====================================================================== -->

#### Data Types

#### Constructor Types

Constructors define the most primitive way to construct a type, it's where the
name comes from. Formally, they are
[product types](https://en.wikipedia.org/wiki/Product_type). Their fields are
always named and fully polymorphic (each field has a distinct polymorphic type).
Constructors are distinguishable. You are not allowed to pass a constructor to
a function accepting other constructor, even if their fields are named the same
way.

```haskell
type Vec3   x y z
type Point3 x y z

vec1 = Vec3   1 2 3 : Vec3   1 2 3 : Vec3   Int Int Int
pt1  = Point3 1 2 3 : Point3 1 2 3 : Point3 Int Int Int

test : Vec3 Int Int Int -> Int
test v = v.x + v.y + v.z

test pt1 -- Compile time error. Expected Vec3, got Point3.
```

#### Algebraic Data Types

Enso allows you to define new types by combining existing ones into so called
[algebraic data types](https://en.wikipedia.org/wiki/Algebraic_data_type). There
are several algebraic operations on types available:

- **Intersection**
  A type intersection combines multiple types into one type that has all the
  features combined. For example, `Serializable & Showable` describes values
  that provide mechanisms for both serialization and printing.

- **Difference**
  A type difference combines multiple types into one type that has all the
  features of the first type but not the features of the second one. For
  example, `Int \ Negative` describes all positive integer values or zero.

- **Union**
  A type union combines multiple types into one type that describes a value
  being of one of the types. For example, `Int | String` describes values that
  are either `Int` or `String`.

```haskell
type Just value
type Nothing
maybe a = just a | nothing

map : (a -> b) -> Maybe a -> Maybe b
map f = case of
    Just a  -> Just (f a)
    Nothing -> Nothing
```

#### Syntax sugar

Enso provides syntactic sugar for easy definition of algebraic data types and
related methods. You are always required to provide explicit name for all the
constructors and all its fields.

```haskell
type Maybe a
    Just value:a
    Nothing

    map : (a -> b) -> Maybe b
    map f = case this
        Just a  -> Just (f a)
        Nothing -> Nothing
```

Please note, that all functions defined in the type definition scope are
desugared to global functions operating on that type. However, all functions
defined as constructor fields are considered to be record fields. They can be
provided with a default implementation and their definition can be changed at
runtime.

#### To Be Described

```haskell
-- Difference between method and a function component
type Foo
    MkFoo
        function : Int -> self
        function = default implementation

    method : Int -> self
    method = implementation
```

#### Data Types as Values

```haskell
sum : a -> b -> a + b
sum = a -> b -> a + b

lessThan : a -> b -> a < b
lessThan = a -> b -> a < b

main =
    print $ sum 1 2             -- 3
    print $ sum Int Int         -- Int
    print $ lessThan 1 2        -- True
    print $ lessThan Int Int    -- Bool
    print $ lessThan 0 Int      -- Bool
    print $ lessThan -1 Natural -- True
```

Please note, that `lessThan -1 Natural` returns `True`, which is just more
specific than `Bool` because it holds true for every natural number.

#### Interfaces

- **TO BE DONE [WD - research]**

<!-- ====================================================================== -->

#### Refinement Types

#### Ordered Lists

Sometimes, it's desired to prove some structure behaviors, like the fact that a
list contains sorted values. Enso allows expressing such constraints in a simple
way. They are often called behavioral types, as they describe the behavior to be
checked. First, let's consider a simple List implementation and see how we can
create a refined type using the high level interface:

```haskell
type List elems
    Empty
    Cons
        head : elems
        tail : List elems

ordered = refined lst ->
    if lst is empty
        then true
        else lst.head < lst.tail.elems
          && isOrdered lst.tail
```

That's it! Now we can use it like this:

```haskell
lst1 = []      : Ordered List Int -- OK
lst1 = [1,2,3] : Ordered List Int -- OK
lst1 = [3,2,1] : Ordered List Int -- ERROR
```

#### Under the Hood

Let's understand how the above example works. First, let's implement it in an
inextendible way, just as a data type which cannot be used for other purpose:

```haskell
data OrderedList elems
    Empty
    Cons
        head : elems
        tail : OrderedList (elems & Refinement (> this.head))
```

The implementation is almost the same, however, the type of the `tail` is much
more interesting. It's an intersection of `elems` and a `Refinement` type. A
refinement type defines a set of values matching the provided requirement. Here,
values in `tail` have to be a subtype of `elems` and also have to be bigger than
the `head` element. Alternatively, you could express the type as:

```haskell
data OrderedList elems
    Empty
    Cons
        head : elems
        tail : OrderedList (t:elems & if t > this.head then t else Void)
```

In both cases, we are using functions applied with type sets. For example,
`this.head` may resolve to a specific negative number while `t` may resolve to
any natural one.

Let's extract the `isOrdered` function from the original example. The function
takes a list as an argument and checks if all of its elements are in an
ascending order. It's worth noting that Enso allows accessing the named type
variable parameters like `lst.tail.elems`. Moreover, let's define a helper
function `refine`:

```haskell
isOrdered : List elems -> Bool
isOrdered lst =
    if lst is Empty
        then true
        else lst.head < lst.tail.elems
          && isOrdered lst.tail

refine f = $ Refinement f
```

Having this function, we could now use it like:

```haskell
lst1 = []      : Refine IsOrdered (List Int) -- OK
lst1 = [1,2,3] : Refine IsOrdered (List Int) -- OK
lst1 = [3,2,1] : Refine IsOrdered (List Int) -- ERROR
```

We can now define an alias `ordered = refine isOrdered`, however it would have
to be used like `Ordered (List Int)`, but in the first example we've been using
it like `Ordered List Int`. It was possible because there is a very special
function defined in the standard library:

```haskell
applyToResult f tgt = case tgt of
    (_ -> _) -> applyToResult << tgt
    _        -> f tgt

refined  = applyToResult << refine
```

The `applyToResult` function is very simple, although, from the first sight it
may look strange. It just takes a function `f` and an argument and if the
argument was not a function, then it applies `f` to it. If the argument was a
function, it just skips it and does the same to the result of the function. Now,
we can define the `refined` function which we used in the beginning as:

```haskell
refined = applyToResult << refine
```

It can be used either as shown in the original example or on the result of the
type expression directly:

```haskell
ordered = refined isOrdered
lst1 = []      : Ordered (List Int) -- OK
lst1 = [1,2,3] : Ordered (List Int) -- OK
lst1 = [3,2,1] : Ordered (List Int) -- ERROR
```

#### Type Inference

Because every value belongs to infinite number of types, it's not always obvious
what type to infer by looking only at the variable definitions. The expression
`fib 10` could be typed as `55`, `Int` or `Any`, `Int`, to mention a few. The
way we type it depends on two factors:

- **The optimizations we want to perform**
  The performance implications are obvious. By computing the value during
  compilation, we do not have to compute it during runtime anymore. On the other
  side, compile time function evaluation is often costly, so such optimization
  opportunities should always be chosen carefully.

- **The information we need to prove correctness of the program**
  In a case we drop the results, like `print $ const 10 (fib 10)`, it's
  completely OK to stop the type checking process on assuming that the type of
  `fib 10` is just any type, or to be more precise, a `fib 10` itself. Its value
  is always discarded and we do not need any more information to prove that the
  type flow is correct. However, if the result of `fib 10` would be passed to a
  function accepting only numbers smaller than `100`, the value would have to be
  computed during compilation time.

<!-- ====================================================================== -->

#### Monadic Inference

Before evaluating a function, monads of all arguments are applied to host
function, so arguments are passed as `in Pure`. Why? Consider:

```haskell
foo a =
   if a == "hi" then print "hello"
   if a == "no" then print "why?"

main =
    foo $ read "test.txt"
```

We've got here `read : Text -> Text in IO ! IO.Error`, but when evaluating
`foo`, the `a` argument is assigned with `Text in Pure`, because `IO` was merged
into main before passing the argument. Otherwise, the file would be read twice
(!) in the body of foo.

Very rarely it is desirable to postpone the monad merging and just pass the
arguments in monads "as is". Example:

```haskell
main =
    a = ...
    if a then read "a.txt" else read "b.txt"
```

You don't want to read both files, that's why these monads sohuld not be
unpacked with `if_then_else`. Thats why its definition is

```haskell
if cond _then (ok in m) _else (fail in n) =
    case cond of
        True  -> ok
        False -> fail
```

If you don't provide the explicit `in m` and `in n`, the args are considered to
be `in Pure`

#### How `=` works

Consider:

```haskell
test =
    body
    a = f
    out
```

Assume:

```haskell
f : F in FM2 in FM1
```

Then:

```haskell
a    : F in FM2 in Pure
body : _ in BM
test : out in FM1 & BM
```

Basically `=` transforms right side to left side like
`(right : R in RM2 in RM1) -> (left : R in RM2 in Pure)`, and it merges `RM1`
with host monad.

<!-- ====================================================================== -->



#### The Dynamic Type

When calling a foreign python we get the result typed as `Dynamic`. Basically,
values typed as `Dynamic` work just like in Python. You can access their fields
/ methods by string, you can add or remove fields, and you always get the
`Dynamic` as result. Every operation on `Dynamic` results in `a ! DynamicError`.

Everything that is possible to express on the `Dynamic` type should be possible
to be expressed using normal data types (see the "Dynamic access" chapter
above).

There is an important change to how UCS works with dynamic types, namely, the
dot syntax always means the field access.

```haskell
num  = untypedNumberFromPythonCode
num2 = num + 1 -- : Dynamic ! DynamicError
num3 = num2 catch case
    DynamicError -> 0
-- num3 : Dynamic
num4 = num3 - 1 -- : Dynamic ! DynamicError

```

```haskell
obj.__model__ =
    { atom  : Text
    , dict  : Map Text Any
    , info  :
        { doc  : Text
        , name : Text
        , code : Text
        , loc  : Location
        }
    , arg  : -- used only when calling like a function
        { doc     : Text
        , default : Maybe Any
        }
    }
```

#### Dynamic access

Even typed data in Enso behaves like if it was fully dynamic. You can access the
field dictionary of each object and alter it. It's amazing for type level
programming, as you could be able to generate types by defining their
dictionaries during "module compilation time". To be described – how to do it –
type is just a named record, which is like a dictionary.

Basically, every property of an object (let them behave like classes, modules or
interfaces) should be accessible and extensible in such way.

```haskell
class Point a
    P3 x:a y:a z:a
        fnfield : this
        fnfield = P3 this.x this.x this.x

    length : a
    length = this.x^2 + this.y^2 + this.z^2 . sqrt

p1 = P3 1 2 3
print $ p1.fields            -- <Map Text Field>
f1 = p1.fields.get "fnfield" -- V3 a b c -> V3 a a a
print $ f1 p1                -- V3 1 1 1
p2 = p1.fields.set "fnfield" $ p -> V3 p.y 0 p.y
print $ p2.fnfield           -- V3 2 0 2

p3 = p1.fields.set "tupleFields" $ p -> [p.x, p.y, p.z]
print $ typeOf p3            -- P3 1 2 3 & {tupleFields: [this.x, this.y, this.z]}
print p3.tupleFields         -- [1,2,3]
p4 = p3.tupleFields = [7,8,9]
print p4                     -- P3 7 8 9

-- What if the name is not known at compilation time?
name : Text
field1 = p1.fields.get name -- field1 : Dynamic
```

<!-- ====================================================================== -->

#### Proving Software Correctness

**Note [To be included somewhere]**: Enso is dependently typed because we can
run arbitrary code on type-level.

**So, what are dependent types?** Dependent types are types expressed in terms
of data, explicitly relating their inhabitants to that data. As such, they
enable you to express more of what matters about data. While conventional type
systems allow us to validate our programs with respect to a fixed set of
criteria, dependent types are much more flexible, they realize a continuum of
precision from the basic assertions we are used to expect from types up to a
complete specification of the program’s behaviour. It is the programmer’s choice
to what degree they want to exploit the expressiveness of such a powerful type
discipline. While the price for formally certified software may be high, it is
good to know that we can pay it in installments and that we are free to decide
how far we want to go. Dependent types reduce certification to type checking,
hence they provide a means to convince others that the assertions we make about
our programs are correct. Dependently typed programs are, by their nature, proof
carrying code.

**If dependent types are so great, why they are not used widely?** Basically,
there are two problems. First, there is a small set of languages allowing for
dependent types, like Agda or Idris. Second, both writing as well as using
dependently typed code is significantly harder than for code using a
conventional type system. The second problem is even bigger because it stands in
the way of easy refactoring of the code base and keeping it in good shape.

**I've heard that dependent type system in Enso is different, how?** The Enso
type system provides a novel approach to dependent types. It allows to just
write simple code and in many cases provides the dependent type system benefits
for free!

#### Power and Simplicity

Consider the following code snippets in Idris. This is a simple, but not very
robust implementation of List. If you try to get the head element of an empty
list, you'll get the runtime error and there is no way to prevent the developer
from using it by mistake:

```Haskell
-----------------------
--- LANGUAGE: IDRIS ---
-----------------------

data List elem
    = Cons elem (List elem)
    | Empty

index : Int -> List a -> a
index 0 (Cons x xs) = x
index i (Cons x xs) = index (i-1) xs

main : IO ()
main = do
    let lst1 : List String = (Cons "Hello!" Nil)
    let lst2 : List String = Nil
    print $ index 0 lst1
    print $ index 0 lst2
```

```haskell
--- Runtime Output ---
Hello!
*** test.idr:18:23:unmatched case in Main.index ***
```

The above program crashed in the middle of execution. Such mistakes (related to
the possibility of the index being out of bounds) are very hard to catch, and
most programming languages do not provide a standard, easy mechanism to prevent
them from happening. Let's improve the situation and use the power of dependent
types to keep the information about the length of the list visible to the
compiler:

```haskell
-----------------------
--- LANGUAGE: IDRIS ---
-----------------------

data List : (len : Nat) -> (elem : Type) -> Type where
    Cons  : (x : elem) -> (xs : List len elem) -> List (S len) elem
    Empty : List Z elem

index : Fin len -> Vect len elem -> elem
index FZ     (Cons x xs) = x
index (FS k) (Cons x xs) = index k xs

main : IO ()
main = do
    let lst1 : List 1 String = Cons "hello" Empty
    let lst2 : List 0 String = Empty
    print $ index 0 lst1
    print $ index 0 lst2
```

```haskell
--- Compilation Error ---
test.idr:18:21:
When elaborating right hand side of main:
When elaborating argument prf to function Data.Fin.fromInteger:
        When using 0 as a literal for a Fin 0
                0 is not strictly less than 0
```

This time the error was caught by the compiler, however, both the
implementation as well as the library interface are much more complex now.

Let's now write the same implementation in Luna:

```haskell
----------------------
--- LANGUAGE: ENSO ---
----------------------

type List a
    Cons value:a tail:a
    Empty

index : Natural -> List a -> a
index = case
    0 -> value
    i -> tail >> index (i-1)

main =
    lst1 = Cons "hello" Empty
    lst2 = Empty
    print $ index 0 lst1
    print $ index 0 lst2
```

```haskell
--- Compilation Error ---
Error in test.enso at line 18:
    The field Empty.tail is not defined.
    Arising from ...
```

Although the Enso implementation is over 15% shorter than the insecure Idris
implementation and over 50% shorter than the secure implementation, it provides
the same robustness as the secure Idris implementation. Moreover, the user
facing interface is kept simple, without information provided explicitly for the
compiler.

#### Another Example

```haskell
-----------------------
--- LANGUAGE: IDRIS ---
-----------------------

import Data.So

countOcc : Eq a => a -> List a -> Nat
countOcc x xs = length (findIndices ((==) x) xs)

validate : String -> Bool
validate x = let
        containsOneAt = (countOcc '@' (unpack x)) == 1
        atNotAtStart  = not (isPrefixOf "@" x)
        atNotAtEnd    = not (isSuffixOf "@" x)
    in containsOneAt && atNotAtStart && atNotAtEnd

data Email : Type where
    MkEmail : (s : String) -> {auto p : So (validate s)} -> Email

implicit emailString : (e : Email) -> String
emailString (MkEmail s) = s

main : IO ()
main = do
    maybeEmail <- getLine

    case choose (validate maybeEmail) of
        Left _  => putStrLn ("Your email: " ++ (MkEmail maybeEmail))
        Right _ => putStrLn "No email."
```

```haskell
----------------------
--- LANGUAGE: ENSO ---
----------------------

isValid : String -> Bool
isValid address
     = address.count '@' == 1
    && not $ address.startWith '@'
    && not $ address.endsWith  '@'

type Email
    Data address : Refine IsValid Text

main =
    mail = Email.Data Console.get
    if mail.error
        then 'Not a valid address.'
        else print mail
```

#### Type Resolution

The natural next question is, how was it possible to get such a drastic quality
improvement? As already mentioned, dependent types are types expressed in terms
of data, explicitly relating their inhabitants to that data. Enso atom types
make it possible to expose all data structures to the compiler automatically, so
they can be statically analyzed. There is no need to explicitly provide any
selected data to the compiler, as it has access to every structural information
by design.

Let's describe where the compiler gets the required information from. Please
note, that the following description is shown for illustration purposes only and
does not represent the real compilation algorithm. First, let's focus on the
definition of the `index` function:

```haskell
index : Natural -> List a -> a
index = case
    0 -> value
    i -> tail >> index (i-1)
```

Without using currying and after applying the Uniform Syntax Call, we can write
it's more explicit form:

```haskell
index : Natural -> List a -> a
index i lst = case i of
    0 -> lst.value
    i -> index (i-1) lst.tail
```

Let's break the function apart:

```haskell
index_1 : 0 -> List a -> a
index_1 0 lst = lst.value

index_2 : ((j:Natural) + 1) -> List a -> a
index_2 i lst = index (i-1) lst.tail

index : Natural -> List a -> a
index i = case i of
    0 -> index_1 i
    i -> index_2 i
```

Based on the provided information, including the fact that the `value` and
`tail` fields are defined only for the `Cons` atom, we can further refine the
types of `index_1` and `index_2`:

```haskell
index_1 : 0                 -> Cons t1 (List t2) -> t1
index_2 : ((j:Natural) + 1) -> Cons t1 (List t2) -> t1
```

Please note that the type `a` was refined to `t1 | t2`. We can now infer a much
more precise type of `index`, which makes it obvious why the code was incorrect.

```haskell
index : Natural -> Cons t1 (List t2) -> t1
```

A similar, but a little more complex case applies if we try to access a nested
element. We leave this exercise to the reader.

#### Bigger Example (to be finished)

```haskell
type List a
    Cons value:a tail:(List a)
    End

head : Cons a (List b) -> a
head = value

last : Cons a (List a) -> a
last = case
    Cons a End  -> a
    Cons a tail -> last tail

init : Cons a (List a) -> List a
init = case
    Cons a End           -> End
    Cons a (Cons b tail) -> Cons a $ init (Cons b tail)

index :: Natural.range lst.length -> lst
```

#### Autolifting functions to types

```haskell
-- Consider
fn : Int -> Int -> Int
fn = a -> b -> a + b

-- If we provide it with 1 and 2 then
fn 1 2 : fn 1 2 : 3

-- Howevere this is true as well
fn 1 2 : fn Int Int : Int

-- Please note that 1:Int AND Int:Int
-- It means that functions can always be provided with type-sets and return type sets, so
fn Int Int -- returns Int
```

#### Function composition

```haskell
sumIncremented1 = map +1 >> fold (+)
sumIncremented2 = fold (+) << map +1
```

However, the following is preferred:

```haskell
sumIncremented1 = . map +1 . fold (+)
```

#### Lazy / Strict

```haskell
if_then_else :: Bool -> Lazy a in n -> Lazy a in m -> a in n | m
if cond _then ok _else fail =
    case cond of
        True  -> ok
        False -> fail

test cond = if_then_else cond -- The arguments are still lazy and accept monads
test cond ok fail = if_then_else cond ok fail -- The arguments are strict and does not accept monads
```

**TODO:** ARA + WD - check with bigger examples if this really holds.
Alternatively we can think of `Lazy a` as a part of the `a` parameter, which
should not be dropped. WD feels it needs to be re-considered.

#### Context Defaults

- Function arguments default to `in Pure` if not provided with an explicit type.
- Function results and variables default to `in m` if not provided with an
  explicit type.

For example:

```haskell
test a b = ...
```

Has the inferred type of

```haskell
test : a in Pure -> b in Pure -> out in m
```

Thus if used like

```haskell
test (print 1) (print 2)
```

The prints will be evaluated before their results are passed to `test`. However,
when provided with explicit signature:

```haskell
test2 : a in m -> b in n -> out in o
test2 a b = ...
```

Then the evaluation

```haskell
test2 (print 1) (print 2)
```

Will pass both arguments as "actions" and their evaluation depends on the
`test2` body definition.

#### Type Based Implementations

```haskell
default : a
default = a . default
```

#### Explicit Types And Subtyping

When explicit type is provided, the value is checked to be the subtype of the
provided type, so all the following lines are correct:

```haskell
a = 1
a : 1
a : Natural
a : Integer
a : Number
a : Type
```

The same applies to functions – the inferred signature needs to be a subtype of
the provided one. However, the intuiting of what a subtype of a function is
could not be obvious, so lets describe it better. Consider a function `foo`:

```haskell
foo : (Natural -> Int) -> String
```

From definition, we can provide it with any value, whose type is the subtype of
`Natural -> Int`. This argument needs to handle all possible values of `Natural`
as an input. Moreover, we know that `foo` assumes that the result of the
argument is any value from the set `Int`, so we cannot provide a function with a
broader result, cause it may make `foo` ill-working (for example if it pattern
matches on the result inside). So the following holds:

```haskell
(Natural -> Natural) : (Natural -> Int)
```

Please note, that we can provide a function accepting broader set of arguments,
so this holds as well:

```haskell
(Int -> Natural) : (Natural -> Natural)
```

So, this holds as well:

```haskell
(Int -> Natural) : (Natural -> Int)
```

(todo: describe variants and contravariants better here).

Consider the following, more complex example:

```haskell
add a name =
    b = open name . to Int
    result = (a + b).show
    print result
    result
```

This function works on any type which implements the `+` method, like `String`,
however, we can narrow it down by providing explicit type signature:

```haskell
add : Natural in Pure -> Text in Pure -> String in IO ! IO.ReadError
add = ...

addAlias = add
```

Now we can create an alias to this function and provide explicit type signature
as well. As long as the `add` signature will be the subtype of `addAlias`
signature, it will be accepted. First, we can skip the explicit error mention:

```haskell
addAlias : Natural in Pure -> Text in Pure -> String in IO
```

Next, we can skip the explicit contexts, because the contexts of arguments
default to `Pure` while the context of the result does not have any restrictions
by default so will be correctly inferred:

```haskell
addAlias : Natural -> Text -> String
```

We can also type the whole function using any broader type. In order to
understand what a subtype of a function is, visualize its transformation as
arrows between categories. The above function takes any value from a set
`Natural` and set `Text` and transforms it to some value in set `String`. We can
use any wider type instead:

```haskell
addAlias : Natural -> Text -> Type
```

However, please note that the following will be not accepted:

```haskell
addAlias : Int -> Type -> Type -- WRONG!
```

<!-- ====================================================================== -->

#### Mutable Fields (FIXME)

```haskell
type Graph a
    Node
        inputs : List (Mutable (Graph a))
        value : a

-- THIS MAY BE WRONG, we need to have semantics how to assign mutable vars to
mutable vars to create mutual refs and also pure vars to create new refs
n1 = Node [n2] 1
n2 = Node [n1] 2
```

<!-- ====================================================================== -->

#### Other Things To Be Described

- Implicit conversions

- modules and imports (from the deprecated section)

- Using and creating Monads, example State implementation (Monad = always
  transformer, at the bottom Pure or IO)

- IO should be more precise, like `IO.Read` or `IO.Write`, while `IO.Read : IO`

- Constrained types (like all numbers bigger that `10`)

- Errors and the catch construct like

  ```haskell
  num3 = num2 catch case
      DynamicError -> 0
  ```

- Catching Errors when not caught explicitly – important for correctness

- Type-level / meta programming – like taking an interface and returning
  interface with more generic types (move a lot of examples from TypeScript
  docs)

- Question – should it be accessed like `End` or like `List.End` ? The later is
  rather better! If so, we need to make changes across the whole doc!

  ```haskell
  type List a
      Cons a (List a)
      End
  ```

- monadfix

- implementing custom contexts (monads). Including example on how to implement a
  "check monad" which has lines checking dataframes for errors.

<!-- ====================================================================== -->

#### Type Holes

```haskell
a :: ??
```

Creates a type hole, which will be reported by the compiler. Describe the
programming with type holes model. A good reference: http://hazel.org

<!-- ====================================================================== -->

#### Scoping Rules and Code Modularity

Imports in Enso can be performed in _any_ scope, and are accessible from the
scope into which they are imported. This gives rise to a particularly intuitive
way of handling re-exports.

Consider the following file `Test.luna`. In this file, the imports of `Thing`
and `PrettyPrint` are not visible when `Test.luna` is imported. However,
`PrettyPrint` and `printer` are made visible from within the scope of `Test`.
This means that a user can write `import Test: printer` and have it work.

```
import Experiment.Thing
import Utils.PrettyPrint

type Test a : PrettyPrint Text (Test a) =
    import Utils.PrettyPrint: printer

    runTest : a -> Text
    runTest test = ...

    prettyPrint : Test a -> Text
    prettyPrint self = ...
```

<!-- ====================================================================== -->

#### Anonymous Types

In addition to the syntax proposed above in [Declaring Types](#declaring-types),
this RFC also proposes a mechanism for quickly declaring anonymous types. These
types are anonymous in that they provide a category of values without applying a
name to their category, and can be created both as types and as values.

While it is possible to use the primary type declaration syntax without
providing an explicit name, this is highly impractical for most places where an
anonymous type becomes useful. This shorthand provides a way to get the same
benefit without the syntactic issues of the former.

#### Anonymous Types as Types

When used in a type context, an anonymous type acts as a specification for an
interface that must be filled. This specification can contain anything from
types to names, and features its own syntax for inline declarations.

Consider the following examples:

- `{Int, Int, Int}`: This type declares a set of values where each value
  contains three integers.
- `{Int, foo : Self -> Int}`: This type declares a set of values with an integer
  and a function from `Self` to an Integer with name `foo`.
- `{Self -> Text -> Text}`: This defines an unnamed function. This may seem
  useless at first, but the input argument can be pattern-matched on as in the
  following example:

  ```
  foo : { Int, Int, Self -> Int } -> Int
  foo rec@{x, y, fn} = fn rec
  ```

`Self` is a piece of reserved syntax that allows anonymous types to refer to
their own type without knowing its name.

#### Anonymous Types as Values

Anonymous types can also be constructed as values using similar syntax. You can
provide values directly, which will work in a context where names are not
required, or you can provide named values as in the following examples:

- `{0, 0}`: This anonymous value will work anywhere a type with two numbers and
  no other behaviour is expected.
- `{x = 0, y = 0, z = 0}`: This one provides explicit names for its values, and
  will work where names are required.
- `{x = 0, fn = someFunction}`: This will also work, defining the value for `fn`
  by use of a function visible in the scope.
- `{x = 0, fn = (f -> pure f)}`: Lambda functions can also be used.

<!-- ====================================================================== -->

<!-- #### On the Semantics of Standalone Implementations
Standalone implementations allow for limited extension methods on types. The
interface methods implemented for a type in the standalone definition can be
used like any other method on an Enso type.

#### Overlapping Interface Implementations
Sometimes it is beneficial to allow interfaces to overlap in one or more of
their type parameters. This does not mean Enso allows _duplicate_ instances (
where all of the type parameters are identical). These can be implemented by
either of the methods above, but the user may often run into issues when
attempting to make use of these interfaces.

Enso thus provides a mechanism for the programmer to manually specify which
instance of an interface should be selected in the cases where resolution is
ambiguous. Consider the following example, using the `PrettyPrinter` interface
defined above.

```
type Point2D : PrettyPrinter Text | PrettyPrinter ByteArray =
    x : Double
    y : Double

    prettyPrint : Point2D -> Text
    prettyPrint self = ...

    prettyPrint : Point2D -> ByteArray
    prettyPrint self = ...

loggerFn (a : PrettyPrinter b) -> Text -> a -> Text
loggerFn msg item = msg <> prettyPrint(Text) item
```

As you can see, the syntax for specifying the instance in the ambiguous case
uses parentheses to apply the type to the `prettyPrint` function.  -->

<!-- ====================================================================== -->

#### Implementing Interfaces

TODO: This section needs discussion. It is a very draft proposal for now.

The nature of Enso's type system means that any type that _satisfies_ an
interface, even without explicitly implementing it, will be able to be used in
places where that interface is expected. However, in the cases of named
interfaces (not [anonymous types](#anonymous-types)), it is a compiler warning
to do so. (TODO: Explain why. What bad would happen otherwise?)

You can explicitly implement an interface in two ways. Examples of both can be
found at the end of the section.

1. **Implementation on the Type**
   Interfaces can be directly implemented as part of the type's definition. In
   this case the type header is annotated with `: InterfaceName` (and filled
   type parameters as appropriate). The interface can then be used (if it has a
   default implementation), or the implementation can be provided in the type
   body.

2. **Standalone Implementation:**
   Interfaces can be implemented for types in a standalone implementation block.
   These take the form of `instance Interface for Type`, with any type
   parameters filled appropriately.

Both of these methods will support extension to automatic deriving strategies in
future iterations of the Enso compiler.

It should also be noted that it is not possible to implement orphan instances of
interfaces in Enso, as it leads to code that is difficult to understand. This
means that an interface must either be implemented in the same file as the
interface definition, or in the same file as the definition of the type for
which the interface is being implemented. (TODO: To be discussed)

Consider an interface `PrettyPrinter` as follows, which has a default
implementation for its `prettyPrint` method.

```haskell
type (t : Textual) => PrettyPrinter t =
    prettyPrint : t
    prettyPrint = self.show
```

For types we own, we can implement this interface directly on the type. Consider
this example `Point` type.

```haskell
type Point : PrettyPrinter Text
    x : Double
    y : Double
    z : Double

    prettyPrint : Text
    prettyPrint = ...
```

If we have a type defined in external library that we want to pretty print, we
can define a standalone instance instead. Consider a type `External`.

```haskell
instance PrettyPrint Text for External =
    prettyPrint = ...
```

<!-- ====================================================================== -->

#### Constructors

While types in Enso describe categories of values, the constructors are the
values themselves. Constructors are used for defining new data structures
containing zero or more values, so called fields. Formally, constructors are
product types, a primitive building block of algebraic data types.

A constructor definition starts with the `type` keyword followed by the
constructor name and lists its fields by name with possible default values. It
is possible to create unnamed fields by using wildcard symbol instead of the
name. Constructors cannot be parametrized and their fields cannot be provided
with explicit type annotations. The formal syntax description is presented
below.

```
consDef   = "type" consName [{consField}]
fieldName = varName | wildcard
consField = fieldName ["=" value]
```

Below we present code snippets with constructors definitions. Constructors with
the same name are just alternative syntactic forms used to describe the same
entity. We will refer to these definitions in later sections of this chapter.

```haskell
-- Boolean values
type True
type False

-- Structure containing two unnamed fields
type Tuple _ _

-- Alternative Point definitions:
type Point x y z

type Point (x = 0) (y = 0) (z = 0)

type Point x=0 y=0 z=0

type Point
    x = 0
    y = 0
    z = 0
```

<!-- ====================================================================== -->

#### Constructors as types

As Enso is a dependently-typed language with no distinction between value- and
type-level syntax, we are allowed to write _very_ specific type for a given
value. As described earlier, constructors are the values belonging to categories
defined by Enso types. However, they are not only members of categories, they
are also useful to describe very specific categories per se. Formally, a
constructor is capable of describing any subset of the set of all possible
values of its fields.

For example, the `True` constructor could be used to describe the set of all
possible values of its fields. While it does not have any fields, the set
contains only two values, the `True` constructor itself and an `undefined`
value. Thus it is correct to write in Enso `True : True` and assume that the
only possible values of a variable typed as `a : True` are either `True` or
`undefined`.

On the other hand, The `Point` constructor does contain fields, thus it could be
used for example to describe all possible points, whose first coordinate is an
integral number, while the second and third coordinates are equal to zero:
`a : Point int 0 0`.

<!-- ====================================================================== -->

Function resolution:

- Always prefer a member function for both `x.f y` and `f y x` notations.
- Only member functions, current module's functions, and imported functions are
  considered to be in scope. Local variable `f` could not be used in the `x.f y`
  syntax.
- Selecting the matching function:
  1. Look up the member function. If it exists, select it.
  2. If not, find all functions with the matching name in the current module and
     all directly imported modules. These functions are the _candidates_.
  3. Eliminate any candidate `X` for which there is another candidate `Y` whose
     `this` argument type is strictly more specific. That is, `Y` self type is a
     substitution of `X` self type but not vice versa.
  4. If not all of the remaining candidates have the same self type, the search
     fails.
  5. Eliminate any candidate `X` for which there is another candidate `Y` which
     type signature is strictly more specific. That is, `Y` type signature is a
     substitution of `X` type signature.
  6. If exactly one candidate remains, select it. Otherwise, the search fails.

<!-- ====================================================================== -->

Inference of optional arguments (see syntax doc). Type applications too.

<!-- ====================================================================== -->

Typechecking scoping rules.

<!-- ====================================================================== -->

Analysis of laziness,
analysis of parallelism

<!-- ====================================================================== -->
