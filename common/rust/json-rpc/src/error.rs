//! Error types used by this crate. `RpcError` is used in remote call results,
//! while `HandlingError` can be raised by `Handler`.

use prelude::*;

use crate::messages::Error;
use crate::messages::Response;

use futures::channel::oneshot::Canceled;

// ================
// === RpcError ===
// ================

/// Errors that can cause a remote call to fail.
#[derive(Debug, Fail)]
pub enum RpcError {
    /// Error returned by the remote server.
    #[fail(display = "peer has replied with an error: {:?}", _0)]
    RemoteError(Error),

    /// Lost connection while waiting for response.
    #[fail(display = "lost connection before receiving reply")]
    LostConnection,

    /// Failed to deserialize message from server.
    #[fail(display = "failed to deserialize from JSON: {}", _0)]
    DeserializationFailed(serde_json::Error),
}

impl From<Canceled> for RpcError {
    fn from(_:Canceled) -> Self {
        RpcError::LostConnection
    }
}

impl From<serde_json::Error> for RpcError {
    fn from(e:serde_json::Error) -> Self {
        RpcError::DeserializationFailed(e)
    }
}


// =====================
// === HandlingError ===
// =====================

/// Errors specific to the Handler itself, not any specific request.
///
/// Caused either internal errors in the handler or bugs in the server.
#[derive(Debug, Fail)]
pub enum HandlingError {
    /// When incoming text message can't be decoded.
    #[fail(display = "failed to decode incoming text message: {}", _0)]
    InvalidMessage(#[cause] serde_json::Error),

    /// Server responded to an identifier that does not match to any known
    /// ongoing request.
    #[fail(display = "server generated response with no matching request: \
    id={:?}", _0)]
    UnexpectedResponse(Response<serde_json::Value>),
}
