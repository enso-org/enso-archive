# Temporary Notes for Backend Planning

- Need to create a backend for the IDE
- Writing a monolith that consolidates the functionality.
- Protocol is custom, and will need to be fully specified.

## Working with Files On Disk
Take a look [here](https://github.com/luna/ide/blob/master/lib/ide/file-manager/README.md).

| Method        | Input                        | Result     |
|---------------|------------------------------|------------|
| copy          | {from:Path, to:Path}         | ()         |
| move          | {from:Path, to:Path}         | ()         |
| delete        | {path:Path}                  | ()         |
| exists        | {path:Path}                  | Boolean    |
| list          | {path:Path}                  | [Path]     |
| tree          | {path:Path}                  | [Path]     |
| read          | {path:Path}                  | String     |
| info          | {path:Path}                  | Attributes | (fstat)
| new           | {path:Path}                  | ()         |
| write         | {path:Path, contents:String} | ()         |

| Method          | Input                       | Result |
|-----------------|-----------------------------|--------|
| filesystemEvent | {path:Path, kind:EventKind} | N/A    |

- `read` should use the in-memory state where necessary
- The IDE will get automatic notifications for changes to the project directory
  tree.
- With `tree`, we should support sending partial trees for large trees.
- `Path` should be a domain-specific representation. It should be an object
  encoding of a path (e.g. a list of path segments + metadata).
- We should mediate all operations to avoid conflicts.

## Editing Files
The open file state needs to be maintained on a per-client basis.

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
