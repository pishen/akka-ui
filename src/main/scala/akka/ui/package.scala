package akka

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import scala.scalajs.js
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.experimental.macros

package object ui {
  implicit class RichDOMTokenList(tokens: DOMTokenList)
      extends EasySeq[String](tokens.length, tokens.apply)

  val sourceBindings0 = mutable.Map
    .empty[EventTarget, mutable.Set[SourceQueueWithComplete[_ <: Event]]]

  val preventDefault = SourceBuilder.Config("preventDefault")
  val stopPropagation = SourceBuilder.Config("stopPropagation")

  implicit class SourceChainer[T <: EventTarget](t: T) {
    def source(
        eventType: String,
        configs: SourceBuilder.Config*
    ): Any = macro SourceBuilder.source[T]
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
                (child +: child.querySelectorAll("*"))
                  .foreach { node =>
                    SourceBuilder.sourceBindings
                      .get(node)
                      .foreach { eventMap =>
                        eventMap.values.foreach { res =>
                          println("remove source binding")
                          res._1.complete()
                        }
                        SourceBuilder.sourceBindings -= node
                      }
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
