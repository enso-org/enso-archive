# Typing the Polyglot Bindings
The polyglot bindings inherently provide a problem for the Enso type system.
When many of the languages with which we can interoperate are highly dynamic and
flexible, or have significant mismatches between their type system and Enso's, 
we can only make a best effort attempt to maintain type safety across this
boundary.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Enso Values](#enso-values)
- [Polyglot Values](#polyglot-values)

<!-- /MarkdownTOC -->

## Enso Values
The underlying nature of our runtime allows us to pass Enso values across the 
polyglot boundary while ensuring that they aren't modified. This means that the
typing information known about a value `v` _before_ it is passed to a polyglot
call is valid after the polyglot call, as long as the following properties hold:

- 

## Polyglot Values
In the presence of a polyglot value, however

// TODO [AA] Move the dynamic design from typing.
