//! This module provides data structures that follow JSON-RPC 2.0 scheme. Their
//! serialization and deserialization using serde_json shall is compatible with
//! JSON-RPC complaint peers.

use serde::Serialize;
use serde::Deserialize;
use shrinkwraprs::Shrinkwrap;

/// An id identifying the call request.
/// 
/// Each request made by client should get a unique id (unique in a context of
/// the current session). Auto-incrementing integer is a common choice.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, Eq, PartialEq, PartialOrd, Ord)]
#[derive(Shrinkwrap)]
pub struct Id(pub i64);

impl std::fmt::Display for Id {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// JSON-RPC protocol version. Only 2.0 is supported.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub enum Version {
    /// Old JSON-RPC 1.0 specification. Not supported.
    #[serde(rename = "1.0")]
    V1,
    /// JSON-RPC 2.0 specification. The supported version.
    #[serde(rename = "2.0")]
    V2,
}

/// All JSON-RPC messages bear `jsonrpc` version number.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[derive(Shrinkwrap)]
pub struct Message<T> {
    /// JSON-RPC Procol version
    pub jsonrpc : Version,

    /// Payload, either a Request or Response or Notification in direct
    /// or serialized form.
    #[serde(flatten)]
    #[shrinkwrap(main_field)]
    pub payload : T
}

impl<T> Message<T> {
    /// Wraps given payload into a JSON-RPC 2.0 message.
    pub fn new(t:T) -> Message<T> {
        Message {
            jsonrpc: Version::V2,
            payload: t,
        }
    }
}

/// A non-notification request.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Request<In> {
    pub id     : Id,
    #[serde(flatten)]
    pub method : In,
}

/// A notification request.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Notification<M> {
    #[serde(flatten)]
    pub method : M,
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Response<Res> {
    pub id : Id,
    #[serde(flatten)]
    pub res: Result<Res>
}

/// Result of the remote call.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[serde(untagged)]
pub enum Result<Res> {
    Success(Success<Res>),
    Error(Error),
}

/// Value yield by a successful remote call.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Success<Res> {
    pub result : Res,
}

/// Error raised on a failed remote call.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Error {
    pub code    : i64,
    pub message : String,
    pub data    : Option<serde_json::Value>
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[serde(untagged)]
pub enum IncomingMessage {
    Response(Response<serde_json::Value>),
    Notification(Notification<serde_json::Value>),
}

/// Message from server to client.
/// 
/// `In` is any serializable (or already serialized) representation of the 
/// method arguments passed in this call. 
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct MethodCall<In> {
    pub method : &'static str,
    pub input  : In
}
