pub type AST = String; // TODO: until we have real AST, see: https://github.com/luna/enso/issues/296

///////////
// Error //
///////////

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug)]
pub enum Error {
    ParsingError(String),
    InteropError(Box<dyn std::error::Error>),
}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        use Error::*;
        match self {
            ParsingError(msg) => write!(f, "Internal parser error: {:?}", msg),
            InteropError(error) => {
                write!(f, "Interop error: {}", error.to_string())
            }
        }
    }
}

////////////
// Parser //
////////////

pub trait Parser {
    fn parse(&mut self, program: String) -> Result<AST>;
}
