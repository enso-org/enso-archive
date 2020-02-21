
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
