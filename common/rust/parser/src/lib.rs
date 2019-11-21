pub mod api;

mod jsclient;
mod wsclient;

use std::ops::DerefMut;
use wasm_bindgen::prelude::*;
use crate::api::IsParser;

/// Handle to a parser implementation.
///
/// Currently this component is implemented as a wrapper over parser written
/// in Scala. Depending on compilation target (native or wasm) it uses either
/// implementation provided by `wsclient` or `jsclient`.
#[derive(shrinkwraprs::Shrinkwrap)]
#[shrinkwrap(mutable)]
pub struct Parser(pub Box<dyn api::IsParser>);

impl Parser {
    /// Obtains a default parser implementation.
    #[cfg(not(target_arch = "wasm32"))]
    pub fn new() -> api::Result<Parser> {
        let client = wsclient::Client::new()?;
        let parser = Box::new(client);
        Ok(Parser(parser))
    }

    /// Obtains a default parser implementation.
    #[cfg(target_arch = "wasm32")]
    pub fn new() -> api::Result<Parser> {
        let client = jsclient::Client::new()?;
        let parser = Box::new(client);
        Ok(Parser(parser))
    }

    /// Obtains a default parser implementation, panicking in case of failure.
    pub fn new_or_panic() -> Parser {
        Parser::new()
            .unwrap_or_else(|e| panic!("Failed to create a parser: {:?}", e))
    }

}

impl api::IsParser for Parser {
    fn parse(&mut self, program: String) -> api::Result<api::AST> {
        self.deref_mut().parse(program)
    }
}

// Called when the wasm module is instantiated
#[wasm_bindgen(start)]
pub fn main() -> Result<(), JsValue> {
    // Use `web_sys`'s global `window` function to get a handle on the global
    // window object.
    let window = web_sys::window().expect("no global `window` exists");
    let document = window.document().expect("should have a document on window");
    let body = document.body().expect("document should have a body");

    // Manufacture the element we're gonna append
    let val = document.create_element("p")?;
    val.set_inner_html("Hello from Rust!");

    body.append_child(&val)?;

    Ok(())
}

#[wasm_bindgen]
pub fn parse() -> String {
    Parser::new_or_panic().parse(String::from("HELLO WORLD")).ok().expect("!!!")
}
