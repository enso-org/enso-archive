package org.enso.languageserver.websocket

import io.circe.literal._
import org.enso.polyglot.runtime.Runtime

class ContextRegistryTest extends BaseServerTest {

  "ContextRegistry" must {

    "create execution context" in {
      val client = new WsTestClient(address)

      client.send(json.executionContextCreateRequest(1))
      val createContextRequest = runtimeConnectorProbe
        .receiveN(1)
        .head
        .asInstanceOf[Runtime.Api.CreateContextRequest]
      runtimeConnectorProbe.lastSender ! Runtime.Api.CreateContextResponse(
        createContextRequest.requestId,
        createContextRequest.contextId
      )
      client.expectJson(
        json.executionContextCreateResponse(1, createContextRequest.contextId)
      )
    }

  }

  object json {

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

    def executionContextCreateResponse(
      reqId: Int,
      contextId: Runtime.Api.ContextId
    ) =
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
  }
}
