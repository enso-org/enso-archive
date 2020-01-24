//! Client library for the JSON-RPC-based File Manager service.

#![warn(missing_docs)]
#![warn(trivial_casts)]
#![warn(trivial_numeric_casts)]
#![warn(unused_import_braces)]
#![warn(unused_qualifications)]
#![warn(unsafe_code)]
#![warn(missing_copy_implementations)]
#![warn(missing_debug_implementations)]

use prelude::*;

use json_rpc::api::Result;
use json_rpc::Handler;
use futures::Stream;
use serde::Serialize;
use serde::Deserialize;
use std::future::Future;
use uuid::Uuid;



// =============
// === Event ===
// =============

/// Event emitted by the File Manager `Client`.
pub type Event = json_rpc::handler::Event<Notification>;



// ============
// === Path ===
// ============

/// Path to a file.
#[derive(Serialize, Deserialize)]
#[derive(Clone, Debug, Eq, PartialEq, PartialOrd, Ord, Hash)]
#[derive(Display)]
#[derive(Shrinkwrap)]
pub struct Path(pub String);

impl Path {
    /// Wraps a `String`-like entity into a new `Path`.
    pub fn new<S>(s:S) -> Path where S:Into<String> {
        Path(s.into())
    }
}



// ====================
// === Notification ===
// ====================

/// Notification generated by the File Manager.
#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(tag = "method", content="params")]
pub enum Notification {
    /// Filesystem event occurred for a watched path.
    #[serde(rename = "filesystemEvent")]
    FilesystemEvent(FilesystemEvent),
}



// =======================
// === FilesystemEvent ===
// =======================

/// Filesystem event notification, generated by an active file watch.
#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
pub struct FilesystemEvent {
    /// Path of the file that the event is about.
    pub path : Path,
    /// What kind of event is it.
    pub kind : FilesystemEventKind
}

/// Describes kind of filesystem event (was the file created or deleted, etc.)
#[derive(Serialize, Deserialize, Debug, PartialEq, Clone, Copy)]
pub enum FilesystemEventKind {
    /// A new file under path was created.
    Created,
    /// Existing file under path was deleted.
    Deleted,
    /// File under path was modified.
    Modified,
    /// An overflow occurred and some events were lost,
    Overflow
}



// ==================
// === Attributes ===
// ==================

/// Attributes of the file in the filesystem.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Attributes{
    /// When the file was created.
    pub creation_time      : FileTime,
    /// When the file was last accessed.
    pub last_access_time   : FileTime,
    /// When the file was last modified.
    pub last_modified_time : FileTime,
    /// What kind of file is this.
    pub file_kind          : FileKind,
    /// Size of the file in bytes.
    /// (size of files not being `RegularFile`s is unspecified).
    pub size_in_bytes      : u64
}

/// A filesystem's timestamp.
pub type FileTime = chrono::DateTime<chrono::FixedOffset>;

/// What kind of file (regular, directory, symlink) is this.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq)]
pub enum FileKind {
    /// File being a directory.
    Directory,
    /// File being a symbolic link.
    SymbolicLink,
    /// File being a regular file with opaque content.
    RegularFile,
    /// File being none of the above, e.g. a physical device or a pipe.
    Other
}



// ==============
// === Client ===
// ==============

/// File Manager client. Contains numerous asynchronous methods for remote calls
/// on File Manager server. Also, allows obtaining events stream by calling
/// `events`.
#[derive(Debug)]
pub struct Client {
    /// JSON-RPC protocol handler.
    handler : Handler<Notification>,
}

impl Client {
    /// Create a new File Manager client that will use given transport.
    pub fn new(transport:impl json_rpc::Transport + 'static) -> Client {
        let handler = Handler::new(transport);
        Client { handler }
    }

    /// Asynchronous event stream with notification and errors.
    ///
    /// On a repeated call, previous stream is closed.
    pub fn events(&mut self) -> impl Stream<Item = Event> {
        self.handler.events()
    }

    /// Method that should be called on each frame.
    ///
    /// Processes incoming transport events, generating File Manager events and
    /// driving asynchronous calls to completion.
    pub fn process_events(&mut self) {
        self.handler.process_events()
    }
}



// ===================
// === RPC Methods ===
// ===================


// === Helper macro ===

