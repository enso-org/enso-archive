//! Module providing `Handler` and related types used by its API.

use prelude::*;

use crate::api;
use crate::transport::Transport;
use crate::transport::TransportCallbacks;
use crate::messages;
use crate::messages::Id;
use crate::Result;

use futures::channel::oneshot;
use failure::Fail;
use serde::de::DeserializeOwned;
use std::future::Future;

/// Errors that can cause a remote call to fail.
#[derive(Debug, Fail)]
pub enum RpcError {
    /// Error returned by the remote server.
    #[fail(display = "peer has replied with an error: {:?}", _0)]
    RemoteError(messages::Error),

    /// Lost connection while waiting for response.
    #[fail(display = "lost connection before receiving reply")]
    LostConnection,

    /// Failed to deserialize message from server.
    #[fail(display = "failed to deserialize from JSON: {}", _0)]
    DeserializationFailed(serde_json::Error),
}

impl From<oneshot::Canceled> for RpcError {
    fn from(_: oneshot::Canceled) -> Self {
        RpcError::LostConnection
    }
}

impl From<serde_json::Error> for RpcError {
    fn from(e: serde_json::Error) -> Self {
        RpcError::DeserializationFailed(e)
    }
}

/// Errors specific to the Handler itself, not any specific request.
///
/// Caused either internal errors in the handler or bugs in the server.
#[derive(Debug, Fail)]
pub enum HandlingError {
    /// When incoming text message can't be decoded.
    #[fail(display = "failed to decode incoming text message: {}", _0)]
    InvalidMessage(#[cause] serde_json::Error),

    /// Server responded to an identifier that does not match to any known
    /// ongoing request.
    #[fail(display = "server generated response with no matching request: \
    id={:?}", _0)]
    UnexpectedResponse(messages::Response<serde_json::Value>),
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

/// Simple counter-based struct used to generate unique Id's.
///
/// The generated Ids are sequence 0, 1, 2, …
#[derive(Debug)]
pub struct IdGenerator {
    /// Next Id value to be returned.
    pub counter:i64,
}
impl IdGenerator {
    /// Obtain the new Id.
    pub fn next(&mut self) -> Id {
        let id = self.counter;
        self.counter += 1;
        Id(id)
    }

    /// Create a new IdGenerator counting from 0.
    fn new() -> IdGenerator {
        IdGenerator::new_from(0)
    }

    /// Create a new IdGenerator that gives Ids beginning with given number.
    fn new_from(counter:i64) -> IdGenerator {
        IdGenerator { counter }
    }
}

/// The buffer shared between `Handler` and `Transport`.
///
/// The `Transport` callbacks store any input there. Then, `Handler` consumes it
/// when prompted with `tick` method.
#[derive(Debug)]
pub struct SharedBuffer {
    /// Incoming text messages.
    pub incoming : Vec<String>,

    /// Whether the transport was closed. This means that the current transport
    /// cannot be used anymore.
    pub closed : bool,
}
impl SharedBuffer {
    /// Create a new empty buffer.
    pub fn new() -> SharedBuffer {
        SharedBuffer {
            incoming : Vec::new(),
            closed   : false,
        }
    }

    /// Returns a new buffer with all the data moved from self.
    ///
    /// After the call incoming messages list in self is empty, however the
    /// status of `closed` flag is not changed.
    pub fn take(&mut self) -> SharedBuffer {
        let incoming = std::mem::replace(&mut self.incoming, Vec::new());
        let closed = self.closed;
        SharedBuffer { incoming, closed }
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


pub struct Callback<T> {
    cb : Option<Box<dyn Fn(T) -> ()>>
}
impl<T> Debug for Callback<T> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "{}", match self.cb {
            Some(_) => "Some(<function>)",
            None    => "None",
        })
    }
}

impl<T> Callback<T> {
    pub fn new() -> Callback<T> {
        Callback { cb : None }
    }

//    pub fn set(&mut self, cb:Box<dyn Fn(T) ->()>) {
//        self.cb = Some(cb);
//    }

