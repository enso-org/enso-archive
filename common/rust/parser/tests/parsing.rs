use prelude::*;
use parser::api::IsParser;

use ast::{Ast, Shape};

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

fn assert_var<StringLike: Into<String>>(name: StringLike, ast: &Ast) {
    let actual: &ast::Var = expect_shape(ast);
    let expected          = ast::Var{ name: name.into() };
    assert_eq!(*actual, expected);
}

fn assert_opr<StringLike: Into<String>>(name: StringLike, ast: &Ast) {
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

    fn deserialize_prefix(&mut self) {
        self.test_shape("foo   bar", |shape:&ast::Prefix<Ast>| {
            assert_var("foo", &shape.func);
            assert_eq!(shape.off, 3);
            assert_var("bar", &shape.arg);
        });
    }

    fn deserialize_infix(&mut self) {
        self.test_shape("foo +  bar", |shape:&ast::Infix<Ast>| {
            assert_var("foo", &shape.larg);
            assert_eq!(shape.loff, 1);
            assert_opr("+", &shape.opr);
            assert_eq!(shape.roff, 2);
            assert_var("bar", &shape.rarg);
        });
    }
    fn deserialize_left(&mut self) {
        self.test_shape("foo +", |shape:&ast::SectLeft<Ast>| {
            assert_var("foo", &shape.arg);
            assert_eq!(shape.off, 1);
            assert_opr("+", &shape.opr);
        });
    }
    fn deserialize_right(&mut self) {
        self.test_shape("+ bar", |shape:&ast::SectRight<Ast>| {
            assert_opr("+", &shape.opr);
            assert_eq!(shape.off, 1);
            assert_var("bar", &shape.arg);
        });
    }
    fn deserialize_sides(&mut self) {
        self.test_shape("+", |shape:&ast::SectSides<Ast>| {
            assert_opr("+", &shape.opr);
        });
    }

    fn deserialize_block(&mut self) {
        self.test_shape(" foo\n bar", |block:&ast::Block<Ast>| {
            assert_eq!(block.ty, ast::BlockType::Continuous);
            assert_eq!(block.indent, 1);
            assert_eq!(block.empty_lines.len(), 0);
            assert_eq!(block.is_orphan, true);

            assert_eq!(block.first_line.off, 0);
            assert_var("foo", &block.first_line.elem);

            assert_eq!(block.lines.len(), 1);
            let second_line = block.lines.iter().nth(0).unwrap();

            assert_eq!(second_line.off, 0);
            assert_var("bar", second_line.elem.as_ref().unwrap());
        });
    }

    fn run(&mut self) {
        /// We are not testing:
        /// * Opr (doesn't parse on its own, covered by Infix and other)
        /// * Module (covered by every single test, as parser wraps everything
        ///   into module)
        ///

        self.deserialize_blank();
        self.deserialize_var();
        self.deserialize_cons();
        self.deserialize_mod();
        self.deserialize_number();
        self.deserialize_prefix();
        self.deserialize_infix();
        self.deserialize_left();
        self.deserialize_right();
        self.deserialize_sides();

        self.deserialize_block();
    }
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
