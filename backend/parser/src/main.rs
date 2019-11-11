use parser::api::Parser;

fn main() {
    let mut parser = parser::Parser::new().unwrap();
    let input = String::from("foo = a + 2");
    println!("Will parse: {}", input);
    let output = parser.parse(input);
    match output {
        Ok(result) => {
            println!("Parser responded with: {:?}", result);
        }
        Err(e) => println!("Failed to obtain a response: {}", e),
    }
}
