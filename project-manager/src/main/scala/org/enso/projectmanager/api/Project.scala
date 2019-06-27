package org.enso.projectmanager.api

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri
import org.enso.projectmanager.RouteHelper
import org.enso.projectmanager.model
import spray.json.DefaultJsonProtocol

case class Project(
  id: String,
  name: String,
  path: String,
  thumb: Option[String],
  lastOpenTime: Option[Long],
  persisted: Boolean)

case class ProjectFactory(routeHelper: RouteHelper) {

  def fromModel(id: UUID, project: model.Project, baseUri: Uri): Project = {
    val thumbUri =
      if (project.hasThumb)
        Some(routeHelper.uriFor(baseUri, routeHelper.thumbPath(id)))
      else None
    Project(
      id.toString,
      project.pkg.name,
      project.pkg.root.getAbsolutePath,
      thumbUri.map(_.toString()),
      None,
      project.isPersistent
    )
  }
}

trait ProjectJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val projectFormat = jsonFormat6(Project.apply)
}
