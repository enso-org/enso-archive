use ast::*;

fn main() {
    let foo = Var { name: "foo".to_string() };
    let arg = Cons { name: "Bar".to_string() };
    let app: Ast = Ast::from(Shape::from( App { func: Ast::from(Shape::from(foo)), off: 0, arg: Ast::from(Shape::from(arg))} ));

    let ast = app;
    let serialized = serde_json::to_string(&ast).unwrap();
    println!("Serialized AST: {}", serialized);

//    let assign = Ast::from(Shape::from(SectLeft { arg: Ast::from(Shape::from(Var { name: "a".to_string()})), off: 1, opr: Opr { name: "=".to_string()} } ));
//
//    println!("foo");
////    println!("{:?}", assign);
//    let i = assign.iter();
//    for val in i {
//        println!("{:?}", val);
//    }

//    let b: Shape<Ast> = Shape::from(a);
//    let c: Ast = Ast::from(b);
//
//
//
////    let serialized = serde_json::to_string(&c).unwrap();
//    let serialized = String::from(r#"{"Var":{"name":"foo"},"span":0,"id":null}"#);
//    let d: Ast = serde_json::from_str(&serialized).unwrap();
//    let serialized = serde_json::to_string(&app).unwrap();
//    println!("serialized = {}", serialized);
//    let deserialized: Ast = serde_json::from_str(&serialized).unwrap();
//    println!("deserialized = {:?}", deserialized);
//    println!("repr = {:?}", deserialized.repr());
}