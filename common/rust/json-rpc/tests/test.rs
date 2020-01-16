use prelude::*;

use json_rpc::*;
use json_rpc::api::RemoteMethodCall;
use json_rpc::api::Result;
use json_rpc::error::RpcError;
use json_rpc::error::HandlingError;
use json_rpc::messages::Id;
use json_rpc::messages::Message;
use json_rpc::messages::Notification;
use json_rpc::messages::Version;

use futures::FutureExt;
use futures::task::Context;
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde::Deserialize;
use std::future::Future;
use std::pin::Pin;
use std::task::Poll;

// =====================
// === Mock Protocol ===
// =====================

// === Remote Method ===

fn pow_impl(msg:MockRequestMessage) -> MockResponseMessage {
    let ret = MockResponse { result : msg.i * msg.i };
    Message::new_success(msg.id,ret)
}

// === Protocol Data ===

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct MockRequest { i:i64 }

impl RemoteMethodCall for MockRequest {
    const NAME:&'static str = "pow";
    type Returned           = MockResponse;
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct MockResponse { result:i64 }

//#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
//#[serde(tag = "method", content="params")]
//enum MockNotification {
//    Meow{ text:String },
//    Bark{ text:String },
//}
//impl MockNotification {
//    pub fn describe_call() -> MethodCall {
//        MethodCall
//    }
//}

// === Helper Aliases ===

type MockRequestMessage = messages::RequestMessage<MockRequest>;

type MockResponseMessage = messages::ResponseMessage<MockResponse>;


// ======================
// === Mock Transport ===
// ======================

#[derive(Debug)]
struct MockTransport {
    pub cb        : Option<Rc<RefCell<dyn TransportCallbacks>>>,
    pub sent_msgs : Vec<String>,
}

impl Transport for MockTransport {
    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>) {
        self.cb = Some(cb);
    }

    fn send_text(&mut self, text:String) {
        println!("Client sends: {}", text);
        self.sent_msgs.push(text.clone());
    }
}

impl MockTransport {
    pub fn new() -> MockTransport {
        MockTransport {
            cb:None,
            sent_msgs:Vec::new(),
        }
    }
    pub fn mock_peer_message_text(&self, message:String) {
        println!("Server sends: {}", message);
        if let Some(ref cb) = self.cb {
            cb.borrow_mut().on_text_message(message);
        }
    }
    pub fn mock_peer_message<T:Serialize>(&self, message:T) {
        let text = serde_json::to_string(&message).expect("failed to serialize");
        self.mock_peer_message_text(text)
    }
    pub fn mock_connection_closed(&self) {
        println!("Mock: Server closed the connection");
        if let Some(ref cb) = self.cb {
            cb.borrow_mut().on_close();
        }
    }

    pub fn expect_message_text(&mut self) -> String {
        self.sent_msgs.pop().expect("client should have sent request")
    }
    pub fn expect_message<T:DeserializeOwned>(&mut self) -> T {
        let text = self.expect_message_text();
        let res  = serde_json::from_str(&text);
        res.expect("failed to deserialize client's message")
    }
}


// ================
// === Executor ===
// ================

fn lumpen_executor<F : Future>(f:&mut Pin<Box<F>>) -> Option<F::Output> {
    let mut ctx = Context::from_waker(futures::task::noop_waker_ref());
    match f.as_mut().poll(&mut ctx) {
        Poll::Ready(result) => Some(result),
        Poll::Pending       => None,
    }
}


// ===================
// === Mock Client ===
// ===================

#[derive(Debug)]
pub struct Client {
    pub handler      :Handler,
    pub errors       :Rc<RefCell<Vec<HandlingError>>>,
    pub notifications:Rc<RefCell<Vec<Notification<serde_json::Value>>>>,
}

impl Client {
    pub fn new(transport:impl Transport + 'static) -> Client {
        let mut handler   = Handler::new(transport);
        let errors        = Rc::new(RefCell::new(Vec::new()));
        let notifications = Rc::new(RefCell::new(Vec::new()));

        let errors2 = errors.clone();
        handler.on_error.set(move |e| {
            errors2.borrow_mut().push(e);
        });

        let notifications2 = notifications.clone();
        handler.on_notification.set(move |e| {
            notifications2.borrow_mut().push(e);
        });

        Client {
            handler,
            errors,
            notifications,
        }
    }
    pub fn pow(&mut self, i:i64) -> impl Future<Output = Result<i64>> {
        let input = MockRequest { i };
        self.handler.open_request(input).map(|result| result.map(|r| r.result))
    }
    pub fn tick(&mut self) {
        self.handler.tick()
    }
}


// ============
// === Test ===
// ============

fn setup() -> (Rc<RefCell<MockTransport>>, Client) {
    let ws = Rc::new(RefCell::new(MockTransport::new()));
    let fm = Client::new(ws.clone());
    (ws,fm)
}

