pub mod api;

mod jsclient;
mod wsclient;

pub struct Parser {
    parser: Box<dyn api::Parser>,
}

impl Parser {
    #[cfg(not(target_arch = "wasm32"))]
    pub fn new() -> api::Result<Parser> {
        let client = wsclient::Client::new()?;
        let parser = Box::new(client);
        Ok(Parser { parser })
    }
    #[cfg(target_arch = "wasm32")]
    pub fn new() -> api::Result<Parser> {
        let client = jsclient::Client::new()?;
        let parser = Box::new(client);
        Ok(Parser { parser })
    }
}

impl api::Parser for Parser {
    fn parse(&mut self, program: String) -> api::Result<api::AST> {
        self.parser.parse(program)
    }
}
