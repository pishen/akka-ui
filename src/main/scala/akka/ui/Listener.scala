package akka.ui

import org.scalajs.dom.raw._

trait Listener {
  type E <: Event
  def name: String
}

trait Click[T <: EventTarget] extends Listener {
  type E = MouseEvent
  override def name = "click"
}

object Click {
  implicit val windowClick = new Click[Window] {}
  implicit val buttonClick = new Click[HTMLButtonElement] {}
}
