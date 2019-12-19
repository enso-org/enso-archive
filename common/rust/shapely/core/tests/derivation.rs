#![feature(generators)]
#![feature(type_alias_impl_trait)]

use shapely::*;
use std::thread::yield_now;

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

#[test]
fn no_params() {
    // `derive(Iterator)` is no-op for structures with no type parameters.
    // We just make sure that it does not cause compilation error.
}

// ========================
// === Enumeration Type ===
// ========================

#[derive(Iterator)]
#[warn(dead_code)] // value is never read and shouldn't be
pub struct Unrecognized{ pub value : String }

#[derive(Iterator)]
pub enum Foo<U, T> {
    Con1(PairUV<U, T>),
    Con2(PairTT<T>),
    Con3(Unrecognized)
}

#[test]
fn enum_is_into_iterator() {
    is_into_iterator::<&Foo<i32, i32>>();
}

#[test]
fn enum_iter1() {
    let v          = Foo::Con1(PairUV(4, 50));
    let mut v_iter = v.into_iter();
    assert_eq!(*v_iter.next().unwrap(),50);
    assert!(v_iter.next().is_none());
}
#[test]
fn enum_iter2() {
    let v: Foo<i32, i32> = Foo::Con2(PairTT(6,60));
    let mut v_iter       = v.into_iter();
    assert_eq!(*v_iter.next().unwrap(),6);
    assert_eq!(*v_iter.next().unwrap(),60);
    assert!(v_iter.next().is_none());
}
#[test]
fn enum_iter3() {
    let v: Foo<i32, i32> = Foo::Con3(Unrecognized{value:"foo".into()});
    let mut v_iter       = v.into_iter();
    assert!(v_iter.next().is_none());
}

//

#[derive(Iterator)]
pub struct DependentTest<U, T> {
    a:T,
    b:(T,U,T),
//    c:PairUV<U, T>,
    d:PairUV<U, Option<T>>,
}

//#[derive(Iterator)]
pub struct DependentField<T> {
    a:T,
    b:Option<PairUV<i32,T>>,
}


type DependentFieldIterator<'t, T> = impl Iterator<Item=&'t T>;
type DependentFieldIteratorMut<'t, T> = impl Iterator<Item=&'t mut T>;

pub fn dependent_field_iterator<'t, T>(t: &'t DependentField<T>) ->
DependentFieldIterator<'t, T>
{
    shapely::GeneratingIterator(move || {
        yield &t.a;
        for t in t.b.iter() {
            for t in t {
                yield &t;
            }
        }
    })
}

pub fn dependent_field_iterator_mut<'t, T>(t: &'t mut DependentField<T>) ->
DependentFieldIteratorMut<'t, T>
{ shapely::GeneratingIterator(move || { yield &mut t.a; }) }

impl<'t, T> IntoIterator for &'t DependentField<T>
{
    type Item = &'t T;
    type IntoIter = DependentFieldIterator<'t, T>;
    fn
    into_iter(self) -> DependentFieldIterator<'t, T>
    { dependent_field_iterator(self)  }
}

impl<'t, T> IntoIterator for &'t mut DependentField<T>
{
    type Item = &'t mut T;
    type IntoIter = DependentFieldIteratorMut<'t, T
    >;
    fn into_iter(self) -> DependentFieldIteratorMut<'t, T>
    { dependent_field_iterator_mut(self) }
}

impl<T> DependentField<T>
{
    pub fn iter(&self) -> DependentFieldIterator<'_, T>
    { dependent_field_iterator(self) }
    pub fn iter_mut(&mut self) ->
    DependentFieldIteratorMut<'_, T>
    { dependent_field_iterator_mut(self) }
}


#[test]
fn dependent_field_iter() {
    let val = DependentField{a:5, b:Some(PairUV(4,6))};
    let mut v_iter = val.into_iter();
    assert_eq!(*v_iter.next().unwrap(), 5);
    assert_eq!(*v_iter.next().unwrap(), 6);
    assert!(v_iter.next().is_none());
}

////

struct DeeplyDependent<T> {
    a: T,
    member: Vec<Option<PairUV<i32,T>>>
}

impl<T> DeeplyDependent<T> {
    fn iter(t: &DeeplyDependent<T>) {
        shapely::GeneratingIterator(move || {
            yield &t.a;

            for vec_mem in t.member.iter() {
                for opt_mem in vec_mem.iter() {
                    for pair_mem in opt_mem.iter() {
                        yield &pair_mem;
                    }
                }
            }
        });
    }
}