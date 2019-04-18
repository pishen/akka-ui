package akka

import akka.stream._
import akka.stream.scaladsl._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import scala.language.experimental.macros

package object ui {
  implicit class RichDOMTokenList(tokens: DOMTokenList)
      extends EasySeq[String](tokens.length, tokens.apply)

  val preventDefault = SourceBuilder.Config("preventDefault")
  val stopPropagation = SourceBuilder.Config("stopPropagation")

  implicit class SourceChainer[T <: EventTarget](t: T) {
    def source(
        eventType: String,
        configs: SourceBuilder.Config*
    ): Any = macro SourceBuilder.source[T]
  }

  implicit class SinkChainer[T <: Element](t: T) {
    def sink(property: String): Any = macro SinkBuilder.sink[T]
    def styleSink(style: String): Any = macro SinkBuilder.styleSink[T]

    def childrenSink(
        implicit materializer: Materializer
    ): Sink[Seq[Element], NotUsed] = SinkBuilder.childrenSink(t)

    def classSink(
        implicit materializer: Materializer
    ): Sink[Seq[String], NotUsed] = SinkBuilder.classSink(t)

    def dummySink(
        implicit materializer: Materializer
    ): Sink[Any, NotUsed] = SinkBuilder.dummySink(t)
  }
}
