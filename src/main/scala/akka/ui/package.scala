package akka

import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._

package object ui {
  implicit class SourceBuilder[T <: EventTarget, E <: Event](t: T) {
    def on[L <: Listener](listener: L)(
        implicit binder: Binder[T, L, E],
        materializer: Materializer
    ): Source[E, akka.NotUsed] = {
      val (queue, source) = Source
        .queue[E](10, OverflowStrategy.dropNew)
        .preMaterialize

      t.addEventListener[E](listener.toString, e => queue.offer(e))
      //TODO: close the Source when T is removed

      source
    }
  }

}
