//use file_manager_client::*;

use prelude::*;

use json_rpc::*;
use file_manager_client::*;
use futures::executor::LocalPool;
use futures::task::{LocalSpawnExt, Context};
use std::future::Future;
use std::pin::Pin;
use std::task::Poll;

struct MockTransport {
    cb:Option<Rc<RefCell<dyn TransportCallbacks>>>,

    sent_msgs: Vec<String>,
}

impl Transport for MockTransport {
    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>) {
        self.cb = Some(cb);
    }

    fn send_text(&mut self, message:String) {
        println!("Client sends: {}", message);
        self.sent_msgs.push(message);
    }
}

impl MockTransport {
    fn new() -> MockTransport {
        MockTransport {
            cb:None,
            sent_msgs:Vec::new(),
        }
    }
    fn mock_received_message(&self, message:String) {
        println!("Server sends: {}", message);
        if let Some(ref cb) = self.cb {
            cb.borrow_mut().on_text_message(message);
        }
    }
}

fn lumpen_executor<F : Future>(f:&mut Pin<Box<F>>) -> Option<F::Output> {
    let mut ctx = Context::from_waker(futures::task::noop_waker_ref());
    match f.as_mut().poll(&mut ctx) {
        Poll::Ready(result) => Some(result),
        Poll::Pending       => None,
    }
}

fn main() {
    let mock_response = r#"{"jsonrpc":"2.0","id":0,"result":{"exists":true}}"#;

    println!("Hello!");
    let transport = Rc::new(RefCell::new(MockTransport::new()));
    let mut fm = FmClient::new(Box::new(transport.clone()));

    let fut = fm.exists("temp.txt".into());
    use futures::FutureExt;;
    let fut = fut.map(|aa| println!("Future continuation for: {:?}", aa));

    let mut fut = Box::pin(fut);

    lumpen_executor(&mut fut);

    transport.borrow_mut().mock_received_message(mock_response.into());

    println!("Will tick!");
    fm.tick();

    println!("Will poll future!");

    lumpen_executor(&mut fut);
//    let mut local = LocalPool::new();
//    local.spawner().spawn_local(fut);
//    local.run_until_stalled();

    println!("Done!");
}
