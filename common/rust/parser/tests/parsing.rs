use prelude::*;
use parser::api::IsParser;

use ast::{Ast, Shape, SegmentRaw, TextLine};
use ast::SegmentFmt::SegmentExpr;

/// Takes Ast being a module with a single line and returns that line's AST.
fn expect_single_line(ast: &Ast) -> &Ast {
    let module: &ast::Module<Ast> = expect_shape(ast);
    assert_eq!(module.lines.len(), 1, "module expected to have a single line");
    let line = module.lines.iter().nth(0).unwrap();
    line.elem.as_ref().unwrap()
}

/// "Downcasts" given AST's Shape to `T`.
fn expect_shape<'t, T>(ast: &'t Ast) -> &'t T
where &'t Shape<Ast>: TryInto<&'t T> {
    match ast.shape().try_into() {
        Ok(shape) => shape,
        _         => {
            let expected_ty = std::any::type_name::<T>();
            panic!("failed converting shape into {}", expected_ty)
        },
    }
}

fn assert_var<StringLike: Into<String>>(ast: &Ast, name: StringLike) {
    let actual: &ast::Var = expect_shape(ast);

    let expected          = ast::Var{ name: name.into() };
    assert_eq!(*actual, expected);
}

fn assert_opr<StringLike: Into<String>>(ast: &Ast, name: StringLike) {
    let actual: &ast::Opr = expect_shape(ast);
    let expected          = ast::Opr{ name: name.into() };
    assert_eq!(*actual, expected);
}

/// Persists parser (which is expensive to construct, so we want to reuse it
/// between tests. Additionally, hosts a number of helper methods.
struct TestHelper(parser::Parser);

impl TestHelper {
    fn new() -> TestHelper {
        TestHelper(parser::Parser::new_or_panic())
    }

    fn parse(&mut self, program: &str) -> Ast {
        self.0.parse(program.into()).unwrap()
    }

    /// Program is expected to be single line module. The line's AST is
    /// returned.
    fn parse_line(&mut self, program: &str) -> Ast {
        let ast = self.parse(program);
        let line = expect_single_line(&ast);
        line.clone()

    }
    /// Program is expected to be single line module. The line's Shape subtype
    /// is obtained and passed to `tester`.
    fn test_shape<T, F>(&mut self, program: &str, tester: F)
    where for<'t> &'t Shape<Ast>: TryInto<&'t T>,
        F: FnOnce(&T) -> () {
        let ast = self.parse_line(program);
        let shape = expect_shape(&ast);
        tester(shape);
    }

    fn deserialize_unrecognized(&mut self) {
        let unfinished = r#"`"#;
        self.test_shape(unfinished, |shape:&ast::Unrecognized| {
            assert_eq!(shape.str, "`");
        });
    }

    fn deserialize_blank(&mut self) {
        let _ast = self.test_shape("_", |_:&ast::Blank| {});
    }

    fn deserialize_var(&mut self) {
        self.test_shape("foo", |var: &ast::Var| {
            let expected_var = ast::Var { name: "foo".into() };
            assert_eq!(var, &expected_var);
        });
    }

    fn deserialize_cons(&mut self) {
        let name = "FooBar";
        self.test_shape(name, |shape:&ast::Cons| {
            assert_eq!(shape.name, name);
        });
    }

    fn deserialize_mod(&mut self) {
        self.test_shape("+=", |shape:&ast::Mod| {
            assert_eq!(shape.name, "+");
        });
    }

    fn deserialize_invalid_suffix(&mut self) {
        self.test_shape("foo'bar", |shape:&ast::InvalidSuffix<Ast>| {
            assert_var(&shape.elem, "foo'");
            assert_eq!(shape.suffix, "bar");
        });
    }

    // We can't test parsing Opr directly, but it is part of infix test.

    // Literals

    fn deserialize_number(&mut self) {
        self.test_shape("127", |shape:&ast::Number| {
            assert_eq!(shape.base, None);
            assert_eq!(shape.int, "127");
        });

        self.test_shape("16_ff", |shape:&ast::Number| {
            assert_eq!(shape.base.as_ref().unwrap(), "16");
            assert_eq!(shape.int, "ff");
        });
    }

