
type Result<T> = websocket::result::WebSocketResult<T>;

type AST = String;

pub trait ParserService {
    fn parse(&mut self, input: String) -> Result<AST> {
        let response = self.call(input)?;
        Ok(deserialize_ast(response))
    }

    fn call(&mut self, input: String) -> Result<String>;
}

// TODO : deserialization should also handle failure
fn deserialize_ast(serialized_ast: String) -> AST {
    serialized_ast
}