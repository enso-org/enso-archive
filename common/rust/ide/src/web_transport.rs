use prelude::*;

use crate::log;

use js_sys::Function;
use json_rpc::Transport;
use json_rpc::TransportEvent;
use failure::Error;

use wasm_bindgen::prelude::*;
use wasm_bindgen::JsCast;
use web_sys::CloseEvent;
use web_sys::MessageEvent;
use web_sys::WebSocket;
//use std::cell::Cell;

#[derive(Clone,Copy,Debug,PartialEq)]
pub enum WebSocketState {
    Connecting,
    Open,
    Closing,
    Closed,
    Unknown(u16),
}

/// Returns current state of the WebSocket connection, translating from magic numbers to our enum.
pub fn websocket_state(ws:&web_sys::WebSocket) -> WebSocketState {
    match ws.ready_state() {
        WebSocket::CONNECTING => WebSocketState::Connecting,
        WebSocket::OPEN       => WebSocketState::Open,
        WebSocket::CLOSING    => WebSocketState::Closing,
        WebSocket::CLOSED     => WebSocketState::Closed,
        num                   => WebSocketState::Unknown(num), // impossible
    }
}

#[derive(Debug)]
pub struct MyWebSocket {
    pub ws                 : web_sys::WebSocket,
    pub on_message_closure : ClosureStorage<MessageEvent>,
    pub on_closed_closure  : ClosureStorage<CloseEvent>,
}

#[derive(Debug)]
pub struct ClosureStorage<T> {
    pub closure:Option<Closure<dyn FnMut(T)>>,
}
impl <T> ClosureStorage<T> {
    pub fn new() -> ClosureStorage<T> {
        ClosureStorage { closure : None }
    }
    pub fn store(&mut self, closure:Closure<dyn FnMut(T)>) {
        self.closure = Some(closure);
    }
    pub fn function(&self) -> Option<&Function> {
        self.closure.as_ref().map(|closure| closure.as_ref().unchecked_ref() )
    }
}
impl MyWebSocket {
    pub async fn new(url:&str) -> MyWebSocket {
        MyWebSocket {
            ws                 : new_websocket(url).await,
            on_message_closure : ClosureStorage::new(),
            on_closed_closure  : ClosureStorage::new(),
        }
    }

    pub fn state(&self) -> WebSocketState {
        websocket_state(&self.ws)
    }
}

#[derive(Debug, Fail)]
enum SendingError {
    #[fail(display = "Failed to send message. Exception: {:?}.", _0)]
    FailedToSend(String),

    #[fail(display = "Failed to send message because socket state is {:?}.", _0)]
    NotOpen(WebSocketState),
}

impl Transport for MyWebSocket {
    fn send_text(&mut self, message:String) -> Result<(), Error> {
        log!("will send text message: {}", message);
        log!("ws declared state: {:?}", websocket_state(&self.ws));

        // Sending through the closed WebSocket can return Ok() with error only
        // appearing in the log. We explicitly check for this to get failure as
        // early as possible.
        //
        // If WebSocket closes after the check, caller will be able to handle it
        // when receiving `TransportEvent::Closed`.
        let state = self.state();
        if state != WebSocketState::Open {
            return Err(SendingError::NotOpen(state))?;
        }

        let ret = self.ws.send_with_str(&message);
        let ret = ret.map_err(|e| {
            SendingError::FailedToSend(format!("{:?}", e))
        });
        Ok(ret?)

//        match self.ws.send_with_str(&message) {
//            Ok(_) => {
//                log!("message successfully sent!");
//                Ok(())
//            }
//            Err(e) => {
//                log!("failed to send a message: {:?}", e);
//                Err(SendingError::FailedToSend(format!("{:?}", e)))?
//            }
//        }
    }

    fn set_event_tx(&mut self, tx:std::sync::mpsc::Sender<TransportEvent>) {
        let tx1 = tx.clone();
        let on_message = Closure::wrap(Box::new(move |e: MessageEvent| {
            let data = e.data();
            if let Some(text) = data.as_string() {
                let _ = tx1.send(TransportEvent::TextMessage(text));
            }
        }) as Box<dyn FnMut(MessageEvent)>);
        self.on_message_closure.store(on_message);
        self.ws.set_onmessage(self.on_message_closure.function());

        let on_close = Closure::wrap(Box::new(move |_e:CloseEvent| {
            let _ = tx.send(TransportEvent::Closed);
        }) as Box<dyn FnMut(CloseEvent)>);
        self.on_closed_closure.store(on_close);
        self.ws.set_onclose(self.on_closed_closure.function());
    }
}

pub async fn new_websocket(url:&str) -> WebSocket {
    log!("Starting new WebSocket connecting to {}...", url);
    let (sender, receiver) = futures::channel::oneshot::channel::<WebSocket>();
    let ws = WebSocket::new(url).unwrap();
    let cloned_ws = ws.clone();
    let sender = Rc::new(RefCell::new(Some(sender)));
    let onopen_callback = Closure::wrap(Box::new(move |_| {
        if let Some(s) = sender.borrow_mut().take() {
            log!("WebSocket successfully opened!");
            let _ = s.send(cloned_ws.clone());
            cloned_ws.set_onopen(None);
        }
    }) as Box<dyn FnMut(JsValue)>);
    ws.set_onopen(Some(onopen_callback.as_ref().unchecked_ref()));
    onopen_callback.forget();
    receiver.await.unwrap()
}
