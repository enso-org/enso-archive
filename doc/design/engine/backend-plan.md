
## Server Initialisation

1. Create a scaffolding for a new service (WebSocket, JSON-RPC). (2)
2. Implement the functionality to open a project from disk. This involves the
   spawn of a new language server set up for the project. (2)
3. Implement the recent projects list (2).
4. Implement creation and deletion of projects (2).
5. Work out how we want sample projects to work (1). 
6. Implement the sample projects system (?).

It should also exit gracefully.

## Capabilities
Extensible capability system.

- `acquire` (IDE to Server)
- `release` (IDE to Server)
- `grant` (Server to IDE)
- `forceRelease` (Server to IDE)

- Capabilities for:
    + `canWrite` (currently exclusive)

1. Implement a proper command-line way to spawn the language server, incorporate
   into the uberjar (1).
2. The above spec (3), edit the existing task.

## Working with Files On Disk
NB: Errors are not specified in the below. 'Result' is only the happy-path.

| Method         | Type    | Params                                       | Result     |
|----------------|---------|----------------------------------------------|------------|
| file/create    | R: C->S | {path:Path, kind: "file" &#124; "directory"} | ()         | 
| file/copy      | R: C->S | {from:Path, to:Path}                         | ()         |
| file/move      | R: C->S | {from:Path, to:Path}                         | ()         |
| file/delete    | R: C->S | {path:Path}                                  | ()         |
| file/exists    | R: C->S | {path:Path}                                  | Boolean    |
| file/list      | R: C->S | {path:Path}                                  | [Path]     |
| file/tree      | R: C->S | {path:Path, depth: Int}                      | DirTree    |
| file/read      | R: C->S | {path:Path}                                  | String     |
| file/info      | R: C->S | {path:Path}                                  | Attributes |
| file/write     | R: C->S | {path:Path, contents:String}                 | ()         |
| file/event     | N: S->C | [{path:Path, kind:EventKind}]                | ~          |
| file/addRoot   | R: C->S | {absolutePath:[String], id: UUID}            | ()         |
| file/rootAdded | N: S->C | {absolutePath:[String], id: UUID}            | ~          |

- `read` should use the in-memory state where necessary
- `write` need not contain a path to a file that exists
- The IDE will get automatic notifications for changes to the project directory
  tree.
- With `tree`, we should support sending partial trees for large trees.
- `Path` should be a domain-specific representation. It should be an object
  encoding of a path (e.g. a list of path segments + metadata).
- We should mediate all operations to avoid conflicts.

1. A task per message above (2 then 1). Order TBC.
2. Implement the `receivesTreeUpdates` capability and use it to send `fileEvent`

## Editing Files
The open file state needs to be maintained on a per-client basis.

| Method      | Type    | Params                            | Result                                                             |
|-------------|---------|-----------------------------------|--------------------------------------------------------------------|
| openFile    | R: C->S | {path:Path}                       | { writeCapability?: CapabilityRegistration, currentVersion: UUID } |
| closeFile   | R: C->S | {path:Path}                       | ()                                                                 |
| saveFile    | R: C->S | {path:Path, currentVersion: UUID} | ()                                                                 |
| applyEdits  | R: C->S | [FileEdit]                        | ()                                                                 |
| didChange   | N: S->C | [FileEdit]                        | ~                                                                  |
| undo        | R: C->S | {requestId?: UUID}                | [WorkspaceEdit]                                                    |
| redo        | R: C->S | {requestId?: UUID}                | [WorkspaceEdit]                                                    | 

- Files should be versioned, and edits to old versions should be rejected
  (initially) or resolved.

1. Determine a high-performance representation to be used for the buffer (3hrs). 
   This should ignore conflict resolution for now.
2. Implement the underlying representation for text buffers (3).
3. Implement the above messages (1 day per message for the first 5). 
4. Determine how we want undo/redo to function from both a practical perspective
   and the UX perspective (3). Use `renameSymbol` as an example. Account for 
   multiclient.
5. Implement undo (3), implement redo (3).
