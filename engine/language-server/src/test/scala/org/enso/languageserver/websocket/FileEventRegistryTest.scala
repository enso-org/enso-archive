package org.enso.languageserver.websocket

import java.nio.file.{Files, Paths}

import io.circe.literal._

class FileEventRegistryTest extends BaseServerTest {

  "FileEventRegistry" must {

    "acquire capability receivesTreeUpdates" in {
      val client = new WsTestClient(address)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/acquire",
            "id": 1,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": null
          }
          """)
    }

    "fail to acquire capability if directory doesn't exist" in {
      val client = new WsTestClient(address)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/acquire",
            "id": 1,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ "inexistent" ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "error": {
              "code": 1003,
              "message": "File not found"
            }
          }
          """)
    }

    "reacquire capability receivesTreeUpdates" in {
      val client = new WsTestClient(address)

      // acquire
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/acquire",
            "id": 1,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": null
          }
          """)

      // reacquire
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/acquire",
            "id": 2,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 2,
            "result": null
          }
          """)
    }

    "fail to release capability it does not hold" in {
      val client = new WsTestClient(address)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/release",
            "id": 1,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "error": {
              "code" : 2001,
              "message" : "Capability not acquired"
            }
          }
          """)
    }

    "release capability receivesTreeUpdates" in {
      val client = new WsTestClient(address)

      // acquire capability
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/acquire",
            "id": 1,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": null
          }
          """)

      // release capability
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/release",
            "id": 2,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 2,
            "result": null
          }
          """)
    }

    "receive file system updates" in {
      val client = new WsTestClient(address)

      // acquire capability
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "capability/acquire",
            "id": 1,
            "params": {
              "method": "receivesTreeUpdates",
              "registerOptions": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ ]
                }
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": null
          }
          """)

      // create file
      val path = Paths.get(testContentRoot.toString, "oneone.txt")
      Files.createFile(path)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "method": "file/event",
            "params": {
               "event": {
                 "path": {
                    "rootId": $testContentRootId,
                    "segments": [ "oneone.txt" ]
                 },
                 "kind": {
                   "type": "Added"
                 }
               }
             }
          }
          """)

      // update file
      Files.write(path, "Hello".getBytes())
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "method": "file/event",
            "params": {
               "event": {
                 "path": {
                    "rootId": $testContentRootId,
                    "segments": [ "oneone.txt" ]
                 },
                 "kind": {
                   "type": "Modified"
                 }
               }
             }
          }
          """)

      // remove file
      Files.delete(path)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "method": "file/event",
            "params": {
               "event": {
                 "path": {
                    "rootId": $testContentRootId,
                    "segments": [ "oneone.txt" ]
                 },
                 "kind": {
                   "type": "Removed"
                 }
               }
             }
          }
          """)
    }

  }

}
