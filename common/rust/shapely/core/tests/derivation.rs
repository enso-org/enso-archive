#![feature(generators)]
#![feature(type_alias_impl_trait)]

use shapely::*;

/// Fails compilation if `T` is not `IntoIterator`.
fn is_into_iterator<T: IntoIterator>(){}

#[derive(Iterator, Eq, PartialEq, Debug)]
pub struct PairTT<T>(T, T);

fn to_vector<T: IntoIterator>(t: T) -> Vec<T::Item>
where T::Item: Copy{
    t.into_iter().collect()
}

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

#[derive(Iterator, Eq, PartialEq, Debug)]
pub struct PairUV<U,V>(U,V);

#[test]
fn two_params() {
    // verify that iter uses only the last type param field
    let pair = PairUV(5, 10);
    assert_eq!(to_vector(pair.iter().copied()), vec![10]);
}


#[derive(Iterator, Eq, PartialEq, Debug)]
pub struct Monomorphic(i32);

#[test]
fn no_params() {
    is_into_iterator::<& Monomorphic>();
    is_into_iterator::<&mut Monomorphic>();

    let mono = Monomorphic(15);
    mono.iter();
}
////////////////////////////////////////////////////////////////////////////////