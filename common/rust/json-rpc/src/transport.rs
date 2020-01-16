//! Traits providing obstraction over transport used by the JSON-RPC client.

use prelude::*;

/// Callback for `Transport`.
pub trait TransportCallbacks : Debug {
    /// A text message has been received.
    fn on_text_message(&mut self, _message:String) {}

    /// A transport has been closed.
    fn on_close(&mut self) {}
}

/// A transport that facilitate JSON-RPC protocol.
///
/// We rely on WebSockets and mock test protocols.
pub trait Transport : Debug {
    /// Send a text message.
    fn send_text(&mut self, message:String);

    /// Set a callback that gets notified on transport events.
    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>);
}

impl<T: Transport> Transport for Rc<RefCell<T>> {
    fn send_text(&mut self, message:String) {
        self.borrow_mut().send_text(message)
    }
    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>) {
        self.borrow_mut().set_callback(cb)
    }
}
