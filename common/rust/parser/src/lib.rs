pub mod api;

mod jsclient;
mod wsclient;

pub struct Parser(Box<dyn api::IsParser>);

impl Parser {
    #[cfg(not(target_arch = "wasm32"))]
    pub fn new() -> api::Result<Parser> {
        let client = wsclient::Client::new()?;
        let parser = Box::new(client);
        Ok(Parser(parser))
    }

    pub fn new_or_panic() -> Parser {
        Parser::new()
            .unwrap_or_else(|e| panic!("Failed to create a parser: {:?}", e))
    }

    #[cfg(target_arch = "wasm32")]
    pub fn new() -> api::Result<ParserWrapper> {
        let client = jsclient::Client::new()?;
        let parser = Box::new(client);
        Ok(parser)
    }
}

impl api::IsParser for Parser {
    fn parse(&mut self, program: String) -> api::Result<api::AST> {
        self.0.parse(program)
    }
}
