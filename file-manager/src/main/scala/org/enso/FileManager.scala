package org.enso

object FileManager {
  import org.enso.fileManager
  import org.enso.fileManager.API._

  val API = fileManager.API

  def fileManager(projectRoot: Path): Behavior[API.InputMessage] =
    Behaviors.setup(context => FileManagerBehavior(projectRoot, context))

  def ask[response <: Success: ClassTag](
    actor: ActorRef[API.InputMessage],
    request.Payload: Request.Payload[response]
  )(implicit timeout: Timeout,
    scheduler: Scheduler
  ): Future[Try[response]] = {
    // actor.ask((replyTo: ActorRef[Try[response]]) => {
    //     Request(replyTo, request.Payload)
    // })

    // FIXME: Such code as ^^^^ move to vvvv 

    actor.ask { (replyTo: ActorRef[Try[response]]) => 
      Request(replyTo, request.Payload)
    }

  }
}