/// Macro that generates a asynchronous method making relevant RPC call to the
/// server. First three args is the name appropriately in CamelCase,
/// snake_case, camelCase. Then goes the function signature, in form of
/// `(arg:Arg) -> Ret`.
///
/// Macro generates:
/// * a method in Client named `snake_case` that takes `(arg:Arg)` and returns
/// `Future<Ret>`.
/// * a structure named `CamelCase` that stores function arguments as fields and
///   its JSON serialization conforms to JSON-RPC (yielding `method` and
///   `params` fields).
/// * `snakeCase` is the name of the remote method.
macro_rules! make_rpc_method {
    ( $name_typename:ident
      $name:ident
      $name_ext:ident
      ($($arg:ident : $type:ty),* $(,)?) -> $out:ty   ) => {
    paste::item! {
        impl Client {
            /// Remote call to the method on the File Manager Server.
            pub fn $name
            (&mut self, $($arg:$type),*) -> impl Future<Output=Result<$out>> {
                let input = [<$name_typename Input>] { $($arg:$arg),* };
                self.handler.open_request(input)
            }
        }

        /// Structure transporting method arguments.
        #[derive(Serialize,Deserialize,Debug,PartialEq)]
        #[serde(rename_all = "camelCase")]
        struct [<$name_typename Input>] {
            $($arg : $type),*
        }

        impl json_rpc::RemoteMethodCall for [<$name_typename Input>] {
            const NAME:&'static str = stringify!($name_ext);
            type Returned = $out;
        }
    }}
}


// === Remote API definition ===

make_rpc_method!(CopyDirectory copy_directory copyDirectory (from:Path, to:Path)         -> ()        );
make_rpc_method!(CopyFile      copy_file      copyFile      (from:Path, to:Path)         -> ()        );
make_rpc_method!(DeleteFile    delete_file    deleteFile    (path:Path)                  -> ()        );
make_rpc_method!(Exists        exists         exists        (path:Path)                  -> bool      );
make_rpc_method!(List          list           list          (path:Path)                  -> Vec<Path> );
make_rpc_method!(MoveDirectory move_directory moveDirectory (from:Path, to:Path)         -> ()        );
make_rpc_method!(MoveFile      move_file      moveFile      (from:Path, to:Path)         -> ()        );
make_rpc_method!(Read          read           read          (path:Path)                  -> String    );
make_rpc_method!(Status        status         status        (path:Path)                  -> Attributes);
make_rpc_method!(Touch         touch          touch         (path:Path)                  -> ()        );
make_rpc_method!(Write         write          write         (path:Path, contents:String) -> ()        );
make_rpc_method!(CreateWatch   create_watch   createWatch   (path:Path)                  -> Uuid      );
make_rpc_method!(DeleteWatch   delete_watch   deleteWatch   (watch_id:Uuid)              -> ()        );



// =============
// === Tests ===
// =============

#[cfg(test)]
mod tests {
    use super::*;
    use super::FileKind::RegularFile;

    use json_rpc::messages::Message;
    use json_rpc::messages::RequestMessage;
    use json_rpc::test_util::transport::mock::MockTransport;
    use serde_json::json;
    use serde_json::Value;
    use std::future::Future;
    use utils::poll_future_output;
    use utils::poll_stream_output;

    fn setup_fm() -> (MockTransport, Client) {
        let transport = MockTransport::new();
        let client    = Client::new(transport.clone());
        (transport,client)
    }

