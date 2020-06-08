---
layout: developer-doc
title: Instruments
category: runtime
tags: [runtime, instruments]
order: 6
---

# Instruments
Instruments are used to track runtime events to allow for profiling, debugging
and other kinds of runtime behavior analysis.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Naming conventions](#naming-conventions)
- [Compilation fix](#compilation-fix)

<!-- /MarkdownTOC -->

## Naming conventions

Instruments are implemented in Java and should all reside in the
`org.enso.interpreter.instrument` package.

Every implemented Instrument must have name that ends with `Instrument` and
reside in the package mentioned above. This requirement is to ensure that the
[Compilation fix](#compilation-fix) described below works.


## Compilation fix

Annotations are used to register the implemented instruments to Graal.
The annotation processor is triggered when recompiling the Java files.
Unfortunately, when doing an incremental compilation, only the changed files are
recompiled and the annotation processor 'forgets' about other instruments that
haven't been recompiled, leading to runtime errors about missing instruments.

To fix that, we add
[`FixInstrumentsGeneration.scala`](../../project/FixInstrumentsGeneration.scala)
task which detects changes to any of the instruments and forces recompilation of
all instruments in the project by removing their classfiles.