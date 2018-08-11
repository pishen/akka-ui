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
    ): Source[E, NotUsed] = {
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

  val sinkBindings = mutable.Map.empty[Node, mutable.Set[ActorRef]]

  implicit class SinkBuilder[N <: Node](n: N) {
    def sink[V: ClassTag](selector: N => V => Unit)(
        implicit system: ActorSystem
    ): Sink[V, akka.NotUsed] = {
      val setter = selector(n)
      val propertyWriter = system.actorOf(PropertyWriter.props(setter))

      sinkBindings
        .getOrElseUpdate(n, mutable.Set.empty[ActorRef])
        .+=(propertyWriter)

      Sink.actorRef[V](propertyWriter, PropertyWriter.Completed)
    }

    def childrenSink(
        implicit system: ActorSystem
    ): Sink[Seq[Node], NotUsed] = {
      val childrenWriter = system.actorOf(ChildrenWriter.props(n))

      sinkBindings
        .getOrElseUpdate(n, mutable.Set.empty[ActorRef])
        .+=(childrenWriter)

      import ChildrenWriter._
      Sink
        .actorRef[ReplaceAll](childrenWriter, Completed)
        .contramap[Seq[Node]](seq => ReplaceAll(seq))
    }
  }
}