    pub fn set<F : Fn(T) -> () + 'static>(&mut self, cb:F) {
        self.cb = Some(Box::new(cb));
    }

    pub fn unset(&mut self) {
        self.cb = None;
    }

    pub fn try_call(&mut self, t:T) {
        if let Some(cb) = &self.cb {
            cb(t);
        }
    }
}

pub type OngoingCalls = HashMap<Id,oneshot::Sender<ReplyMessage>>;

/// Handler is a main provider of RPC protocol. Given with a transport capable
/// of transporting text messages, it manages whole communication with a peer.
///
/// It allows making request, where method calls are described by values
/// implementing `RemoteMethodCall`. The response is returned as a `Future`.
///
/// Notifications and internal messages are emitted using an optionally set
/// callbacks.
#[derive(Debug)]
pub struct Handler {
    /// Contains handles to calls that were made but no response has came.
    pub ongoing_calls   : OngoingCalls,
    /// Provides identifiers for requests.
    pub id_generator    : IdGenerator,
    /// Transports text messages between this handler and the peer.
    pub transport       : Box<dyn Transport>,
    /// Facilitates communication between `Transport` and this `Handler`.
    pub buffer          : Rc<RefCell<SharedBuffer>>,
    /// Callback called when internal error happens.
    pub on_error        : Callback<HandlingError>,
    /// Callback called when notification from server is received.
    pub on_notification : Callback<messages::Notification<serde_json::Value>>,
}

impl Handler {
    pub fn new(transport:Box<dyn Transport>) -> Handler {
        let mut ret = Handler {
            ongoing_calls   : OngoingCalls::new(),
            id_generator    : IdGenerator::new(),
            transport,
            buffer          :  Rc::new(RefCell::new(SharedBuffer::new())),
            on_error        : Callback::new(),
            on_notification : Callback::new(),
        };
        ret.transport.set_callback(ret.buffer.clone());
        ret
    }

    pub fn open_request<In : api::RemoteMethodCall>
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
        let message = api::into_request_message(input,id);
        self.ongoing_calls.insert(message.payload.id, sender);

        let serialized_message = serde_json::to_string(&message).unwrap();
        self.transport.send_text(serialized_message);
        ret
    }

    pub fn process_response
    (&mut self, message:messages::Response<serde_json::Value>) {
        println!("Got response to request {}", message.id);
        if let Some(sender) = self.ongoing_calls.remove(&message.id) {
            let _ = sender.send(message.result);
        } else {
            self.error_occurred(HandlingError::UnexpectedResponse(message));
        }
    }
    pub fn process_notification
    (&mut self, message:messages::Notification<serde_json::Value>) {
        println!("Got notification: {:?}", message);
        self.on_notification.try_call(message);
    }

    pub fn process_incoming_message(&mut self, message:String) {
        println!("Process {}", message);
        match serde_json::from_str(&message) {
            Ok(messages::IncomingMessage::Response(response)) =>
                self.process_response(response),
            Ok(messages::IncomingMessage::Notification(notification)) =>
                self.process_notification(notification),
            Err(err) =>
                self.error_occurred(HandlingError::InvalidMessage(err)),
        }
    }

    pub fn error_occurred(&mut self, error: HandlingError) {
        println!("Internal error occurred: {}", error);
        self.on_error.try_call(error);
    }

    pub fn tick(&mut self) {
        let buffer = match self.buffer.try_borrow_mut() {
            Ok(mut buffer) => buffer.take(),
            Err(_)         => return,

        };

        for msg in buffer.incoming {
            self.process_incoming_message(msg);
        }

        if buffer.closed {
            // Dropping all ongoing calls will mark their futures as cancelled.
            self.ongoing_calls.clear();
        };
    }
}