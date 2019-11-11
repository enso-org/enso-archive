//use crate::;

use parser::api::ParserService;
use parser::websocket::{Config, Client};

fn main() {

//    println!("{:?}", );//    let res = Response::Success{result: String::from("foo")};
////    let serialized = serde_json::to_string(&res);
////    serialized.map(|s| println!("{}", s));

    let config = Config::from_env();
    println!("Connecting to {}", config.address_string());
    let mut client = Client::new(&config).unwrap();
    println!("Established connection with {}", config.address_string());

    let input = String::from("import Foo.Bar");
    println!("Will parse {}", input);
    let output = client.call(input);
    match output {
        Ok(result) => {
            println!("Parser responded text: {:?}", result);
            let json: serde_json::Result<Response> = serde_json::from_str(&result);
            println!("Parser responded response: {:?}", json);
        },
        Err(e) =>
            println!("Failed to obtain a response: {}", e),
    }
}
