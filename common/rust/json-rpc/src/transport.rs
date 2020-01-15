//! Traits providing obstraction over transport used by the JSON-RPC client.

use prelude::*;

pub trait TransportCallbacks {
    fn on_text_message(&mut self, message:String) {}
    fn on_close(&mut self) {}
}

pub trait Transport {
    //    fn close(&mut self);
    fn send_text(&mut self, message:String);
    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>);
}
impl<T: Transport> Transport for Rc<RefCell<T>> {
    //    fn close(&mut self);
    fn send_text(&mut self, message:String) {
        self.borrow_mut().send_text(message)
    }
    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>) {
        self.borrow_mut().set_callback(cb)
    }
}