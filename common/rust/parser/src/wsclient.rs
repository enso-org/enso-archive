#![cfg(not(target_arch = "wasm32"))]

use websocket::{
    stream::sync::TcpStream, ClientBuilder, Message, OwnedMessage,
};

type WsTcpClient = websocket::sync::Client<TcpStream>;

use crate::api;

use api::Error::*;
use Error::*;

// ==========================
// == Constants & literals ==
// ==========================

const LOCALHOST: &str = "localhost";
const DEFAULT_PORT: i32 = 30615;
const DEFAULT_HOSTNAME: &str = LOCALHOST;

const HOSTNAME_VAR: &str = "ENSO_PARSER_HOSTNAME";
const PORT_VAR: &str = "ENSO_PARSER_PORT";

// ===========
// == Error ==
// ===========

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug)]
pub enum Error {
    WrongUrl(websocket::client::ParseError),
    ConnectivityError(websocket::WebSocketError),
    NonTextResponse(websocket::OwnedMessage),
    JsonSerializationError(serde_json::error::Error),
}

impl From<Error> for api::Error {
    fn from(e: Error) -> Self {
        InteropError(Box::new(e))
    }
}
impl From<websocket::client::ParseError> for Error {
    fn from(error: websocket::client::ParseError) -> Self {
        WrongUrl(error)
    }
}
impl From<websocket::WebSocketError> for Error {
    fn from(error: websocket::WebSocketError) -> Self {
        ConnectivityError(error)
    }
}
impl From<serde_json::error::Error> for Error {
    fn from(error: serde_json::error::Error) -> Self {
        JsonSerializationError(error)
    }
}
impl std::error::Error for Error {}
impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        use Error::*;
        match self {
            WrongUrl(url_parse_error) => write!(
                f,
                "Failed to parse given address url: {}",
                url_parse_error
            ),
            ConnectivityError(ws_error) => {
                write!(f, "Connection error: {}", ws_error)
            }
            NonTextResponse(msg) => match msg {
                websocket::OwnedMessage::Close(close_data) => {
                    write!(f, "Peer closed connection: {:?}", close_data)
                }
                _ => write!(f, "Expected text response, got: {:?}", msg),
            },
            JsonSerializationError(msg) => {
                write!(f, "JSON (de)serialization failed: {:?}", msg)
            }
        }
    }
}

// ==============
// == Protocol ==
// ==============

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub enum Request {
    ParseRequest { program: String },
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub enum Response {
    Success { ast: String },
    Error { message: String },
}

// ============
// == Config ==
// ============

pub struct Config {
    pub host: String,
    pub port: i32,
}

impl Config {
    pub fn address_string(&self) -> String {
        format!("ws://{}:{}", self.host, self.port)
    }

    pub fn from_env() -> Config {
        let host = env_var_or(HOSTNAME_VAR, DEFAULT_HOSTNAME);
        let port = env_var_or(PORT_VAR, "").parse().unwrap_or(DEFAULT_PORT);
        Config { host, port }
    }
}

fn env_var_or(varname: &str, default_value: &str) -> String {
    std::env::var(varname).unwrap_or_else(|_| String::from(default_value))
}

// ============
// == Client ==
// ============

pub struct Client {
    connection: WsTcpClient,
}

impl Client {
    pub fn from_conf(config: &Config) -> Result<Client> {
        let address = config.address_string();
        let mut builder = ClientBuilder::new(&address)?;
        let connection = builder.connect_insecure()?;
        Ok(Client { connection })
    }

    pub fn new() -> Result<Client> {
        let config = Config::from_env();
        println!("Connecting to {}", config.address_string());
        let client = Client::from_conf(&config)?;
        println!("Established connection with {}", config.address_string());
        Ok(client)
    }

    fn send_request(&mut self, request: Request) -> Result<()> {
        let request_txt = serde_json::to_string(&request)?;
        let message = Message::text(request_txt);
        self.connection.send_message(&message)?;
        Ok(())
    }

    fn recv_response(&mut self) -> Result<Response> {
        let response = self.connection.recv_message()?;
        match response {
            OwnedMessage::Text(text) => {
                let response = serde_json::from_str(&text)?;
                Ok(response)
            }
            _ => Err(NonTextResponse(response)),
        }
    }

    fn rpc_call(&mut self, request: Request) -> Result<Response> {
        self.send_request(request)?;
        self.recv_response()
    }
}

impl api::IsParser for Client {
    fn parse(&mut self, program: String) -> api::Result<api::AST> {
        let request = Request::ParseRequest { program };
        let response = self.rpc_call(request)?;
        match response {
            Response::Success { ast } => Ok(ast),
            Response::Error { message } => Err(ParsingError(message)),
        }
    }
}

// ===========
// == tests ==
// ===========

#[test]
fn wrong_url_reported() {
    let invalid_hostname = String::from("bgjhkb 7");
    let wrong_config = Config {
        host: invalid_hostname,
        port: 8080,
    };
    let client = Client::from_conf(&wrong_config);

    if let Err(WrongUrl(_)) = client {
    } else {
        assert!(false, "expected WrongUrl error");
    }
}
