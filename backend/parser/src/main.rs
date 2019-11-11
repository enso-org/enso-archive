use parser::api::ParserService;
use parser::websocket::{Config, Client};

fn main() {
    let config = Config::from_env();
    println!("Connecting to {}", config.address_string());
    let mut client = Client::new(&config).unwrap();
    println!("Established connection with {}", config.address_string());

    let input = String::from("foo = a + 2");
    println!("Will parse {}", input);
    let output = client.call(input);
    match output {
        Ok(result) => {
            println!("Parser responded text: {:?}", result);
        },
        Err(e) =>
            println!("Failed to obtain a response: {}", e),
    }
}
