---
layout: developer-doc
title: Searcher
category: runtime
tags: [runtime, search, execution]
order: 7
---

# Searcher
The language auto-completion feature requires an ability to search the codebase
using different queues. This document describes the Searcher module, which
consists of the suggestions database and the ranking algorithm for ordering the
results.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Suggestions Database](#suggestions-database)
  - [Static Analysis](#static-analysis)
  - [Runtime Inspection](#runtime-inspection)
  - [Search Indexes](#search-indexes)
- [Results Ranking](#results-ranking)

<!-- /MarkdownTOC -->

## Suggestions Database
Suggestions database holds entries for fulfilling the auto-completion requests
with additional indexes used to answer the different search criteria. The
database is populated partially by analyzing the sources and enriched with the
extra information from the runtime.

### Static Analysis
The database is filled by analyzing the _IR_ parsed from the source.

Language constructs for suggestions database extracted from the _IR_:

- Atoms
- Methods
- Functions
- Local assignments
- Documentation

In addition, it holds the locality information for each entry. Locality
information specifies the scope where the node was defined, which is used in the
results [ranking](#results-ranking) algorithm.

### Runtime Inspection
The _IR_ is missing information about the types. Runtime instrumentation is used
to capture the types and refine the database entries.

Information for suggestions database gathered in runtime:

- Types

### Search Indexes
The database allows searching the entries by the following criteria, applying
the [ranking](#results-ranking) algorithm:

- name
- type
- documentation text
- documentation tags

## Results Ranking
The search result has an intrinsic ranking based on the scope and the type specificity.

### Scope
The results from the local scope have the highest rank in the search result. As
the scope becomes wider, the rank decreases.

``` ruby
const_x = 42

main =
    x1 = 0
    foo x =
        x2 = x + 1
        calculate #
```

For example, when completing the argument of `calculate: Number -> Number`
function, the search results will have the order of: `x2` > `x1` > `const_x`.

### Type

The values in Enso can have multiple types. Suggestions based on the type are
ranked from the most specific to the most abstract.

``` ruby
7 : 7 : Natural : Integer : Number : Any : Any : ...
```

The types are ordered based on the typeset subsumption described in the [Type
Hierarchy](../types/hierarchy.md) document.
