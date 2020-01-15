#![feature(trait_alias)]

pub mod messages;
pub mod api;
pub mod handler;
pub mod transport;

use prelude::*;

pub use crate::transport::Transport;
pub use crate::transport::TransportCallbacks;
pub use handler::Handler;

use std::future::Future;
use serde::Serialize;
use serde::Deserialize;
use serde::de::DeserializeOwned;
use serde_json::json;

use futures::channel::oneshot;
use futures::future::FutureExt;
use std::thread;
use futures::channel::oneshot::Sender;
use std::collections::BTreeMap;
use std::cell::Cell;
use std::fmt::Debug;
use prelude::RefCell;
use std::rc::Rc;
use wasm_bindgen::JsValue;
use wasm_bindgen::__rt::std::sync::mpsc::Receiver;
use failure::Error;
use failure::Fail;


pub type Result<T> = std::result::Result<T, Error>;

mod tests {
    use super::*;
    use crate::messages::*;


//    #[test]
//    fn request_serialization() {
//        let method  = ExistsInput{path:"./temp.txt".into()};
//        let payload = Request { id : Id(1), method };
//        let message = Message { jsonrpc: Version::V2, payload: payload};
//
//        let text = serde_json::to_string(&message).unwrap();
//        println!("{}", text)
//    }



//    #[test]
//    fn deserialize_response() {
//        let response_text = r#"{"jsonrpc": "2.0", "result": 19, "id": 1}"#;
//        type IncMsg = Message<IncomingMessage<NoMethods>>;
//        let message = serde_json::from_str::<IncMsg>(response_text).unwrap();
//        if let IncomingMessage::Response(ref resp) = message {
//            assert_eq!(resp.id, 1);
//            assert_eq!(resp.res, Result::Success(Success { result: 19.into() }));
//        } else {
//            panic!("expected to parse to a response message")
//        }
//
//
//        println!("{:?}", message)
//    }

//    #[test]
//    fn message_serialization() {
//        let sample_request_text = r#"{
//            "jsonrpc" : "2.0",
//            "method"  : "touch",
//            "params"  : {"path" : "./temp.txt"},
//            "id"      : 1
//        }";
//
//        type RequestMessage = Message<Request<ExistsInput>>;
//        let msg = serde_json::from_str::<Message>(sample_request_text).unwrap();
//
//
//        println!("{:?}", msg);
//    }

    fn check_serialization(json_text:&str, value:Version) {
        let got_json_text = serde_json::to_string(&value).unwrap();
        assert_eq!(got_json_text, json_text);

        let got_value = serde_json::from_str::<Version>(json_text).unwrap();
        assert_eq!(got_value, value);
    }

    #[test]
    fn version_serialization() {
        check_serialization("\"2.0\"", Version::V2);
        check_serialization("\"1.0\"", Version::V1);
    }
}


