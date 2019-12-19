use prelude::*;

use quote::quote;
use proc_macro2::{TokenStream,Ident,Span};
use macro_utils::{fields_list, type_matches, repr, type_depends_on, ty_path_type_args, field_ident_token};
use inflector::Inflector;
use syn::Field;

pub struct DependentValue<'t> {
    pub ty          : &'t syn::Type,
    pub value       : TokenStream,
    pub target_param: &'t syn::GenericParam,
    pub through_ref : bool
}

impl<'t> DependentValue<'t> {
    /// Returns Some when type is dependent and None otherwise.
    pub fn try_new
    (ty: &'t syn::Type, value:TokenStream, target_param:&'t syn::GenericParam)
     -> Option<DependentValue<'t>> {
        if type_depends_on(ty, target_param) {
            Some(DependentValue{ty,value,target_param,through_ref:false})
        } else {
            None
        }
    }

    /// Describe sub-values of the tuple
    pub fn collect_tuple
    (tuple:&'t syn::TypeTuple, target_param:&'t syn::GenericParam)
     -> Vec<DependentValue<'t>> {
        tuple.elems.iter().enumerate().filter_map(|(ix,ty)| {
            let ix    = syn::Index::from(ix);
            let ident = quote!(t.#ix);
            DependentValue::try_new(ty,ident,target_param)
        }).collect()
    }

    pub fn yield_value(&self, is_mut: bool) -> TokenStream {
        match self.ty {
            syn::Type::Tuple(tuple) => self.yield_tuple_value(tuple, is_mut),
            syn::Type::Path(path)   => {
                if type_matches(&self.ty, &self.target_param) {
                    self.yield_direct_value(is_mut)
                } else {
                    self.yield_dependent_ty_path_value(path, is_mut)
                }
            }
            _ =>
                panic!("Don't know how to yield value from type {}", repr(&self.ty)),
        }
    }

    // e.g. value:T
    // yield &mut value;
    pub fn yield_direct_value
    (&self, is_mut: bool) -> TokenStream {
        let value = &self.value;
        let opt_mut = is_mut.as_some(quote!( mut ));
        let opt_ref = (!self.through_ref).as_some(quote!( & #opt_mut ));
        quote!(  yield #opt_ref #value; )
    }

    // e.g. for t:(T,U,T)
    // yield &mut t.0;
    // yield &mut t.2;
    pub fn yield_tuple_value
    (&self, ty:&syn::TypeTuple, is_mut: bool)
     -> TokenStream {
        let value     = &self.value;
        let mut_kwd   = is_mut.as_some(quote!( mut ));
        let top_ident = &self.value;
        let subfields = DependentValue::collect_tuple(ty, self.target_param);
        let yield_sub = subfields.iter().map(|f| f.yield_value(is_mut)).collect_vec();
        quote!( {
            let t = & #mut_kwd #value;
            #(#yield_sub)*
        })
    }

    /// Obtain the type of iterator-yielded value.
    pub fn type_path_elem_type(&self, ty_path:&'t syn::TypePath) -> &syn::Type {
        let mut type_args = ty_path_type_args(ty_path);
        let     last_arg  = match type_args.pop() {
            Some(arg) => arg,
            None      => panic!("Type {} has no segments!", repr(&ty_path))
        };

        // Last and only last type argument is dependent.
        for non_last_segment in type_args {
            assert!(!type_depends_on(non_last_segment, self.target_param)
                    , "Type {} has non-last argument {} that depends on {}"
                    , repr(ty_path)
                    , repr(non_last_segment)
                    , repr(self.target_param)
            );
        }
        assert!(type_depends_on(last_arg, self.target_param));
        last_arg
    }

    /// Yields values of the dependent type.
    pub fn yield_dependent_ty_path_value
    (&self, ty_path:&'t syn::TypePath, is_mut: bool)
     -> TokenStream {
        let opt_mut   = is_mut.as_some(quote!( mut ));
        let elem_ty = self.type_path_elem_type(ty_path);
        let elem    = quote!(t);

        let elem_info = DependentValue{
            value        : elem.clone(),
            target_param : self.target_param,
            ty           : elem_ty,
            through_ref  : true,
        };
        let yield_elem = elem_info.yield_value(is_mut);
        let value      = &self.value;
        let iter       = if is_mut {
            quote!(iter_mut)
        } else {
            quote!(iter)
        };

        quote! {
            for #opt_mut #elem in #value.#iter() {
                #yield_elem
            }
        }
    }

    /// Describe relevant fields of the struct definition.
    pub fn collect_struct
    (data:&'t syn::DataStruct, target_param:&'t syn::GenericParam)
    -> Vec<DependentValue<'t>> {
        let fields    = fields_list(&data.fields);
        let dep_field = fields.iter().enumerate().filter_map(|(i,f)| {
            let ident = field_ident_token(f,i.into());
            let value = quote!(t.#ident);
            DependentValue::try_new(&f.ty,value,target_param)
        });
        dep_field.collect()
    }
}

/// Does enum variant depend on given type.
pub fn variant_depends_on
(var:&syn::Variant, target_param:&syn::GenericParam) -> bool {
    var.fields.iter().any(|field| type_depends_on(&field.ty, target_param))
}

/// Parts of derivation output that are specific to enum- or struct- target.
pub struct OutputParts<'ast> {
    pub iterator_tydefs  : TokenStream,
    pub iter_body        : TokenStream,
    pub iter_body_mut    : TokenStream,
    pub iterator_params  : Vec<&'ast syn::GenericParam>,
}

/// Common data used when generating derived Iterator impls.
///
/// Examples are given for `pub struct Foo<S, T> { foo: T }`
pub struct DerivingIterator<'ast> {
    pub data          : &'ast syn::Data,         // { foo: T }
    pub ident         : &'ast syn::Ident,        // Foo
    pub params        : Vec<&'ast syn::GenericParam>, // <S, T>
    pub t_iterator    : syn::Ident,              // FooIterator
    pub t_iterator_mut: syn::Ident,              // FooIteratorMut
    pub iterator      : syn::Ident,              // foo_iterator
    pub iterator_mut  : syn::Ident,              // foo_iterator_mut
    pub target_param  : &'ast syn::GenericParam  // T
}

