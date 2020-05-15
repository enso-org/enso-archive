# Enso Protocol Language Server Message Specification
This document contains the specification of the Enso protocol messages that
pertain to the language server component. Please familiarise yourself with the
[common](./protocol-common.md) features of the protocol before reading this
document.

For information on the design and architecture of the protocol, as well as its
transport formats, please look [here](./protocol-architecture).

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Types](#types)
    - [`File`](#file)
    - [`DirectoryTree`](#directorytree)
    - [`FileAttributes`](#fileattributes)
    - [`UTCDateTime`](#utcdatetime)
    - [`FileEventKind`](#fileeventkind)
    - [`Position`](#position)
    - [`Range`](#range)
    - [`TextEdit`](#textedit)
    - [`SHA3-224`](#sha3-224)
    - [`FileEdit`](#fileedit)
    - [`FileContents`](#filecontents)
    - [`FileSystemObject`](#filesystemobject)
    - [`WorkspaceEdit`](#workspaceedit)
- [Connection Management](#connection-management)
    - [`session/initProtocolConnection`](#sessioninitprotocolconnection)
    - [`session/initBinaryConnection`](#sessioninitbinaryconnection)

<!-- /MarkdownTOC -->

## Types
There are a number of types that are used only within the language server's
protocol messages. These are specified here.

### `File`
A representation of a file on disk.

#### Format

```typescript
interface File {
  name: String; // Includes the file extension
  type: String;
}
```

### `DirectoryTree`
A directory tree is a recursive type used to represent tree structures of files
and directories. It contains files and symlinks in the `files` section and
directories in the `directories` section. When the tree was requested with the
parameter limiting the maximum depth, the bottom of the `DirectoryTree` will
contain `Directory` node in the `files` section indicating that there is a
directory, but the contents are unknown because we've reached the maximum depth.

#### Format

```typescript
interface DirectoryTree {
  path: Path;
  name: String;
  files: [FileSystemObject];
  directories: [DirectoryTree];
}
```

### `FileAttributes`
A description of the attributes of a file required by the IDE. These attributes
may be expanded in future.

#### Format

```typescript
/**
 * A representation of the attributes of a file.
 *
 * @param creationTime creation time
 * @param lastAccessTime last access time
 * @param lastModifiedTime last modified time
 * @param kind type of [[FileSystemObject]], can be:
 * `Directory`, `File`, `Other`
 * @param byteSize size in bytes
 */
interface FileAttributes {
  creationTime: UTCDateTime;
  lastAccessTime: UTCDateTime;
  lastModifiedTime: UTCDateTime;
  kind: FileSystemObject;
  byteSize: number;
}
```

### `UTCDateTime`
Time in UTC time zone represented as ISO-8601 string

#### Format

```typescript
type UTCDateTime = String;
```

### `FileEventKind`
The kind of event being described for a watched file.

#### Format

```typescript
type FileEventKind = Added | Removed | Modified;
```

### `Position`
A representation of a position in a text file.

#### Format

```typescript
interface Position {
  /**
   * Line position in a document (zero-based).
   */
  line: number;

  /**
   * Character offset on a line in a document (zero-based). Assuming that the
   * line is represented as a string, the `character` value represents the gap
   * between the `character` and `character + 1`.
   *
   * If the character value is greater than the line length it defaults back to
   * the line length.
   */
  character: number;
}
```

### `Range`
A representation of a range of text in a text file.

#### Format

```typescript
interface Range {
  /**
   * The range's start position.
   */
  start: Position;

  /**
   * The range's end position.
   */
  end: Position;
}
```

### `TextEdit`
A representation of a change to a text file at a given position.

#### Format

```typescript
interface TextEdit {
  range: Range;
  text: String;
}
```

### `SHA3-224`
The `SHA3-224` message digest encoded as a base16 string.

#### Format

``` typescript
type SHA3-224 = String;
```

### `FileEdit`
A representation of a batch of edits to a file, versioned.

`SHA3-224` represents hash of the file contents. `oldVersion` is the version
you're applying your update on, `newVersion` is what you compute as the hash
after applying the changes. In other words,

``` python
hash(origFile) == oldVersion
hash(applyEdits(origFile, edits)) == newVersion
```

it's a sanity check to make sure that the diffs are applied consistently.

#### Format

```typescript
interface FileEdit {
  path: Path;
  edits: [TextEdit];
  oldVersion: SHA3-224;
  newVersion: SHA3-224;
}
```

### `FileContents`
A representation of the contents of a file.

#### Format

```typescript
interface FileContents[T] {
  contents: T;
}

class TextFileContents extends FileContents[String];
```

### `FileSystemObject`
A representation of what kind of type a filesystem object can be.

#### Format

```typescript
type FileSystemObject
  = Directory
  | SymlinkLoop
  | File
  | Other;

/**
 * Represents a directory.
 *
 * @param name a name of the directory
 * @param path a path to the directory
 */
interface Directory {
  name: String;
  path: Path;
}

/**
 * Represents a symbolic link that creates a loop.
 *
 * @param name a name of the symlink
 * @param path a path to the symlink
 * @param target a target of the symlink. Since it is a loop,
 * target is a subpath of the symlink
 */
interface SymlinkLoop {
  name: String;
  path: Path;
  target: Path;
}

/**
 * Represents a file.
 *
 * @param name a name of the file
 * @param path a path to the file
 */
interface File {
  name: String;
  path: Path;
}

/**
 * Represents unrecognized object.
 * Example is a broken symbolic link.
 */
interface Other {
  name: String;
  path: Path;
}
```

### `WorkspaceEdit`
This is a message to be specified once we better understand the intricacies of
undo/redo.

> The actionables for this section are:
>
> - Work out the design of this message.
> - Specify this message.

## Connection Management
In order to properly set-up and tear-down the language server connection, we
need a set of messages to control this process.

### `session/initProtocolConnection`
This message initialises the connection used to send the textual protocol
messages. This initialisation is important such that the client identifier can
be correlated between the textual and data connections.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  clientId: UUID;
}
```

#### Result

```typescript
{
  contentRoots: [UUID];
}
```

#### Errors
- [`SessionAlreadyInitialisedError`](#sessionalreadyinitialisederror) to signal
that session is already initialised.

### `session/initBinaryConnection`
This message initialises the data connection used for transferring binary data
between engine and clients. This initialisation is important such that the
client identifier can be correlated between the data and textual connections.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Data
- **Visibility:** Public

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

//A command initializing a data session.
table InitSessionCommand {

  //A unique identifier of a client initializing the session.
  identifier: EnsoUUID (required);

}

root_type InitSessionCommand;
```

#### Result

```
namespace org.enso.languageserver.protocol.binary;

//Indicates an operation has succeeded.
table Success {}
```

#### Errors
N/A
