use std::fs::File;
use std::io::prelude::*;
use std::io::BufReader;

const PARSER_PATH: &str = "./pkg/scala-parser.js";
const PARSER_PATH_FIX: &str = "./pkg/scala-parser-fix.js";

fn main() -> std::io::Result<()> {
    // FIX for a scalajs es-module bug (solved in scalajs 1.0)
    let     file = File::open(PARSER_PATH)?;
    let mut temp = File::create(PARSER_PATH_FIX)?;

    let buffered = BufReader::new(file);

    writeln!(temp, "var __ScalaJSEnv = {{ global: window }};")?;
    for line in buffered.lines() {
        writeln!(temp, "{}", line?)?;
    }

    Ok(())
}