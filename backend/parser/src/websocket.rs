use websocket::stream::sync::TcpStream;
use websocket::{ClientBuilder, Message, OwnedMessage};
use websocket::result::WebSocketError::NoDataAvailable;

type WsTcpClient = websocket::sync::Client<TcpStream>;
type Result<T> = websocket::result::WebSocketResult<T>;

use crate::api::ParserService;

//////////////////////////
// Constants & literals //
//////////////////////////

const LOCALHOST: &str = "localhost";
const DEFAULT_PORT: i32 = 30615;
const DEFAULT_HOSTNAME: &str = LOCALHOST;
const DEFAULT_RESOURCE: &str = "/";

const HOSTNAME_ENV_VAR_NAME: &str = "ENSO_PARSER_HOSTNAME";
const PORT_ENV_VAR_NAME: &str = "ENSO_PARSER_PORT";
const RESOURCE_ENV_VAR_NAME: &str = "ENSO_PARSER_RESOURCE";


////////////
// Config //
////////////

pub struct Config {
    pub host: String,
    pub port: i32,
    pub resource: String,
}

impl Config {
    pub fn address_string(&self) -> String {
        format!("http://{}:{}{}", self.host, self.port, self.resource)
    }

    pub fn from_env() -> Config {
        let host = env_var_or(HOSTNAME_ENV_VAR_NAME, DEFAULT_HOSTNAME);
        let port = env_var_or(PORT_ENV_VAR_NAME, "")
            .parse().unwrap_or(DEFAULT_PORT);
        let resource = env_var_or(RESOURCE_ENV_VAR_NAME, DEFAULT_RESOURCE);
        Config {host, port, resource}
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
}

impl ParserService for Client {
    fn call(&mut self, input: String) -> Result<String> {
        let message = Message::text(input);
        self.connection.send_message(&message)?;
        let response = self.connection.recv_message()?;
        match response {
            OwnedMessage::Text(text) => Ok(text),
            _ => Err(NoDataAvailable),
        }
    }
}