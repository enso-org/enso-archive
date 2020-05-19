package org.enso.languageserver.websocket.json
import io.circe.literal._

class IoRedirectionTest extends BaseServerTest {

  "Standard output" must {

    "send append notification when std out is redirected" in {
      val client = getInitialisedWsClient()
      client.send(json"""
            {
              "jsonrpc": "2.0",
              "method": "io/redirectStandardOutput",
              "id": 1,
              "params": null
            }
          """)
      client.expectJson(json"""
             {"jsonrpc":"2.0","id":1,"result":null}
          """)
      stdOut.write("test1".getBytes)
      client.expectJson(json"""
             {
               "jsonrpc":"2.0",
               "method":"io/standardOutputAppended",
               "params":{"output":"test1"}
             }
          """)
      stdOut.write("test2".getBytes)
      client.expectJson(json"""
               {"jsonrpc":"2.0",
               "method":"io/standardOutputAppended",
               "params":{"output":"test2"}}
          """)
      client.send(json"""
            {
              "jsonrpc": "2.0",
              "method": "io/suppressStandardOutput",
              "id": 2,
              "params": null
            }
          """)
      client.expectJson(json"""
             {"jsonrpc":"2.0","id":2,"result":null}
          """)
      stdOut.write("test3".getBytes)
      client.expectNoMessage()
    }

  }

}
