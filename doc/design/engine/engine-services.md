# The Enso Engine Services
Enso is a sophisticated language, but in order to provide a great user
experience to our users we also need the ability to provide great tooling. This
tooling means a language server, but it also means a set of extra peripheral
components that ensure we can run Enso in a way that the product requires.

These services are responsible for providing the whole-host of language- and
project-level tooling to the IDE components, whether they're hosted in the cloud
or locally on a user's machine.

To that end, we need to have a well-specified idea of what the various services
do, and how they interact. This document contains a design for the engine
services components, as well as any open questions that may remain.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Architecture](#architecture)
  - [Language Server](#language-server)
  - [File Manager](#file-manager)
  - [Multi-Client Coordinator](#multi-client-coordinator)
  - [Supervisor](#supervisor)
- [The Protocol Itself](#the-protocol-itself)
  - [Protocol Communication Patterns](#protocol-communication-patterns)
  - [The Protocol Transport](#the-protocol-transport)
  - [The Protocol Format](#the-protocol-format)
- [Protocol Functionality](#protocol-functionality)
  - [Functionality Post 2.0](#functionality-post-20)
- [Protocol Message Specification](#protocol-message-specification)

<!-- /MarkdownTOC -->

## Architecture
While it may initially seem like these service components could all be rolled
into one, division of responsibilities combines with plain necessity to mean
that we require a set of services instead. This section deals with an
architecture proposal for how to make these services work together.

It can be summarised with three main ideas:

1. **Defined Services:** These, such as the language server and file manager are
   just doing their isolated jobs. They make the assumption that they only have
   a single client connected to them.
2. **Multi-Client Coordination:** This is an independent service that deals with
   handling connections from multiple clients (IDEs). This service will then
   combine the messages from those clients to create a single authoritative
   message stream that the services in point 1 can deal with.
3. **Supervisor:** The supervisor process is responsible for set-up and
   tear-down of the other processes, as well as for restarting any of the other
   processes correctly if they fail.

> The actionables for this section are:
> 
> - Determine any feasible alternatives for this architecture.
> - Make a final decision on how to architect the set of back-end services.

### Language Server
The language server is solely responsible for handling the duties commonly
attributed to a language server, as well as the special functionality needed by
Enso Studio. Its functionality can be summarised as follows, though not all of
this is necessary for 2.0:

- **Completion Information:** It should be able to provide a set of candidate
  completions for a given location in the code.
- **Introspection Information:** It should be able to provide introspection
  information from the running interpreter, which consists primarily of types
  and values.
- **Textual Diff Management:** It needs to be able to accept and publish diffs
  of the program source code. As part of this, it needs to keep the node
  metadata up to date.
- **Analysis Operations:** It should be able to service various IDE-style
  analysis requests (e.g. jump-to-definition or find usages)
- **Arbitrary Code Execution:** It should be able to execute arbitrary Enso code
  on values in scope.
- **Refactoring:** Common refactoring operations for Enso programs, including
  renaming, code formatting, and so on.
- **IO Management:** Though this is arguably a feature of the runtime rather
  than the language server itself, this refers to the ability to watch files and
  monitor IO in order to recompute minimal subsets of the program.

### File Manager
The file manager service is responsible for actually handling the physical files
on disk. While there are some arguments for including this in the language
server, it makes far more sense as a separate component. 

This component is responsible for the following:

- **Code File Management:** Handling the loading and saving of code files on 
  disk in response to commands from the GUI.
- **Data File Management:** Handling the upload and download of data files that
  the users want to work with. These files should be accessible by the language
  server, but it doesn't need to know about how they got there or how they get
  edited.
- **Version Control:** In the future, this component will also become 
  responsible for interacting with the underlying version control system that
  stores the project data, and creating a coherent file history view for users.

### Multi-Client Coordinator
This coordinator process is responsible for accepting connections from multiple
users' IDEs in order to enable a multi-client editing experience. It has to take
the messages that come in across these multiple connections and reconcile them
to create a single 'stream of truth' for the file manager and language server,
as neither are multi-client aware.

This component is responsible for the following:

- Accepting connections from multiple clients.
- Distributing updates between clients.
- Reconciling the edits of multiple people at once to create a coherent edit
  stream for the language server.
- De-duplicating requests where relevant (e.g. value subscription pooling) by
  tracking which clients are to receive which responses.

It should be noted that _all_ protocol messages will go via this coordinator
service, and it will hence be the 'entry point' to the set of services.

### Supervisor
The supervisor process is an orchestrator, and is responsible for setting up and
tearing down the other services, as well as restarting them when they fail. Its
responsibilities can be summarised as follows:

- Starting up the set of services for a given project.
- Tearing down the set of services correctly when a project is closed.
- Restarting any of the services properly when they fail. Please note that this
  may require the ability to kill and re-start services that haven't crashed due
  to dependencies between services.

## The Protocol Itself
The protocol refers to the communication format that all of the above services
speak between each other and to the GUI. This protocol is not specialised only
to language server operations, as instead it needs to work for all of the 
various services in this set.

### Protocol Communication Patterns
Whatever protocol we decide on will need to have support for a couple of main
communication patterns

### The Protocol Transport

### The Protocol Format
This section describes the format of a protocol message. This format should be
adhered to by all messages and should obey the following tenets:

- It should permit easy debugging, remaining human readable where possible.
- It should have good support across multiple languages.

## Protocol Functionality
This entire section deals 

### Functionality Post 2.0
In addition to the functionality discussed in detail above, there are further
augmentations that could sensibly be made to the Engine services to support a
much better editing and user-experience for Enso. These are listed briefly below
and will be expanded upon as necessary in the future.

## Protocol Message Specification
This section exists to contain a specification of each of the messages the
protocol supports. This is in order to aid the proper creation of clients, and
to serve as an agreed-upon definition for the protocol between the IDE team and
the Engine team.

> The actionables for this section are:
> 
> - As we establish the _exact_ format for each of the messages supported by the
>   services, record the details of each message here.








#### Protocol Selection
1. There are a couple of different communication patterns, that are used with the GUI communication:
    a. Pub/sub, for "value just computed" or "another client just modified the code"
    b. Req/res, for "list modules in the current project", "list functions in module",
      "run X code on the value of node Y".
    c. Req/ack, for "modify code", "run code".

    This seems to point in the direction of WebSockets or a mixed socket / HTTP approach.

    Q: Does it matter at all to the engine team? (it matters to Ara)
    Q: Which is preferred by the GUI?
    Q: Do we need true req/res? If we go with full WS, should the request carry some ID?

    Ara: Maintain LSP == use WS.
    MK: Learn WTH JSON RPC is.

2. Text  vs. binary.
   Text (e.g. json) is easier to test and use (makes for a more open protocol, easily
   accessible for other plugin / frontend authors). It also has no significant overhead for
   most payloads. There is significant overhead for large chunks of binary data (e.g. graphics),
   which is a show stopper given the intended use of Enso.

   Q: Should we settle for full-binary, or only use binary for visualizations data (possibly on a separate <web>socket)?

   Ara: Def not full binary, because obviously not. Use a separate socket for visualizations.

   Q: Do we ever want to send raw binary data? Like stream a video back to GUI, e.g. flowbox?

#### Functionality
1. Project state management:
  -a. Get project metadata (name, maintainer, version, deps...)
  a. List modules
  b. Remove module
  c. Create module
  d. Git?? Ara: yes, probably not 2.0
1.25. File storage:
  CRUD operations.
  Ara: Does it make sense for the language server to know about storage? Should it be a different service?
    Possibly a whole-new service, working side by side the LS, handling storage.
1.5. "Data" File management:
  CRUD operations.
  NOTE: This is not really the language server! But it's needed. Offload to cloud?
2. Module contents management:
  a. List methods
  b. Apply diff
    Diffs must be minimal, for optimal cache handling.

    A "diff was applied" notification needs to be sent to other clients.

    ALSO: conflict resolution algorithm. (first-come first-serve
     OR write-lock for a single GUI in the first version)

    Q: What guarantees do we want from the GUI diffs? What can we realistically expect?

    Ara: "It's complicated". Possibly AST-based diff minimization will be required server-side.

    Also, nodes have their unique IDs for use with the visualizations engine and cache.
    The IDs are stored at the bottom of a file, identified by absolute code locations.

    Q: Who (which team, and why is it GUI?) is implementing the algorithm to recompute
    these locations on code diffs?

    It must be done in Scala, since it will be used by the backend.
    OR: every diff coming from the GUI contains the new ID map. But that screws potential
    for other frontends up.

3. Execution management
  a. Execute method (with arguments)
  b. Execute function with arguments from a selected application (i.e. enter a node through its call site)
  c. Attach value listener (arbitrary code span)
    The value listeners trigger updates (by id) everytime a node at that position is executed (or, later,
    typechecked).
    These need to be stored per a running-GUI.

    Q: Since listeners are per-GUI, what constraints do we place on GUI comms? Do the GUIs need
    to "introduce themselves"? Do we require a heartbeat (for cache flushes)?

    Q: What do these updates contain? Is it empty, or is the short representation and type of the value
    attached?
  d. Detach execution listener.
4. Visualizations
  a. Send the result of running the requested Enso expression on the cached value.
5. Misc
  a. Searcher hints.
    Q: API style? global + updates (diff) feed?
    ???????
