//! This crate defines a custom derive macro `Iterator`. Should not be used
//! directly, but only through `shapely` crate, as it provides utilities
//! necessary for the generated code to compile.

extern crate proc_macro;

use prelude::*;

use inflector::Inflector;
use proc_macro2::{TokenStream,Ident,Span};
use quote::quote;
use syn;
use macro_utils::{fields_list,type_matches,type_depends_on};


/// For `struct Foo<T>` or `enum Foo<T>` provides:
/// * `IntoIterator` implementations for `&'t Foo<T>` and `&mut 't Foo<T>`;
/// * `iter` and `into_iter` methods.
///
/// The iterators will:
/// * for structs: go over each field that declared type is same as the
///   struct's last type parameter.
/// * enums: delegate to current constructor's nested value if it is takes `T`
///   type argument; or return empty iterator otherwise.
///
/// Caller must have the following features enabled:
/// ```
/// #![feature(generators)]
/// #![feature(type_alias_impl_trait)]
/// ```
///
/// When used on type that takes no type parameters, like `struct Foo`, does
/// nothing but yields no errors.
#[proc_macro_derive(Iterator)]
pub fn derive_iterator
(input: proc_macro::TokenStream) -> proc_macro::TokenStream {
    let decl   = syn::parse_macro_input!(input as syn::DeriveInput);
    let params = &decl.generics.params.iter().collect::<Vec<_>>();
    match params.last() {
        Some(last_param) => derive_iterator_for(&decl, &last_param),
        None             => proc_macro::TokenStream::from(quote! {})
    }
}

