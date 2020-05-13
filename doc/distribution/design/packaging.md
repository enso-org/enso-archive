# Library Packaging Design

## Build Reproducibility
It is crucial for any good development environment to provide reproducible
builds, such that it is impossible for it to go wrong by mismatching library
versions.

> The actionables for this section are:
> - Decide on the strategies of ensuring consistent library resolution. This
>   may include hashing the downloaded versions of libraries and publishing
>   stack-style resolvers for sets of libraries that are proven to work well
>   together.