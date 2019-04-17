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
import scala.reflect.macros.whitebox.Context

package object ui {
  implicit class RichDOMTokenList(tokens: DOMTokenList)
      extends EasySeq[String](tokens.length, tokens.apply)

  val sourceBindings0 = mutable.Map.empty[EventTarget, mutable.Set[SourceQueueWithComplete[_ <: Event]]]
  val sourceBindings =
    mutable.Map.empty[EventTarget, mutable.Map[
      String,
      (SourceQueueWithComplete[_ <: Event], Source[_ <: Event, NotUsed])
    ]]

  implicit class SourceBuilder[T <: EventTarget](t: T) {
    def source(eventType: String): Any = macro SourceBuilderImpl.source[T]

    def source0[E <: Event](
        selector: T => js.Function1[E, _] => Unit,
        preventDefault: Boolean = false
    )(
        implicit materializer: Materializer
    ): Source[E, NotUsed] = {
      val (eventQueue, source) = Source
        .queue[E](10, OverflowStrategy
          .dropNew)
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

      sourceBindings0
        .getOrElseUpdate(t, mutable.Set.empty)
        .+=(eventQueue)

      source
    }
  }

  object SourceBuilderImpl {
    def source[T: c.WeakTypeTag](c: Context)(
        eventType: c.Expr[String]
    ): c.Expr[Any] = {
      import c.universe._

      val q"$_[$_]($elem).$_($_)" = c.macroApplication

      val eventName = eventType.tree match {
        case Literal(Constant(s: String)) => s
        case _ =>
          c.abort(c.enclosingPosition, "eventType must be a string literal.")
      }

      val listenerName = "on" + eventName
      val listenerTerm = TermName(listenerName)

      val E = {
        val listener = implicitly[WeakTypeTag[T]].tpe.member(listenerTerm)
        if (listener.fullName == "<none>") {
          c.abort(
            c.enclosingPosition,
            s"Couldn't find $listenerName listener on target element."
          )
        } else {
          listener.typeSignature.resultType.typeArgs.head
        }
      }

      val res = q"""
        import akka.stream._
        import akka.stream.scaladsl._
        import scala.collection.mutable
        def createHub() = {
          val (queue, source) = Source
            .queue[$E](10, OverflowStrategy.dropNew)
            .toMat(BroadcastHub.sink)(Keep.both)
            .run()
          $elem.$listenerTerm = e => queue.offer(e)
          (queue, source)
        }
        val (queue, source) = akka.ui.sourceBindings
          .getOrElseUpdate($elem, mutable.Map.empty)
          .getOrElseUpdate($eventName, createHub())
        source.asInstanceOf[Source[$E, NotUsed]]
      """

      println(showCode(res))
      c.Expr(res)
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
                    sourceBindings0
                      .get(node)
                      .foreach { queues =>
                        queues.foreach(_.complete())
                      }
                    sourceBindings0 -= node
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
