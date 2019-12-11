use std::fs::{File, create_dir_all};
use std::io::prelude::*;
use std::io::BufReader;

const PARSER_PATH: &str = "../../target/scala-parser.js";
const PARSER_PATH_FIX: &str = "pkg/scala-parser.js";

fn prepend(input: File, mut output: File, text: &str) -> std::io::Result<()> {
    let buffered = BufReader::new(input);
    writeln!(output, "{}", text)?;
    for line in buffered.lines() {
        writeln!(output, "{}", line?)?;
    }
    Ok(())
}

/* fixes a scalajs bug https://github.com/scala-js/scala-js/issues/3677/ */
fn scalajs_fix() -> std::io::Result<()>   {
    let original = File::open(PARSER_PATH)
          .expect("Could not find file enso/common/target/scala-parser.js");
    create_dir_all("pkg/").expect("Could not create file /pkg/");
    let fixed = File::create(PARSER_PATH_FIX)
          .expect("Could not create file enso/common/rust/parser/pkg/scala-parser.js");
    prepend(original, fixed, "var __ScalaJSEnv = { global: window };")
}
fn main() -> std::io::Result<()>  {
    scalajs_fix()?;
    Ok(())
}