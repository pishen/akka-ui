package akka.ui

import org.scalajs.dom.html._
import org.scalajs.dom.raw._

trait Binder[T <: EventTarget, L <: Listener]

object Binder {
  implicit val buttonClick = new Binder[Button, Click.type] {}
}