    fn deserialize_text_line_raw(&mut self) {
        self.test_shape("\"foo\"", |shape:&ast::TextLineRaw| {
            assert_eq!(shape.text.len(), 1);
            let segment  = shape.text.iter().nth(0).unwrap();
            let expected = ast::SegmentPlain{value: "foo".to_string()};
            let expected = SegmentRaw::from(expected);
            assert_eq!(*segment, expected);
        });

        let tricky_raw = r#""\\\"\n""#;
        self.test_shape(tricky_raw, |shape:&ast::TextLineRaw| {
            assert_eq!(shape.text.len(), 3);

            let mut segments = shape.text.iter();
            let mut next_segment = || segments.next().unwrap();

            assert_eq!(*next_segment(), ast::Slash{}.into() );
            assert_eq!(*next_segment(), ast::RawQuote{}.into() );
            assert_eq!(*next_segment(), ast::Invalid{ str: 'n'}.into() );
            // TODO can ast::Quote be used here?
            //  if not, what is the point of having it?
        });
    }

    fn deserialize_text_line_fmt(&mut self) {
        // plain
        self.test_shape("'foo'", |shape:&ast::TextLineFmt<Ast>| {
            assert_eq!(shape.text.len(), 1);
            let segment  = shape.text.iter().nth(0).unwrap();
            let expected = ast::SegmentPlain{value: "foo".to_string()};
            let expected = ast::SegmentFmt::from(expected);
            assert_eq!(*segment, expected);
        });

        // raw escapes
        let tricky_fmt = r#"'\\\'\"'"#;
        self.test_shape(tricky_fmt, |shape:&ast::TextLineFmt<Ast>| {
            assert_eq!(shape.text.len(), 3);

            let mut segments = shape.text.iter();
            let mut next_segment = || segments.next().unwrap();

            assert_eq!(*next_segment(), ast::Slash{}.into() );
            assert_eq!(*next_segment(), ast::Quote{}.into() );
            // TODO: the behavior below is actually a bug in parser, however
            //  we don't test the parser but the ability to read its results.
            assert_eq!(*next_segment(), ast::Invalid{ str: '"'}.into() );
        });

        // expression empty
        let expr_fmt = r#"'``'"#;
        self.test_shape(expr_fmt, |shape:&ast::TextLineFmt<Ast>| {
            assert_eq!(shape.text.len(), 1);
            let segment = shape.text.iter().next().unwrap();
            match segment {
                SegmentExpr(expr) => assert_eq!(expr.value, None),
                _                 => panic!("wrong segment type received"),
            }
        });

        // expression non-empty
        let expr_fmt = r#"'`foo`'"#;
        self.test_shape(expr_fmt, |shape:&ast::TextLineFmt<Ast>| {
            assert_eq!(shape.text.len(), 1);
            let segment = shape.text.iter().next().unwrap();
            match segment {
                SegmentExpr(expr) => {
                    assert_var(expr.value.as_ref().unwrap(), "foo");
                },
                _ => panic!("wrong segment type received"),
            }
        });

        let expr_fmt = r#"'\n\u0394\U0001f34c'"#;
        self.test_shape(expr_fmt, |shape:&ast::TextLineFmt<Ast>| {
            assert_eq!(shape.text.len(), 3);
            let mut segments = shape.text.iter();
            let mut next_segment = || segments.next().unwrap();

            let expected = ast::Escape::Character{c:'n'};
            assert_eq!(*next_segment(), expected.into());

            let expected = ast::Escape::Unicode16{digits: "0394".into()};
            assert_eq!(*next_segment(), expected.into());

            // TODO We don't test Unicode21 as it is not yet supported by
            //      parser.

            let expected = ast::Escape::Unicode32{digits: "0001f34c".into()};
            assert_eq!(*next_segment(), expected.into());
        });
    }

    fn deserialize_unfinished_text(&mut self) {
        let unfinished = r#""\"#;
        self.test_shape(unfinished, |shape:&ast::TextUnclosed<Ast>| {
            let line                    = &shape.line;
            let line: &ast::TextLineRaw = line.try_into().unwrap();

            let segment  = line.text.iter().next().unwrap();
            let expected = ast::Unfinished {};
            assert_eq!(*segment, expected.into());
        });
    }

    fn deserialize_dangling_base(&mut self) {
        self.test_shape("16_", |shape:&ast::DanglingBase| {
            assert_eq!(shape.base, "16");
        });
    }

    fn deserialize_prefix(&mut self) {
        self.test_shape("foo   bar", |shape:&ast::Prefix<Ast>| {
            assert_var(&shape.func, "foo");
            assert_eq!(shape.off, 3);
            assert_var(&shape.arg, "bar");
        });
    }

