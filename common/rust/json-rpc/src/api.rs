//! Module contains entities used by client implementor to describe remote API.

use prelude::*;

use crate::messages::Id;
use crate::messages::make_request_message;
use crate::messages::RequestMessage;

use serde::Serialize;
use serde::de::DeserializeOwned;

/// Structure describing a call values to a remote method.
///
/// A serialized value of this trait represents the method's input arguments.
pub trait RemoteMethodCall: Serialize + Debug {
    /// Name of the remote method.
    const NAME:&'static str;

    /// A type of value returned from successful remote call.
    type Returned:DeserializeOwned;
}

/// Make a request message from given RemoteMethodInput value.
pub fn into_request_message<In: RemoteMethodCall>
(input:In, id:Id) -> RequestMessage<In> {
    make_request_message(id,In::NAME,input)
}
