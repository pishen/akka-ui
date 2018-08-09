package akka

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.reflect.ClassTag

package object ui {
  implicit class SourceBuilder[T <: EventTarget](t: T) {
    def source[E <: Event](selector: T => js.Function1[E, _] => Unit)(
      implicit materializer: Materializer
    ): Source[E, akka.NotUsed] = {
      val (queue, source) = Source
        .queue[E](10, OverflowStrategy.dropNew)
        .preMaterialize

      selector(t)(e => queue.offer(e))

      // Add a mark to indicate that there's a binding on this element
      t match {
        case (e: Element) =>
          e.classList.add("akka-ui-binded")
        case _ =>
          // do nothing
      }
      // Complete the Source when T is removed (use custom Event)
      t.addEventListener("akka-ui-removed", (e: Event) => queue.complete())

      source
    }
  }

  implicit class SinkBuilder[E <: Element](e: E) {
    def sink[V: ClassTag](selector: E => V => Unit)(
      implicit system: ActorSystem,
      materializer: Materializer
    ): Sink[V, akka.NotUsed] = {
      val setter = selector(e)
      val elementWriter = system.actorOf(ElementWriter.props(setter))

      //TODO: kill elementWriter when element removed

      Sink.actorRef[V](elementWriter, ElementWriter.Completed)
    }
  }
}
