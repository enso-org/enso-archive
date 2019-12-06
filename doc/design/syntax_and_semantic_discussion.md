# Notes on Enso's Syntax and Semantices
As we get closer to the development of the more sophisticated features of the
language, as well as have a more fully-featured interpreter, we really need to
clarify exactly how certain syntactic and semantic elements of the language
behave. 

This document aims to clarify the behaviour of many language constructs, as
well as expose any open questions that we have about them. It is not intended
to be a design document directly itself, but the information contained here
will be _used_ to later contribute to various, more-specialised design
documents.

<!-- Table of contents -->

## Variable Naming
One of the key features of Enso as a language is a total lack of separation
between the value and type level syntaxes. This enables a staggering uniformity
of programming in the language, allowing arbitrary computations in types as
well as in values. This means that arbitrary names can refer to both types and
values (as they are one and the same). 

However, this means that naming becomes a bit of a concern. Without a syntactic
disambiguator, it becomes much harder to keep a minimal syntax for things like
pattern matches. To this end, Enso itself enforces naming conventions:

- Everything is named in `camelCase`.
- When you want to refer to a name in a pattern, you can instead use the same
  name, but in `UpperCamelCase`. For example, `fooBar` becomes `FooBar`. This
  allows us a uniformity and syntactic marker for disambiguation.

For much of the history of the language's development, we have been happy with
using `camelCase` and `UpperCamelCase` naming conventions to mark this
distinction, but recently it has been raised that we might instead prefer to
use `snake_case` to refer to variables. A few thoughts on this follow:

- Snake case tends to be far more readable than camel case. This is primarily
  down to the fact that the `_` is far more readily readable as a space.
- However, with `snake_case`, we have to still have some syntactic identifier
  for type names in patterns, which would be `SnakeCase`. Unlike the
  distinction with camel case, this creates a much larger visual disparity
  with snake case.
- In all cases, mixed style (e.g. `foo_Bar`) would be disallowed to allow the
  language source to be uniform.
- If we go with `snake_case`, we should come up with another syntax for the
  definition of mixfix functions, and we may want to do this anyway. The
  current proposal for this is `if check _then ok _else fail`, which may be
  something that we want to adopt regardless of the decision on this section.

Please note that this file sticks to the pre-determined naming convention for
Enso, as no final decision has been made on whether or not it should be
changed.

> No final decision has been made on this point yet, so the actionables are as
> follows:
> 
> - Wojciech wants to think about it more.
> - The decision needs to be made by the next design meeting on 2019-12-10.
> - Support in the parser is fairly simple, though some consideration is needed
>   for how to support the mixfix definition syntax as methods.

## Top-Level Evaluation
An ongoing discussion for the language design has been whether or not to allow
for the top-level evaluation of statements in Enso. In order to help make a
decision, we listed the following use-cases for top-level evaluation. These are
annotated using the following key:

- `[?,_]` - We don't know how to implement it, but it may be possible.
- `[-,_]` - Not possible to implement using purely syntactic macros.
- `[M,_]` - Possible to implement using purely syntactic macros.
- `[_,H]` - High priority. This will be used often.
- `[_,M]` - Medium priority. This will be used with a medium frequency.
- `[_,L]` - Low priority. Nice to have, but we can likely live without it.
- `[_,!]` - Something that we never want to have in the language.

The use-cases we have considered are as follows:

- `[-,L]` Creating top-level constructs in `IO`, such as `IORef`. This is, in
  general, considered to be bad style, but can sometimes be useful.
- `[-,L]` Using enso files like python is able to be for scripting work. The
  ability to write constructs at the top-level and just evaluate them.
- `[M,H]` The ability to generate structures and / types for a dataframe at
  compilation time, or the automatic generation of an API for a library. A key
  recognition is that dependent types and type-level execution replace much of
  the need to be able to query the type-checker and runtime while writing a
  syntactic macro.
- `[M,H]` Static metaprogramming (transformations from `AST -> AST`) to let
  users generate types and functions based on existing AST. There is the
  potential to want to be able to evaluate actions in `IO` while doing this,
  but it may not be necessary.
