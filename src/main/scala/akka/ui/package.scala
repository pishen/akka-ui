package akka

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import scala.scalajs.js
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

package object ui {
  implicit class RichDOMTokenList(tokens: DOMTokenList)
      extends EasySeq[String](tokens.length, tokens.apply)

  val sourceBindings = mutable.Map
    .empty[EventTarget, mutable.Set[SourceQueueWithComplete[_ <: Event]]]

  implicit class SourceBuilder[T <: EventTarget](t: T) {
    def source[E <: Event](
        selector: T => js.Function1[E, _] => Unit,
        preventDefault: Boolean = false
    )(
        implicit materializer: Materializer
    ): Source[E, NotUsed] = {
      val (eventQueue, source) = Source
        .queue[E](10, OverflowStrategy.dropNew)
        .toMat(BroadcastHub.sink)(Keep.both)
        .run()

      selector(t) { event =>
        if (preventDefault) {
          event.preventDefault()
        }
        eventQueue.offer(event)
      }

      t match {
        case e: Element => e.classList.add("akka-ui-binded")
        case _ => //do nothing
      }

      sourceBindings
        .getOrElseUpdate(t, mutable.Set.empty)
        .+=(eventQueue)

      source
    }
  }

  val sinkBindings =
    mutable.Map.empty[Node, mutable.Set[SinkQueueWithCancel[_]]]

  implicit class SinkBuilder[T <: Element](t: T) {
    def sink[V](selector: T => V => Unit)(
        implicit materializer: Materializer
    ): Sink[V, NotUsed] = {
      val setter = selector(t)
      val (sink, queue) = MergeHub
        .source[V]
        .toMat(Sink.queue[V])(Keep.both)
        .run()

      def pull(): Unit = {
        queue.pull().foreach {
          case Some(v) =>
            setter(v)
            pull()
          case None =>
            println("You should not come here.")
        }
      }
      pull()

      t.classList.add("akka-ui-binded")

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty)
        .+=(queue)

      sink
    }

    def childrenSink(
        implicit materializer: Materializer
    ): Sink[Seq[Element], NotUsed] = {
      val (sink, queue) = MergeHub
        .source[Seq[Element]]
        .toMat(Sink.queue[Seq[Element]])(Keep.both)
        .run()

      def pull(): Unit = {
        queue.pull().foreach {
          case Some(children) =>
            children.foreach(child => t.appendChild(child))
            t.children
              .dropRight(children.size)
              .foreach { child =>
                // remove the bindings
                (child +: child.querySelectorAll(".akka-ui-binded"))
                  .foreach { node =>
                    sourceBindings
                      .get(node)
                      .foreach { queues =>
                        queues.foreach(_.complete())
                      }
                    sourceBindings -= node
                    sinkBindings
                      .get(node)
                      .foreach { queues =>
                        queues.foreach(_.cancel())
                      }
                    sinkBindings -= node
                  }
                // remove the child
                t.removeChild(child)
              }
            pull()
          case None =>
            println("You should not come here.")
        }
      }
      pull()

      t.classList.add("akka-ui-binded")

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty)
        .+=(queue)

      sink
    }

    def classSink(
        implicit materializer: Materializer
    ): Sink[Seq[String], NotUsed] = {
      val (sink, queue) = MergeHub
        .source[Seq[String]]
        .toMat(Sink.queue[Seq[String]])(Keep.both)
        .run()

      def pull(): Unit = {
        queue.pull().foreach {
          case Some(classes) =>
            classes.foreach(t.classList.add)
            t.classList
              .filterNot(classes contains _)
              .foreach(t.classList remove _)
            pull()
          case None =>
            println("You should not come here.")
        }
      }
      pull()

      t.classList.add("akka-ui-binded")

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty)
        .+=(queue)

      sink
    }

    def dummySink[V](
        implicit materializer: Materializer
    ): Sink[V, NotUsed] = {
      val (sink, queue) = MergeHub
        .source[V]
        .toMat(Sink.queue[V])(Keep.both)
        .run()

      def pull(): Unit = {
        queue.pull().foreach {
          case Some(v) =>
            //do nothing
            pull()
          case None =>
            println("You should not come here.")
        }
      }
      pull()

      t.classList.add("akka-ui-binded")

      sinkBindings
        .getOrElseUpdate(t, mutable.Set.empty)
        .+=(queue)

      sink
    }
  }
}
