package akka

import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._
import scala.scalajs.js

package object ui {
  def fromListener[E <: Event](setter: js.Function1[E, _] => Unit)(
    implicit materializer: Materializer
  ): Source[E, akka.NotUsed] = {
    val (queue, source) = Source
      .queue[E](10, OverflowStrategy.dropNew)
      .preMaterialize

    setter(e => queue.offer(e))
    //TODO: close the Source when T is removed
    source
  }
}
