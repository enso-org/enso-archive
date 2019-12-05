use parser::Parser;
use wasm_bindgen_test::{wasm_bindgen_test_configure, wasm_bindgen_test};
use wasm_bindgen::prelude::*;

wasm_bindgen_test_configure!(run_in_browser);


#[wasm_bindgen_test]
fn web_test() {

    let mut parser = Parser::new_or_panic();

    let mut parse = |input| {
       let output = parser.parse(String::from(input));
       Result::from(output).unwrap()
    };

    assert_eq!(parse(""), "{\"ModuleOf\":{\"lines\":[{\"elem\":null,\"off\":0}]}}");
}