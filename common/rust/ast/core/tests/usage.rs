use ast::*;

#[test]
fn var_smart_constructor() {
    let sample_name = "foo".to_string();
    let v = Ast::var(sample_name.clone());
    match v.shape() {
        Shape::Var(var) if *var.name == sample_name =>
            (),
        _ =>
            panic!("expected Var with name `{}`", sample_name),
    }
}

#[test]
fn ast_wrapping() {
    // We can convert leaf `Var` into AST without worrying about span nor id.
    let sample_name = "foo".to_string();
    let v = Var{ name: sample_name.clone() };
    let ast = Ast::from(v);
    assert_eq!(ast.wrapped.id, None);
    assert_eq!(ast.wrapped.wrapped.span, sample_name.span());
}

#[test]
fn serialization_round_trip() {
    let var_name = "foo";
    let v1      = Var { name: var_name.to_string() };
    let v1_str  = serde_json::to_string(&v1).unwrap();
    let v2: Var = serde_json::from_str(&v1_str).unwrap();
    assert_eq!(v1, v2);

    let id        = Some(15);
    let ast1      = Ast::new(v1, id);
    let ast_str   = serde_json::to_string(&ast1).unwrap();
    let ast2: Ast = serde_json::from_str(&ast_str).unwrap();
    assert_eq!(ast1, ast2);
}

