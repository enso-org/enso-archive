module Fixtures where

import Prelude

import Data.List as List

import Data.Int (Int64)



------------------
-- === List === --
------------------




--------------------------
-- === Input Values === --
--------------------------

millionElementList :: [Int64]
millionElementList = [1..1000000]

hundredMillion :: Int64
hundredMillion = 100000000



----------------------
-- === Fixtures === --
----------------------

sumTCO :: Int64 -> Int64
sumTCO sumTo = let
    summator accumulator current =
        if current == 0 then accumulator
        else summator (accumulator + current) (current - 1)
    in summator 0 sumTo

sumList :: [Int64] -> Int64
sumList list = let
    summator accumulator list = case list of
        x : xs -> summator (accumulator + x) xs
        []     -> accumulator
    in summator 0 list

reverseList :: [Int64] -> [Int64]
reverseList list = let
    reverser accumulator lst = case lst of
        (x : xs) -> reverser (x : accumulator) xs
        []       -> accumulator
    in reverser [] list

sumListLeftFold :: [Int64] -> Int64
sumListLeftFold = List.foldl' (+) 0