- `[-,!]` Dynamic metaprogramming to let users mutate program state at runtime
  (e.g. changing atom shapes, function definitions), also known as 'monkey
  patching'. This is not something we want in the language, but we do perhaps
  want the ability to do so on values of type `Dynamic`. 
- `[M,H]` 'Remembering' things when compiling a file, such as remembering all
  structures marked by an `AST` annotation. An example use case for a mechanism
  like this is to generate pattern matches for all possible `AST` types. This
  can be done by letting macros write to a per-file peristent block of storage
  that could be serialised during precompilation.
- `[M,H]` Grouping of macros (e.g. `deriveAll = derive Ord Debug Show). This
  can be easily handled by doing discovery on functions used as macros, and
  treating it as a macro as well.
- `[?,M]` Method-missing magic, akin to ruby. This is likely able to be handled
  using other, existing language mechanisms.

In summary and when considering the above use-cases, it seems that there is
little need for top-level expression evaluation in Enso. We can support all of
the above-listed important use-cases using syntactic (`AST -> AST`) macros,
while allowing for top-level evaluation would enable users to write a lot of
overly-magical code, which will always be a code-smell.

Syntactic macros, however, do not easily support a scripting workflow, but the
solution to this problem is simple. We can just provide an `enso run <file>` 
command which will search for and execute the `main` function in the provided
file.

> The actionables for this section are as follows:
> 
> - Formalise and clarify the semantics of `main`.

## High-Level Syntax and Semantic Notes
While the majority of syntactic design for the language has utilised top-level
bindings in a syntax similar to that of Haskell or Idris, some consideration
has been given to instead introducing function bindings using a `def` keyword.

This has a few major problems, including:

- The typing of variables becoming very ugly, with bad alignment.

  ```ruby
  foo : Int -> Int -> Int
  def foo a b = a + b
  ```

- The standard Haskell/Idris-style definition syntax would no longer be valid,
  but would also not be used anywhere.
- There would be duplicated syntax for doing the same thing (e.g. `val1 = 5`
  and `def val1 = 5` would be equivalent).
- The `=` operator would still need to be used for single-line function
  definitions, making the syntax inconsistent.
- Interface definitions become very confusing:

  ```ruby
  type HasName
    name : String
    
  type HasName2
    def name : String
  ```

Additionally, in the current syntax, a block assigned to a variable is one that
has its execution implicitly suspended until it is forced. This has a few 
things that should be noted about it.

- There is a big mismatch between the semantics of assigning inline to a
  variable versus assigning a block to a variable. The following are not
  equivalent:

  ```ruby
  a = foo x y

  a =
    foo x y
  ```

- We could have a `suspend` function provided in the standard library, as
  laziness of argument evaluation is determined through type-signatures and is
  done automatically in the compiler.
- Such a function would likely not see heavy use.

As Enso types (other than atoms), are defined as sets of values (see the
section on [set types](#set-types) for details), we need a way to include an
atom inside another type that doesn't define it. 

- It would be potentially possible to disambiguate this using syntactic markers
  but this is likely to be unclear to users.
- Instead we propose to use a keyword (e.g. `use` or `include`) to signify the
  inclusion of an atom inside a type definition.

> The actionable items for this section are as follows:
> 
> - Further discussion on the semantics of top-level blocks, with a decision
>   made by 2019-12-10.
> - Make a decision regarding a keyword for including other atoms in a type
>   (e.g. `use`).

## Annotations
Much like annotations on the JVM, annotations in Enso are tags that perform a
purely syntactic transformation on the entity to which they are applied. The
implementation of this requires both parser changes and support for
user-defined macros, but for now it would be possible to work only with a set
of hard-coded annotation macros.

Annotations can be arbitrarily nested, so a set of annotation macros become
implicitly nested inside each other:

```ruby
@derive Eq Debug
@make_magic
type Maybe a
  use Nothing
  type Just 
```

The above example is logically translated to:

```ruby
derive Eq Debug
  make_magic
    type Maybe a
      use Nothing
      type Just (value : a)
```

In the presence of annotations and macros, it becomes more and more important
that we are able to reserve words such as `type` to ensure that users can
always have a good sense of what the most common constructs in the language
mean, rather than allowing them to be overridden outside of the stdlib.

This would allow types to automatically derive `Debug`, for example, which
would be a function `debug` which prints detailed debugging information about
the type (e.g. locations, source info, types, etc).

> The actionables for this section are:
> 
> - Decide if we want to reserve certain key bits of syntax for use only by
>   the standard library (with a focus on `type`).

## Types
Atoms are the fundamental building blocks of types in Enso. Where broader types
are sets of values, Atoms are 'atomic' and have unique identity. They are the
nominally-typed subset of Enso's types, that can be built up into a broader
notion of structural, set-based typing. All kinds of types in Enso can be 
defined in arbitrary scopes, and need not be defined on the top-level.

For more information on Enso's type-system, please take a look in the
[`types.md`](type-system/types.md) document.

### Atoms
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

#### Anonymous Atoms
Using the same keyword used to define atoms it is possible to define an 
anonymous atom. The key disambiguator is syntactic, using upper- or lower-case
starts for names.

```ruby
point = type x y z
p1 = point 1 2 3 : Point Int Int Int
```

There are no differences in functionality between anonymous and named atoms.

> Actionables for this section:
> 
> - What is the motivating use-case for an anonymous atom?

### Set Types
More complex types in Enso are known as typesets. All of these types are 
_structural_. This means that unification on these types takes place based upon
the _structure_ of the type (otherwise known as its 'shape'). 

Two typesets `A` and `B` can be defined to be equal as follows, where equality
means that the sets represent the same type.

1. `A` and `B` contain the same set of _labels._ A label is a _name_ given to
   a type.
2. For each label in `A` and `B`, the type of the label in `A` must be equal to
   the type of the same label in `B`:
   
   1. Atoms are only equal to themselves, accounting for application.
   2. Types are equal to themselves recursively by the above.

Two typesets `A` and `B` also have a _subsumption_ relationship `<:` defined
between them. `A` is said to be subsumed by `B` (`A <: B`) if the following
hold:

1. `A` contains a subset of the labels in `B`.
2. For each label in `A`, its type is a subset of the type of the same label in
   `B` (or equal to it):

   1. An atom may not subsume another atom.
   2. A type is subsumed by another time recursively by the above, accounting
      for defaults (e.g. `f : a -> b = x -> c` will unify with `f : a -> c`)

#### The Type Hierarchy
These typesets are organised into a _modular lattice_ of types, such that it is
clear which typesets are subsumed by a given typeset. There are a few key
things to note about this hierarchy:

- The 'top' type, that contains all typesets and atoms is `Any`.
- The 'bottom' type, that contains no typesets or atoms is `Nothing`.

#### Set Types and Smart Constructors
Enso defines the following operations on typesets that can be used to combine
and manipulate them:

- **Union:** `|` (e.g. `Maybe a = Nothing | Just a`)
- **Intersection:** `&` (e.g. `Person = HasName & HasPhone`)
- **Subtraction:** `\` (e.g. `NegativeNumber = Int \ Nat \ 0`)

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

#### Type and Interface Definitions
Typesets are defined using



Interfaces as conformity to a shape.

#### Anonymous Set Types


## ============================
## === Types and interfaces ===
## ============================

## There is a type macro / keyword which works this way:

## 1. If provided with only name and fields, it creates an atom:

type Just value
## translates to 
atom Just value


## 2. If provided with a body and sub-atom definitions, defines a smart
##    constructor and bunch of related functions:

type Maybe a
    Nothing
    type Just value:a

    is_just = match self of
        Nothing -> False
        Just _  -> True

## translates to:

type Maybe a
    Nothing
    type Just value:a

is_just: Maybe a -> _
is_just self = match self of
    Nothing -> False
    Just _  -> True

## which conceptually (read the explanation below) translates to:

maybe a = 
    atom Just value
    { (Nothing | Just a) & is_just: is_just & nothing: Nothing }

is_just: Maybe a -> _
is_just self = match self of
    Nothing -> False
    Just _  -> True

## Formalise this as needed
## Syntax + examples for anonymous record

## IMPORTANT NOTE
##
## The above example uses a pseudo syntax of anonymous record. If `type` would 
## be a keyword, not a macro, we would NOT need to support this syntax ever,
## because every use case we want to express could be then expressible by 
## interfaces. I think this is a very good idea not to polute the syntax more,
## so think about the above translation just as an visual explanation of how
## it works under the hood.
## 
## Moreover, if we make `type` a keyword, we can also remove `atom` as keyword.

## 3. If provided with body but without new atom definitions, it is an 
##    interface. This meaning is very clear if you think about our types from
##    the categories perspective. Such type defines behavior, and shape 
##    (for example, "this category needs to contain this shared element"), but 
##    does not define any **own atoms**. This is then a natural interface!

type HasName
    name: String

print_name: t:HasName -> Nothing
print_name t = t.name

type Human name
name (self: Int) = "IntegerName"

main = 
    print_name (Human "Zenek")
    print_name 7

## More complex example:

type Semigroup
    append: self -> self

type Monoid
    self: Semigroup # If we type `a:Monoid`, we know that `a:Semigroup`.
    Nothing

<<-ARA
Furthermore, and related, I think we want to be able to constrain types of 
type variables (partial-data style). It's useful in practice, and relies only
on existing GADT-style evidence discharge under the hood. This is not something
for now (as it requires the type checker), but is fully backwards compatible.
ARA

## ===============================
## === Implementing interfaces ===
## ===============================

## Types do not need to explicitely implement interfaces (type-checked 
## duck-typing). However, when defining a new type, we may explicitelly tell 
## it defines an interface and include default method definitions:

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


<<-ARA
Another reason why being able to explicitly declare them is important is that it
lets us check that all portions of that interface _have_ been defined and hence
potentially provide better diagnostics.
ARA

## ========================
## === Pattern Matching ===
## ========================

type Vector a
    V2 x:a y:a
    V3 x:a y:a z:a

main = 
    v = Vector.V3 x y z

    ## Position-based pattern matching
    case v of
        Vector.V3 x y z -> print x

    ## Constructor-based pattern matching
    case v of
        Vector.V3 -> print v.x ## refined v to be V3 

    ## Name-based pattern matching
    ## Syntax to be refined
    case v of
        Vector.V3 {x,y} -> print x
        {x}             -> print x

    ## Anonymous case, renaming of fields?
    case _ of
        v : Vector.V3 -> v.foo
        V3 x=a y=b    -> a + b # uniform with construction

    ## TODO code examples of this usage being important


## ================
## === Wrappers ===
## ================

## It works similar to deref coercion in Rust. 
## BUILT IN

type Wrapper
    wrapped   : lens self.unwrapped
    unwrapped : t
    unwrapped = t # Default implementation based on inferred type.

## Example use case:

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

<<-ARA
Works only for self

- line 487 == over wrapped (+1)
ARA


## ========================
## === Auto Conversions ===
## ========================

## NOTE: This is a special interface. Types need to implement this interface 
## explicitly to use this machanism.

type Into t
    into: t

## Example use cases

type Vector a
    type V3 x:a y:a z:a

    self: Into String
    into = 'V3 `self.x` `self.y` `self.z`'

    self: Into (a: Semigroup)
    into = self.x + self.y + self.z

test: Int -> String
test i = print 'I got the magic value `i`!'

main = 
    test 7    # OK!
    test 'hi' # FAIL: type mismatch, no definition `Into Int` for String.
    test (Vector.V3 1 2 3) # OK, prints 'I got the magic value 6'.


## === VERY IMPORTANT NOTE 1 ===
## The types should be auto-converted in the right place, so if we do:
test2 i = 
    print 'Got: `i`'
    test i

## Then the type of `test2` should be infered to accept `Int` and when 
## evaluating like `test2 (Vector.V3 1 2 3)`, it should print `Got: 6`. 
## This is crazy important from the GUI perspective, as GUI should display
## controls for conversions (read below).

## === VERY IMPORTANT NOTE 2 === 
## Please note that the `Into` interface can be implemented also by function 
## accepting arguments IFF all the arguments are provided with default value. 
## For example, the following code is correct:

Int: Into (Vector Int)
Int.into only_first=false = 
    if only_first 
        then Vector.V3 self 0 0 
        else Vector.V3 self self self

## GUI should display conversion controls in place where type gets converted 
## with a checkbox "only-first". If the checkbox is clicked, the code will be
## transformed to explicit conversion call. 

## === VERY IMPORTANT NOTE 3 ===
## We recognize that some features, like inference of conversion place are 
## not possible till we've got type inferencer. Thus we need to discuss the 
## limited scope of this functionality, however, it should exist (even only for
## funcs with explicit type signatures), as it is the backbone for API design
## techniques. As a reminder - we've been talking a long time about auto 
## conversions vs functions with polymorphic input. The former can be supported
## by GUI and provided with input widgets, while the later can not.


## ===============================
## === Broken values promotion ===
## ===============================

## Broken values which are not handled are automatically promoted to the 
## parent scope. For example, assuming that:

open: String -> String in IO ! IO.Exception 
open = ...

test = 
    print 'Opening the gates!'
    txt = open 'gates.txt'
    print 'Gates were opened!'
    7

## Because we never handled the broken value, it was automatically populated
## to parent scope and the type of test was inferred to be:

test: Int in IO ! IO.Exception

## This is similar to using `?` operator in Rust or TypeScript.

## IMPORTANT NOTE
## This functionality cannot be implemented without type inferencer, so for now,
## we assume that we silently drop unhandled broken values. Ugly, but needed.

<<-ARA
Design the first version of the stdlib to make use of async exceptions for IO
style operations. As the typechecker evolves, we can migrate (breakingly) the
stdlib.
ARA

## ==============================
## === Overlappable functions ===
## ==============================

## Applies the provided function to all fields of a structure and collects 
## result in a list. The fields are traversed in reversed order.

fold_fields: (f: field -> out) -> struct -> List out
fold_fields f struct =

    go: List out -> struct -> List out
    go out (t a) = go (f a, out) t
    go out _     = out

    go Nothing struct


## A more type-explicit implementation of the above.

fold_fields: (f: field -> out) -> struct -> List out
fold_fields f struct =

    go: List out -> t (a: field) -> List out 
    go out (t a) = go (f a, out) t

    go: List out -> t -> List out 
    go out _ = out

    go Nothing struct

## Please note that the function `f` was not typed as `f: Any -> Any` because 
## then it would not work correctly. We are allowed to provide any valid
## sub-type of a given type (in the set-type meaning) to a function. So if we 
## have a function `foo: Natural -> String` we can pass there an argument of 
## type `7`, but we cannot pass argument of type `Int` before pattern matching.
## If we define function accepting `Any`, like `foo: Any -> String` we can pass
## there just anything. Function sub-typing is trickier because of 
## contravariance of args. Thus, if we have `foo: (Any -> Any) -> String` we 
## are not allowed to pass the `Int -> String` as the first argument because
## it is NOT true that `(Int -> String) : (Any -> Any)`. Basically, the truth is 
## that if `a:sa` and `b:sb` then `(sa -> b) : (a -> sb). Thus, if we want to 
## provide a function which traverses all arguments, we want the user to be able
## to pass there a function `Int -> String` - it should force the type checker 
## to check if every field is `Int` - we can do exactly the same thing in 
## exactly the same way in overlappable instances in Haskell:
## 
##     class TraverseFields (ctx: Type -> Context) (t :: k)
##     {-# OVERLAPPABLE #-} instance TraverseFields t
##     instance (ctx a, TraverseFields t) => TraverseFields (t a) 
##
## Of course, this implementation lacks the body, as in order to pass 
## argument of non `*` kind we would need to use generics or HLists, which would 
## be a lot of typing here, but the implementation will stay exactly the same.

## NOTE
## Please note that if not using the `def` keyword, the following line may be
## considered ambiguous: `t a = v`. Does it mean a function definition or 
## structural pattern matching? Fortunatelly, structural pattern matching is 
## not needed often, and if its needed, it's needed by really advanced users,
## so this syntax always means defining a new function. If you want to use 
## structural matching, use this instead `(t a) = v`. This problem do not 
## appear when using `case of` or when defining function like above (which
## just requires parens): `fold_fields out (t a) = ...`.

<<-MK
Alternative proposal: a builtin method that can take an atom and split it into
its constructor and a list (or, better for performance, vector) of its fields?
You can achieve all the same things except most of them will be easier to achieve
_and_ there's no need for new, possibly ambiguous syntax.

Structural pattern matching to come in the future on both records and atoms.
MK

## === Constraint-based resolution ===
##
## This would probably not be possible without type checker, but it's worth 
## describing it here. While resolving functions in the future, constraints 
## should be taken into consideration. For example:

## This just comes down to defining the dispatch algorithm.

type HasName
    name: String

greet: t -> Nothing in IO
greet _ = print 'I have no name!'

greet: (t:HasName) -> Nothin in IO
greet t = print 'Hi, my name is `t.name`!'

type Person
    Cons name:String

main = 
    p1 = Person.Cons "Joe"
    greet p1 # -> Hi, my name is Joe
    greet 7  # -> I have no name!

## It is possible to do this without a typechecker, but would be duplicated
## effort that could not later be re-used by the typechecker.


## ============================
## === Function composition ===
## ============================

## It's worth noting that the composition operators we know from Haskell are 
## hard to use and often useless. We need to define many helper operators, like
## we did in the past: `.:`, `.:.`, `.::`, `.::.`, etc. It's worth noting that
## in 99% of cases what you want to do is to curry after applying all agruments.
## Thus, we should introduce a function composition operator which composes
## functions after all arguments were applied. For example:

compute_coeff = (+) >> (*5)

## On the left side we've got function with 2 arguments, on the right side with 
## one argument. The result consumes two arguments, applies to the first 
## function, and then applies the result to the second one. There is also `<<`
## operator which does the same thing but in another direction.

## FUNNY NOTE
## We may want to extend this behavior to more advanced use cases, consider:

do_funny_thing = (+) >> (*) 

## Which may consume 2 arguments, pass them to `+` function, and then pass the
## result as the first result to `*`, so we can evaluate it like:
## `do_funny_things 2 3 4`, which gets translated to `(2 + 3) * 4`.

## TODO code examples for why the second case is useful before determining if it
## should be kept.

## ==============
## === Lenses ===
## ==============

type Engine
    type Combustion
        power:          Int
        cylinder_count: Int

    type Electric
        power:   Int
        is_blue: Bool


type Vehicle
    type Car 
        color:     String
        max_speed: Int
        engine:    Engine

    type Bike
        color: String


type Person
    type Cons
        name:    String
        vehicle: Vehicle


main = 
    p1 = Person.Cons "Joe" (Vehicle.Car 'pink' 300 (Engine.Combustion 500 8))
    print $ p1.name                   # -> Joe
    print $ p1.vehicle.color          # -> pink
    print $ p1.vehicle.max_speed      # -> Some 300
    print $ p1.vehicle.engine.power   # -> Some 500
    print $ p1.vehicle.engine.is_blue # -> None
    p1.vehicle.color     = 'red'      # OK
    p1.vehicle.max_speed = 310        # FAIL: security reasons. Allowing this
                                      #       in Haskell was the worst decision
                                      #       ever. After refactoring it 
                                      #       silently does nothing there.

    p2 = p1.vehicle.max_speed    ?= 310 # OK
    p3 = p1.vehicle.engine.power  = 510 # FAIL
    p4 = p1.vehicle.engine.power ?= 510 # OK

    lens_name      = .name
    lens_color     = .vehicle.color
    lens_max_speed = .vehicle.max_speed
    lens_power     = .vehincle.engine.power

    ## Function like usage:
    print $ lens_name      p1 
    print $ lens_color     p1 
    print $ lens_max_speed p1 
    print $ lens_power     p1 

    p1 . at lens_name = ... # OK


## IMPORTANT NOTE
## We may want to get more utils from lenses in the future, but the above subset
## Covers 90% of needed use cases (if not more).

<<-MK
Not sure if it's all doable without TC.
Reason being, in a nested setter, like `p1.vehicle.engine.power` there's some
magic that will have to go on to not treat `p1.vehicle` as just `Car`.
While this is easily doable syntactically here, I foresee edge cases.
MK

<<-ARA
I also foresee edge cases. I would also like to just clarify if you think we
would be fine formalising this on top of the standard theory of optics (as
embodied in `lens` or `optics` in Haskell).

We _can_ base on optics theory for typing these, though not optics as embodied
in `optics` or `lens`.
ARA

## ======================
## === Special fields ===
## ======================

foo.0 ## translates to `foo.index 0`
foo."ID" ## translates to foo.byName "ID"


#################################################################
## Imports, qualified access, modules, and self initialization ##
#################################################################

Int.inc                   = self + 1 ## is just a sugar to:
inc (self:Int)            = self + 1 ## which is a sugar to:
inc (module:A) (self:Int) = self + 1 ## which is the final form.

## Rules:
##   1. When referenced from the same file, `module` is applied automaticaly.
##   2. When used implicitly, like `5.inc`, the `module` is applied as well.
##   3. When imported, like `import A` and used explicitly, the `module` is
##      just an argument: `x = A.inc 5` or `y = inc A 5`.
##   4. You are not allowed to define in a single module a constant and 
##      extension method with the same name. For example, defininig
##      `foo = 7` and `foo (self:Int) = 8` is not allowed.

## Alternative rules:
##   1. When `inc` is provided with `A` (explicitly, not variable of type A),
##      then it is passed as argument.
##   2. In other cases, `A` is passed automatically.
##   3. The rule 4 above.

## IMPORTANT
## This allows the following to work correctly. Please note that there may be
## different modules in scope which define `Int.inc`. It is important to 
## **be able to explicitely disambiguate** such cases. This solution solves it:

## A.enso ## 
def inc (self:Int) = self + 1

## B.enso ##
import A
print $ 5.inc
print $ inc 5
print $ A.inc 5
print $ inc A 5

<<-MK
I'm worried about the impact of this on performance without TC.
This essentially means we have dispatch by two arguments instead of by just one,
so twice the dispatch overhead.

My proposal would be to sacrifice uniform uniform syntax call for the module parameter,
making the whole thing more similar to haskell's qualified imports.

This would disallow the `inc A 5` syntax for before we have a typechecker.
The win here is that dynamic dispatch does not get more complicated, as the whole thing
is now decidable statically, so we're not sacrificing performance for what seems to be
an edge case.
MK

<<-ARA
I'll echo the above concerns.
Modules as records/sets?
ARA

## ===============
## === DYNAMIC ===
## ===============

<<-ARA
PLEASE NOTE: This is very sketchy and probably has holes in it. It is a _vision_
that I have not had the time to verify as sound, but I'm trying to give us a 
more coherent way to talk about `Dynamic`.

Up to this point we've done a lot of hand-waving about the `Dynamic` type, so I
would like to offer some more concrete proposals about how it behaves. This
proposal assumes that all of our types are structural under the hood, and would
need re-thinking if we decide not to pursue that direction.

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

TBC
ARA

## ============
## === MAIN ===
## ============

<<-ARA
The entry point for the program is defined as a special top-level binding `main`
in the file `main.enso`.

`enso run foo.enso` looks for a main in the run file
ARA

## ===============
## === SET TYPES ===
## ===============

<<-ARA
In the past we've talked about all non-atom types being used in a structural
manner (static duck typing), so I think we need to briefly discuss exactly how
we want this to work:

- From all of the above discussion the AEDT paper still serves our needs. 
- Do we want to allow for overriding of labels -> Yes, in the case of multi
  dispatch.
- Does a `type` definition (that doesn't purely generate an atom) generate a
  record? Yes.

ARA

## ============================
## === FUNCTION COMPOSITION ===
## ============================

<<-ARA
Function composition will take place using `|>` for `&` and `<|` for `$`. These
operators might take more characters to type, but they are much clearer as to
what is actually going on.

To be revisited. 
ARA


## ========================
## === DYNAMIC DISPATCH ===
## ========================

<<-ARA
Need to clarify some specifics for dyndispatch

- Multi/single dispatch as eventual?? Yes, potentially in the future.
- Can we elide dispatch on the 'module' argument, and do we want to actually
  dispatch on it?
- Need a rigorous specification of how modules behave, because they are no
  longer able to be represented purely as structural types. 
AR
