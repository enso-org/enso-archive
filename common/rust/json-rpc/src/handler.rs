use prelude::*;

use std::collections::BTreeMap;
use futures::channel::oneshot;
use failure::Error;
use failure::Fail;
use serde::de::DeserializeOwned;

use crate::api;
use crate::transport::Transport;
use crate::transport::TransportCallbacks;
use crate::messages;
use crate::messages::Id;
use crate::Result;
use std::future::Future;

#[derive(Debug, Fail)]
pub enum RpcError {
    #[fail(display = "remote call has been cancelled")]
    Canceled(#[cause] oneshot::Canceled),

    #[fail(display = "peer has replied with an error: {:?}", _0)]
    RemoteError(messages::Error),

    #[fail(display = "lost connection before receiving reply")]
    LostConnection,

}

/// Partially decoded reply message.
///
/// Known if `Error` or `Success` but returned value remains in JSON form.
pub type ReplyMessage = messages::Result<serde_json::Value>;

/// Converts remote message with JSON-serialized result into `Result<Ret>`.
pub fn decode_result<Ret:DeserializeOwned>
(result:messages::Result<serde_json::Value>) -> Result<Ret> {
    match result {
        messages::Result::Success(ret) =>
            Ok(serde_json::from_value::<Ret>(ret.result)?),
        messages::Result::Error(err) =>
            Err(RpcError::RemoteError(err))?,
    }
}

pub fn decode_incoming(text:&str) -> Result<messages::IncomingMessage> {
    Ok(serde_json::from_str(text)?)
}

pub struct IdGenerator {
    pub counter:i64,
}
impl IdGenerator {
    fn next(&mut self) -> Id {
        let id = self.counter;
        self.counter += 1;
        Id(id)
    }
    fn new() -> IdGenerator {
        IdGenerator { counter : 0 }
    }
}

pub struct SharedBuffer {
    incoming : Vec<String>,
    closed   : bool,
}
impl SharedBuffer {
    pub fn new() -> SharedBuffer {
        SharedBuffer {
            incoming:Vec::new(),
            closed:false,
        }
    }
}
impl TransportCallbacks for SharedBuffer {
    fn on_text_message(&mut self, message:String) {
        self.incoming.push(message);
    }
    fn on_close(&mut self) {
        self.closed = true;
    }
}

pub struct Handler {
    ongoing_calls:BTreeMap<Id,oneshot::Sender<ReplyMessage>>,
    id_generator:IdGenerator,
    transport:Box<dyn Transport>,
    buffer: Rc<RefCell<SharedBuffer>>
}

impl Handler {
    pub fn new(transport:Box<dyn Transport>) -> Handler {
        let mut ret = Handler {
            ongoing_calls :  BTreeMap::new(),
            id_generator  : IdGenerator::new(),
            transport,
            buffer        :  Rc::new(RefCell::new(SharedBuffer::new()))
        };
        ret.transport.set_callback(ret.buffer.clone());
        ret
    }

    pub fn open_request<In : api::RemoteMethodInput>
    (&mut self, input:In) -> impl Future<Output = Result<In::Returned>> {
        println!("Setting the request future channel");
        use futures::FutureExt;
        let (sender, receiver) = oneshot::channel::<ReplyMessage>();
        let ret = receiver.map(|result_or_cancel| -> Result<In::Returned> {
            let result = result_or_cancel?;
            decode_result(result)
        });

        let id = self.id_generator.next();
        println!("Opening request {:?}", id);
        let message = input.to_request_message(id).unwrap(); // FIXME
        self.ongoing_calls.insert(message.payload.id, sender);

        let serialized_message = serde_json::to_string(&message).unwrap();
        self.transport.send_text(serialized_message);
        ret
    }

    pub fn process_response
    (&mut self, message:messages::Response<serde_json::Value>) {
        println!("Got response to request {}", message.id);
        if let Some(sender) = self.ongoing_calls.remove(&message.id) {
            sender.send(message.res);
        } else {
            println!("Unexpected server reply!");
        }
    }
    pub fn process_notification
    (&mut self, message:messages::Notification<serde_json::Value>) {
        println!("Got notification: {:?}", message)
    }

    pub fn process_incoming_message(&mut self, message:String) {
        println!("Process {}", message);
        match decode_incoming(&message) {
            Ok(messages::IncomingMessage::Response(response)) =>
                self.process_response(response),
            Ok(messages::IncomingMessage::Notification(notification)) =>
                self.process_notification(notification),
            Err(err) =>
                println!("Failed to decode incoming message: {}", message),
        }
    }

    pub fn tick(&mut self) {
        let msgs = self.buffer.borrow_mut().incoming.drain(..).collect::<Vec<_>>();
        for msg in msgs {
            self.process_incoming_message(msg)
        }
    }
}