/// Returns identifiers of fields with type matching `target_param`.
///
/// If the struct is tuple-like, returns index pseudo-identifiers.
fn matching_fields
( data:&syn::DataStruct
, target_param:&syn::GenericParam
) -> Vec<TokenStream> {
    let fields           = fields_list(&data.fields);
    let fields           = fields.iter().enumerate();
    let ret              = fields.filter_map(|(i, f)| {
        let type_matched = type_matches(&f.ty, target_param);
        type_matched.as_some_from(|| {
            match &f.ident {
                Some(ident) => quote!(#ident),
                None => {
                    let ix = syn::Index::from(i);
                    quote!(#ix)
                }
            }
        })
    }).collect::<Vec<_>>();
//    println!("{:?}", ret.iter().map(|a| repr(&a)).collect::<Vec<_>>());
    ret
}

fn variant_depends_on
(var:&syn::Variant, target_param:&syn::GenericParam) -> bool {
    var.fields.iter().any(|field| type_depends_on(&field.ty, target_param))
}

/// Derives iterator that iterates over fields of type `target_param`.
fn derive_iterator_for
( decl         : &syn::DeriveInput
, target_param : &syn::GenericParam
) -> proc_macro::TokenStream {
//    println!("==============================================================");
    let data           = &decl.data;
    let params         = &decl.generics.params.iter().collect::<Vec<_>>();
    let ident          = &decl.ident;
    let t_iterator     = format!("{}Iterator"    , ident);
    let t_iterator_mut = format!("{}IteratorMut" , ident);
    let iterator       = t_iterator.to_snake_case();
    let iterator_mut   = t_iterator_mut.to_snake_case();
    let t_iterator     = Ident::new(&t_iterator     , Span::call_site());
    let t_iterator_mut = Ident::new(&t_iterator_mut , Span::call_site());
    let iterator       = Ident::new(&iterator       , Span::call_site());
    let iterator_mut   = Ident::new(&iterator_mut   , Span::call_site());

    let iterator_params = match data {
        syn::Data::Struct(_) => params.clone(),
        syn::Data::Enum  (_) => vec!(target_param),
        _                    =>
            panic!("Only Structs and Enums can derive(Iterator)!"),
    };

    // Introduces iterator type definitions.
    let iterator_tydefs = match data {
        syn::Data::Struct(_) => quote!(
            // type FooIterator<'t, T>    = impl Iterator<Item = &'t T>;
            // type FooIteratorMut<'t, T> = impl Iterator<Item = &'t mut T>;
            type #t_iterator<'t, #(#iterator_params),*> =
                impl Iterator<Item = &'t #target_param>;
            type #t_iterator_mut<'t, #(#iterator_params),*> =
                impl Iterator<Item = &'t mut #target_param>;
        ),
        syn::Data::Enum(_) => quote!(
            // type FooIterator<'t, U> =
            //     Box<dyn Iterator<Item=&'t U> + 't>;
            // type FooIteratorMut<'t, U> =
            //     Box<dyn Iterator<Item=&'t mut U> + 't>;
            type #t_iterator<'t, #(#iterator_params),*>  =
                Box<dyn Iterator<Item=&'t #target_param> + 't>;
            type #t_iterator_mut<'t, #(#iterator_params),*> =
                Box<dyn Iterator<Item=&'t mut #target_param> + 't>;
        ),
        _ => panic!("Only Structs and Enums can derive(Iterator)!"),
    } ;

    // Introduces bodies of function generating iterators.
    let (iter_body, iter_body_mut) = match data {
        syn::Data::Struct(ref data) => {
            let matched_fields = matching_fields(data, target_param);

            // shapely::EmptyIterator::new()
            let empty_body = quote! { shapely::EmptyIterator::new() };

            // shapely::GeneratingIterator(move || {
            //     yield &t.foo;
            // })
            let body = quote! {
                shapely::GeneratingIterator
                (move || { #(yield &t.#matched_fields;)* })
            };

            // shapely::GeneratingIterator(move || {
            //     yield &mut t.foo;
            // })
            let body_mut = quote! {
                shapely::GeneratingIterator
                (move || { #(yield &mut t.#matched_fields;)* })
            };

            if matched_fields.is_empty() {
                (empty_body.clone(), empty_body)
            } else {
                (body, body_mut)
            }
        },

        syn::Data::Enum(ref data) => {
            // For types that use target type parameter, refer to their
            // `IntoIterator` implementation. Otherwise, use `EmptyIterator`.
            let arms = data.variants.iter().map(|var| {
                let con = &var.ident;
                let iter = if variant_depends_on(var, target_param) {
                    quote!(elem.into_iter())
                }
                else {
                    quote!(shapely::EmptyIterator::new())
                };
                quote!(#ident::#con(elem) => Box::new(#iter))
            });

            // match t {
            //     Foo::Con1(elem) => Box::new(elem.into_iter()),
            //     Foo::Con2(elem) => Box::new(shapely::EmptyIterator::new()),
            // }
            let body = quote!(
                match t {
                    #(#arms,)*
                }
            );
            (body.clone(), body)
        }
        _ => panic!("Only Structs and Enums can derive(Iterator)!"),
    };

    let output = quote! {
        #iterator_tydefs

        // pub fn foo_iterator<'t, T>
        // (t: &'t Foo<T>) -> FooIterator<'t, T> {
        //    shapely::GeneratingIterator(move || {
        //        yield &t.foo;
        //    })
        // }
        pub fn #iterator<'t, #(#params),*>
        (t: &'t #ident<#(#params),*>) -> #t_iterator<'t, #(#iterator_params),*> {
            #iter_body
        }

        // pub fn foo_iterator_mut<'t, T>
        // (t: &'t mut Foo<T>) -> FooIteratorMut<'t, T> {
        //    shapely::GeneratingIterator(move || {
        //        yield &t.foo;
        //    })
        // }
        pub fn #iterator_mut<'t, #(#params),*>
        (t: &'t mut #ident<#(#params),*>) -> #t_iterator_mut<'t, #(#iterator_params),*> {
            #iter_body_mut
        }

        // impl<'t, T> IntoIterator for &'t Foo<T> {
        //     type Item     = &'t T;
        //     type IntoIter = FooIterator<'t, T>;
        //     fn into_iter(self) -> FooIterator<'t, T> {
        //         foo_iterator(self)
        //     }
        // }
        impl<'t, #(#params),*> IntoIterator for &'t #ident<#(#params),*> {
            type Item     = &'t #target_param;
            type IntoIter = #t_iterator<'t, #(#iterator_params),*>;
            fn into_iter(self) -> #t_iterator<'t, #(#iterator_params),*> {
                #iterator(self)
            }
        }

        // impl<'t, T> IntoIterator for &'t mut Foo<T> {
        //     type Item     = &'t mut T;
        //     type IntoIter = FooIteratorMut<'t, T>;
        //     fn into_iter(self) -> FooIteratorMut<'t, T> {
        //         foo_iterator_mut(self)
        //     }
        // }
        impl<'t, #(#params),*> IntoIterator for &'t mut #ident<#(#params),*> {
            type Item     = &'t mut #target_param;
            type IntoIter = #t_iterator_mut<'t, #(#iterator_params),*>;
            fn into_iter(self) -> #t_iterator_mut<'t, #(#iterator_params),*> {
                #iterator_mut(self)
            }
        }

        // impl Foo<T> {
        //     pub fn iter(&self) -> FooIterator<'_, T> {
        //         #foo_iterator(self)
        //     }
        //     pub fn iter_mut(&mut self) -> FooIteratorMut<'_, T> {
        //         #foo_iterator_mut (self)
        //     }
        // }
        impl<#(#params),*> #ident<#(#params),*> {
            pub fn iter(&self) -> #t_iterator<'_, #(#iterator_params),*> {
                #iterator(self)
            }
            pub fn iter_mut(&mut self) -> #t_iterator_mut<'_, #(#iterator_params),*> {
                #iterator_mut(self)
            }
        }
    };

    proc_macro::TokenStream::from(output)
}


// Note [Expansion Example]
// ~~~~~~~~~~~~~~~~~~~~~~~~
// In order to make the definition easier to read, an example expansion of the
// following definition was provided for each quotation:
//
// #[derive(Iterator)]
// pub struct Foo<S, T> { foo: T }
//
// For examples that are enum-specific rather than struct-specific, the
// following definition is assumed:
//
// #[derive(Iterator)]
// pub enum Foo<T> {
//     Con1(Bar<T>),
//     Con2(Baz),
// }

