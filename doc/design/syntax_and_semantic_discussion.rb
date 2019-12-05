## =======================
## === Variable Naming ===
## =======================

## This file uses other naming convention than we used to use. It uses the 
## snake_case naming for variables, which gets translated to `CamelCase` on
## type level. **THIS IS NOT A SERIOUS PROPOSITION NOW**. However, I want to 
## discuss it with you. For a long time I was very, very against snake case,
## as it felt unnatural and strange. But this was an opinion based solely
## on my lack of experience with such langs. After using Rust for a while
## I'm deeply surprised how clear the code is to read and how my reading comfort
## increased.

## If we came to a conclusion that we want it, the following rules would be 
## needed:
## - The var `foo_bar_baz` is the same as `FooBarBaz`.
## - Using mixed style is disallowed, for example `foo_Bar`, or even `fooBar`.
## - Definition of mixfix functions would need to use other syntax, like
##   `if check _then ok _else fail = ...` instead of the current:
##   `if_then_else check of fail`, which actually may be even nicer. 
<<-MK
Always been for snake_case, it rocks.
Not sure how I feel about auto conversion to CamelCase, it should only exist for
patterns.
MK

<<-ARA
I find snake_case very readable but hate typing it. I'm mostly ambivalent toward
this suggestion. I think snake_case is very good for readability, but the
conversion from `foo_bar_baz` to `FooBarBaz` is far more jarring than if it were
from `fooBarBaz`. 

Are there other places than patterns where there is a syntactic ambiguity? If
not, then I agree with marcin that the only time `FooBarBaz` should be allowed
is in patterns. I still find it jarring and lean towards a different signifier
for patterns, but I realise that discussion was shelved a long time ago!

I like the `if check _then ok _else fail` proposal for mixfix definitions.
ARA

<<-MEETING
A point has been made that the conversion from snake_case to UpperCamelCase for
pattern matching can be quite visually jarring:

- Wojciech wants to think about it more.
- We need to make a decision but it's arbitrarily supported by the parser so is
  easy to change.
MEETING

## =============================
## === Top-level expressions ===
## =============================

## Below we present a list of use-cases for top-level expression evaluation. 
## The items are marked by special symbols which mean:
## - [?,_] - We do not know how to implement it, but maybe it's possible.
## - [-,_] - Impossible when using only macros.
## - [+,_] - Possible when using only macros.
## - [_,H] - High priority, very frequent usages.
## - [_,M] - Medium priority.
## - [_,L] - Low priority, nice to have, but we can definitely live without it.
## - [_,!] - Something we never want in the language.
##
## Use cases:
## - [-,M] Creating top level things in IO, like a global `IORef`.
## - [-,L] Nice scripting interface - just write code and eval.

<<-ARA
While we don't need top-level evaluation, I think you're underestimating the 
utility of a scripting workflow for rapid prototyping. It's very useful in
Python, for example.

I propose that we provide a `script` macro that implicitly wraps the top-level 
of a file in a `main` function. This would allow for easily writing stand-alone
scripts in Enso without _actually_ requiring top-level evaluation.
ARA

<<-MEETING
We just want a `runHaskell` style `enso run` that will start executing from the
`main` function.

_However_, this new evaluation style mucks with Jupyter bindings quite badly,
but this can be handled by multiple cell types. 
MEETING

## - [M,H] Generating data structs for a dataframe based on an input file or
##         automatic generation of API for a library.
## - [M,H] Static metaprogramming (AST -> AST) - generating structs and funcs 
##         based on existing AST. Possibly evaluating IO actions (IO is needed 
##         for cache flag).
## - [-,!] Dynamic metaprogramming - changing the atom shapes at runtime, 
##         changing func defs in runtime, heavy monkey patching. We don't want 
##         it because every usage of it would be a big code smell and would 
##         break any possible type inference engine.
##         IMPORTANT NOTE: we want this ability when using Dynamic type. Then
##         the ability to define new methods on such type are often useful,
##         but in no way this requires top-level expressions.
## - [M,H] "Registering / remembering" things when compiling a file. Fo example,
##         remembering all structs marked by `ast` annotation. Example use case
##         is to generate pattern matches for all possible AST types. This can 
##         be done by allowing macros to read and write to some per-file 
##         persistant storage (when pre-compiling the file, the storage should 
##         be serialized as part of the file interface).
## - [M,H] Grouping few macros together, like for example:
##         >> derive_all = derive ord debug show
##         >> @derive_all 
##         >> type Maybe a
##         >> Nothing
##         >> type Just value:a  
##         However, this can be done by discovering that the function is just
##         used as a macro and treating it as a macro as well.
## - [?,M] Ruby-like `method_missing` magic. I believe that the subset of use 
##         cases of that we can simulate using other language elements, like
##         Rust-like deref coercions. Then this is rather unnecessary.
##
##
## To sum this up, after considering all the use cases, it seems there is 
## completely no need to use top-level expressions in the language. We can 
## support all the important use cases with simple syntactic (AST -> AST) 
## macros, while allowing for real top-level / type-level code evaluation
## we also allow for a deep-magic hackery, which we will always consider a big
## code smell (like runtime changing a shape of a type).

