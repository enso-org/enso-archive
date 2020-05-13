# Enso Libraries Packaging

Given the open-community model of Enso as a programming language, it is crucial
to provide an extensible package management system.
This document describes the behavior of the first prototype of such a system,
together with some future expansion plans.

## Enso Package Structure

The general directory structure of an Enso package is as follows:

```
My_Package
├── package.yaml
├── polyglot
│   ├── java
│   │   └── jar.jar
│   └── js
│       └── library.js
└── src
    ├── Main.enso
    └── Sub_Module
        ├── Helper.enso
        └── Util.enso
```

### the `src` Directory

The `src` directory contains all Enso sources, organized in a hierarchical
structure. The structure of this directory dictates how particular modules
are imported in all of Enso code.

Note that all files and directories in this subtree must be named according
to the Enso style for data types (i.e. `Upper_Snake_Case`).

A file located at the path `My_Package/src/Sub_Module/Helper.enso` will be
imported like so:

```
import My_Package.Sub_Module.Helper
```
Notice that the name of the package appears as the first segment of the name.
Note that the package name is not specified by the containing directory's name,
but rather it is described in the `package.yaml` file.

### the `polyglot` Directory

The `polyglot` directory contains per-language subdirectories containing files
used by the supported polyglot languages. The contents of each subdirectory is
specified on a per-language basis, in the polyglot documentation section.

### the `package.yaml` File

`package.yaml` describes certain package metadata, such as its name, authors
and version. It also includes the list of dependencies of the package.
The following is an example of this manifest file.

```yaml
license: MIT
name: My_Package # mandatory
version: 1.0.1
author: "John Doe <john.doe@example.com>"
maintainer: "Jane Doe <jane.doe@example.com>"
enso_version: 1.2.0
dependencies:
  - name: Base
    version: "1.2.0" # TODO: Specify and implement version strings. Currently only supports exact matches.
  - name: Http
    version: "4.5.3"
```