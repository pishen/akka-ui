package akka.ui

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.macros.whitebox.Context

object SinkBuilder {
  val sinkBindings =
    mutable.Map.empty[Element, mutable.Map[
      String,
      (Sink[_, NotUsed], SinkQueueWithCancel[_])
    ]]

  def build[V](
      elem: Element,
      propertyName: String,
      setter: V => Unit
  )(
      implicit materializer: Materializer
  ): Sink[V, NotUsed] = {
    def createHub() = {
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

      (sink, queue)
    }
    val (sink, _) = sinkBindings
      .getOrElseUpdate(elem, mutable.Map.empty)
      .getOrElseUpdate(propertyName, createHub())
    sink.asInstanceOf[Sink[V, NotUsed]]
  }

  def sink[T: c.WeakTypeTag](c: Context)(
      property: c.Expr[String]
  ): c.Expr[Any] = {
    import c.universe._

    val q"$_[$_]($elem).$_($_)" = c.macroApplication

    val propertyName = property.tree match {
      case Literal(Constant(s: String)) => s
      case _ =>
        c.abort(c.enclosingPosition, "property must be a string literal.")
    }

    val propertyTerm = TermName(propertyName)

    val V = {
      val member = weakTypeOf[T].member(propertyTerm)
      if (member.fullName == "<none>") {
        c.abort(
          c.enclosingPosition,
          s"Couldn't find $propertyName property on ${weakTypeOf[T]}."
        )
      } else {
        member.typeSignature.resultType
      }
    }

    val res = q"""
      akka.ui.SinkBuilder.build[$V](
        $elem,
        $propertyName,
        v => $elem.$propertyTerm = v
      )
    """

    //println(showCode(res))
    c.Expr(res)
  }

  def styleSink[T: c.WeakTypeTag](c: Context)(
      style: c.Expr[String]
  ): c.Expr[Any] = {
    import c.universe._

    val q"$_[$_]($elem).$_($_)" = c.macroApplication

    val styleName = style.tree match {
      case Literal(Constant(s: String)) => s
      case _ =>
        c.abort(c.enclosingPosition, "style must be a string literal.")
    }

    val styleTerm = TermName(styleName)

    val V = {
      val member = weakTypeOf[T]
        .member(TermName("style"))
        .typeSignature
        .resultType
        .member(styleTerm)
      if (member.fullName == "<none>") {
        c.abort(
          c.enclosingPosition,
          s"Couldn't find $styleName style on ${weakTypeOf[T]}."
        )
      } else {
        member.typeSignature.resultType
      }
    }

    val res = q"""
      akka.ui.SinkBuilder.build[$V](
        $elem,
        "style." + $styleName,
        v => $elem.style.$styleTerm = v
      )
    """

    //println(showCode(res))
    c.Expr(res)
  }

  def childrenSink(parent: Element)(
      implicit materializer: Materializer
  ): Sink[Seq[Element], NotUsed] = {
    val setter = (children: Seq[Element]) => {
      //appendChild can relocate the elements which already exist
      children.foreach(child => parent.appendChild(child))
      //remove the remaining children
      def traverse(elem: Element): Seq[Element] = {
        elem.children.flatMap(traverse)
      }
      parent.children
        .dropRight(children.size)
        .foreach { child =>
          // remove the bindings
          (child +: child.querySelectorAll("*"))
            .collect { case (e: Element) => e }
            .foreach { elem =>
              SourceBuilder.sourceBindings
                .get(elem)
                .foreach { eventMap =>
                  eventMap.foreach {
                    case (event, res) =>
                      //println(s"remove source binding for $event")
                      res._1.complete()
                  }
                  SourceBuilder.sourceBindings -= elem
                }
              sinkBindings
                .get(elem)
                .foreach { propertyMap =>
                  propertyMap.foreach {
                    case (property, res) =>
                      //println(s"remove sink binding for $property")
                      res._2.cancel()
                  }
                  sinkBindings -= elem
                }
            }
          // remove the child
          parent.removeChild(child)
        }
    }

    build[Seq[Element]](parent, "$children", setter)
  }

  def classSink(elem: Element)(
      implicit materializer: Materializer
  ): Sink[Seq[String], NotUsed] = {
    val setter = (classes: Seq[String]) => {
      classes.foreach(elem.classList.add)
      elem.classList
        .filterNot(classes contains _)
        .foreach(elem.classList remove _)
    }

    build[Seq[String]](elem, "$class", setter)
  }

  def dummySink(elem: Element)(
      implicit materializer: Materializer
  ): Sink[Any, NotUsed] = {
    build[Any](elem, "$dummy", _ => ())
  }
}
