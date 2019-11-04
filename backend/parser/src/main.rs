//use crate::;

use parser::api::ParserService;
use parser::websocket::{Config, Client};

fn main() {
    let config = Config::from_env();
    let mut client = Client::new(&config).unwrap();

    let input = String::from("import Foo.Bar");
    let output = client.call(input);
    match output {
        Ok(result) => println!("OK: {}", result),
        Err(e) => println!("Error: {}", e),
    }
}