impl DerivingIterator<'_> {
    pub fn new<'ast>
    (decl: &'ast syn::DeriveInput, target_param : &'ast syn::GenericParam)
     -> DerivingIterator<'ast> {
        let data           = &decl.data;
        let params         = decl.generics.params.iter().collect::<Vec<_>>();
        let ident          = &decl.ident;
        let t_iterator     = format!("{}Iterator"    , ident);
        let t_iterator_mut = format!("{}IteratorMut" , ident);
        let iterator       = t_iterator.to_snake_case();
        let iterator_mut   = t_iterator_mut.to_snake_case();
        let t_iterator     = Ident::new(&t_iterator     , Span::call_site());
        let t_iterator_mut = Ident::new(&t_iterator_mut , Span::call_site());
        let iterator       = Ident::new(&iterator       , Span::call_site());
        let iterator_mut   = Ident::new(&iterator_mut   , Span::call_site());
        DerivingIterator {
            data,
            ident,
            params,
            t_iterator,
            t_iterator_mut,
            iterator,
            iterator_mut,
            target_param
        }
    }

    /// Handles all enum-specific parts.
    pub fn prepare_parts_enum(&self, data:&syn::DataEnum) -> OutputParts {
        let t_iterator      = &self.t_iterator;
        let t_iterator_mut  = &self.t_iterator_mut;
        let ident           = &self.ident;
        let target_param    = &self.target_param;
        let iterator_params = vec!(self.target_param);
        let iterator_tydefs = quote!(
            // type FooIterator<'t, U> =
            //     Box<dyn Iterator<Item=&'t U> + 't>;
            // type FooIteratorMut<'t, U> =
            //     Box<dyn Iterator<Item=&'t mut U> + 't>;
            type #t_iterator<'t, #(#iterator_params),*>  =
                Box<dyn Iterator<Item=&'t #target_param> + 't>;
            type #t_iterator_mut<'t, #(#iterator_params),*> =
                Box<dyn Iterator<Item=&'t mut #target_param> + 't>;
        );
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
        let iter_body       = quote!( match t {  #(#arms,)*  } );
        let iter_body_mut   = iter_body.clone();
        OutputParts{iterator_tydefs,iter_body,iter_body_mut,iterator_params}
    }

    /// Handles all struct-specific parts.
    pub fn prepare_parts_struct(&self, data:&syn::DataStruct) -> OutputParts {
        let t_iterator = &self.t_iterator;
        let t_iterator_mut = &self.t_iterator_mut;
        let target_param = &self.target_param;
        let iterator_params = self.params.clone();
        let iterator_tydefs = quote!(
            // type FooIterator<'t, T>    = impl Iterator<Item = &'t T>;
            // type FooIteratorMut<'t, T> = impl Iterator<Item = &'t mut T>;
            type #t_iterator<'t, #(#iterator_params),*> =
                impl Iterator<Item = &'t #target_param>;
            type #t_iterator_mut<'t, #(#iterator_params),*> =
                impl Iterator<Item = &'t mut #target_param>;
        );
        let matched_fields = DependentValue::collect_struct(data, target_param);
        let yield_fields = matched_fields.iter().map(|field| {
            field.yield_value(false)
        }).collect_vec();
        let yield_mut_fields = matched_fields.iter().map(|field| {
            field.yield_value(true)
        }).collect_vec();

        // shapely::EmptyIterator::new()
        let empty_body = quote! { shapely::EmptyIterator::new() };

        // shapely::GeneratingIterator(move || {
        //     yield &t.foo;
        // })
        let body = quote! {
            shapely::GeneratingIterator
            (move || { #(#yield_fields)* })
        };

        // shapely::GeneratingIterator(move || {
        //     yield &mut t.foo;
        // })
        let body_mut = quote! {
            shapely::GeneratingIterator
            (move || { #(#yield_mut_fields)* })
        };

        let (iter_body, iter_body_mut) = match matched_fields.is_empty() {
            true => (empty_body.clone(), empty_body),
            false => (body, body_mut)
        };
        OutputParts{iterator_tydefs,iter_body,iter_body_mut,iterator_params}
    }

    /// Handles common (between enum and struct) code and assembles it all
    /// into a final derivation output.
    pub fn assemble_output(&self, parts:OutputParts) -> TokenStream {
        let iterator_tydefs = &parts.iterator_tydefs;
        let iter_body       = &parts.iter_body;
        let iter_body_mut   = &parts.iter_body_mut;
        let iterator_params = &parts.iterator_params;
        let iterator        = &self.iterator;
        let iterator_mut    = &self.iterator_mut;
        let t_iterator      = &self.t_iterator;
        let t_iterator_mut  = &self.t_iterator_mut;
        let params          = &self.params;
        let ident           = &self.ident;
        let target_param    = &self.target_param;

        quote!{
            #iterator_tydefs

            // pub fn foo_iterator<'t, T>
            // (t: &'t Foo<T>) -> FooIterator<'t, T> {
            //    shapely::GeneratingIterator(move || {
            //        yield &t.foo;
            //    })
            // }
            pub fn #iterator<'t, #(#params),*>
            (t: &'t #ident<#(#params),*>)
             -> #t_iterator<'t, #(#iterator_params),*> {
                #iter_body
            }

            // pub fn foo_iterator_mut<'t, T>
            // (t: &'t mut Foo<T>) -> FooIteratorMut<'t, T> {
            //    shapely::GeneratingIterator(move || {
            //        yield &t.foo;
            //    })
            // }
            pub fn #iterator_mut<'t, #(#params),*>
            (t: &'t mut #ident<#(#params),*>)
            -> #t_iterator_mut<'t, #(#iterator_params),*> {
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
            impl<'t, #(#params),*>
            IntoIterator for &'t mut #ident<#(#params),*> {
                type Item     = &'t mut #target_param;
                type IntoIter = #t_iterator_mut<'t, #(#iterator_params),*>;
                fn into_iter
                (self) -> #t_iterator_mut<'t, #(#iterator_params),*> {
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
                pub fn iter_mut
                (&mut self) -> #t_iterator_mut<'_, #(#iterator_params),*> {
                    #iterator_mut(self)
                }
            }
        }
    }

    pub fn output(&self) -> TokenStream {
        let parts = match self.data {
            syn::Data::Struct(data) => self.prepare_parts_struct(data),
            syn::Data::Enum  (data) => self.prepare_parts_enum  (data),
            _                       =>
                panic!("Only Structs and Enums can derive(Iterator)!"),
        };
        self.assemble_output(parts)
    }
}

pub fn derive
(decl: &syn::DeriveInput, target_param : &syn::GenericParam) -> TokenStream {
    let derive = DerivingIterator::new(decl,target_param);
    derive.output()
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

