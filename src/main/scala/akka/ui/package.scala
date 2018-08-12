package akka

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import scala.scalajs.js
import scala.reflect.ClassTag
import scala.collection.mutable

package object ui {
  implicit class RichDOMTokenList(tokens: DOMTokenList)
      extends EasySeq[String](tokens.length, tokens.apply)

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

  implicit class SinkBuilder[T <: Element](t: T) {
    def sink[V: ClassTag](selector: T => V => Unit)(
        implicit system: ActorSystem
    ): Sink[V, NotUsed] = {
      val setter = selector(t)
      val sinkActor = system.actorOf(SinkActor.props(setter))

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty[ActorRef])
        .+=(sinkActor)

      Sink.actorRef[V](sinkActor, SinkActor.Completed)
    }

    def childrenSink(
        implicit system: ActorSystem
    ): Sink[Seq[Element], NotUsed] = {
      val props = SinkActor.props[Seq[Element]] { children =>
        children.foreach(child => t.appendChild(child))
        t.childNodes
          .dropRight(children.size)
          .foreach { child =>
            // remove the bindings
            sourceBindings
              .get(child)
              .foreach(actors => actors.foreach(_ ! PoisonPill))
            sourceBindings -= child
            sinkBindings
              .get(child)
              .foreach(actors => actors.foreach(_ ! PoisonPill))
            sinkBindings -= child
            // remove the node
            t.removeChild(child)
          }
      }
      val sinkActor = system.actorOf(props)

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty[ActorRef])
        .+=(sinkActor)

      Sink.actorRef[Seq[Element]](sinkActor, SinkActor.Completed)
    }

    def classSink(
        implicit system: ActorSystem
    ): Sink[Seq[String], NotUsed] = {
      val props = SinkActor.props[Seq[String]] { classes =>
        classes.foreach(t.classList.add)
        t.classList.filterNot(classes contains _)
          .foreach(t.classList remove _)
      }
      val sinkActor = system.actorOf(props)

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty[ActorRef])
        .+=(sinkActor)

      Sink.actorRef[Seq[String]](sinkActor, SinkActor.Completed)
    }
  }
}
