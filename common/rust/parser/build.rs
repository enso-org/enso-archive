use std::fs::{File, create_dir_all};
use std::io::prelude::*;
use std::io::BufReader;

const PARSER_PATH: &str = "../../target/scala-parser.js";
const PARSER_PATH_FIX: &str = "pkg/scala-parser-fix.js";

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
          .expect(&format!("{} {}", "There should be file", PARSER_PATH));
    create_dir_all("pkg/").expect("Could not create file /pkg/");
    let fixed = File::create(PARSER_PATH_FIX)
          .expect(&format!("{} {}", "Could not create file ", PARSER_PATH_FIX));
    prepend(original, fixed, "var __ScalaJSEnv = { global: window };")
}
fn main() -> std::io::Result<()>  {
    scalajs_fix()?;
    Ok(())
}