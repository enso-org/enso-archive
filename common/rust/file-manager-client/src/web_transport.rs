use prelude::*;

use crate::log;

use js_sys::Function;
use json_rpc::Transport;
use json_rpc::TransportCallbacks;

use wasm_bindgen::prelude::*;
use wasm_bindgen::JsCast;
//use web_sys::Blob;
use web_sys::CloseEvent;
//use web_sys::ErrorEvent;
use web_sys::MessageEvent;
use web_sys::WebSocket;

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
}

impl Transport for MyWebSocket {
    fn send_text(&mut self, message:String) {
        log!("will send text message: {}", message);
        self.ws.send_with_str(&message);
    }

    fn set_callback(&mut self, cb:Rc<RefCell<dyn TransportCallbacks>>) {
        let cb1 = cb.clone();
        let on_message = Closure::wrap(Box::new(move |e: MessageEvent| {
            let data = e.data();
            log!("incoming data: {:?}", data);
            if let Some(text) = data.as_string() {
                log!("received text data: {}", text);
                cb1.borrow_mut().on_text_message(text);
            }
//            else if let Ok(blob) = data.dyn_into::<Blob>() {
//                log!("received binary data: {:?}", blob);
//                cb.borrow_mut().on_binary_message(blob.into());
//            }
//            else {
//                log!("received unknown kind of data: {:?}", data);
//            }
        }) as Box<dyn FnMut(MessageEvent)>);
        self.on_message_closure.store(on_message);
        self.ws.set_onmessage(self.on_message_closure.function());

        let on_close = Closure::wrap(Box::new(move |e:CloseEvent| {
            log!("socket closed {:?}", e);
            cb.borrow_mut().on_close();
        }) as Box<dyn FnMut(CloseEvent)>);

        self.on_closed_closure.store(on_close);
        self.ws.set_onclose(self.on_closed_closure.function());
    }
}

pub async fn new_websocket(url:&str) -> WebSocket {
    log!("Starting new WebSocket...");
    let (sender, receiver) = futures::channel::oneshot::channel::<WebSocket>();
    let ws = WebSocket::new(url).unwrap();
    let cloned_ws = ws.clone();
    let sender = Rc::new(RefCell::new(Some(sender)));
    let onopen_callback = Closure::wrap(Box::new(move |_| {
        if let Some(s) = sender.borrow_mut().take() {
            log!("WebSocket successfully opened!");
            s.send(cloned_ws.clone());
            cloned_ws.set_onopen(None);
        }
    }) as Box<dyn FnMut(JsValue)>);
    ws.set_onopen(Some(onopen_callback.as_ref().unchecked_ref()));
    onopen_callback.forget();
    receiver.await.unwrap()
}
