use websocket::stream::sync::TcpStream;
use websocket::{ClientBuilder, Message, OwnedMessage};

type WsTcpClient = websocket::sync::Client<TcpStream>;

use crate::api::{ParserService, ParserError};
use crate::api::Result;
use crate::websocket::Request::ParseRequest;

//////////////////////////
// Constants & literals //
//////////////////////////

const LOCALHOST: &str = "localhost";
const DEFAULT_PORT: i32 = 30615;
const DEFAULT_HOSTNAME: &str = LOCALHOST;

const HOSTNAME_VAR: &str = "ENSO_PARSER_HOSTNAME";
const PORT_VAR: &str = "ENSO_PARSER_PORT";




//////////////
// Protocol //
//////////////

#[derive(Debug, serde::Serialize, serde::Deserialize)]
enum Request {
    ParseRequest{program: String},
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
enum Response {
    Success{ast: String},
    Error{message: String}
}

////////////
// Config //
////////////

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
        let port = env_var_or(PORT_VAR, "")
            .parse().unwrap_or(DEFAULT_PORT);
        Config {host, port}
    }
}

fn env_var_or(varname: &str, default_value: &str) -> String {
    std::env::var(varname).unwrap_or(String::from(default_value))
}

////////////
// Client //
////////////

pub struct Client {
    connection: WsTcpClient,
}

impl Client {
    pub fn new(config: &Config) -> Result<Client> {
        let address = config.address_string();
        let mut builder = ClientBuilder::new(&address)?;
        let connection = builder.connect_insecure()?;
        Ok(Client {connection})
    }

    pub fn text_rpc(&mut self, input: String) -> Result<String> {
        let message = Message::text(input);
        self.connection.send_message(&message)?;
        let response = self.connection.recv_message()?;
        match response {
            OwnedMessage::Text(text) => Ok(text),
            _ => Err(ParserError::NonTextResponse(response)),
        }
    }
}

impl ParserService for Client {
    fn call(&mut self, input: String) -> Result<String> {
        let request = ParseRequest {program: input};
        let request_txt = serde_json::to_string(&request)?;
        let response_txt  = self.text_rpc(request_txt)?;
        let response: Response = serde_json::from_str(&response_txt)?;
        match response {
            Response::Success{ast} => Ok(ast),
            Response::Error{message} => Err(ParserError::ParsingError(message)),
        }
    }
}

///////////
// tests //
///////////

#[test]
fn wrong_url_reported() {
    let invalid_hostname = String::from("bgjhkb 7");
    let wrong_config = Config { host: invalid_hostname, port: 8080 };
    let client = Client::new(&wrong_config);

    if let Err(ParserError::WrongUrl(_)) = client {} else {
        assert!(false, "expected WrongUrl error");
    }
}