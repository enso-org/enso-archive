use prelude::*;

use macro_utils::{path_segment_generic_args, rewrite_stream, matching_ident};
use quote::quote;
use proc_macro2::TokenStream;
use syn::punctuated::Punctuated;use syn::Expr;
use syn::Token;
use proc_macro::TokenTree;

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

impl ReprDescription {
    pub fn make_impl
    (&self, trait_name:&str, methods:&TokenStream) -> TokenStream {
        let trait_name = syn::parse_str::<syn::TypePath>(trait_name).unwrap();
        let ty      = &self.ty;
        let ty_args = &self.ty_args;
        let exprs   = &self.exprs;
        quote! {
            impl<#(#ty_args : #trait_name),*> #trait_name for #ty {
                #methods
            }
        }
    }

    pub fn make_repr(&self) -> TokenStream {
        let exprs = &self.exprs;
        self.make_impl("HasRepr", &quote!{
            fn write_repr(&self, target:&mut String) {
                #(#exprs.write_repr(target);)*
            }
        })
    }

    pub fn make_span(&self) -> TokenStream {
        let exprs = &self.exprs;
        self.make_impl("HasSpan", &quote!{
            fn span(&self) -> usize {
                0 #(+ #exprs.span())*
            }
        })
    }

    pub fn make_repr_span(&self) -> TokenStream {
        let mut ret = self.make_repr();
        ret.extend(self.make_span());
        ret
    }
}

