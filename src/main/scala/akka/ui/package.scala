package akka

import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._
import scala.language.higherKinds

package object ui {
  implicit class SourceBuilder[T <: EventTarget](t: T) {
    def on[L[_] <: Listener](
        implicit listener: L[T],
        materializer: Materializer
    ): Source[listener.E, akka.NotUsed] = {
      val (queue, source) = Source
        .queue[listener.E](10, OverflowStrategy.dropNew)
        .preMaterialize

      t.addEventListener[listener.E](listener.name, e => queue.offer(e))
      //TODO: close the Source when T is removed

      source
    }
  }


}