    #[test]
    fn test_notification() {
        let (mut transport, mut client) = setup_fm();
        let mut events                  = Box::pin(client.events());
        assert!(poll_stream_output(&mut events).is_none());

        let expected_notification = FilesystemEvent {
            path : Path::new("./Main.luna"),
            kind : FilesystemEventKind::Modified,
        };
        let notification_text = r#"{
            "jsonrpc": "2.0",
            "method": "filesystemEvent",
            "params": {"path" : "./Main.luna", "kind" : "Modified"}
        }"#;
        transport.mock_peer_message_text(notification_text);
        assert!(poll_stream_output(&mut events).is_none());
        client.process_events();
        let event = poll_stream_output(&mut events);
        if let Some(Event::Notification(n)) = event {
            assert_eq!(n, Notification::FilesystemEvent(expected_notification));
        } else {
            panic!("expected notification event");
        }
    }

    /// Tests making a request using file manager:
    /// * creates FM client and uses `make_request` to make a request
    /// * checks that request is made for `expected_method`
    /// * checks that request input is `expected_input`
    /// * mocks receiving a response from server with `result`
    /// * checks that FM-returned Future yields `expected_output`
    fn test_request<Fun, Fut, T>
    ( make_request:Fun
    , expected_method:&str
    , expected_input:Value
    , result:Value
    , expected_output:T )
    where Fun : FnOnce(&mut Client) -> Fut,
          Fut : Future<Output = Result<T>>,
          T   : Debug + PartialEq {
        let (mut transport, mut client) = setup_fm();
        let mut fut                     = Box::pin(make_request(&mut client));

        let request = transport.expect_message::<RequestMessage<Value>>();
        assert_eq!(request.method, expected_method);
        assert_eq!(request.input,  expected_input);

        let response = Message::new_success(request.id, result);
        transport.mock_peer_message(response);

        client.process_events();
        let output = poll_future_output(&mut fut).unwrap().unwrap();
        assert_eq!(output, expected_output);
    }

    #[test]
    fn version_serialization_and_deserialization() {
        let main                = Path::new("./Main.luna");
        let target              = Path::new("./Target.luna");
        let path_main           = json!({"path" : "./Main.luna"});
        let from_main_to_target = json!({
            "from" : "./Main.luna",
            "to"   : "./Target.luna"
        });
        let true_json = json!(true);
        let unit_json = json!(null);

        test_request(
            |client| client.copy_directory(main.clone(), target.clone()),
            "copyDirectory",
            from_main_to_target.clone(),
            unit_json.clone(),
            ());
        test_request(
            |client| client.copy_file(main.clone(), target.clone()),
            "copyFile",
            from_main_to_target.clone(),
            unit_json.clone(),
            ());
        test_request(
            |client| client.delete_file(main.clone()),
            "deleteFile",
            path_main.clone(),
            unit_json.clone(),
            ());
        test_request(
            |client| client.exists(main.clone()),
            "exists",
            path_main.clone(),
            true_json,
            true);

        let list_response_json  = json!([          "Bar.luna",           "Foo.luna" ]);
        let list_response_value = vec!  [Path::new("Bar.luna"),Path::new("Foo.luna")];
        test_request(
            |client| client.list(main.clone()),
            "list",
            path_main.clone(),
            list_response_json,
            list_response_value);
        test_request(
            |client| client.move_directory(main.clone(), target.clone()),
            "moveDirectory",
            from_main_to_target.clone(),
            unit_json.clone(),
            ());
        test_request(
            |client| client.move_file(main.clone(), target.clone()),
            "moveFile",
            from_main_to_target.clone(),
            unit_json.clone(),
            ());
        test_request(
            |client| client.read(main.clone()),
            "read",
            path_main.clone(),
            json!("Hello world!"),
            "Hello world!".into());

        let parse_rfc3339 = |s| {
            chrono::DateTime::parse_from_rfc3339(s).unwrap()
        };
        let expected_attributes = Attributes {
            creation_time      : parse_rfc3339("2020-01-07T21:25:26Z"),
            last_access_time   : parse_rfc3339("2020-01-21T22:16:51.123994500+00:00"),
            last_modified_time : parse_rfc3339("2020-01-07T21:25:26Z"),
            file_kind          : RegularFile,
            size_in_bytes      : 125125,
        };
        let sample_attributes_json = json!({
            "creationTime"      : "2020-01-07T21:25:26Z",
            "lastAccessTime"    : "2020-01-21T22:16:51.123994500+00:00",
            "lastModifiedTime"  : "2020-01-07T21:25:26Z",
            "fileKind"          : "RegularFile",
            "sizeInBytes"       : 125125
        });
        test_request(
            |client| client.status(main.clone()),
            "status",
            path_main.clone(),
            sample_attributes_json,
            expected_attributes);
        test_request(
            |client| client.touch(main.clone()),
            "touch",
            path_main.clone(),
            unit_json.clone(),
            ());
        test_request(
            |client| client.write(main.clone(), "Hello world!".into()),
            "write",
            json!({"path" : "./Main.luna", "contents" : "Hello world!"}),
            unit_json.clone(),
            ());

        let uuid_value = uuid::Uuid::parse_str("02723954-fbb0-4641-af53-cec0883f260a").unwrap();
        let uuid_json  = json!("02723954-fbb0-4641-af53-cec0883f260a");
        test_request(
            |client| client.create_watch(main.clone()),
            "createWatch",
            path_main.clone(),
            uuid_json.clone(),
            uuid_value);
        let watch_id   = json!({
            "watchId" : "02723954-fbb0-4641-af53-cec0883f260a"
        });
        test_request(
            |client| client.delete_watch(uuid_value.clone()),
            "deleteWatch",
            watch_id.clone(),
            unit_json.clone(),
            ());
    }
}
