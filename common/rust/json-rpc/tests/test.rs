


use prelude::*;

use json_rpc::*;
use futures::executor::LocalPool;
use futures::task::{LocalSpawnExt, Context};
use std::future::Future;
use std::pin::Pin;
use std::task::Poll;


use serde::Serialize;
use serde::Deserialize;
use futures::FutureExt;
use json_rpc::messages::{make_success_message, Id, Version, Message, make_error_message};
use serde::de::DeserializeOwned;
use json_rpc::handler::RpcError::RemoteError;
use json_rpc::handler::{RpcError, HandlingError};

type MockRequestMessage = messages::RequestMessage<MockRequest>;
type MockResponseMessage = messages::ResponseMessage<MockResponse>;

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

fn lumpen_executor<F : Future>(f:&mut Pin<Box<F>>) -> Option<F::Output> {
    let mut ctx = Context::from_waker(futures::task::noop_waker_ref());
    match f.as_mut().poll(&mut ctx) {
        Poll::Ready(result) => Some(result),
        Poll::Pending       => None,
    }
}

struct LumpenExecutor {
    futures : Vec<Pin<Box<dyn Future<Output = ()>>>>,
}
impl LumpenExecutor {
    pub fn run(&mut self) {
        let mut ctx = Context::from_waker(futures::task::noop_waker_ref());
        for mut f in self.futures.iter_mut() {
            match f.as_mut().poll(&mut ctx) {
                Poll::Ready(result) => Ok(result),
                Poll::Pending       => Err(f),
            };
        }
    }
}

fn lumpen_executor2<F : Future>(f:F) -> std::result::Result<F::Output, impl Future> {
    let mut f = Box::pin(f);
    let mut ctx = Context::from_waker(futures::task::noop_waker_ref());
    match f.as_mut().poll(&mut ctx) {
        Poll::Ready(result) => Ok(result),
        Poll::Pending       => Err(f),
    }
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct MockRequest {
    i:i64,
}
impl api::RemoteMethodCall for MockRequest {
    const NAME:&'static str = "pow";
    type Returned           = MockResponse;

}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
struct MockResponse {
    result:i64,
}

#[derive(Debug)]
pub struct Client {
    pub handler:Handler,
    pub errors :Rc<RefCell<Vec<HandlingError>>>,
}

impl Client {
    pub fn new(transport:Box<dyn Transport>) -> Client {
        let mut handler = Handler::new(transport);
        let errors = Rc::new(RefCell::new(Vec::new()));

        let errors2 = errors.clone();
        handler.on_error.set(move |e| {
            errors2.borrow_mut().push(e);
        });

        Client {
            handler,
            errors,
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

fn pow_impl(msg:MockRequestMessage) -> MockResponseMessage {
    let ret = MockResponse { result : msg.i * msg.i };
    make_success_message(msg.id, ret)
}

#[test]
fn test() {
    let ws = Rc::new(RefCell::new(MockTransport::new()));
    let mut fm = Client::new(Box::new(ws.clone()));

    // test successful call
    {
        let mut fut = Box::pin(fm.pow(8));

        // validate request sent
        let req_msg = ws.borrow_mut().expect_message::<MockRequestMessage>();
        assert_eq!(req_msg.id, Id(0));
        assert_eq!(req_msg.method, "pow");
        assert_eq!(req_msg.i, 8);
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

    // test remote error call
    {
        let mut fut = Box::pin(fm.pow(8));
        assert!(lumpen_executor(&mut fut).is_none()); // no reply

        // reply with error
        let req_msg = ws.borrow_mut().expect_message::<MockRequestMessage>();
        let error_code = 5;
        let error_description = "wrong!";
        let error_data = None;
        let error_msg: MockResponseMessage = make_error_message(
            req_msg.id,
            error_code,
            error_description.into(),
            error_data.clone(),
        );
        ws.borrow_mut().mock_peer_message(error_msg);

        fm.tick();
        let result = lumpen_executor(&mut fut);
        let result = result.expect("result should be present");
        let result = result.expect_err("result should be a failure");
        if let RemoteError(e) = result {
            assert_eq!(e.code, error_code);
            assert_eq!(e.data, error_data);
            assert_eq!(e.message, error_description);
        } else {
            panic!("Expected an error to be RemoteError");
        }
    }

    // test server replying garbage
    {
        let mut fut = Box::pin(fm.pow(8));
        assert!(lumpen_executor(&mut fut).is_none()); // no reply
        ws.borrow_mut().mock_peer_message_text("hello, nice to meet you".into());
        fm.tick();
        assert!(lumpen_executor(&mut fut).is_none()); // no valid reply
        let internal_error = fm.errors.borrow_mut().pop();
        let internal_error = internal_error.expect("there should be an error");
        if let HandlingError::InvalidMessage(e) = internal_error {
        } else {
            panic!("Expected an error to be InvalidMessage");
        }
    }

    // test socket closing during the call
    {
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
}