    fn deserialize_infix(&mut self) {
        self.test_shape("foo +  bar", |shape:&ast::Infix<Ast>| {
            assert_var(&shape.larg, "foo");
            assert_eq!(shape.loff, 1);
            assert_opr(&shape.opr, "+");
            assert_eq!(shape.roff, 2);
            assert_var(&shape.rarg, "bar");
        });
    }
    fn deserialize_left(&mut self) {
        self.test_shape("foo +", |shape:&ast::SectLeft<Ast>| {
            assert_var(&shape.arg, "foo");
            assert_eq!(shape.off, 1);
            assert_opr(&shape.opr, "+");
        });
    }
    fn deserialize_right(&mut self) {
        self.test_shape("+ bar", |shape:&ast::SectRight<Ast>| {
            assert_opr(&shape.opr, "+");
            assert_eq!(shape.off, 1);
            assert_var(&shape.arg, "bar");
        });
    }
    fn deserialize_sides(&mut self) {
        self.test_shape("+", |shape:&ast::SectSides<Ast>| {
            assert_opr(&shape.opr, "+");
        });
    }

    fn deserialize_block(&mut self) {
        self.test_shape(" foo\n bar", |block:&ast::Block<Ast>| {
            assert_eq!(block.ty, ast::BlockType::Continuous);
            assert_eq!(block.indent, 1);
            assert_eq!(block.empty_lines.len(), 0);
            assert_eq!(block.is_orphan, true);

            assert_eq!(block.first_line.off, 0);
            assert_var(&block.first_line.elem, "foo");

            assert_eq!(block.lines.len(), 1);
            let second_line = block.lines.iter().nth(0).unwrap();

            assert_eq!(second_line.off, 0);
            assert_var(second_line.elem.as_ref().unwrap(), "bar");
        });
    }

    /// Tests parsing a number of sample macro usages.
    ///
    /// As macros geneerate usually really huge ASTs, this test only checks
    /// that we are able to deserialize the response and that it is a macro
    /// match node. Node contents is not covered.
    fn deserialize_macro_matches(&mut self) {
        let mut expect_match = |program| {
            let ast = self.parse_line(program);
            expect_shape::<ast::Match<Ast>>(&ast);
        };
        expect_match("foo -> bar");
        expect_match("()");
        expect_match("(foo -> bar)");
        expect_match("type Maybe a\n    Just val:a");
        expect_match("foreign Python3\n  bar");
        expect_match("if foo > 8 then 10 else 9");
        expect_match("skip bar");
        expect_match("freeze bar");
        expect_match("case foo of\n  bar");
    }

    fn deserialize_macro_ambiguous(&mut self) {
        self.test_shape("if  foo", |shape:&ast::Ambiguous| {
            let segment = &shape.segs.head;
            assert_var(&segment.head, "if");

            let segment_body = segment.body.as_ref().unwrap();
            assert_eq!(segment_body.off, 2);
            assert_var(&segment_body.wrapped, "foo");
        });
    }

    fn run(&mut self) {
        /// We are not testing:
        /// * Opr (doesn't parse on its own, covered by Infix and other)
        /// * Module (covered by every single test, as parser wraps everything
        ///   into module)
        ///

        self.deserialize_unrecognized();

        self.deserialize_blank();
        self.deserialize_var();
        self.deserialize_cons();
        self.deserialize_mod();
        self.deserialize_invalid_suffix();
        self.deserialize_number();
        self.deserialize_text_line_raw();
        self.deserialize_text_line_fmt();
        self.deserialize_unfinished_text();
        self.deserialize_dangling_base();
        self.deserialize_prefix();
        self.deserialize_infix();
        self.deserialize_left();
        self.deserialize_right();
        self.deserialize_sides();

        self.deserialize_block();
        self.deserialize_macro_matches();
        self.deserialize_macro_ambiguous();
    }
}

//deserialize_option_unit

#[test]
fn playground() {
    use ast::*;
    let unit1: Option<()> = Some(());
    let unit2: Option<()> = None;
    println!("Unit ():\t{}", serde_json::to_string(&unit1).unwrap());
    println!("Unit ():\t{}", serde_json::to_string(&unit2).unwrap());

    let sss = "null".to_string();
    let unit3: Option<()> = serde_json::from_str(&sss).unwrap();
    println!("Unit3 ():\t{:?}", unit3);

    let pat = MacroPatternMatchRawBegin { pat:  MacroPatternRawBegin };
//    let m = MacroPatternMatchRaw::<Ast>::Begin(pat);

    let json_text = std::fs::read_to_string("Z:/tmp2.json").unwrap();
    let pat = serde_json::from_str::<MacroPatternMatchRaw<Shifted<Ast>>>(&json_text).unwrap();
//    println!("{}", serde_json::to_string(&m).unwrap());
}

/// A single entry point for all the tests here using external parser.
///
/// Setting up the parser is costly, so we run all tests as a single batch.
/// Until proper CI solution for calling external parser is devised, this
/// test is marked with `#[ignore]`.
#[test]
//#[ignore]
fn parser_tests() {
    TestHelper::new().run()
}