#[test]
fn test_success_call() {
    let (mut ws, mut fm) = setup();
    let call_input = 8;
    let mut fut = Box::pin(fm.pow(8));
    let expected_first_request_id = Id(0);

    // validate request sent
    let req_msg = ws.borrow_mut().expect_message::<MockRequestMessage>();
    assert_eq!(req_msg.id, expected_first_request_id);
    assert_eq!(req_msg.method, MockRequest::NAME);
    assert_eq!(req_msg.i, call_input);
    assert_eq!(req_msg.jsonrpc, Version::V2);

    assert!(lumpen_executor(&mut fut).is_none()); // no reply

    // let's reply
    let reply = pow_impl(req_msg);
    ws.borrow_mut().mock_peer_message(reply);

    // before tick message should be in buffer and callbacks should not
    // complete
    assert_eq!(fm.handler.buffer.borrow_mut().incoming.len(), 1);
    assert!(lumpen_executor(&mut fut).is_none()); // not ticked

    // now tick
    fm.tick();
    assert_eq!(fm.handler.buffer.borrow_mut().incoming.len(), 0);
    let result = lumpen_executor(&mut fut);
    let result = result.expect("result should be present");
    let result = result.expect("result should be a success");
    assert_eq!(result, 8*8);
}

#[test]
fn test_error_call() {
    let (mut ws, mut fm) = setup();
    let mut fut = Box::pin(fm.pow(8));
    assert!(lumpen_executor(&mut fut).is_none()); // no reply

    // reply with error
    let req_msg = ws.borrow_mut().expect_message::<MockRequestMessage>();
    let error_code = 5;
    let error_description = "wrong!";
    let error_data = None;
    let error_msg: MockResponseMessage = Message::new_error(
        req_msg.id,
        error_code,
        error_description.into(),
        error_data.clone(),
    );
    ws.borrow_mut().mock_peer_message(error_msg);

    // receive error
    fm.tick();
    let result = lumpen_executor(&mut fut);
    let result = result.expect("result should be present");
    let result = result.expect_err("result should be a failure");
    if let RpcError::RemoteError(e) = result {
        assert_eq!(e.code, error_code);
        assert_eq!(e.data, error_data);
        assert_eq!(e.message, error_description);
    } else {
        panic!("Expected an error to be RemoteError");
    }
}

#[test]
fn test_garbage_reply_error() {
    let (mut ws, mut fm) = setup();
    let mut fut = Box::pin(fm.pow(8));
    assert!(lumpen_executor(&mut fut).is_none()); // no reply
    ws.borrow_mut().mock_peer_message_text("hello, nice to meet you".into());
    fm.tick();
    assert!(lumpen_executor(&mut fut).is_none()); // no valid reply
    let internal_error = fm.errors.borrow_mut().pop();
    let internal_error = internal_error.expect("there should be an error");
    if let HandlingError::InvalidMessage(_) = internal_error {
    } else {
        panic!("Expected an error to be InvalidMessage");
    }
}

#[test]
fn test_disconnect_error() {
    let (mut ws, mut fm) = setup();
    let mut fut = Box::pin(fm.pow(8));
    assert!(lumpen_executor(&mut fut).is_none()); // no reply
    ws.borrow_mut().mock_connection_closed();
    assert!(lumpen_executor(&mut fut).is_none()); // no reply
    fm.tick();
    let result = lumpen_executor(&mut fut);
    let result = result.expect("result should be present");
    let result = result.expect_err("result should be a failure");
    if let RpcError::LostConnection = result {
    } else {
        panic!("Expected an error to be RemoteError");
    }
}
//
//fn test_notification(mock_notif:MockNotification) {
//    let (mut ws, mut fm) = setup();
//    let serialized_call  = serde_json::to_value(mock_notif.clone()).unwrap();
//
//    let message          = Message::new(serialized_call);
//    let aaa = serde_json::to_string(&message.clone());
//
//
//    assert!(fm.notifications.borrow().is_empty());
//    ws.borrow_mut().mock_peer_message(message.clone());
//    assert!(fm.notifications.borrow().is_empty());
//    fm.tick();
//    assert_eq!(fm.notifications.borrow().is_empty(), false);
//
//    let notif = fm.notifications.borrow_mut().pop().unwrap();
//    let notif = fm.handler.decode_notification::<MockNotification>(notif.0);
//    let notif = notif.unwrap();
//    assert_eq!(notif, mock_notif);
//}
//
//#[test]
//fn test_recognizing_notifications() {
//    let meow_notification = MockNotification::Meow {text:"meow!".into()};
//    test_notification(meow_notification);
//
//    let bark_notification = MockNotification::Bark {text:"woof!".into()};
//    test_notification(bark_notification);
//}
//
