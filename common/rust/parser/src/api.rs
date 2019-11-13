// ============
// == Parser ==
// ============

pub trait Parser {
    fn parse(&mut self, program: String) -> Result<AST>;
}

// =========
// == AST ==
// =========

// TODO: placeholder until we have real AST, see: https:==github.com=luna=enso=issues=296
pub type AST = String;

// ===========
// == Error ==
// ===========

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug)]
pub enum Error {
    /// Error due to inner workings of the parser.
    ParsingError(String),
    /// Error related to wrapping = communication with the parser service.
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