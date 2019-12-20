use prelude::*;

use macro_utils::path_segment_generic_args;
use quote::quote;
use proc_macro2::TokenStream;
use syn::punctuated::Punctuated;use syn::Expr;
use syn::Token;

pub fn not_supported
(input: proc_macro::TokenStream) -> proc_macro::TokenStream {
    let target = syn::parse::<syn::PathSegment>(input).unwrap();
    let ty_args = path_segment_generic_args(&target);
    let ret = quote!{
        // Sample expansion for: Import<T>
        //
        // impl<T> HasSpan for Import<T> {
        //     fn span(&self) -> usize {
        //         panic!("HasSpan is not supported for Spaceless AST!")
        //     }
        // }
        // impl<T> HasRepr for Import<T> {
        //     fn write_repr(&self, target:&mut String) {
        //         panic!("HasRepr not supported for Spaceless AST!")
        //     }
        // }
        impl<#(#ty_args),*> HasSpan for #target {
            fn span(&self) -> usize {
                panic!("HasSpan not supported for Spaceless AST!")
            }
        }
        impl<#(#ty_args),*> HasRepr for #target {
            fn write_repr(&self, target:&mut String) {
                panic!("HasRepr not supported for Spaceless AST!")
            }
        }
    };
    ret.into()
}

/// Creates a HasRepr and HasSpan implementations for a given enum type.
///
/// Given type may only consist of single-elem typle-like constructors.
/// The implementation uses underlying HasSpan implementation for each stored
/// value.
pub fn derive_for_enum
(decl:&syn::DeriveInput, data:&syn::DataEnum)
 -> TokenStream  {
    let ident     = &decl.ident;
    let params    = decl.generics.params.iter().collect_vec();
    let span_arms = data.variants.iter().map(|v| {
        let con_ident = &v.ident;
        quote!( #ident::#con_ident (elem) => elem.span() )
    });
    let repr_arms = data.variants.iter().map(|v| {
        let con_ident = &v.ident;
        quote!( #ident::#con_ident (elem) => elem.write_repr(target) )
    });
    let ret = quote! {
        impl<#(#params: HasSpan),*> HasSpan for #ident<#(#params),*> {
            fn span(&self) -> usize {
                match self {
                    #(#span_arms),*
                }
            }
        }
        impl<#(#params: HasRepr),*> HasRepr for #ident<#(#params),*> {
            fn write_repr(&self, target:&mut String) {
                match self {
                    #(#repr_arms),*
                }
            }
        }
    };
    ret
}


pub struct ReprDescription {
    pub ty     :syn::PathSegment,
    pub exprs  :Vec<syn::Expr>,
    pub ty_args:Vec<syn::GenericArgument>,
}

impl ReprDescription {
    fn new
    (mut input:TokenStream,new_name:Option<&str>)
    -> syn::Result<ReprDescription> {
        if let Some(new_name) = new_name {
            use macro_utils::replace_ident_tokens;
            let from = "_PLACEHOLDER_";
            input = replace_ident_tokens(input.clone(), from, new_name);
        }
        syn::parse2(TokenStream::from_iter(input))

    }
}

impl syn::parse::Parse for ReprDescription {
    fn parse(input: syn::parse::ParseStream) -> syn::Result<Self> {
        let ty:syn::PathSegment = input.parse()?;
        input.parse::<Option<syn::token::Comma>>()?;
        let exprs   = Punctuated::<Expr,Token![,]>::parse_terminated(input)?;
        let exprs   = exprs.iter().cloned().collect::<Vec<_>>();
        let ty_args = path_segment_generic_args(&ty);
        let ty_args = ty_args.into_iter().cloned().collect(); // get rid of &
        Ok(ReprDescription {ty,exprs,ty_args})
    }
}

pub fn make_repr2(input: TokenStream) -> TokenStream {
    let rr : ReprDescription = syn::parse2(input).unwrap();
    let ty = rr.ty;
    let ty_args = rr.ty_args;
    let exprs = rr.exprs;
    let output = quote!{
        impl<#(#ty_args : HasSpan),*> HasSpan for #ty {
            fn span(&self) -> usize {
                0 #(+ #exprs.span())*
            }
        }

        impl<#(#ty_args : HasRepr),*> HasRepr for #ty {
            fn write_repr(&self, target:&mut String) {
                #(#exprs.write_repr(target);)*
            }
        }
    };
    output
}

pub fn make_repr3(input: TokenStream) -> TokenStream {
    let info_repr = ReprDescription::new(input.clone(), Some("write_repr")).unwrap();
    let info_span = ReprDescription::new(input.clone(), Some("sum_span"  )).unwrap();
    let ty        = info_repr.ty;
    let ty_args   = info_repr.ty_args;

    let repr_exprs = info_repr.exprs;
    let span_exprs = info_span.exprs;

    let output = quote!{
        impl<#(#ty_args : HasSpan),*> HasSpan for #ty {
            fn span(&self) -> usize {
                let mut target = 0;
                #(target = #span_exprs;)*
                target
            }
        }

        impl<#(#ty_args : HasRepr),*> HasRepr for #ty {
            fn write_repr(&self, target:&mut String) {
                #(#repr_exprs;)*
            }
        }
    };
    output
//    quote!()
}
