use std::fs::File;
use std::io::prelude::*;
use wasm_bindgen::UnwrapThrowExt;

const PARSER_PATH: &str =
   "../../../common/scala/syntax/specialization/js/target/scala-2.12/syntax-opt.js";

fn main() -> std::io::Result<()> {
   std::fs::copy(PARSER_PATH, "./pkg/syntax-opt.js")?;

   Ok(())
}