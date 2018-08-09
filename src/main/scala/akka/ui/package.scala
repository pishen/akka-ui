package akka

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._
import scala.scalajs.js
import scala.reflect.ClassTag
import scala.collection.mutable

package object ui {
  val sourceBindings = mutable.Map.empty[EventTarget, mutable.Set[ActorRef]]

  implicit class SourceBuilder[T <: EventTarget](t: T) {
    def source[E <: Event](selector: T => js.Function1[E, _] => Unit)(
        implicit materializer: Materializer
    ): Source[E, akka.NotUsed] = {
      val (eventReader, source) = Source
        .actorRef[E](10, OverflowStrategy.dropNew)
        .preMaterialize

      selector(t)(e => eventReader ! e)

      sourceBindings
        .getOrElseUpdate(t, mutable.Set.empty[ActorRef])
        .+=(eventReader)

      source
    }
  }

  val sinkBindings = mutable.Map.empty[Element, mutable.Set[ActorRef]]

  implicit class SinkBuilder[E <: Element](e: E) {
    def sink[V: ClassTag](selector: E => V => Unit)(
        implicit system: ActorSystem
    ): Sink[V, akka.NotUsed] = {
      val setter = selector(e)
      val propertyWriter = system.actorOf(PropertyWriter.props(setter))

      sinkBindings
        .getOrElseUpdate(e, mutable.Set.empty[ActorRef])
        .+=(propertyWriter)

      Sink.actorRef[V](propertyWriter, PropertyWriter.Completed)
    }
  }
}