//
//#[derive(Debug)]
//struct FileManagerError {}
//impl FileManagerError {
//    fn new_cancelled() -> FileManagerError { FileManagerError {} }
//    fn new_deserialization() -> FileManagerError { FileManagerError {} }
//}
//
//impl From<oneshot::Canceled> for FileManagerError {
//    fn from(_: oneshot::Canceled) -> Self {
//        FileManagerError::new_cancelled()
//    }
//}
//impl From<serde_json::error::Error> for FileManagerError {
//    fn from(_: serde_json::error::Error) -> Self {
//        FileManagerError::new_deserialization()
//    }
//}
//
//type Callback<Output> = std::result::Result<Output, FileManagerError>;
//
//struct IdGenerator {
//    counter:i64
//}
//impl IdGenerator {
//    fn new() -> IdGenerator { IdGenerator {counter:0} }
//    fn generate(&mut self) -> i64 {
//        self.counter += 1;
//        self.counter
//    }
//}
//
//trait TransportCallbacks {
//    fn on_text_message(&mut self, message:String) {}
//    fn on_close(&mut self) {}
//}
//
//trait Transport {
//    fn close(&mut self);
//    fn send_text(&mut self, message:String);
//    fn setup_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>);
//}
//
//trait Client {
//    fn get_transport(&mut self) -> &mut dyn Transport;
//    fn dispatch_notification(&self, method:&str, inputs:JsValue);
//}
//
//struct GeneralRequest {
//    id : Id,
//    method: &'static str,
//    params : serde_json::Value,
//}
//
//struct RequestManager {
//    pub id_generator:IdGenerator,
//    pub open_requests:std::collections::BTreeMap<i64,Sender<String>>,
//}
//
////impl RequestManager {
////    fn new() -> RequestManager {
////        RequestManager {
////            id_generator  : IdGenerator::new(),
////            open_requests : BTreeMap::new(),
////        }
////    }
////
////    fn open_request
////    (&mut self, ) -> Receiver<String> {
////
////    }
////}
////
////struct FileManager {
////    pub requests : RequestManager,
////    pub transport: Rc<RefCell<dyn Transport>>,
////}
////
//trait Input: serde::Serialize {
//    type Response : Response + 'static;
//    const METHOD: &'static str;
//}
//
//trait Response: DeserializeOwned {}
//
////#[derive(Serialize, Deserialize)]
////struct Request<In> {
////    id: i64,
////    #[serde(flatten)]
////    input: In,
////    method: &'static str,
////    jsonrpc: &'static str,
////}
////
////impl<In> Request<In> {
////    fn new(id:i64, input:In) -> Request<In> where In : Input {
////        Request {
////            id, input, method:In::METHOD, jsonrpc:"2.0"
////        }
////    }
////}
////
//// ==============
//// === Exists ===
//// ==============
//
//#[derive(Serialize, Deserialize)]
//struct ExistsInput { path:Path }
//impl Input for ExistsInput {
//    const METHOD: &'static str = "exists";
//    type Response = ExistsResponse;
//}
//
//#[derive(Serialize, Deserialize)]
//struct ExistsResponse { exists:bool }
//impl Response for ExistsResponse { }
//
/////////////////////////////////////
//
//type Result<R> = std::result::Result<R, FileManagerError>;
//
//
//
//trait FutureResult<R> = Future<Output = Result<R>>;
//
//#[derive(Serialize, Deserialize, Debug)]
//pub struct GeneralResponse {
//    pub id:Id,
//    pub result:serde_json::Value,
//}
//
//#[derive(Debug)]
//pub enum IncomingMessage {
//    Notification(),
//    Response(GeneralResponse),
//}
//
//fn decode_incoming_message(text:&str) -> Result<IncomingMessage> {
//    let json = serde_json::from_str::<serde_json::Value>(text)?;
//    if let serde_json::Value::Object(ref map) = json {
//        if map.contains_key("id") {
//            let resp = serde_json::from_value::<GeneralResponse>(json)?;
//            Ok(IncomingMessage::Response(resp))
//        } else {
//            panic!("TODO TODO ")
//        }
//    } else {
//        panic!("message is not a JSON object: {:?}", json)
//    }
//}
//
//
////impl TransportCallbacks for FileManager {
////
////
////    fn on_text_message(&mut self, message:String) {
////        println!("FM: got from server: {}", message);
////        let msg = decode_incoming_message(&message);
////        if let Ok(IncomingMessage::Response(response)) = msg {
////            if let Some(a) = self.open_requests.remove(&response.id) {
////                a.send(message);
////            }
////        }
////    }
////    fn on_close(&mut self) {}
////}
////
////impl FileManager {
////    fn new(transport : Rc<RefCell<dyn Transport>>) -> Rc<RefCell<FileManager>> {
////        let mut fm = FileManager {
////            requests: RequestManager::new(),
////            transport,
////        };
////
////        let fm = Rc::new(RefCell::new(fm));
////        fm.borrow().transport.borrow_mut().setup_callback(fm.clone());
//////        fm.borrow_mut().transport.setup_callbacks(Box::new(|msg| {
//////            if let Some(fm) = weak_fm.upgrade() {
//////                fm.borrow_mut().on_text_message(msg);
//////            }
//////        }));
////        fm
////    }
////
////    fn open_request<In:Input>
////    (&mut self, request:Request<In>) -> Box<dyn FutureResult<In::Response>>{
////        let (sender, receiver) = oneshot::channel::<String>();
////        let ret = receiver.map(|response_result| -> Result<In::Response> {
////            serde_json::from_str(&response_result?).map_err(|e| e.into())
////        });
////
////        let request_json = match serde_json::to_string(&request) {
////            Ok(encoded_json) => encoded_json,
////            Err(e) => {
////                let err = FileManagerError{};
////                let fut = futures::future::ready(Err(err));
////                return Box::new(fut);
////            }
////        };
////
////        self.open_requests.insert(request.id, sender);
////        self.transport.borrow_mut().send_text(request_json);
////        Box::new(ret)
////    }
////
////    fn send_request<In: Input>
////    (&mut self, input: In) -> Box<dyn FutureResult<In::Response>> {
////        let id = self.id_generator.generate();
////        let req = Request::new(id, input);
////        self.open_request(req)
////    }
////
////    pub fn exists(&mut self, path:Path) -> Box<dyn FutureResult<ExistsResponse>> {
////        let req = ExistsInput { path };
////        self.send_request(req)
////    }
////}
////
////struct MockTransport {
////    user : Option<Rc<RefCell<dyn TransportCallbacks>>>
////}
////impl MockTransport {
////    pub fn mock_server_message(&mut self, message:String) {
////        println!("Server replies with message: {}", message);
////        if let Some(ref user) = self.user {
////            user.borrow_mut().on_text_message(message)
////        }
////    }
////
////    fn new() -> MockTransport { MockTransport {user: None} }
////}
////impl Transport for MockTransport {
////    fn close(&mut self) {}
////    fn send_text(&mut self, message:String) {
////        println!("Client sends to server: {}", message);
////    }
////    fn setup_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>) {
////        self.user = Some(cb)
////    }
////}
////
////fn main() {
//////    let mut transport = Rc::new(RefCell::new(MockTransport::new()));
//////    let mut fm = FileManager::new(transport.clone());
//////
//////    use futures::future::FutureExt;
//////
//////    let exists = fm.borrow_mut().exists("C:/temp".into());
//////
//////    (*exists).then(|arg| {
//////        println!("Compledef!!!! {}", arg)
//////    });
//////    transport.borrow_mut().mock_server_message(r#"{"jsonrpc": "2.0", "result": 19, "id": 3}"#.into());
//////
//////    println!("fooo");
//////}
//
//
//
