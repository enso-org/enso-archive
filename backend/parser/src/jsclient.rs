use crate::{api, api::Parser};

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug)]
pub enum Error {
    NotImplemented,
}

impl std::error::Error for Error {}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "JS parser client has not been yet implemented!")
    }
}

pub struct Client {}

impl Client {
    pub fn new() -> Result<Client> {
        Err(Error::NotImplemented)
    }
}

impl Parser for Client {
    fn parse(&mut self, _program: String) -> api::Result<api::AST> {
        Err(api::Error::InteropError(Box::new(Error::NotImplemented)))
    }
}
