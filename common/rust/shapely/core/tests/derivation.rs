#![feature(generators)]
#![feature(type_alias_impl_trait)]

use shapely::*;

// =============
// === Utils ===
// =============

/// To fail compilation if `T` is not `IntoIterator`.
fn is_into_iterator<T: IntoIterator>(){}

fn to_vector<T>(t: T) -> Vec<T::Item>
where T: IntoIterator,
      T::Item: Copy {
    t.into_iter().collect()
}

// =====================================
// === Struct with single type param ===
// =====================================

#[derive(Iterator, Eq, PartialEq, Debug)]
pub struct PairTT<T>(T, T);

#[test]
fn derive_iterator_single_t() {
    is_into_iterator::<& PairTT<i32>>();
    is_into_iterator::<&mut PairTT<i32>>();

    let get_pair = || PairTT(4, 49);

    // just collect values
    let pair = get_pair();
    let collected = pair.iter().copied().collect::<Vec<i32>>();
    assert_eq!(collected, vec![4, 49]);

    // IntoIterator for &mut Val
    let mut pair = get_pair();
    for i in &mut pair {
        *i = *i + 1
    }
    assert_eq!(pair, PairTT(5, 50));

    // iter_mut
    for i in pair.iter_mut() {
        *i = *i + 1
    }
    assert_eq!(pair, PairTT(6, 51));

    // IntoIterator for & Val
    let pair = get_pair(); // not mut anymore
    let mut sum = 0;
    for i in &pair {
        sum += i;
    }
    assert_eq!(sum, pair.0 + pair.1)
}

// ===================================
// === Struct with two type params ===
// ===================================

#[derive(Iterator, Eq, PartialEq, Debug)]
pub struct PairUV<U,V>(U,V);

#[test]
fn two_params() {
    // verify that iter uses only the last type param field
    let pair = PairUV(5, 10);
    assert_eq!(to_vector(pair.iter().copied()), vec![10]);
}

// ======================================
// === Struct without any type params ===
// ======================================

#[derive(Iterator, Eq, PartialEq, Debug)]
pub struct Monomorphic(i32);

#[derive(Iterator)]
pub struct Unrecognized{ vvv : String }
//
//impl IntoIterator for Unrecognized {
//    type Item = i32;
//    type IntoIter = ::std::vec::IntoIter<Self::Item>;
//
//    fn into_iter(self) -> Self::IntoIter {
//        self.0.into_iter()
//    }
//}


#[derive(Iterator)]
pub enum FooA<T> {
    Con1(PairUV<T, T>),
    Con2(PairTT<T>),
    Con3(Unrecognized)
}

pub enum Foo<T> {
    Con1(PairUV<T, T>),
    Con2(PairTT<T>),
    Con3(Unrecognized),
}

type FooIterator<'t, U> = Box<dyn Iterator<Item=&'t U> + 't>;
type FooIteratorMut<'t, U> = Box<dyn Iterator<Item=&'t mut U> + 't>;

pub fn foo_iterator<'t, U>
(t: &'t Foo<U>) -> FooIterator<'t, U> {
    match t {
        Foo::Con1(elem) => Box::new(elem.into_iter()),
        Foo::Con2(elem) => Box::new(elem.into_iter()),
        Foo::Con3(elem) => Box::new(shapely::EmptyIterator::new()),
    }
}

impl<'t, U> IntoIterator for &'t Foo<U>
{
    type Item = &'t U;
    type IntoIter = FooIterator<'t, U>;
    fn into_iter(self) -> FooIterator<'t, U>
    { foo_iterator(self) }
}


pub fn foo_iterator_mut<'t, U>
(t: &'t mut Foo<U>) -> FooIteratorMut<'t, U> {
    match t {
        Foo::Con1(elem) => Box::new(elem.into_iter()),
        Foo::Con2(elem) => Box::new(elem.into_iter()),
        Foo::Con3(elem) => Box::new(shapely::EmptyIterator::new()),
    }
}


impl<U> Foo<U>
{
    pub fn iter(&self) -> FooIterator<'_, U>
    { foo_iterator(self) }
}


#[test]
fn enum_iter() {
    let v = Foo::Con1(PairUV(4, 50));

}

#[test]
fn no_params() {
    // `derive(Iterator)` is no-op for structures with no type parameters.
    // We just make sure that it does not cause compilation error.
}