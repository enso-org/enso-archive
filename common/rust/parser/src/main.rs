/// Simple interactive tester - calls parser with its argument (or a
/// hardcoded default) and prints the result
fn main() {
    let default_input = String::from("import Foo.Bar\nfoo = a + 2");
    let input = std::env::args().skip(1).next().unwrap_or(default_input);
    println!("Will parse: {}", input);

    let mut parser = parser::new_parser()
        .unwrap_or_else(|e| panic!("Failed to create a parser: {:?}", e));
    let output = parser.parse(input);
    match output {
        Ok(result) => {
            println!("Parser responded with: {:?}", result);
        }
        Err(e) => println!("Failed to obtain a response: {:?}", e),
    }
}
