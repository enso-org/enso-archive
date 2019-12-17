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

Many of these reserved words are implemented as macros in the parser, but these
macros are always in scope and cannot be overridden, hidden, or redefined.

> The actionables for this section are as follows:
>
> - In the future, we need to determine if we need `all` and `each` explicit
>   keywords in the case of dependency. Explicit examples are required.

## Layout Rules
Enso is a layout-aware programming language, in that layout rules are used to
determine code structure.

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
  (`\``) character. Splices are run, and then the result is converted to a
  string using `show`. These strings also have support for escape sequences.

  ```ruby
  fmt_string = 'Hello, my name is `time.now.year - 1990`'
  ```

### Inline Text Literals
In Enso, inline text literals are opened and closed using the corresponding
quote type for the literal.

### Text Block Literals


#### Indentation Blocks

Enso uses indentation to determine the structure of the code.

In general, every indented line constitutes a sub-structure of the nearest
previous line with a smaller indentation. We refer to them as child line and
parent line, respectively. There are a few additional layout rules:

- **Operator at the end of a parent line**
  If a line ends with an operator then all of its child lines form a code block.
  Code blocks in Enso are a syntactic sugar for monadic bindings, you will
  learn about them in later chapters. The most common usage is a function
  definition body after the last arrow operator:

  ```haskell
  test = a -> b ->
      sum = a + b
      print 'The sum is `sum`'
  ```

* **Operator at the beginning of a child line**
  If all the children lines start with operators, they form a single expression,
  while the operators behave left associative with the lowest precedence level.
  In other words, every line forms a separate expression and the final
  expression is built line-by-line, top to bottom. The most common usage is to
  use the dot operator to create chained method calls. Please note that the
  operator at the beginning of a child line is used after the line expression is
  formed, so in the following code both `tst1` and `tst2` have exactly the same
  value.

  ```haskell
  nums = 1..100
       . each random
       . sort
       . take 100

  tst1 = 12 * (1 + 2)
  tst2 = 12
       * 1 + 2
  ```

* **Otherwise**
  In all other cases, every child line is considered to form a separate
  expression passed as an argument to the parent expression. The most common
  usage is to split long expressions to multiple lines. The following example
  uses the named argument mechanism.

  ```haskell
  geo1 = sphere (radius = 15) (position = vector 10 0 10) (color = rgb 0 1 0)
  geo2 = sphere
      radius   = 15
      position = vector 10 0 10
      color    = rgb 0 1 0
  ```

- **Debug line breaker `\\`**

  There is also a special, debug line-break operator `\\` which, when placed at
  the beginning of a child line, tells Enso to glue the line with the previous
  one. However, the line-break operator should not be used in production code,
  as it's always better to re-structure the code to separate method calls
  instead. In the following code, both `debugFunc` and `validFunc` work in the
  same way, but the definition of `validFunc` is formatted properly.

  ```haskell
  debugFunc = v -> v2 ->
    print (v2.normalize * ((v.x * v.x) + (v.y * v.y)
      \\ + (v.z * v.z)).sqrt)

  validFunc = v -> v2 ->
    len = ((v.x * v.x) + (v.y * v.y) + (v.z * v.z)).sqrt
    v'  = v2.normalize * len
    print v'
  ```

#### Naming Rules

Naming convention unifies how code is written by different developers, increases
the immediate understanding and allows to provide compiler with useful
information in a very convenient fashion. In particular, pattern matching
requires a way to distinguish between free variables and already declared ones.
Enso uses a simple naming convention to provide such information:

- You are free to use capitalized and uncapitalized identifiers as function and
  variable names. Type definition identifier should always be capitalized.
- Capitalized and uncapitalized identifiers are not distinguishable and always
  refer to the same value.
- Using capitalized identifier to refer to uncapitalized one is allowed in
  pattern matching only. Using uncapitalized identifier to refer to capitalized
  one is disallowed.
- In pattern matching capitalized identifiers refer to values in the scope,
  while uncapitalized identifiers are used for free variables only.

Using upper case letters for constructors in pattern matching has important
benefits. Whenever you see an upper case identifier, you know it is a data
structure being taken apart, which makes it much easier for a human to see what
is going on in a piece of code. Moreover, while using this convention,
construction and pattern matching is as simple as writing the right name and
does not require any magic from the compiler or usage of special symbols.

#### Type System

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

#### Type Signatures

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

#### Variables

#### Bringing Variables to Scope

The only way to bring new variables into scope is by using pattern matching.
There are two places in the code where pattern matching occurs – on the left
side of assignment operator and on the left side of lambda operator.

```haskell
<pattern>  = ...
<pattern> -> ...
```

#### To Be Described

- explicit typing
- immutable memory

#### Functions

