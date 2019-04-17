package akka.ui

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.raw._
import scala.collection.mutable
import scala.reflect.macros.whitebox.Context
import scala.scalajs.js

object SourceBuilder {
  case class Config(flag: String)

  val sourceBindings =
    mutable.Map.empty[EventTarget, mutable.Map[
      String,
      (SourceQueueWithComplete[_ <: Event], Source[_ <: Event, NotUsed])
    ]]

  def build[E <: Event](
      elem: EventTarget,
      eventName: String,
      setter: js.Function1[E, Unit] => Unit,
      configs: Config*
  )(
      implicit materializer: Materializer
  ): Source[E, NotUsed] = {
    def createHub() = {
      val (queue, source) = Source
        .queue[E](10, OverflowStrategy.dropNew)
        .toMat(BroadcastHub.sink)(Keep.both)
        .run()
      val hasPreventDefault = configs.contains(preventDefault)
      val hasStopPropagation = configs.contains(stopPropagation)
      setter { e =>
        if (hasPreventDefault) {
          e.preventDefault()
        }
        if (hasStopPropagation) {
          e.stopPropagation()
        }
        queue.offer(e)
      }
      (queue, source)
    }
    val (_, source) = sourceBindings
      .getOrElseUpdate(elem, mutable.Map.empty)
      .getOrElseUpdate(eventName, createHub())
    source.asInstanceOf[Source[E, NotUsed]]
  }

  def source[T: c.WeakTypeTag](c: Context)(
      eventType: c.Expr[String],
      configs: c.Expr[Config]*
  ): c.Expr[Any] = {
    import c.universe._

    val q"$_[$_]($elem).$_(..$_)" = c.macroApplication

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
      akka.ui.SourceBuilder.build[$E](
        $elem,
        $eventName,
        f => $elem.$listenerTerm = f,
        ..$configs
      )
    """

    //println(showCode(res))
    c.Expr(res)
  }
}
