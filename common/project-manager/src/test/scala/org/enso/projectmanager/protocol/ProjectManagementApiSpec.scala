package org.enso.projectmanager.protocol

import java.io.File
import java.nio.file.Paths
import java.util.UUID

import io.circe.literal._
import io.circe.parser.parse
import org.enso.projectmanager.data.SocketData

class ProjectManagementApiSpec extends BaseServerSpec {

  "project/create" must {

    "check if project name is not empty" in {
      val client = new WsTestClient(address)
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "project/create",
            "id": 1,
            "params": {
              "name": ""
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "error": { "code": 4001, "message": "Cannot create project with empty name" }
          }
          """)
    }

    "validate project name" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "luna-test-project4/#$$%^@!"
              }
            }
          """)
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":1,
          "error":{
            "code":4001,
            "message":"Project name contains forbidden characters: %,!,@,#,$$,^,/"
            }
          }
          """)
    }

    "fail when the project with the same name exists" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "foo"
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 1,
            "result" : {
              "projectId" : $getGeneratedUUID
            }
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 2,
              "params": {
                "name": "foo"
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "error":{
              "code":4003,
              "message":"Project with the provided name exists"
            }
          }
          """)
    }

    "create project structure" in {
      val projectName = "luna_test-project1"
      val projectDir  = new File(userProjectDir, projectName)
      val packageFile = new File(projectDir, "package.yaml")
      val mainEnso    = Paths.get(projectDir.toString, "src", "Main.enso").toFile

      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": $projectName
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 1,
            "result" : {
              "projectId" : $getGeneratedUUID
            }
          }
          """)
      packageFile shouldBe Symbol("file")
      mainEnso shouldBe Symbol("file")
    }
  }

  "project/delete" must {

    "fail when project doesn't exist" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 1,
              "params": {
                "projectId": ${UUID.randomUUID()}
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":1,
            "error":{
              "code":4004,
              "message":"Project with the provided id does not exist"
            }
          }
          """)

    }

    "fail when project is running" in {
      val projectName = "to-remove"
      val client      = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": {
                "name": $projectName
              }
            }
          """)
      val projectId = getGeneratedUUID
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 0,
            "result" : {
              "projectId" : $projectId
            }
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 1,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client.expectMessage()
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 2,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "error":{
              "code":4008,
              "message":"Cannot remove open project"
            }
          }
          """)

      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 3,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":3,
            "result": null
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 3,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client.expectMessage()
    }

    "remove project structure" in {
      val projectName = "to-remove"
      val projectDir  = new File(userProjectDir, projectName)

      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": {
                "name": $projectName
              }
            }
          """)
      val projectId = getGeneratedUUID
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 0,
            "result" : {
              "projectId" : $projectId
            }
          }
          """)
      projectDir shouldBe Symbol("directory")
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 1,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":1,
            "result": null
          }
          """)

      projectDir.exists() shouldBe false
    }

  }

  "project/open" must {

    "fail when project doesn't exist" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": ${UUID.randomUUID()} 
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code":4004,
              "message":"Project with the provided id does not exist"
            }
          }
          """)
    }

    "start the Language Server if not running" in {
      val projectName = "to-remove"

      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": {
                "name": $projectName
              }
            }
          """)
      val projectId = getGeneratedUUID
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 0,
            "result" : {
              "projectId" : $projectId
            }
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 1,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      val Right(openReply) = parse(client.expectMessage())
      val socketField = openReply.hcursor
        .downField("result")
        .downField("languageServerAddress")
      val Right(host)          = socketField.downField("host").as[String]
      val Right(port)          = socketField.downField("port").as[Int]
      val languageServerClient = new WsTestClient(s"ws://$host:$port")
      languageServerClient.send(json"""
          {
            "jsonrpc": "2.0",
            "method": "file/read",
            "id": 1,
            "params": {
              "path": {
                "rootId": ${UUID.randomUUID()},
                "segments": ["src", "Main.enso"]
              }
            }
          }
            """)
      languageServerClient.expectJson(json"""
          {
            "jsonrpc":"2.0",
             "id":1,
             "error":{"code":1001,"message":"Content root not found"}}
            """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 2,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "result": null
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 3,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":3,
            "result": null
          }
          """)
    }

    "not start new Language Server if one is running" in {
      val projectName = "to-remove"

      val client1 = new WsTestClient(address)
      client1.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": {
                "name": $projectName
              }
            }
          """)
      val projectId = getGeneratedUUID
      client1.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 0,
            "result" : {
              "projectId" : $projectId
            }
          }
          """)
      client1.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 1,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      val Right(openReply) = parse(client1.expectMessage())
      val socketField = openReply.hcursor
        .downField("result")
        .downField("languageServerAddress")
      val Right(host) = socketField.downField("host").as[String]
      val Right(port) = socketField.downField("port").as[Int]
      val client2     = new WsTestClient(address)
      client2.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client2.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 0,
            "result" : {
              "languageServerAddress" : { "host": $host, "port": $port }
            }
          }
          """)

      client1.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 2,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client1.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "error" : {
              "code" : 4007,
              "message" : "Cannot close project because it is open by other peers"
             }
          }
          """)

      client2.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 2,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client2.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "result": null
          }
          """)
      client1.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 3,
              "params": {
                "projectId": $projectId 
              }
            }
          """)
      client1.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":3,
            "result": null
          }
          """)
    }

  }

  "project/close" must {

    "fail when project is not open" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 0,
              "params": {
                "projectId": ${UUID.randomUUID()} 
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code":4006,
              "message":"Cannot close project that is not open"
            }
          }
          """)

    }

    "close project when the requester is the only client" in {
      val projectName = "to-remove"

      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": {
                "name": $projectName
              }
            }
          """)
      val projectId = getGeneratedUUID
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 0,
            "result" : {
              "projectId" : $projectId
            }
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 1,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      val Right(openReply) = parse(client.expectMessage())
      val socketField = openReply.hcursor
        .downField("result")
        .downField("languageServerAddress")
      val Right(host)          = socketField.downField("host").as[String]
      val Right(port)          = socketField.downField("port").as[Int]
      val languageServerClient = new WsTestClient(s"ws://$host:$port")

      languageServerClient.send("test")
      languageServerClient.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : null,
            "error" : {
              "code" : -32700,
              "message" : "Parse error"
            }
          }
            """)

      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 2,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "result": null
          }
          """)
      languageServerClient.send("test")
      languageServerClient.expectNoMessage()
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 3,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":3,
            "result": null
          }
          """)
    }

  }

  "project/listRecent" must {

    "return empty list if none of projects was open" in {
      implicit val client = new WsTestClient(address)
      //given
      val fooId = createProject("foo")
      val barId = createProject("bar")
      val bazId = createProject("baz")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/listRecent",
              "id": 0,
              "params": {
                "numberOfProjects": 5
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": []
            }
          }
          """)
      deleteProject(fooId)
      deleteProject(barId)
      deleteProject(bazId)
    }

    "returned sorted list of recently open projects" in {
      implicit val client = new WsTestClient(address)
      //given
      val fooId = createProject("foo")
      val barId = createProject("bar")
      val bazId = createProject("baz")
      testClock.moveTimeForward()
      openProject(fooId)
      testClock.moveTimeForward()
      openProject(barId)
      val barOpenTime = testClock.currentTime
      testClock.moveTimeForward()
      openProject(bazId)
      val bazOpenTime = testClock.currentTime
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/listRecent",
              "id": 0,
              "params": {
                "numberOfProjects": 2
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": [
                {"name":"baz", "id":$bazId, "lastOpened":$bazOpenTime},
                {"name":"bar", "id":$barId, "lastOpened":$barOpenTime}
              ]
            }
          }
          """)
      //teardown
      closeProject(fooId)
      closeProject(barId)
      closeProject(bazId)
      deleteProject(fooId)
      deleteProject(barId)
      deleteProject(bazId)
    }

  }

  def createProject(name: String)(implicit client: WsTestClient): UUID = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": {
                "name": $name
              }
            }
          """)
    val projectId = getGeneratedUUID
    client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projectId": $projectId
            }
          } 
          """)
    projectId
  }

  def openProject(
    projectId: UUID
  )(implicit client: WsTestClient): SocketData = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
    val Right(openReply) = parse(client.expectMessage())
    val socketField = openReply.hcursor
      .downField("result")
      .downField("languageServerAddress")
    val Right(host) = socketField.downField("host").as[String]
    val Right(port) = socketField.downField("port").as[Int]
    SocketData(host, port)
  }

  def closeProject(
    projectId: UUID
  )(implicit client: WsTestClient): Unit = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
    client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": null
          }
          """)
  }

  def deleteProject(
    projectId: UUID
  )(implicit client: WsTestClient): Unit = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
    client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": null
          }
          """)
  }

}
