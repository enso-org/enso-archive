package org.enso.projectmanager.control.effect

import zio.ZIO

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ZioAsync[R] extends Async[ZIO[R, +*, +*]] {

  implicit val immediateEc = ExecutionContext.fromExecutor(ImmediateExecutor)

  override def async[E, A](
    register: (Either[E, A] => Unit) => Unit
  ): ZIO[R, E, A] =
    ZIO.effectAsync[R, E, A] { cb =>
      register { result =>
        cb(ZIO.fromEither(result))
      }

    }

  override def fromFuture[A](thunk: () => Future[A]): ZIO[R, Throwable, A] =
    ZIO.effectAsync[R, Throwable, A] { cb =>
      thunk().onComplete {
        case Success(value)     => cb(ZIO.succeed(value))
        case Failure(exception) => cb(ZIO.fail(exception))
      }
    }
}
