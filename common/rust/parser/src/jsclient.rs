use crate::{api, api::IsParser};
use prelude::*;
use wasm_bindgen::prelude::*;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, Fail)]
pub enum Error {
   #[fail(display = "JSON (de)serialization failed: {:?}", _0)]
   JsonSerializationError(#[cause] serde_json::error::Error)
}

impl From<Error> for api::Error {
   fn from(e: Error) -> Self {
      api::interop_error(e)
   }
}

impl From<serde_json::error::Error> for Error {
   fn from(error: serde_json::error::Error) -> Self {
      Error::JsonSerializationError(error)
   }
}

#[wasm_bindgen(module = "/foo.js")] //"../../../common/scala/syntax/specialization/.js/target/scala-2.12/syntax-opt.js")]
extern "C" {
   type Parser;

   fn parser() -> Parser;

   #[wasm_bindgen(method)]
   fn run(this: &Parser, input: String) -> String;
}

/// Wrapper over the JS-compiled parser.
///
/// Can only be used when targeting WebAssembly.
pub struct Client {
   parser: Parser
}

impl Client {
   #[allow(dead_code)]
   pub fn new() -> Result<Client> {
      Ok(Client { parser : parser() })
   }
}

impl IsParser for Client {
   fn parse(&mut self, _program: String) -> api::Result<api::AST> {
      Ok(self.parser.run(_program))
   }
}
