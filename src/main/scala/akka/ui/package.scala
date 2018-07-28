package akka

import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._
import scala.language.higherKinds

package object ui {
  implicit class SourceBuilder[T <: EventTarget, E <: Event](t: T) {
    def on[A](
        implicit listener: Listener[A, T, E],
        materializer: Materializer
    ): Source[E, akka.NotUsed] = {
      val (queue, source) = Source
        .queue[E](10, OverflowStrategy.dropNew)
        .preMaterialize

      t.addEventListener[E](listener.name, e => queue.offer(e))
      //TODO: close the Source when T is removed

      source
    }
  }


}
