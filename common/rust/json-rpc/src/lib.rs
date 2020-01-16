#![feature(trait_alias)]
#![warn(missing_docs)]

//! This is a library aimed to facilitate implementing JSON-RPC protocol
//! clients. The main type is `Handler` that a client should build upon.

pub mod api;
pub mod error;
pub mod handler;
pub mod messages;
pub mod transport;

pub use transport::Transport;
pub use transport::TransportCallbacks;
pub use handler::Handler;
