//! Module contains entities used by client implementor to describe remote API.

use prelude::*;

use serde::Serialize;
use serde::de::DeserializeOwned;

use crate::messages;
use crate::Result;

/// Structure describing a call values to a remote method.
///
/// A serialized value of this trait represents the method's input arguments.
pub trait RemoteMethodInput : Serialize + Debug {
    /// Name of the remote method.
    const NAME:&'static str;

    /// A type of value returned from successful remote call.
    type Returned:DeserializeOwned;


    fn describe_call
    (&self) -> Result<messages::MethodCall<serde_json::Value>> {
        Ok(messages::MethodCall {
            method:Self::NAME,
            input :serde_json::to_value(&self)?,
        })
    }

    fn to_request_message
    (&self, id:messages::Id) -> Result<messages::Message<messages::Request<messages::MethodCall<serde_json::value::Value>>>> {
        let request = messages::Request {
            id,
            method : self.describe_call()?,
        };
        Ok(messages::Message::new(request))
    }
}