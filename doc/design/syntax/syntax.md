# Enso: The Syntax
When working with a programming language, the syntax is the first thing that a
user encounters. This makes it _utterly integral_ to how users experience the
language, and, in the case of Enso, the tool as a whole.

Enso is a truly novel programming language in that it doesn't have _one_ syntax,
but instead has two. These syntaxes are dual: visual and textual. Both are
first-class, and are truly equivalent ways to represent and manipulate the
program. To that end, the design of the language's syntax requires careful
consideration, and this document attempts to explain both the _what_, of Enso's
syntax, but also the _why_.

Furthermore, Enso is novel in the fact that it does not enforce any artificial
restriction between the syntaxes of its type and value levels: they are one and
the same. This enables a staggering level of uniformity when programming in the
language, allowing arbitrary computations on types, because in a
dependently-typed world, they are just values.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Encoding](#encoding)
- [Naming](#naming)
  - [Localised Naming](#localised-naming)
  - [Operator Naming](#operator-naming)
  - [Reserved Names](#reserved-names)
- [Layout Rules](#layout-rules)
  - [Maximum Line Length](#maximum-line-length)
  - [Indented Blocks](#indented-blocks)
- [Text Literals](#text-literals)
  - [Inline Text Literals](#inline-text-literals)
  - [Text Block Literals](#text-block-literals)
- [Types and Type Signatures](#types-and-type-signatures)
  - [Type Signatures](#type-signatures)
  - [Operations on Types](#operations-on-types)
  - [Type Definitions](#type-definitions)
- [Macros](#macros)
  - [Annotations](#annotations)
- [Top-Level Syntax](#top-level-syntax)
  - [Main](#main)
  - [Against Top-Level Evaluation](#against-top-level-evaluation)
- [Functions](#functions)
  - [Lambdas](#lambdas)
  - [Defining Functions](#defining-functions)
  - [Calling Functions](#calling-functions)
  - [Methods](#methods)
  - [Blocks](#blocks)
- [Function Arguments](#function-arguments)
  - [Named Arguments](#named-arguments)
  - [Defaulted Arguments](#defaulted-arguments)
  - [Positional Arguments](#positional-arguments)
  - [Optional Arguments](#optional-arguments)
  - [Splats Arguments](#splats-arguments)
  - [Type Applications](#type-applications)
- [Scoping Rules](#scoping-rules)
  - [Variable Scoping](#variable-scoping)
  - [Name Visibility](#name-visibility)
- [Field Access](#field-access)
  - [Pattern Matching](#pattern-matching)
  - [Lenses](#lenses)

<!-- /MarkdownTOC -->

## Encoding
While many modern programming languages are moving in a direction of being
liberal with the input they accept, we find that this often leads to the
resultant code being more difficult to use.

- **Source Encoding:** All input source code to Enso is UTF-8 encoded.
- **Indentation:** Indentation is four spaces, and all tabs are converted to 4
  spaces. This is not configurable on purpose

> The actionables for this section are:
>
> - Should the indentation rules be enforced by the parser / a later error
>   detection pass?

## Naming
Names in Enso are restricted to using ASCII characters. This arises from the
simple fact that all names should be easy to type without less common input
methods. Furthermore, we enforce a rigid style for naming. This is in aid of
giving Enso code a uniform identity.

Given that Enso is dependently-typed, with no artificial separation between the
type and value-level syntaxes, an arbitrary name can refer to both types and
values. This means that naming itself can become a bit of a concern. At first
glance, there is no meaningful syntax-based disambiguation in certain contexts
(such as patterns and type signatures) between introducing a fresh variable, or
an occurrence of one already in scope.

As we still want to have a minimal syntax for such use-cases, Enso enforces the
following rules around naming:

- All identifiers are named using `snake_case`.
- This can also be written `Snake_Case`
- In contexts where it is _ambiguous_ as to whether a name is fresh or should
  bind an identifier in scope, the second format refers to binding a name in
  scope, while the first refers to a fresh variable.
- This behaviour _only_ occurs in ambiguous contexts. In all other contexts,
  both conventions refer to that name already in scope
- No mixed-format names are allowed.
- We _strongly encourage_ using capitalised identifiers to refer to atoms.

While, through much of the language's history, we have used `camelCase` (with
its disambiguating cousin `CamelCase`), this has been decided against for one
primary reason:

- Names using snake case are far easier to read, and optimising code for
  readability is _overwhelmingly_ important in a context where novice users are
  involved.

### Localised Naming
We do, however, recognise that there is sometimes a need for unicode characters
in names (e.g. designing a high-level visual library that targets a narrow
domain in a specific country). To that end, Enso allows users to specify
optional localised names as part of a function's documentation.

Special support is provided for providing completions based on these localised
names in the language server, and in Enso Studio.

### Operator Naming
While some languages allow use of unicode characters for naming operators, we
will not. The reasoning behind this is simple, and is best explained by
paraphrasing the [Idris wiki](https://github.com/idris-lang/Idris-dev/wiki/Unofficial-FAQ#will-there-be-support-for-unicode-characters-for-operators).

- Unicode operators are hard to type, making it far more difficult to use other
  peoples' code. Even if some editors provide input methods for such symbols,
  they do not provide a good UX.
- Not every piece of software has good support for Unicode. Even though this is
  changing, it is not there yet, thus raising barriers to entry.
- Many Unicode characters are hard to distinguish.

In essence, while the use of Unicode operators can make code look pretty, a font
with well-defined ligatures can do the same.

### Reserved Names
Even though we do not intend to reserve any names at the level of the lexer or
parser, there are a number of constructs so core to the operation of Enso as a
language that we do not want to let them be overridden or redefined by users.
These constructs are known as reserved names, and these restrictions are
enforced in the compiler.

We reserve these names because allowing their redefinition would severely hinder
the readability and consistency of Enso code. They are as follows:

- `type`: This reserved name is used to define new atoms and typesets.
- `->`: This reserved name is the 'function' type, and represents a mapping from
  the type of its first operand to the type of its second operand.
- `:`: This reserved name is the type attribution operator. It ascribes the type
  described by its right operand to its left operand.
- `=`: This reserved name is the assignment operator, and assigns the value of
  its right operand to the name on its left. Under the hood this desugars to the
  relevant implementation of monadic bind.
- `.`: This is the standard function composition operator.
- `case ... of`: This reserved name is the case expression that is fundamental
  to the operation of control flow in the language.
- `this`:  This reserved name is the one used to refer to the enclosing type in
  a method or type definition.
- `here`: This reserved name is the one used to refer to the enclosing module.
- `in`: Used to specify the monadic context(s) in a type signature.

Many of these reserved words are implemented as macros in the parser, but these
macros are always in scope and cannot be overridden, hidden, or redefined.

> The actionables for this section are as follows:
>
> - In the future, we need to determine if we need `all` and `each` explicit
>   keywords in the case of dependency. Explicit examples are required.

## Layout Rules
Enso is a layout-aware programming language, in that layout rules are used to
determine code structure. The layout rules in Enso are intended to provide for
an intuitive way to format code.

### Maximum Line Length
The maximum length of a line in an Enso source file is restricted to 80
characters outside of text blocks. If your code exceeds this limit, the compiler
will emit a warning message.

There is no option to change this limit in order to enforce visual consistency
in user code. The reasoning behind this is as follows:

- The default soft-wrapping of long lines in editors is highly disruptive to the
  visual structure of code, making it harder to understand.
- Code should still be understandable on smaller screens or with multiple-column
  views.

### Indented Blocks
Indentation in Enso is used to start a block. Every indented line is considered
to be a sub-structure of the nearest previous line with lower indentation. We
refer to these as the 'child' and the 'parent' lines respectively. This means
that any region at the same indentation is considered to be part of the same
block, and blocks may contain blocks.

```ruby
block =
    x = 2 . replicate 7 . map show . intercalate ","
    IO.println x
```

In addition, we have a set of custom layout rules that impact exactly how blocks
are defined. These are described in the following subsections.

#### Trailing Operator on the Parent Line
If a line ends with an operator then all of its child lines form a
[_code_ block](#code-blocks). The most common usage of this kind of indented
block is a function definition body (following the `=` or `->`).

```ruby
test = a -> b ->
    sum = a + b
```

#### Leading Operator on All Child Lines
If all the child lines in a block begin with an operator, the lines in the block
are considered to form a single expression.

This expression is built as follows:

1. Every line in the block is built as a standard inline expression, ignoring
   the leading operator.
2. The final expression is built top to bottom, treating the leading operators
   as left-associative with the lowest possible precedence level.

Please note that the operator at the _beginning_ of each child line is used
_after_ the line expression is formed.

```ruby
nums = 1..100
    . each random
    . sort
    . take 100
```

#### No Leading or Trailing Operators
In the case where neither the parent line ends with a trailing operator, or the
child lines begin with an operator, every child line is considered to form a
separate expression passed as an argument to the parent expression. The most
common usage of this is to split long expressions across multiple lines.

```ruby
geo1 = sphere (radius = 15) (position = vector 10 0 10) (color = rgb 0 1 0)
geo2 = sphere
    radius   = 15
    position = vector 10 0 10
    color    = rgb 0 1 0
```

#### Debug Line Breaks
In certain cases it may be useful to debug line breaks in code. To this end, we
provide a debug line-break operator `\\` which, when placed at the beginning of
a child line tells Enso to glue that line to the end of the previous one.

This should be avoided in production code and its use will issue a warning.

```ruby
debugFunc = v -> v2 ->
    print (v2.normalize * ((v.x * v.x) + (v.y * v.y)
      \\ + (v.z * v.z)).sqrt)

validFunc = v -> v2 ->
    len = ((v.x * v.x) + (v.y * v.y) + (v.z * v.z)).sqrt
    v2  = v2.normalize * len
    print v2
```

## Text Literals
Enso provides rich support for textual literals in the language, supporting both
raw and interpolated strings natively.

- **Raw Strings:** Raw strings are delimited using the standard double-quote
  character (`"`). Raw strings have support for escape sequences.

  ```ruby
  raw_string = "Hello, world!"
  ```

- **Interpolated Strings:** Interpolated strings support the splicing of
  executable Enso expressions into the string. Such strings are delimited using
  the single-quote (`'`) character, and splices are delimited using the backtick
  (`` ` ``) character. Splices are run, and then the result is converted to a
  string using `show`. These strings also have support for escape sequences.

  ```ruby
  fmt_string = 'Hello, my age is `time.now.year - person.birthday.year`'
  ```

### Inline Text Literals
In Enso, inline text literals are opened and closed using the corresponding
quote type for the literal. They may contain escape sequences but may _not_ be
broken across lines.

```ruby
inline_raw = "Foo bar baz"
inline_interpolated = 'Foo `bar` baz'
```

### Text Block Literals
In Enso, text block literals rely on _layout_ to determine the end of the block,
allowing users to only _open_ the literal. Block literals are opened with three
of the relevant quote type, and the contents of the block are determined by the
following layout rules:

- The first child line of the block sets the baseline left margin for the block.
  Any indentation up to this margin will be removed.
- Any indentation further than this baseline will be retained as part of the
  text literal.
- The literal is _closed_ by the first line with a _lower_ level of indentation
  than the first child lineand will not contain the final blank line.

```
block_raw = '''
    part of the string
        still part of the string

    also part of the string

not_string_expr = foo bar
```

## Types and Type Signatures
Enso is a statically typed language, meaning that every variable is associated
with information about the possible values it can take. In Enso, the type
language is the same as the term language, with no artificial separation. For
more information on the type system, please see the [types](../types/types.md)
design document.

This section will refer to terminology that has not been defined in _this_
document. This is as this document is a specification rather than a guide, and
it is expected that you have read the above-linked document on the type-system
design as well.

### Type Signatures
Enso allows users to provide explicit type signatures for values through use of
the type attribution operator `:`. The expression `a : b` says that the value
`a` has the type `b` attributed to it.

```ruby
foo : (m : Monoid) -> m.self
```

Type signatures in Enso have some special syntax:

- The reserved name `in` is used to specify the monadic context in which a value
  resides. The Enso expression `a in IO` is equivalent to the Haskell
  `MonadIO a`.

  ```ruby
  foo : Int -> Int in IO
  ```

- The operator `!` is used to specify the potential for an _error_ value in a
  type. The expression `a ! E` says that the type is either an `a` or an error
  value of type `E`.

  ```ruby
  / : Number -> Number -> Number ! ArithError
  ```

In Enso, a type signature operates to constrain the values that a given variable
can hold. Type signatures are _always_ checked, but Enso may maintain more
specific information in the type inference and checking engines about the type
of a variable. This means that:

- Enso will infer constraints on types that you haven't necessarily written.
- Type signatures can act as a sanity check in that you can encode your 
  intention as a type 

### Operations on Types
Enso also provides a set of rich operations on its underlying type-system notion
of typesets. Their syntax is as follows:

- **Union - `|`:** The resultant typeset may contain a value of the union of its
  arguments.
- **Intersection - `&`:** The resultant typeset may contain values that are
  members of _both_ its arguments.
- **Subtraction - `\`:** The resultant typeset may contain values that are in
  the first argument's set but not in the second.

### Type Definitions
Types in Enso are defined by using the `type` reserved name. This works in a
context-dependent manner that is discussed properly in the
[type system design document](../types/types.md), but is summarised briefly
below.

- **Name and Fields:** When you provide the keyword with only a name and some
  field names, this creates an atom.

  ```ruby
  type Just value
  ```

- **Body with Atom Definitions:** If you provide a body with atom definitions,
  this defines a smart constructor that defines the atoms and related functions
  by returning a typeset.

  ```ruby
  type Maybe a
      Nothing
      type Just (value : a)

      isJust = case self of
          Nothing -> False
          Just _ -> True

      nothing = not isJust
  ```

- **Body Without Atom Definitions:** If you provide a body and do not define any
  atoms within it, this creates an interface that asserts no atoms as part of
  it.

  ```ruby
  type HasName
  name: String
  ```

In addition, users may write explicit `self` constraints in a type definition,
using the standard type-ascription syntax:

```ruby
type Semigroup
    <> : self -> self

type Monoid
    self : Semigroup
    use Nothing
```

## Macros
Enso provides a macro system that allows users to perform AST to AST
transformations on the provided pieces of code. While many languages' macros
provide their users with access to the compilation and type-checking phases
(scala, for example), there are a few reasons that we don't want to:

- The power of a dependently-typed language obviates the need for the ability to
  manipulate types at compile time.
- Syntactic macros are far more predictable than those that can perform type
  manipulation and compute values.
- We do not want to introduce a metaprogramming system that is too complex.

> The actionables for this section are:
>
> - Fully specify the macro system.
> - Fully specify the interactions between the parser-based macro system and the
>   runtime.

### Annotations
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

#### Automatic Deriving
In order to make the language easier to debug, we have all types automatically
derive an interface `DebugShow`. This interface provides a function that will
print all the significant information about the value (e.g. locations, types,
source information, etc).

## Top-Level Syntax
Like almost all statically-typed programming languages, the top-level of an Enso
file is non-executable. The top level may contain the following constructs:

- Type definitions (both complex and simple)
- Method definitions
- Function definitions

> The actionables for this section are as follows:
>
> - Fully specify the top-level syntax for Enso.

### Main
The entry point for an Enso program is defined in a special top-level binding
called `main` in the file `Main.enso`. However, we also provide for a scripting
workflow in the form of `enso run`, which will look for a definition of `main`
in the file it is provided.

```ruby
main = IO.println "Hello, World!"
```

### Against Top-Level Evaluation
At points during Enso's development it was up for debate as to whether we wanted
the language to have an executable top-level (akin to scripting languages like
Python). In order to make a decision, we listed the following use cases, and the
corresponding analysis is provided here for posterity.

|  Label  | Meaning |
| --------| ------- |
| `[?,_]` | We don't know how to implement it, but it may be possible.
| `[-,_]` | Not possible to implement using purely syntactic macros.
| `[M,_]` | Possible to implement using purely syntactic macros.
| `[_,H]` | High priority. This will be used often.
| `[_,M]` | Medium priority. This will be used with a medium frequency.
| `[_,L]` | Low priority. Nice to have, but we can likely live without it.
| `[_,!]` | Something that we never want to have in the language.

The use-cases we have considered are as follows:

|  Label  | Description |
| ------- | ----------- |
| `[-,L]` | Creating top-level constructs in `IO`, such as `IORef`. This is, in general, considered to be bad style, but can sometimes be useful. |
| `[-,L]` | Using enso files like python is able to be for scripting work. The ability to write constructs at the top-level and just evaluate them. |
| `[M,H]` | The ability to generate structures and / types for a dataframe at compilation time, or the automatic generation of an API for a library. A key recognition is that dependent types and type-level execution replace much of the need to be able to query the type-checker and runtime while writing a syntactic macro. |
| `[M,H]` | Static metaprogramming (transformations from `AST -> AST`) to let users generate types and functions based on existing AST. There is the potential to want to be able to evaluate actions in `IO` while doing this, but it may not be necessary. |
| `[-,!]` | Dynamic metaprogramming to let users mutate program state at runtime (e.g. changing atom shapes, function definitions), also known as 'monkey patching'. This is not something we want in the language, but we do perhaps want the ability to do so on values of type `Dynamic`. |
| `[M,H]` | 'Remembering' things when compiling a file, such as remembering all structures marked by an `AST` annotation. An example use case for a mechanism like this is to generate pattern matches for all possible `AST` types. This can be done by letting macros write to a per-file peristent block of storage that could be serialised during precompilation. |
| `[M,H]` | Grouping of macros (e.g. `deriveAll = derive Ord Debug Show`). This can be easily handled by doing discovery on functions used as macros, and treating it as a macro as well. |
| `[?,M]` | Method-missing magic, akin to ruby. This is likely able to be handled using other, existing language mechanisms. |

In summary and when considering the above use-cases, it seems that there is
little need for top-level expression evaluation in Enso. We can support all of
the above-listed important use-cases using syntactic (`AST -> AST`) macros,
while allowing for top-level evaluation would enable users to write a lot of
overly-magical code, which will always be a code-smell.

Syntactic macros, however, do not easily support a scripting workflow, but the
solution to this problem is simple. We can just provide an `enso run <file>`
command which will search for and execute the `main` function in the provided
file.

## Functions
Enso is a purely-functional programming language. As a result it has support for
[first-class and higher-order functions](https://en.wikipedia.org/wiki/Functional_programming#First-class_and_higher-order_functions),
meaning that you can pass functions as arguments to other functions, return
functions from functions, assign them to variables, store them in data
structures and so on.

Functions in Enso are curried by default.

### Lambdas
The most primitive non-atom construct in Enso is the lambda. This is an
anonymous function in one argument. A lambda is defined using the `->` operator,
where the left hand side is an argument, and the right hand side is the body of
the function (containing arbitrary code).

Some functional languages such as Haskell allow for the definition of a lambda
with multiple arguments, but in Enso the type signature use of `-> `and the 
lambda use of `->` are one and the same. We do not want to have to put the
components of a type signature in parentheses, so we only allow one argument
before each arrow.

- Lambdas can close over variables in their surrounding scope.
- If you want to define a multi-argument lambda, you can do it by having a 
  lambda return another lambda (e.g. `a -> b -> a + b`).

> The actionables for this section are:
> 
> - Clarify whether we _really_ want to disallow the `a b -> a + b` multi-arg
>   lambda syntax.

### Defining Functions
A function definition is just syntactic sugar for the definition of a lambda,
and hence has all the properties that a lambda does.

### Calling Functions

### Methods
Enso makes a distinction between functions and methods. In Enso, a method is a
function where the first argument (known as the `this` argument) is associated
with a given atom.

Methods can be defined in Enso in two ways:

1. **In the Body of a Type:** A function defined in the body of a `type`
   definition is automatically converted to a method on all the atoms defined in
   the body of that type definition.

  ```ruby
  type Maybe a
      Nothing
      type Just (value : a)

      isJust = case self of
          Nothing -> False
          Just _ -> True
  ```

2. **As an Extension Method:** A function defined _explicitly_ on an atom counts
   as an extension method on that atom. It can be defined on a typeset to apply
   to all the atoms within that typeset.

  ```ruby
  Number.floor = case this of
      Integer -> ...
      ...
  ```

#### This vs. Self
Though it varies greatly between programming languages, we have chosen `this` to
be the name of the 'current type' rather than `self`. This is a purely aesthetic
decision, and the final clincher was the ability to write `this` and `that`, as
opposed to `self` and `that`.

### Blocks
Top-level blocks in the language are evaluated immediately. This means that the
layout of the code has no impact on semantics of the code:

- To suspend blocks, we provide a `suspend` function in the standard library.
  This means that the following `a` and `b` are equivalent.

  ```ruby
  a = foo x y

  b =
    foo x y
  ```

- This function takes any expression as an argument (including a block), and
  suspends the execution of that expression such that it is not evaluated until
  forced later.

  ```ruby
  susp = suspend
    x = foo x y z
    x.do_thing
  ```

Additionally, in the current syntax, a block assigned to a variable is one that
has its execution implicitly suspended until it is forced. This has a few
things that should be noted about it.

- We could have a `suspend` function provided in the standard library, as
  laziness of argument evaluation is determined through type-signatures and is
  done automatically in the compiler.
- Such a function would likely not see heavy use.

## Function Arguments

### Named Arguments

### Defaulted Arguments

### Positional Arguments

### Optional Arguments

### Splats Arguments

### Type Applications

## Scoping Rules

### Variable Scoping

### Name Visibility

## Field Access

### Pattern Matching

#### The Underscore in Pattern Matching
`const a _ = a` behaves differently than underscore in expressions (implicit
lambda).

### Lenses


















#### Creating and Using Functions

Functions are defined in a similar way to variables. The only difference is that
the function name is followed by parameters separated by spaces. For example,
the following code defines a function taking two values and returning their sum.

```haskell
sum x y = x + y
```

Putting a space between two things in expressions is simply _function
application_. For example, to sum two numbers by using the function defined
above, simply write `sum 1 2`.

Under the hood, the function definition is translated to a much more primitive
construct, a variable assigned with an expression of nested, unnamed functions,
often referred to as lambdas. In contrast to the function definition, lambda
definition accepts a single argument only:

Functions allow expressing complex logic easily by encapsulating and reusing
common behaviors. The following code defines a sequence of one hundred numbers,
uses each of them to get a new random number, discards everything but the first
10 numbers, and then sorts them. Please note the usage of the `each` function,
which takes an action and a list as arguments, and applies the action to every
element of the list. The `random` returns a pseudo-random number if applied with
a seed value (it always returns the same value for the same seed argument).

```haskell
list       = 1 .. 100
randomList = each random list
headOfList = head 10 randomList
result     = sort headOfList
```

#### Function Type

As the function definition translates under the hood to an ordinary variable
assignment, you can use the type expression to provide the compiler with an
additional information about arguments and the result type. In the same fashion
to variables, if no explicit type is provided, the type is assigned with the
value itself:

```haskell
sum : x -> y -> x + y
sum = x -> y -> x + y
```

By using an explicit type, you can narrow the scope of possible values accepted
by the function. For example, the above definition accepts any type which can be
concatenated, like numbers or texts, while the following one accepts numbers
only:

```haskell
sum : Number -> Number -> Number
sum = x -> y -> x + y
```

> Is this still true? To do with type signatures

Each function is assigned with an _arity_. Although you will not often use this
term when writing the code, it's a useful concept used later in this document.
Arity is the number of arguments and lambdas statically used in the function
definition. Note that arity is not deducible from the type. For example, the
function `fn` has the arity of `2` even though its type suggests it takes `3`
arguments:

```haskell
fn : Bool -> Bool -> Bool -> Bool
  fn a = b -> case a && b of
    True  -> not
    False -> id
```

#### Code Blocks

You can think of code blocks like about functions without arguments. Code blocks
do not accept arguments, however they can invoke actions when used. Let's just
see how to define and use a code block. The definition is just like a variable
definition, however, there is a new line immediately after the `=` sign.
Consider the following code. It just ask the user about name and stores the
answer in the `name` variable:

```haskell
print "What is your name?"
name = Console.get
```

We can now define a main function, or to be more precise, the main code block:

```haskell
getName =
    print "What is your name?"
    Console.get
```

In contrast to expression, code blocks are not evaluated immediately. In order
to evaluate the code block, simply refer to it in your code:

```haskell
greeter =
    name = getName
    print "It's nice to meet you, #{name}!"
```

You may now wonder, what the type of a code block is. The code block `getName`
returns a `Text`, so your first guess may be that it's type is simply
`getName : Text`. Although the compiler is very permissive and will accept this
type signature, the more detailed one is `getName : Text in IO`, or to be really
precise `getName : Text in IO.Read ! IO.ReadError`. A detailed description of
how code blocks work and what this type means will be provided in the chapter
about contexts later in this book.

There are rare situations when you want to evaluate the code block in place. You
can use the `do` keyword for exactly this purpose. The do function just accepts
a code block, evaluates it and returns its result. An example usage is shown
below:

```haskell
greeter =
    name = do
        print "What is your name?"
        Console.get
    print "It's nice to meet you, #{name}"
```

Without the `do` keyword the code block would not be executed and `name` would
refer to the code block itself, not its final value.

#### Uniform Calling Syntax (UCS)

Enso uses Uniform Calling Syntax which generalizes two function call notations
`lst.map +1` and `map +1 lst`. The generalization assumes flipped argument order
for operators, so `a + b` is equal to `a.+ b`. Paraphrasing Bjarne Stroustrup
and Herb Sutter, having two call syntaxes makes it hard to write generic code.
Libraries authors will either have to support both syntaxes (verbose,
potentially doubling the size of the implementation) or make assumptions about
how objects of certain types are to be invoked (and we may be wrong close to 50%
of the time).

Each of these notations has advantages but to a user the need to know which
syntax is provided by a library is a bother. Thus implementation concerns can
determine the user interface. We consider that needlessly constraining.

The following rules apply:

- Two notations, one semantics. Both notations are equivalent and always resolve
  to the same behavior.

- The expression `base.fn` is a syntactic sugar for `fn (this=base)`. In most
  cases, the `this` argument is the last argument to a function, however,
  sometimes the argument name could be omitted. Consider the following example
  including sample implementation of the concatenation operator:

  ```haskell
  >> : (a -> b) -> (b -> c) -> a -> c
  >> f g this = g $ f this

  vecLength = map (^2) >> sum >> sqrt
  print $ [3,4].vecLength -- Result: 5
  ```

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

For example, the following code results in a compile time error. The self type
`[Int, Int]` is strictly more specific than the type `[a,b]` and thus this
candidate was selected in the step 3 of the algorithm. However, it is impossible
to unify `1` and `Text`.

```haskell
test = n -> [a,b] ->
    [a+n, b+n]

test : Text -> [Int, Int] -> [Text, Text]
test = s -> [a,b] ->
    [s + a.show , s + b.show]

[1,2].test 1
```

#### Operators



> Space-based operator parsing rules. `a.b` vs. `a . b`.

Operators are functions with non alphanumeric names, like `+`, `-` or `*`.
Operators are always provided with two arguments, one on the left, one one the
right side, for example, in order to add two numbers together you can simply
write `1 + 2`. It could be a surprise, but we've been using a lot of operators
so far – a space is a special operator which applies arguments to functions!
Space has a relatively high precedence, higher than any operator, so the code
`max 0 10 + max 0 -10` is equivalent to `(max 0 10) + (max 0 -10)`. Another
interesting operator is the field accessor operator, often referred to as the
dot operator. It is used to access fields of structures. For example, to print
the first coordinate of a point `pt` you can simply write `print pt.x`. However,
please note that the way the accessor function behaves differs from probably
every language you've learned so far. You'll learn more about it in the
following sections.

Enso gives a lot of flexibility to developers to define custom operators.
Formally, any sequence of the following characters forms an operator
`.!$%&*+-/<>?^~\`. The operator definition is almost the same as function
definition, with an optional precedence relation declaration. Consider the
following definition from the standard library:

```haskell
@prec  [> *, < $]
@assoc left
a ^ n = a * a ^ (n-1)
```

The `prec` decorator specifies the
[precedence relation](https://en.wikipedia.org/wiki/Order_of_operations) to
other operators. Here, we specified that the precedence is higher than the
precedence of the multiplication operator. The precedences are inherited in
Enso, so if the multiplication operator was defined with a higher precedence
than addition, the new operator above would inherit this dependency as well. The
`assoc` decorator defines the
[operator associativity](https://en.wikipedia.org/wiki/Operator_associativity) –
it is either left, right or none. If you do not provide the information, no
precedence relations would be defined and the associativity will default to
left.

#### Precedence

Operator precedence is a collection of rules that reflect conventions about
which procedures to perform first in order to evaluate a given mathematical
expression. For example, multiplication operator is given higher
precedence than addition, which means that multiplication will be
performed before addition in a single expression like `2 + 5 * 10`.

However, in contrast to most languages, the operator precedence depends on
whether a particular operator is surrounded with spaces or not. **The precedence
of any operator not surrounded with spaces is always higher than the precedence
of any operator surrounded with spaces.** For example, the code `2+5 * 10`
results in `70`, not `50`!

The space-based precedence allows for writing much cleaner code than any other
functional language, including all languages from the ML family, like Haskell,
Agda or Idris. Let's consider the previous example:

```haskell
list       = 1 .. 100
randomList = each random list
headOfList = head 10 randomList
result     = sort headOfList
```

It could be easily refactored to a long one-liner:

```haskell
result = sort (head 10 (each random (1 .. 100)))
```

Such expression is arguably much less readable than the original code, as it
does not allow to read in a top-bottom, left-right fashion. However, by using
the Uniform Calling Syntax, we can further transform the code:

```haskell
result = (((1 .. 100).each random).head 10).sort
```

Much better. We can now read the expression from left to right. The result is
still a little bit verbose, as we need to use many nested parentheses. The
space-based precedence combined with the fact that the accessor is just a
regular operator in Enso allow us to throw them away! The rule is simple – the
space operator has higher precedence than any operator surrounded with spaces:

```haskell
result = 1..100 . each random . head 10 . sort
```

#### Sections

Operator section is just a handy way to apply the left or the right argument to
an operator and return a curried function. For example, the expression `(+1)` is
a function accepting a single argument and returning an incremented value.
Incrementing every value in a list is a pure joy when using sections:

```haskell
list  = 1 .. 100
list2 = list.each (+1)
```

Because the space-based precedence applies to sections as well, the above code
may be further simplified to:

```haskell
list  = 1 .. 100
list2 = list.each +1
```

Another interesting example is using the accessor operator with the section
syntax. The following code creates a list of one hundred spheres with random
positions sorts them based on the first position coordinate. The `.position.x`
is just a section which defines a function taking a parameter and returning its
nested field value.

```haskell
spheres       = 1..100 . each i -> sphere (position = point i.random 0 0)
sortedSpheres = spheres . sortBy .position.x
```

#### Mixfix Functions

- Mixfix definitions use a 'separated' snake case (e.g. `if c _then a _else b`).
Mixfix functions are just functions containing multiple sections, like
`if ... then ... else ...`. In Enso, every identifier containing underscores
indicates a mixfix operator. between each section there is always a single
argument and there is a special syntactic sugar for defining mixfix operators.
Consider the implementation of the `if_then_else` function from the standard
library:

```haskell
if cond _then (ok in m) _else (fail in n) =
    case cond of
        True  -> ok
        False -> fail
```

For now, please ignore the `in m` and `in n` parts, you will learn about them in
the following chapters. When using mixfix functions, all the layout rules apply
like if every section was a separate operator, so you can write an indented
block of code after each section. Consider the following example, which asks the
user to guess a random number:

```haskell
main =
    print 'Guess the number (1-10)!'
    guess  = Console.get
    target = System.random 1 10

    if guess == target then print 'You won!' else
        print 'The correct answer was #{target}'
        answerLoop

    answerLoop =
        print 'Do you want to try again? [yes / no]'
        answer = Console.get
        case answer of
            'yes' -> main
            'no'  -> nothing
            _     ->
                print "I don't understand."
                answerLoop
```

#### Arguments

#### Named Arguments

Unlike the majority of purely functional programming languages, Enso supports
calling functions by providing arguments by name. Consider a function that
creates a sphere based on the provided radius, position, color and geometry type
(like polygons or
[NURBS](https://en.wikipedia.org/wiki/Non-uniform_rational_B-spline)). All the
arguments are named and can be used explicitly when evaluating the function.

```haskell
sphere : Number -> Point -> Color -> Geometry.Type
sphere radius position color type = undefined
```

Remembering the order of the arguments is cumbersome. Such code is also often
hard to understand and reason about:

```haskell
s1 = sphere 10 (point 0 0 0) (color.rgb 0.5 0.5 0.5) geometry.NURBS
```

By using named arguments, we can transform the code to:

```haskell
s1 = sphere (radius = 10) (position = point 0 0 0) (color = color.rgb 0.5 0.5 0.5)
            (creator = geometry.NURBS)
```

By applying the layout rules described above, we can transform the code to a
much more readable form:

```haskell
s1 = sphere
    radius   = 10
    position = point 0 0 0
    color    = color.rgb 0.5 0.5 0.5
    creator  = geometry.NURBS
```

#### Default Arguments

Consider the sphere example above again. Providing always all the arguments
manually is both cumbersome and error prone:

```haskell
s1 = sphere 10 (point 0 0 0) (color.rgb 0.5 0.5 0.5) geometry.NURBS
```

Function definition allows providing a default value to some of the arguments.
The value will be automatically applied if not provided explicitly. For example,
the above code is equivalent to:

```haskell
s1 = sphere 10
```

Informally, when you call a function, Enso will traverse all not provided
arguments in order and will apply the default values unless it finds the first
argument without a default value defined. To disable this behavior, you can use
the special `...` operator. The following code creates a curried function which
accepts radius, color and geometry type and creates a sphere with radius of
placed in the center of the coordinate system:

```haskell
centeredSphere radius = sphere radius (point 0 0 0) ...
```

By using the `...` operator in combination with named arguments, we can make the
code much more readable:

```haskell
centeredSphere = sphere
    position = point 0 0 0
    ...
```

#### Positional Arguments

Enso supports so called positional arguments call syntax. Consider the sphere
example above. How can you define a new function which accepts radius, color and
geometry type and returns a sphere always placed in the center of the coordinate
system? There are few ways. First, you can create the function explicitly (you
will learn more about function definition in the following chapters):

```haskell
originSphere radius color creator = sphere radius (point 0 0 0) color creator
```

Alternatively, you can use the positional arguments call syntax:

```haskell
originSphere = sphere _ (point 0 0 0) _ _
```

Of course, you can combine it with the operator canceling default argument
application:

```haskell
originSphere = sphere _ (point 0 0 0) ...
```

There is an important rule to remember. Enso gathers all positional arguments
inside a particular function body or expression enclosed in parentheses in order
to create a new function, so the following code creates a function accepting two
arguments. It will result the sum of the first argument squared and the second
argument.

```haskell
squareFirstAndAddSecond = _ ^2 + _
```

#### Optional Arguments

Optional arguments are not a new feature, they are modeled using the default
arguments mechanism. Consider the following implementation of a `read` function,
which reads a text and outputs a value of a particular type:

```haskell
read : Text -> t -> t
read text this = t.fromText text
```

You can use this function by explicitly providing the type information in either
of the following ways:

```haskell
val1 = read '5' Int
val2 = Int.read '5'
```

However, the need to provide the type information manually could be tedious,
especially in context when such information could be inferred, like when passing
`val1` to a function accepting argument of the `Int` type. Let's re-write the
function providing the default value for `this` argument to be ... itself!

```haskell
read : Text -> (t=t) -> t
read text (this=this) = t.fromText text
```

The way it works is really simple. You can provide the argument explicitly,
however, if you don't provide it, it's just assigned to itself, so no new
information is provided to the compiler. If the compiler would not be able to
infer it then, an error would be raised. Now, we are able to use it in all the
following ways:

```haskell
fn : Int -> Int
fn = id

val1 = read '5' Int
val2 = Int.read '5'
val3 = read '5'
fn val3
```

Enso provides a syntactic sugar for the `t=t` syntax. The above code can be
written in a much nicer way as:

```haskell
read : Text -> t? -> t
read text this? = t.fromText text
```

#### Splats Arguments

Enso provides both args and kwargs splats arguments. You can easily construct
functions accepting variable number of both positional as well as keyword
arguments. Splats arguments are an amazing utility mostly for creating very
expressive EDSLs. Consider the following function which just prints arguments,
each in a separate line:

```haskell
multiPrint : args... -> Nothing in IO
multiPrint = args... -> args.each case
    Simple      val -> print val
    Keyword key val -> print '#{key} = #{val}'

multiPrint 1 2 (a=3) 4
```

```haskell
--- Results ---
1
2
a = 3
4
```

#### Variable Scoping

Type variables, function variables, and lambda variables live in the same space
in Enso. Moreover, as there is no distinction between types and values, you can
use the same names both on type level as well as on value level because they
refer to the same information in the end:

```haskell
mergeAndMap : f -> lst1 -> lst2 -> out
mergeAndMap = f -> lst1 -> lst2 -> (lst1 + lst2) . map f
```

If different names are used on type level and on a value level to refer a
variable, it's natural to think that this variable could be pointed just by two
separate names. The following code is valid as well:

```haskell
mergeAndMap = (f : a -> b) -> (lst1 : List a) -> (lst2 : List a) -> (lst1 + lst2) . map f
```

Which, could also be distributed across separate lines as:

```haskell
mergeAndMap : (a -> b) -> List a -> List a -> List b
mergeAndMap f lst1 lst2 = (lst1 + lst2) . map f
```

An interesting pattern can be observed in the code above. Taking in
consideration that every value and type level expressions have always the same
syntax, the lambda `(a -> b) -> List a -> List a -> List b` could not have much
sense at the first glance, as there are three pattern matches shadowing the
variable `a`. So how does it work? There is an important shadowing rule. **If in
a chain of lambdas the same name was used in several pattern matches, the names
are unified and have to be provided with the same value.** The chain of lambdas
could be broken with any expression or code block.

This rule could not make a lot of sense on the value level, as if you define
function `add a a = a + a` you will be allowed to evaluate it as `add 2 2`, but
not as `add 2 3`, however, you should not try to use the same names when
defining functions on value level nevertheless.

By applying this rule, the type of the above example makes much more sense now.
Especially, when evaluated as `mergeAndMap show [1,2] ['a','b']`, the variables
will be consequently instantiated as `a = Number | Text`, and `b = Text`, or to
be very precise, `a = 1|2|'a'|'b'`, and `b = '1'|'2'|'a'|'b'`.

Because type variables are accessible in the scope of a function, it's
straightforward to define a polymorphic variable, whose value will be an empty
value for the expected type:

```haskell
empty : t
empty = t.empty

print (empty : List Int) -- Result: []
print (empty : Text)     -- Result: ''
```

#### Type Applications

All libraries that sometimes need passing of an explicit type from the user
should be designed as the `read` utility, so you can optionally pass the type if
you want to, while it defaults to the inference otherwise. However, sometimes
it's handy to just ad-hoc refine a type of a particular function, often for
debugging purposes. Luna allows to both apply values by name as well as refining
types by name. The syntax is very similar, consider this simple function:

```haskell
checkLength : this -> Bool
checkLength this =
    isZero = this.length == 0
    if isZero
        then print "Oh, no!"
        else print "It's OK!"
    isZero
```

This function works on any type which has a method `length` returning a number.
We can easily create a function with exactly the same functionality but its
input type restricted to accept lists only:

```haskell
checkListLength = checkLength (this := List a)
```

As stated earlier, in most cases there is a nicer way of expressing such logic.
In this case, it would be just to create a function with an explicit type. The
following code is equivalent to the previous one:

```haskell
checkListLength : List a -> Bool
checkListLength = checkLength
```

#### Field Modifiers (lenses)

You can add the equal sign `=` as an operator suffix to transform it into a
modifier. Modifiers allow updating nested structures fields.

In general, the following expressions are equivalent. The `+` is used as an
example and can be freely replaced with any other operator:

```haskell
foo' = foo.bar += t
-- <=>
bar'  = foo.bar
bar'' = t + bar'
foo'  = foo.bar = bar''
```

Please note the inversed order in the `t + bar` application. In most cases it
does not change anything, however, it simplifies the usage of such modifiers as
`foo.bar $= f` in order to modify a nested field with an `f` function.

Examples:

```haskell
type Vector
    V3 x:Number y:Number z:Number

type Sphere
    MkSphere
        radius   : Number
        position : Vector

-- Position modification
s1 = MkSphere 10 (V3 0 0 0)
s2 = s1.position.x += 1

-- Which could be also expressed as
p1 = s1.position
p2 = p1.x += 1
s2 = s1.position = p2

-- Or as a curried modification
s2 = s1.position.x $= +1
```

#### Prisms

Alternative map implementations:

```haskell
type Shape a
    Circle
        radius:a
    Rectangle
        width:a
        height:a

map1 : (a -> b) -> Shape a -> Shape b
map1 f self = case self of
    Circle    r   -> Circle    (f r)
    Rectangle w h -> Rectangle (f w) (f h)

map2 : (a -> b) -> Shape a -> Shape b
map2 f self = self
    ? radius $= f
    ? width  $= f
    ? height $= f

map3 : (a -> b) -> Shape a -> Shape b
map3 f self = if self.is Circle
    then self . radius $= f
    else self . width  $= f
              . height $= f

map4 : (a -> b) -> Shape a -> Shape b
map4 f self =
    maybeNewCircle    = self.circle.radius $= f
    maybeNewRectangle = self.rectangle.[width,height] $= f
    case maybeNewCircle of
        Just a  -> a
        Nothing -> case maybeNewRectangle of
            Just a  -> a
            Nothing -> error "impossible"
```


#### Explicit type signatures

Enso was designed in a way to minimize the need for explicit type signatures.
However, you are always free to provide one to check your assumptions regarding
the types. There are two major ways explicit type signatures are used in Enso:

- **Explicit type constraints**
  Explicit type signatures in type and function definitions constrain the
  possible value set. For example, you will not be allowed to pass a text to a
  function provided with an explicit type `fn : Int -> Int`.

- **Explicit type checks**
  Explicit type signatures in other places in the code are used as type checks.
  If you type your variable as `Number`, it does not mean that Enso will forget
  the other information inferred so far. It will always check if the signature
  is correct and report an error in case it's not. For example, the following
  code will type check correctly.

  ```haskell
  dayNumber = 1 | ... | 7
  printDay : DayNumber -> Nothing
  printDay = print

  myDay = 1 : Number
  printDay myDay
  ```

**Example 1**

```haskell
square : (Text -> Text) | (Number -> Number)
square val = case val of
    Text   -> 'squared #{val}'
    Number -> val * val

action f a b = print 'The results are #{f a} and #{f b}'

main = action square "10" 10
```

**Example 2**

```haskell
foo : Number -> Text | Integer
foo = if x < 10 then "test" else 16

fn1 : Text | Number -> Number
fn1 = ...

fn2 : Text | Vector Number -> Number
fn2 = ...

fn3 : 16 -> 17
fn3 = +1

main =
    val = foo 12
    fn1 val -- OK
    fn2 val -- ERROR
    fn3 val -- OK
```
