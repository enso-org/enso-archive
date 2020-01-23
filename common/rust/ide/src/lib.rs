use prelude::*;

use serde::Serialize;
use serde::Deserialize;
use json_rpc::*;
use json_rpc::api::Result;
use std::future::Future;
//use futures::FutureExt;

use uuid::Uuid;
use wasm_bindgen::__rt::std::time::SystemTime;

////////////////////////////////////////////////////////////////////////////////

pub mod web_transport;
pub mod web_main;

pub type Path = String;

#[derive(Debug)]
pub struct FmClient {
    pub handler:Handler,
}

macro_rules! make_rpc_method {
    ( $nameTypename:ident $name:ident $name_ext:ident ($($arg:ident : $type:ty),* $(,)?) -> $out:ty ) => {
    paste::item! {
        impl FmClient {
            pub fn $name
            (&mut self, $($arg:$type),*) -> impl Future<Output=Result<$out>> {
                let input = [<$nameTypename Input>] { $($arg:$arg),* };
                self.handler.open_request(input)
            }
        }

        #[derive(Serialize,Deserialize,Debug,PartialEq)]
        struct [<$nameTypename Input>] {
            $($arg : $type),*
        }

        impl api::RemoteMethodCall for [<$nameTypename Input>] {
            const NAME:&'static str = stringify!($name_ext);
            type Returned = $out;
        }
    }}
}

impl FmClient {
    pub fn new(transport:impl Transport + 'static) -> FmClient {
        let mut handler = Handler::new(transport);
        handler.on_error.set(|e| {
            log!("Encountered handling error: {:?}", e);
        });
        handler.on_notification.set(|n| {
            log!("Received a notification: {:?}", n);
        });
        FmClient { handler }
    }

    pub fn tick(&mut self) {
        self.handler.process_events()
    }
}

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
make_rpc_method!(DeleteWatch   delete_watch   deleteWatch   (path:Path)                  -> ()        );

#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Attributes{
    pub creation_time      : FileTime,
    pub last_access_time   : FileTime,
    pub last_modified_time : FileTime,
    pub file_kind          : FileKind,
    pub size_in_bytes      : u64
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct FileTime(pub SystemTime);

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub enum FileKind {
    Directory, RegularFile, SymbolicLink, Other
}


#[cfg(test)]
mod tests {
    use super::*;
    use crate::transport::mock::MockTransport;
    use failure::_core::cell::RefCell;
    use std::rc::Rc;
    use crate::FmClient;
    use serde_json::json;
    use serde_json::Value;
    use json_rpc::messages::{RequestMessage, Message};
    use std::future::Future;
    use serde::de::DeserializeOwned;


    use std::pin::Pin;
    use std::task::Context;
    use std::task::Poll;

    /// Polls the future, performing any available work. If future is complete,
    /// returns result. Otherwise, returns control when stalled.
    fn poll_for_output<F : Future>(f:&mut Pin<Box<F>>) -> Option<F::Output> {
        let mut ctx = Context::from_waker(futures::task::noop_waker_ref());
        match f.as_mut().poll(&mut ctx) {
            Poll::Ready(result) => Some(result),
            Poll::Pending       => None,
        }
    }


    fn fixture<Fun, Fut, T>
    (make_request:Fun
    , expected_method:&str
    , expected_input:Value
    , result:Value
    , expected_output:T)
    where Fun : FnOnce(&mut FmClient) -> Fut,
          Fut : Future<Output= json_rpc::Result<T>>,
          T   : Debug + PartialEq {
        let     ws  = Rc::new(RefCell::new(MockTransport::new()));
        let mut fm  = FmClient::new(ws.clone());
        let mut fut = Box::pin(make_request(&mut fm));

        let request = ws.borrow_mut().expect_message::<RequestMessage<Value>>();
        assert_eq!(request.method, expected_method);
        assert_eq!(request.input, expected_input);

        let response = Message::new_success(request.id, result);
        ws.borrow_mut().mock_peer_message(response);

        fm.tick();
        let output = poll_for_output(&mut fut).unwrap().unwrap();
        assert_eq!(output, expected_output);
    }

    #[test]
    fn version_serialization_and_deserialization() {
        let main = "./Main.luna";
        let target = "./Target.luna";

        let path_main = json!({"path" : "./Main.luna"});
        let from_main_to_target = json!({"from" : "./Main.luna", "to" : "./Target.luna"});
        let true_json = json!(true);
        let unit_json = json!(true);

        println!("AAA {}", serde_json::to_string(&()).unwrap());

        fixture(
            |mut fm| fm.copy_directory(main.into(), target.into()),
            "copyDirectory",
            from_main_to_target.clone(),
            unit_json.clone(),
            ());
        fixture(
            |mut fm| fm.copy_file(main.into(), target.into()),
            "copyFile",
            from_main_to_target.clone(),
            unit_json.clone(),
            ());
        fixture(
            |mut fm| fm.delete_file(main.into()),
            "deleteFile",
            path_main.clone(),
            unit_json.clone(),
            ());
        fixture(
            |mut fm| fm.exists(main.into()),
            "exists",
            path_main.clone(),
            json!(true),
            true);
        fixture(
            |mut fm| fm.list(main.into()),
            "list",
            path_main.clone(),
            json!(["Bar.luna",       "Foo.luna"       ]),
            vec!  ["Bar.luna".into(),"Foo.luna".into()]);


    }
}