Enso is a purely functional programming language. It supports
[first-class and higher-order functions](https://en.wikipedia.org/wiki/Functional_programming#First-class_and_higher-order_functions),
which means that you can pass functions as arguments to other functions, return
them as functions results, assign them to variables, and store them in data
structures.

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

```haskell
sum = x -> y -> x + y
```

Expressing functions accepting multiple arguments as nested lambda expressions
is a clever trick, often referred to as _curried functions_. What does that
mean? The `sum` function looks like it takes two parameters and returns their
sum. In reality, doing `sum 1 2` first creates a function that takes a parameter
and returns either sum of `1` and that parameter. Then, `2` is applied to that
function and that function produces our desired result. That sounds like a
mouthful but it's actually a really cool concept. The following two calls are
equivalent:

```haskell
sum 1 2
(sum 1) 2
```

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

#### Open Questions

- Do we want to support explicit signatures for the following use case? The
  function `f` is applied with two named arguments, but we do not know their
  ordering. We only know that the function accepts at least `3` arguments and
  that the 3rd argument can be `7`.

  ```haskell
  test : a -> b -> (<<a,b>> -> 7 -> x) -> x
  test a b f = f (x=a) (y=b) 7
  ```

- Should this code work (double `b` name)? If so, what is the type signature
  containing names?

  ```haskell
  fn1 c b d = a + 1
  fn2 a b   = fn1

  value = fn2
      a = 1
      b = 2
      c = 3
      b = 4
      d = 5
  ```

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

#### Field Modifiers

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

#### Simplified Type Signatures

Types in Enso can be expressed in a very detailed form. Consider an `open`
function, which reads a file from disc. It's type could be expressed as:

```haskell
open : FilePath -> Text ! FileReadError in IO
```

The are two important operators used here. The first one is the `!` operator,
which just means that instead of this value, we can get an error. The second one
is the `in` operator, which tells

```haskell
openReadAndCompare
    : FilePath -> Bool ! (IOError | ConversionError) in IO & State Int
openReadAndCompare path =
    currentNumber = State.get
    contents      = open path
    convertedNum  = contents.as Int
    convertedNum < currentNumber
```

#### Record Types

```haskell

Point = {x: Number, y: Number, z: Number}

type Point
    x: Number
    y: Number
    z: Number

```

Pattern matching works in a structural manner. The same applies to `|`,
`&`,etc.

#### TO BE DESCRIBED NICER

#### Monadic arguments

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

#### Lists

Lists in Luna are defined as follows:

```haskell
type List a
    Cons value:a tail:(List a)
    End
```

And can be used like:

```haskell
lst1 = List.Cons 1 (List.Cons "foo" List.End)
     : List.Cons 1 (List.Cons "foo" List.End)
     : List (Int | String)
lst2 = [1,"foo"] : [1,"foo"] : List (Int | String)
```

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

#### Underscore in Pattern Matching

`const a _ = a` behaves differently than underscore in expressions (implicit
lambda).

#### Type Holes

```haskell
a :: ??
```

Creates a type hole, which will be reported by the compiler. Describe the
programming with type holes model. A good reference: http://hazel.org

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

#### ==== DEPRECATED (Useful parts) ====

#### Types

#### Types. Unified Classes, Modules and Interfaces

Enso unifies the abstraction of classes, modules and interfaces under a single
first-class umbrella. All of the following functionalities are provided by the
`type` keyword, resulting in a highly flexible language construct:

- **Classes.** Types provide containers for data and associated behavior.
- **Modules.** Types provide namespacing for code and data.
- **Interfaces.** Types provide behavior description required of a type.

At a fundamental level, the definition of a new `type` in Enso is the creation
of a (usually named) category of values described by the data and behavior it
possesses. These are first-class values in Enso, and can be created and
manipulated at runtime.

#### Type Signatures

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

It's worth to note that the type operator is just a regular operator with a very
low precedence and it is defined in the standard library.

#### Types as Classes

The following chapter describes the replacement for the currently used concept
of _classes_. We have been always dreaming about true dependently typed language
and the way classes currently work stands in the way to achieve the dreams. The
change is, however, not as drastic as it seems. It is rather a process of
extending the current model to provide more fine grained control over the
objects and types.

Enso is an Object Oriented programming language. It provides the notion of
objects and methods so at first glance, Enso types may seem like conventional
_classes_ from traditional object-oriented languages. However, these concepts
differ significantly. Enso types have much more power, yet much simpler design,
disallowing concepts like inheritance in favour of composition and algebraic
data types.

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

#### Methods

A method is a function associated with a given constructor. The primitive method
definition syntax is very similar to function definition, however it also
includes the constructor in its head:

```haskell
True.not  = False
False.not = True
Point x y z . length = (x^2 + y^2 + z^2).sqrt
Tuple a b . swap = Tuple b a
```

Most often methods are defined in the same module as the appropriate
constructors. Please refer to sections about interfaces and extension methods to
learn more about other possibilities.

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

#### Type combinators

The careful reader will notice here, that `int` is a category of all possible
integral numbers, while the numbers are considered constructors themselves. Enso
provides an operator used to join types together, the so called pipe operator.
The hypothetical `int` definition could look like `int = .. | -1 | 0 | 1 | ...`.
We can use this mechanism to easily express even complex type dependencies. For
example we can tell Enso that a particular value has the type of `int | text`.
Enso will allow us to either use pattern matching to discover at runtime which
type are we really dealing with or will allow to use only methods which have
common interface among all constructors described by the type. It will for
example allow us to print such value to the screen.

#### Pattern matching

The proposed syntax changes allow us to improve pattern matching rules and make
them much more understandable, especially for new users. As we have described
earlier, there is no need to use qualified constructor names or special cases in
patterns anymore. Moreover, a new form of pattern matching is introduced, the so
called "type pattern matching".

While constructors allow combining fields into a single structure and type
combinators allow joining types into more general ones, the pattern matching
mechanism allows going in the opposite direction. In the most common use case
pattern matching will be performed during runtime, however it is worth to note
that the Enso compiler has enough information to perform pattern matching during
compilation if the appropriate values could be deduced in the compilation
process. There are two forms of pattern matching, namely constructor pattern
matching and generalized type pattern matching. The former syntax is practically
identical to the existing one, while the later uses the `type` keyword to denote
that we are performing pattern matching on type descriptor. Let's see how the
new syntax looks like in practice:

```haskell
type shape a
    type Circle
        radius :: a

    type Rectangle
        width  :: a
        height :: a


main =
    c1 = Circle 5 :: shape int
    v  = if something then c1 else 0

    print case v of
        Circle r   -> 'it is circle'
        type shape -> 'it is other shape'
        _          -> 'it is something else'

    print case v of type
        shape -> 'it is shape'
        int   -> 'it is int'

```

#### Polymorphism

Formally polymorphism is the provision of a single interface to entities of
different types. Enso does not provide any special construction to support
polymorphism, because even very complex polymorphic types could be described
just by using type-level functions. Consider the following example code:

```haskell
type Point x y z
point a = Point a a a

main =
    p1 = Point 1 2 3 :: point int
    print p1
```

The `point` function is the most basic form of polymorphic type definition in
Enso. It defines all sets of points such that every point component belongs to
the provided type. To better understand this relation, please consider the
following valid expressions:

```haskell
p1 = Point 1 2 3 : Point 1 2 3
p1 = Point 1 2 3 : Point int int int
p1 = Point 1 2 3 : point int

Point 1 2 3 : Point 1 2 3 : Point int int int : point int
```

This is a very flexible mechanism, allowing expressing even complex ideas in a
simple and flexible manner. An example is always worth more than 1000 words, so
please consider yet another example usage:

```haskell
taxiDistance : point real -> point real -> real
taxiDistance p1 p2 = (p2.x - p1.x).abs + (p2.y - p1.y).abs + (p2.z - p1.z).abs

main =
    p1 = Point 1 2 3
    print $ taxiDistance p1
```

#### Generalized type definitions

While we can define constructors, methods and compose them to create more
powerful types using the described methods, such definitions require significant
amount of code and do not reflect the real dependencies between the definitions.
This is the reason why Enso provides a syntactic sugar allowing to define
everything we have learned so far in more concise form.

It is worth emphasizing that generalized type definitions are only a simpler way
to define multiple constructors, combine them into a common type and define
common methods. They do not provide any additional value or functionality. The
generalized type definition syntax is presented below:

```
typeDef = "type" varName [":" interface] [({consDef} | {consField})] [method]
```

The body of a type can contain functions, data, or even _other types_, and _yes_
because you were wondering, types _can_ be defined inductively or using a GADT
style. We can re-write the earlier provided definitions using the form as
follows:

```haskell
type bool
    type True
    type False

    not = case self of
        True  -> False
        False -> True
```

```haskell
type point a
    x y z = 0 : a

    length = (x^2 + y^2 + z^2).sqrt
```

```haskell
type tuple a b
    _ : a
    _ : b

    swap = Tuple b a
```

While using this form we define common methods on a set of constructors, like
the method `not`, and we use pattern matching to chose the right algorithm path,
this approach does not have any performance penalties, because the compiler is
provided with enough information to optimize this check away if the value is
known at compile time.

One important thing to note here is that if you don't define any explicit
constructors, an implicit one will be generated automatically and will be named
the same way as the type but starting with an upper-letter instead. Now we can
use the above definitions as follow:

```haskell
test check =
    p1 = Point 1 2 3 : point int
    p2 = Point 4 5 6 : point real
    px = if check then p1 else p2
    print px.length
```

**Bonus question**
What is the most concrete type of the `px` variable above if we do not have any
information about the value of `check`? The answer is of course
`px : (Point 1 2 3 | Point 4 5 6)`, which is a sub type of the type
`Point (1|4) (2|5) (3|6)`.

#### Types as Modules

The same notion of a type can be used to provide the functionality that is
traditionally expected of a _module_ (in the common, not ML sense). In most
programming languages, their module system provides a mechanism for code-reuse
through grouping and namespacing. Indeed, Enso's types provide both of these
functionalities:

- **Grouping of Code**
  A `type` declaration acts as a container for code, with functions able to be
  declared in its scope.
- **Namespacing**
  Unless otherwise declared (through a direct import statement), a `type` in
  Enso also provides a namespace to constructs declared inside its scope.

#### Files and modules

Files in Enso should contain at least one `type` definition, with one type named
the same as the file. This `type` is known as the 'primary' type, and it is this
type that is referred to when importing the 'module'. A file `data/map.luna` may
contain `type map`, `type helper` and various other types, but the only things
visible outside the file are the primary type and things defined or imported
into its scope. Inside the file, however, everything can be seen, with no need
to forward-declare.

#### Module Examples

The concepts are best illustrated by example. Consider the following type. If it
is imported simply as `import math` (see [Importing Types](#importing-types)),
then `pi` value is only accessible within the scope of `math` (by using
`math.pi`).

However, please note that `math.pi` is not some kind of a special construct for
a qualified data access. The `math` is a zero-argument constructor of the `math`
module (type). The expression `math.pi` is creating the module object and then
accessing its `pi` field. Of course such creation would be optimized away during
the compilation process.

File `math.luna`:

```haskell
type math
    pi: 3.14
```

File `main.luna`:

```haskell
type main
    import math
    main = print math.pi
```

#### Types as Interfaces

A type in Enso can also act as a 'contract', a specification of the behavior
expected of a type. The use of types as interfaces in Enso is, as you might
expect, contravariant. As long as the type satisfies the category defined by the
interface, it can be used in its place. This leads to the expected semantics
where a type `Foo` implementing `Bar` can be used where a `Bar` is expected.

Interfaces in Enso can range from general to very specific. As they define a
_category_ of values, interfaces can specify anything from function signatures
that must be present, all the way to names that must be present in the type's
scope and default behavior. The following are all valid ways to define types for
use as interfaces in Enso.

```haskell
-- This interface requires a function called someFunction with the correct sig.
type Interface1
    someFunction : Int -> String

-- This interface requires a function and a variable both named appropriately.
type (a : Numeric) => Interface2 a
    someVar : a

    someFunction : a -> a
    someFunction = ...

-- This interface requires a function foo with the appropriate type.
type Interface3 a = { foo : a -> a }
```

For more information on the last example, please read the section on
[anonymous types](#anonymous-types).

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

HOLES!!!

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

#### Imports

To go along with the new system proposed in this RFC around code modularity, the
syntax for dealing with imports has been tweaked slightly. The following import
syntaxes are valid:

- **Direct Imports:** These import the primary module from the file. This brings
  the type and its constructors into scope. For example `import Data.Map` would
  bring `Map` and its constructors into scope.
- **Specified Imports:** These allow the specification of additional functions
  to be brought into the current scope. For example `import Data.Map: fromList`
  would bring `Map`, its constructors and `fromList` into scope.
- **Renamed Imports:** These allow the programmer to rename the imported
  type. For example `import Data.Containers.Map as MapInterface` brings `Map`
  into scope named as `MapInterface`. Here, constructors are also imported.
- **Specialised Imports:** These allow the programmer to specialise type
  arguments as part of the import. For example `import Data.Map String` will
  import `Map` and its constructors with their first type arguments specialised
  to `String`.

These above import styles can be combined, for example renaming a partially
specialised import (`import Data.Map String as StringMap`), or specialising
functions imported into scope (`import Data.Map String: fromList`). Much like
curried type application seen elsewhere in this proposal, it is possible to
partially apply the type arguments of an import, as seen above.

<!-- #### The File Scope
Files in Enso should contain at least one `type`, with one type named the same
as the file. This `type` is known as the 'primary' type, and it is this type
that is referred to when importing the 'module'. A file `Data/Map.luna` may
contain `type Map`, `type Helper` and various other types, but the only things
visible outside the file are the primary type and things defined in its scope.
Inside the file, however, everything can be seen, with no need to
forward-declare. -->

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
