# Caching
Given that Enso is a highly interactive language and platform, we want to take
every measure to ensure that we provide a highly responsive experience to our
users. To that end, one of the key tenets of the new runtime's featureset for
aiding in this is the inclusion of a _caching_ mechanism.

Caching, in this case, refers to the runtime's ability to 'remember' the values
computed in the currently observed scopes. In combination with the data
dependency analysis performed by the compiler, this allows the runtime to
recompute the _minimal_ set of expressions when the user makes a change, rather
than having to recompute the entire program.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Dataflow Analysis](#dataflow-analysis)
    - [Identifying Expressions](#identifying-expressions)
- [Cache Eviction Strategy](#cache-eviction-strategy)

<!-- /MarkdownTOC -->

## Dataflow Analysis
Dataflow analysis is the process by which the compiler discovers the
relationships between program expressions. The output of the process is a data
dependency graph that can be queried for an expression, and returns the set of
all expressions that depended on that expression.

Internally we represent this as a directed graph:

- An edge from `a` to `b` indicates that the expression `a` is depended on by
  the expression `b`.
- These dependencies are _direct_ dependencies on `a`.
- We reconstruct transitive dependencies from this graph.

An expression `a` can be any Enso expression, including definitions of dynamic
symbols. Given that dynamic symbols need not be in scope, care has to be taken
with registering them properly.

### Identifying Expressions

> The actionables for this section are:
>
> - Work out how we want to identify expressions for the purposes of caching.

## Cache Eviction Strategy
The cache eviction strategy refers to the process by which, for a given change,
we decide which elements should be evicted from the cache. In the current form,
the following rules are applied when an expression identified by some key `k` is
changed:

1. All expressions that depend on the result of `k` are evicted from the cache.
2. If `k` is a dynamic symbol, all expressions that depend on _any instance_ of
   the dynamic symbol are evicted from the cache.

Expressions that have been evicted from the cache subsequently have to be
recomputed by the runtime.