## ======================
## === Syntax changes ===
## ======================

## After thinking for long time about the syntax, there are two possible 
## solutions to ambiguities problems:
##
## 1. Rules:
##    - Newline after `=` means a block which does not evaluate in-place.
##    - Allowing to use a keyword alternatively, like `foo = lazy print 'hi'`.
##
## 2. Introducing the `def` keyword for function definitions.
##
##
## After thinking more about it, there are several problems with the solution 2:
## - If we can type variables, the following code should be valid (and ugly):
##   >> foo: Int -> Int -> Int
##   >> def foo a + b = a + b
##
## - The following syntax would not be valid anymore, and it would just not be 
##   used for anything:
##   >> add a b = a + b
##
## - We would have a strange double-syntax for the same things in many places:
##   >> val1 = 5
##   >> def val2 = 5
##
## - Even worse with interfaces (which is very confusing):
##   >> type HasName
##   >>     name: String
##   >>
##   >> type HasName2
##   >>     def name: String 
##
## - We would still need to use `=` for single line defs, like:
##   >> def add a b = a + b
##
## Conclusion: let's stay with our good old agreement on point 1. No syntax 
## changes.

<<-ARA
I still don't like blocks being lazy by default just because it's inconsistent
with everything else assigned to a variable being eager.

- Instead we should have a `suspend` function.
- Further discussion on Tuesday.
ARA

## ===================
## === Annotations ===
## ===================

## Requires: parser changes, user-defined macros support.
## For now: it can just work for some hardcoded annotations.

## Annotations are a purely syntactic transformation. They just behave like
## the definition following them was nested, so for example, the following code:

@derive eq debug
@make_magic
type Maybe a
    Nothing
    type Just value:a

## Would logically gets translated to:

derive eq debug
    make_magic
        type Maybe a
            Nothing
            type Just value:a


## Moreover, there should be a way to define auto-annotations, something which 
## would be applied to for example every `type` definition. This is just a note,
## because it is actually already possible with this design. You can implement
## custom `type` macro which resolves to annotation + normal `type` resolution.

## IMPORTANT NOTE: Let's assume that stdlib exports type macro which adds by 
## default `deriving debug` to every type. The debug is like show but for 
## printing the inner structure always, while show may show nicely formatted
## things to the user:
##
## >> type Debug
## >>     debug: String
## >> 
## >> type Show
## >>     show: String
## >>     
## >>     # Default implementation redirecting to `debug`.
## >>     show:
## >>         self: Debug 
## >>         String
## >>     show = debug

<<-MK
1. Need more spec on the diff between show and debug. -> debugShow
2. Exposing always-on option for user-implemented annotations seems dangerous,
   – a playground for possible abuse and breaking user code.

- Debug is purely for debugging purposes: should print locations, etc.
MK

<<-ARA
Agreed that macros that can override arbitrary built-in functionality (e.g.
`type`) are incredibly ripe for abuse and have the propensity to lead to 
codebases where things aren't what they seem.

- Think if we should introduce `type` as a reserved name + keyword.
- Atom should be an intrinsic + reserved name.
ARA

## =============
## === Atoms ===
## =============

## There is a keyword `atom` which defines atoms. It is used in stdlib but it 
## is not exported to the user by default (explicit import needed).
##
## Atoms are product types which have named fields, while each field has a 
## distinct type (not dependent on types of other fields). For example:
##
## >> atom Nothing
## >> atom Just value
## >> atom V3 x y z
## >> atom V2 x y
##
## Used like:
##
## >> v = V3 1 2 3 : V3 1 2 3 : V3 Int Int Int : V3 Any Any Any : Any


## === Anonymous atoms ===
##
## It is possible to define anonymous atoms. Think of it like just ordinary 
## anonymous structs:
##
## >> point = atom x y z
## >> p1 = point 1 2 3 : Point Int Int Int
##
## There are no other differences here. Anonymous atoms cannot be passed to 
## functions accepting specific atom. The same way, functions accepting 
## annonymous atoms, like `foo t:(Point _ _ _) = ...` are not accepting 
## other types with similar structure. Use interfaces if you need this ability.
##
## Anonymous atoms have an identity (atom x y z != atom x y z).
##
## Atoms / types should be able to be defined in arbitrary scopes.
##
## IMPORTANT NOTE
## Given these constraints, we can in reality support only this form of atoms,
## because the named version is easily expressible as a macro.


## ===================================
## === ADTS and smart constructors ===
## ===================================

## There are following operators defined on type-sets:
## - Union        : `maybe a = Nothing | Just a`.
## - Intersection : `human = HasName & HasPhone`.
## - Subtraction  : `negative = Int \ Nat \ 0`.

## We often label functions producing complex types as "smart constructors".
## Some functions which are bijective can be used for pattern matching (in 
## future). Of course, more complex examples may require some serious compiler 
## support, so for now there would be support only for trivial functions, as the
## `maybe a = ...` definition above. A more complex example (probably not 
## supported now):

type V3 x y z

zV2 x y = V3 x y 0

test v = match v of
    ZV2 x y   -> ...
    V3  x y z -> ...

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
## === RECORDS ===
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
ARA
