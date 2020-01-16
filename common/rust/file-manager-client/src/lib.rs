use prelude::*;

use serde::Serialize;
use serde::Deserialize;
use json_rpc::*;
use json_rpc::api::Result;
use std::future::Future;
use futures::FutureExt;


////////////////////////////////////////////////////////////////////////////////

pub mod web_transport;
pub mod web_main;

pub type Path = String;

#[derive(Debug)]
pub struct FmClient {
    pub handler:Handler,
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

    pub fn copy_file
    (&mut self, from:Path, to:Path) -> impl Future<Output = Result<()>> {
        let input = CopyFileInput { from, to };
        self.handler.open_request(input).map(|result| result.map(|_| ()))
    }
    pub fn exists(&mut self, path:Path) -> impl Future<Output = Result<bool>> {
        println!("exists?");
        let input = ExistsInput { path };
        self.handler.open_request(input).map(|result| result.map(|r| r.exists))
    }
    pub fn touch(&mut self, path:Path) -> impl Future<Output = Result<()>> {
        println!("exists?");
        let input = ExistsInput { path };
        self.handler.open_request(input).map(|result| result.map(|_| ()))
    }

    pub fn tick(&mut self) {
        self.handler.tick()
    }
}

////////////////////////////////////////////////////////////////////////////////

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct CopyFileInput {
    from : String,
    to   : String,
}
impl api::RemoteMethodCall for CopyFileInput {
    const NAME:&'static str = "copy";
    type Returned = CopyFileResponse;
}
#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct CopyFileResponse {}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct ExistsInput {
    path : String
}

impl api::RemoteMethodCall for ExistsInput {
    const NAME:&'static str = "exists";
    type Returned = ExistsResponse;
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct ExistsResponse {
    exists : bool
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct TouchInput {
    path : String
}

impl api::RemoteMethodCall for TouchInput {
    const NAME:&'static str = "Touch";
    type Returned = TouchResponse;
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct TouchResponse {
    exists : bool
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct FileSystemEventInput {}
