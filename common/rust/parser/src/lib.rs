pub mod api;

mod jsclient;
mod wsclient;

type ParserWrapper = Box<dyn api::Parser>;

#[cfg(not(target_arch = "wasm32"))]
pub fn new_parser() -> api::Result<ParserWrapper> {
    let client = wsclient::Client::new()?;
    let parser = Box::new(client);
    Ok(parser)
}

#[cfg(target_arch = "wasm32")]
pub fn new_parser() -> api::Result<ParserWrapper> {
    let client = jsclient::Client::new()?;
    let parser = Box::new(client);
    Ok(parser)
}