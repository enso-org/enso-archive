package org.enso.languageserver.websocket.rpc

import org.enso.polyglot.runtime.Runtime.Api
import io.circe.literal._

object ExecutionContextMessages {

  def localCall(expressionId: Api.ExpressionId) =
    json"""
          { "type": "LocalCall",
            "expressionId": $expressionId
          }
          """

  def ok(reqId: Int) =
    json"""
          { "jsonrpc": "2.0",
            "id": $reqId,
            "result": null
          }
          """

  def executionContextCreateRequest(reqId: Int) =
    json"""
            { "jsonrpc": "2.0",
              "method": "executionContext/create",
              "id": $reqId,
              "params": null
            }
            """

  def executionContextCreateResponse(reqId: Int, contextId: Api.ContextId) =
    json"""
          { "jsonrpc": "2.0",
            "id": $reqId,
            "result" : {
              "canModify" : {
                "method" : "canModify",
                "registerOptions" : {
                  "contextId" : $contextId
                }
              },
              "receivesEvents" : {
                "method" : "receivesEvents",
                "registerOptions" : {
                  "contextId" : $contextId
                }
              }
            }
          }
          """

  def executionContextDestroyRequest(reqId: Int, contextId: Api.ContextId) =
    json"""
            { "jsonrpc": "2.0",
              "method": "executionContext/destroy",
              "id": $reqId,
              "params": {
                "contextId": $contextId
              }
            }
            """

  def executionContextPushRequest(
    reqId: Int,
    contextId: Api.ContextId,
    expressionId: Api.ExpressionId
  ) =
    json"""
          { "jsonrpc": "2.0",
            "method": "executionContext/push",
            "id": $reqId,
            "params": {
              "contextId": $contextId,
              "stackItem": ${localCall(expressionId)}
            }
          }
          """

  def executionContextPopRequest(
    reqId: Int,
    contextId: Api.ContextId
  ) =
    json"""
          { "jsonrpc": "2.0",
            "method": "executionContext/pop",
            "id": $reqId,
            "params": {
              "contextId": $contextId
            }
          }
          """
}
