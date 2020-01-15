use prelude::*;

use serde::Serialize;
use serde::Deserialize;
use json_rpc::*;
use std::future::Future;
use futures::FutureExt;


////////////////////////////////////////////////////////////////////////////////

pub type Path = String;

pub struct FmClient {
    pub handler:Handler,
}

impl FmClient {
    pub fn new(transport:Box<dyn Transport>) -> FmClient {
        FmClient {
            handler:Handler::new(transport),
        }
    }

    pub fn exists(&mut self, path:Path) -> impl Future<Output = Result<bool>> {
        println!("exists?");
        let input = ExistsInput { path };
        self.handler.open_request(input).map(|result| result.map(|r| r.exists))
    }

    pub async fn exists2(&mut self, path:Path) -> Result<bool> {
        println!("exists?");
        let input = ExistsInput { path };
        let result = self.handler.open_request(input).await;
        result.map(|r| r.exists)
    }

    pub fn tick(&mut self) {
        self.handler.tick()
    }
}

////////////////////////////////////////////////////////////////////////////////

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct ExistsInput {
    path : String
}

impl api::RemoteMethodInput for ExistsInput {
    const NAME:&'static str = "exists";
    type Returned = ExistsResponse;
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct TouchInput {
    path : String
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct ExistsResponse {
    exists : bool
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct FileSystemEventInput {}
