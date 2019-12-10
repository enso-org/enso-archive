use std::fs::File;
use std::io::prelude::*;
use std::io::BufReader;

const PARSER_PATH: &str = "./pkg/scala-parser.js";
const PARSER_PATH_FIX: &str = "./pkg/scala-parser-fix.js";

fn prepend(input: File, mut output: File, text: &str) -> std::io::Result<()> {
    let buffered = BufReader::new(input);
    writeln!(output, "{}", text)?;
    for line in buffered.lines() {
        writeln!(output, "{}", line?)?;
    }
    Ok(())
}

/* fixes a scalajs bug https://github.com/scala-js/scala-js/issues/3677/ */
fn scalaJSFix() -> std::io::Result<()>   {
    let original = File::open(PARSER_PATH)
          .expect(&format!("{} {}", "There should be file", PARSER_PATH));
    let mut fixed = File::create(PARSER_PATH_FIX)
          .expect(&format!("{} {}", "There should be file", PARSER_PATH_FIX));
    prepend(original, fixed, "var __ScalaJSEnv = {{ global: window }};")
}
fn main() -> std::io::Result<()>  {
    #[cfg(target_arch = "wasm32")]
    scalaJSFix()?;
    Ok(())
}