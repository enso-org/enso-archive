
## Working with Files On Disk

| Method         | Type    | Params                                       | Result     |
|----------------|---------|----------------------------------------------|------------|
| file/write     | R: C->S | {path:Path, contents:String}                 | ()         |
| file/create    | R: C->S | {path:Path, kind: "file" &#124; "directory"} | ()         | 
| file/copy      | R: C->S | {from:Path, to:Path}                         | ()         |
| file/move      | R: C->S | {from:Path, to:Path}                         | ()         |
| file/delete    | R: C->S | {path:Path}                                  | ()         |
| file/exists    | R: C->S | {path:Path}                                  | Boolean    |
| file/list      | R: C->S | {path:Path}                                  | [Path]     |
| file/tree      | R: C->S | {path:Path, depth: Int}                      | DirTree    |
| file/read      | R: C->S | {path:Path}                                  | String     |
| file/info      | R: C->S | {path:Path}                                  | Attributes |
| file/event     | N: S->C | [{path:Path, kind:EventKind}]                | ~          |
| file/addRoot   | R: C->S | {absolutePath:[String], id: UUID}            | ()         |
| file/rootAdded | N: S->C | {absolutePath:[String], id: UUID}            | ~          |

## Editing Files

| Method      | Type    | Params                            | Result                                                             |
|-------------|---------|-----------------------------------|--------------------------------------------------------------------|
| openFile    | R: C->S | {path:Path}                       | { writeCapability?: CapabilityRegistration, currentVersion: UUID } |
| closeFile   | R: C->S | {path:Path}                       | ()                                                                 |
| saveFile    | R: C->S | {path:Path, currentVersion: UUID} | ()                                                                 |
| applyEdits  | R: C->S | [FileEdit]                        | ()                                                                 |
| didChange   | N: S->C | [FileEdit]                        | ~                                                                  |
| undo        | R: C->S | {requestId?: UUID}                | [WorkspaceEdit]                                                    |
| redo        | R: C->S | {requestId?: UUID}                | [WorkspaceEdit]                                                    | 
