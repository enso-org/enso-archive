pub type Result<T> = std::result::Result<T, ParserError>;

/////////////////
// ParserError //
/////////////////

#[derive(Debug)]
pub enum ParserError {
    WrongUrl(websocket::client::ParseError),
    ConnectivityError(websocket::WebSocketError),
    NonTextResponse(websocket::OwnedMessage),
    ParsingError(String),
    DeserializationError(String),
}

impl std::fmt::Display for ParserError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        use ParserError::*;
        match self {
            WrongUrl(url_parse_error) =>
                write!(f, "Failed to parse given address url: {}", url_parse_error),
            ConnectivityError(ws_error) =>
                write!(f, "Connection error: {}", ws_error),
            NonTextResponse(msg) => match msg {
                websocket::OwnedMessage::Close(close_data) =>
                    write!(f, "Peer closed connection: {:?}", close_data),
                _ =>
                    write!(f, "Expected text response, got: {:?}", msg),
            },
            ParsingError(msg) =>
                write!(f, "Internal parser error: {:?}", msg),
            DeserializationError(msg) =>
                write!(f, "Response deserialization error: {:?}", msg),
        }
    }
}

impl From<websocket::client::ParseError> for ParserError {
    fn from(error: websocket::client::ParseError) -> Self {
        ParserError::WrongUrl(error)
    }
}
impl From<websocket::WebSocketError> for ParserError {
    fn from(error: websocket::WebSocketError) -> Self {
        ParserError::ConnectivityError(error)
    }
}

///////////////////
// ParserService //
///////////////////

pub trait ParserService {
    fn parse(&mut self, input: String) -> Result<AST> {
        let response = self.call(input)?;
        ast_from_parser_response(response)
    }

    fn call(&mut self, input: String) -> Result<String>;
}

/////////////////
// AST //
/////////////////

type AST = String;

// TODO : deserialization should also handle failure (e.g. if schema was mismatched)
fn ast_from_parser_response(serialized_ast: String) -> Result<AST> {
    // TODO check for parser internal error
    Ok(serialized_ast)
}