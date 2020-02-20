# Temporary Notes for Backend Planning

- Need to create a backend for the IDE
- Writing a monolith that consolidates the functionality.
- Protocol is custom, and will need to be fully specified.

## Working with Files On Disk
Take a look [here](https://github.com/luna/ide/blob/master/lib/ide/file-manager/README.md).

| Method        | Input                        | Result     |
|---------------|------------------------------|------------|
| copyDirectory | {from:Path, to:Path}         | ()         |
| copyFile      | {from:Path, to:Path}         | ()         |
| deleteFile    | {path:Path}                  | ()         |
| exists        | {path:Path}                  | Boolean    |
| list          | {path:Path}                  | [Path]     |
| moveDirectory | {from:Path, to:Path}         | ()         |
| moveFile      | {from:Path, to:Path}         | ()         |
| read          | {path:Path}                  | String     |
| info          | {path:Path}                  | Attributes | (fstat)
| touch         | {path:Path}                  | ()         |
| write         | {path:Path, contents:String} | ()         |
| createWatch   | {path:Path}                  | UUID       |
| deleteWatch   | {watchId:UUID}               | ()         |

| Method          | Input                       | Result |
|-----------------|-----------------------------|--------|
| filesystemEvent | {path:Path, kind:EventKind} | N/A    |

- `read` should use the in-memory state where necessary
- The 'watch' system doesn't really make sense. The IDE should be notified on
  any change relevant to it (e.g. changes to the project tree) regardless of its
  active watches.
- Do we want a separate `copyFile` and `copyDirectory`? Why not just `copy` and
  `move`?
- `delete` should also be generic, like the above.
- Should probably have `tree`, rather than recursively calling `list`. We should
  support sending partial trees in the case where there is a severe level of
  nesting.
- `Path` should be a domain-specific representation, rather than a list of 
  strings or equivalent (paths are hard). Need a type that can be sensibly
  encoded as JSON. What are our requirements?

We should mediate all operations to avoid conflicts.

## Editing Files
The open file state needs to be maintained on a per-client basis.

- `openFile` -> different semantics regarding internal buffers
- `closeFile` -> different semantics regarding internal buffers
- `saveFile` -> client to server to say that they are saving the file, should 
  require a capability to save
- `changeFile` -> bidirectional? It's useful to get acks from client, should
  require a capability to edit
- `didChange` -> server to client changes, we can use it for sync on change
- `getContents` -> client to server for file state

- Files should be versioned
- Do we want to stick to (start, end) or (row1, col1, row2, col2). It means that
  we can be compatible where able, but is harder overall.
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

## Questions to IDE

- What do IDE want the semantics of touch to be? Why is it not just `create`?
- What kind of internal structures are the IDE using for text editing?
