package org.enso.gateway

trait TestJsons {
  def request: String

  def expectedResponseWithSpaces: String

  def expectedResponse: String =
    expectedResponseWithSpaces
      .filterNot(_.isWhitespace)
      .replace("_", " ")
}

object TestJsons {

  object Initialize extends TestJsons {
    val request =
      """|{
         |  "jsonrpc": "2.0",
         |  "method": "initialize",
         |  "id": 10,
         |  "params": {
         |    "capabilities": {}
         |  }
         |}""".stripMargin

    val expectedResponseWithSpaces =
      """|{
         |  "jsonrpc": "2.0",
         |  "id": 10,
         |  "result": {
         |    "capabilities": {},
         |    "serverInfo": {
         |      "name": "Enso_Language_Server",
         |      "version": "1.0"
         |    }
         |  }
         |}""".stripMargin
  }

  object WrongJsonrpc extends TestJsons {
    val request =
      """|{
         |  "jsonrpc": "3.0",
         |  "method": "initialize",
         |  "id": 10
         |}""".stripMargin

    val expectedResponseWithSpaces =
      """|{
         |  "jsonrpc": "2.0",
         |  "id": 10,
         |  "error": {
         |    "code": 1,
         |    "message": "Wrong_JSON-RPC_Version",
         |    "data": {
         |      "retry": false
         |    }
         |  }
         |}""".stripMargin
  }

  object WrongMethod extends TestJsons {
    val request =
      """|{
         |  "jsonrpc": "2.0",
         |  "id": 10,
         |  "method": "doesntExist"
         |}""".stripMargin

    val expectedResponseWithSpaces =
      """|{
         |  "jsonrpc": "2.0",
         |  "id": 10,
         |  "error": {
         |    "code": -32601,
         |    "message": "Method_not_found"
         |  }
         |}""".stripMargin
  }

}
