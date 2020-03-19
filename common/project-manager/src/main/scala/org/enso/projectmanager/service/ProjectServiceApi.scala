package org.enso.projectmanager.service

import zio.{ZEnv, ZIO}

trait ProjectServiceApi {

  def createProject(name: String): ZIO[ZEnv, CreateProjectFailure, Unit]

}
