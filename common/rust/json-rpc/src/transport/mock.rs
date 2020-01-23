//! Module provides a `MockTransport` that implements `Transport`.
//!
//! It is meant to be used in tests.


use prelude::*;

use crate::transport::Transport;
use crate::transport::TransportEvent;

use std::collections::VecDeque;
use failure::Error;
use serde::de::DeserializeOwned;
use serde::Serialize;



// ====================
// === SendingError ===
// ====================

/// Errors emitted by the `MockTransport`.
#[derive(Clone,Copy,Debug,Fail)]
pub enum SendingError {
    /// Cannot send message while the connection is closed.
    #[fail(display = "Cannot send message when socket is closed.")]
    TransportClosed,
}



// ========================
// === Transport Status ===
// ========================

/// Status of the `MockTransport`.
#[derive(Clone,Copy,Debug)]
pub enum Status {
    /// Transport is functional, can send messages.
    Open,
    /// Transport is not functional at the moment, cannot send messages.
    Closed
}



// ======================
// === Transport Data ===
// ======================

/// Mock transport shared data. Collects all the messages sent by the owner.
///
/// Allows mocking messages from the peer.
#[derive(Debug)]
pub struct MockTransportData {
    /// Events sink.
    pub event_tx  : Option<std::sync::mpsc::Sender<TransportEvent>>,
    /// Messages sent by the user.
    pub sent_msgs : VecDeque<String>,
    /// Transport status.
    pub is_closed : bool,
}

impl Default for MockTransportData {
    fn default() -> MockTransportData {
        MockTransportData {
            event_tx  : None,
            sent_msgs : VecDeque::new(),
            is_closed : false,
        }
    }
}



// ======================
// === Mock Transport ===
// ======================

/// Shareable wrapper over `MockTransportData`.
#[derive(Clone, Debug, Default)]
pub struct MockTransport(Rc<RefCell<MockTransportData>>);

impl Transport for MockTransport {
    fn send_text(&mut self, text:String) -> Result<(), Error> {
        let mut me = self.0.borrow_mut();
        if me.is_closed {
            Err(SendingError::TransportClosed)?
        } else {
            me.sent_msgs.push_back(text.clone());
            Ok(())
        }
    }

    fn set_event_tx(&mut self, tx:std::sync::mpsc::Sender<TransportEvent>) {
        self.0.borrow_mut().event_tx = Some(tx);
    }
}

impl MockTransport {
    /// Create a new `MockTransport`.
    pub fn new() -> MockTransport {
        MockTransport::default()
    }

    /// Generates event that mocks receiving a text message from a peer.
    pub fn mock_peer_message_text<S:Into<String>>(&mut self, message:S) {
        let message = message.into();
        if let Some(ref tx) = self.0.borrow_mut().event_tx {
            let _ = tx.send(TransportEvent::TextMessage(message));
        }
    }

    /// Generates event that mocks receiving a text message from a peer with
    /// serialized JSON contents.
    pub fn mock_peer_message<T:Serialize>(&mut self, message:T) {
        let text = serde_json::to_string(&message);
        let text = text.expect("failed to serialize mock message");
        self.mock_peer_message_text(text)
    }

    /// Mocks event generated when peer closes the socket (or connection is lost
    /// for any other reason).
    pub fn mock_connection_closed(&mut self) {
        if let Some(ref tx) = self.0.borrow_mut().event_tx {
            self.0.borrow_mut().is_closed = true;
            let _          = tx.send(TransportEvent::Closed);
        }
    }

    /// Takes the message sent by the client and returns its texts.
    ///
    /// If the client did not sent any messages, panics.
    /// If the client sent multiple messages, the first one is returned.
    /// Further messages can be obtained by subsequent calls.
    pub fn expect_message_text(&mut self) -> String {
        self.0.borrow_mut().sent_msgs.pop_front().expect("client should have sent request")
    }

    /// Similar to `expect_message_text` but deserializes the message into
    /// given type `T` from JSON.
    pub fn expect_message<T:DeserializeOwned>(&mut self) -> T {
        let text = self.expect_message_text();
        let res  = serde_json::from_str(&text);
        res.expect("failed to deserialize client's message")
    }
}