# Temporary Notes for Backend Planning

- Need to create a backend for the IDE
- Writing a monolith that consolidates the functionality.
- Protocol is custom, and will need to be fully specified.

## Working with Files On Disk
Take a look [here](https://github.com/luna/ide/blob/master/lib/ide/file-manager/README.md).

TODO Spec: Path, DirTree, Attributes

| Method    | Type    | Params                                        | Result     |
|-----------|---------|-----------------------------------------------|------------|
| copy      | R: C->S | {from:Path, to:Path}                          | ()         |
| move      | R: C->S | {from:Path, to:Path}                          | ()         |
| delete    | R: C->S | {path:Path}                                   | ()         |
| exists    | R: C->S | {path:Path}                                   | Boolean    |
| list      | R: C->S | {path:Path}                                   | [Path]     |
| tree      | R: C->S | {path:Path}                                   | DirTree    |
| read      | R: C->S | {path:Path}                                   | String     |
| info      | R: C->S | {path:Path}                                   | Attributes |
| new       | R: C->S | {path:Path, kind: "file" &#124; "directory" } | ()         |
| write     | R: C->S | {path:Path, contents:String}                  | ()         |
| fileEvent | N: S->C | [{path:Path, kind:EventKind}]                 | ~          |

- `read` should use the in-memory state where necessary
- The IDE will get automatic notifications for changes to the project directory
  tree.
- With `tree`, we should support sending partial trees for large trees.
- `Path` should be a domain-specific representation. It should be an object
  encoding of a path (e.g. a list of path segments + metadata).
- We should mediate all operations to avoid conflicts.

## Editing Files
The open file state needs to be maintained on a per-client basis.

TODO Spec: TextEdit

| Method      | Type    | Params                                | Result                                       |
|-------------|---------|---------------------------------------|----------------------------------------------|
| openFile    | R: C->S | {path:Path}                           | { writeCapability: CapabilityRegistration? } |
| closeFile   | R: C->S | {path:Path}                           | ()                                           |
| saveFile    | R: C->S | {path:Path}                           | ()                                           |
| applyEdits  | R: C->S | [{path:Path, edits: [TextEdit] }]     | ()                                           |
| didChange   | N: S->C | [{path:Path, edits: [TextEdit] }]     | ~                                            |
| getContents | R: C->S | {path:Path}                           | String                                       |
| undo        | R: C->S | {requestId: UUID}                     | ()                                           |
| redo        | R: C->S | {requestId: UUID}                     | ()                                           | 

- `openFile` -> different semantics regarding internal buffers
- `closeFile` -> different semantics regarding internal buffers
- `saveFile` -> client to server to say that they are saving the file, should 
  require a capability to save
- `changeFile` -> should require a capability to edit, ide to server
- `didChange` -> server to client changes, we can use it for sync on change
- `getContents` -> client to server for file state
- `undo`
- `redo`

- Should saving be handled automatically?
- Files should be versioned, and edits to old versions should be rejected
  (initially) or resolved.
- Stick to (r, c, r, c) for now as it is better supported by most underlying
  structures.
- What should our internal rep for files be like? Look into text editor theory.
- We need internal tracking for undo/redo state (e.g. `renameSymbol` being
  reverted).

- Articles on multiclient editing for Marcin.

## Capabilities
Extensible capability system.

- `acquire` (IDE to Server)
- `release` (IDE to Server)
- `grant` (Server to IDE)
- `forceRelease` (Server to IDE)

- Capabilities for:
    + `canWrite` (currently exclusive)
    + `canRead` 
    + `canVisualise`
    + `receivesTreeUpdates`
