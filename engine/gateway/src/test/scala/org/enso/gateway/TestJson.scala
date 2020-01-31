package org.enso.gateway

import io.circe.Json
import io.circe.literal._

sealed trait TestJson {
  def request: Json
}

sealed trait RequestJson extends TestJson {
  def expectedResponse: Json
}

object TestJson {

  object Initialize extends RequestJson {
    val request =
      json"""
         {
           "jsonrpc": "2.0",
           "method": "initialize",
           "id": 10,
           "params": {
             "capabilities": {}
           }
         }"""

    val expectedResponse =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "result": {
             "capabilities": {
               "textDocumentSync" : {
                 "openClose" : true,
                 "change" : 1,
                 "willSaveWaitUntil" : true,
                 "didSave" : true
               }
             },
             "serverInfo": {
               "name": "Enso Language Server",
               "version": "1.0"
             }
           }
         }"""
  }

  object Initialized extends TestJson {
    val request =
      json"""
         {
           "jsonrpc": "2.0", 
           "method": "initialized", 
           "params" : {}
         }"""
  }

  object WrongJsonrpc extends RequestJson {
    val request =
      json"""
         {
           "jsonrpc": "3.0",
           "method": "initialize",
           "id": 10
         }"""

    val expectedResponse =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "error": {
             "code": 1,
             "message": "Wrong JSON-RPC version",
             "data": {
               "retry": false
             }
           }
         }"""
  }

  object WrongMethod extends RequestJson {
    val request =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "method": "doesntExist"
         }"""

    val expectedResponse =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "error": {
             "code": -32601,
             "message": "Method not found"
           }
         }"""
  }

  object Shutdown extends RequestJson {
    val request =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "method": "shutdown"         
         }"""

    val expectedResponse =
      json"""
         {
           "jsonrpc" : "2.0",
           "id" : 10         
         }"""
  }

  object Exit extends TestJson {
    val request =
      json"""
         {
           "jsonrpc" : "2.0",
           "method": "exit"        
         }"""
  }

  object ApplyWorkspaceEdit extends RequestJson {
    val request =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "method": "workspace/applyEdit"         
         }"""

    val expectedResponse =
      json"""
         {
           "jsonrpc" : "2.0",
           "id" : 10,
           "result" : {
             "applied" : false
           }
         }"""
  }

  object WillSaveTextDocumentWaitUntil extends RequestJson {
    val request =
      json"""
         {
           "jsonrpc": "2.0",
           "id": 10,
           "method": "textDocument/willSaveWaitUntil"         
         }"""

    val expectedResponse =
      json"""
         {
           "jsonrpc" : "2.0",
           "id" : 10
         }"""
  }

}
