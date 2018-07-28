package akka.ui

import org.scalajs.dom.raw._

trait Listener {
  type E <: Event
  def name: String
}

object Click extends Listener {
  type E = MouseEvent
  def name = "click"
}
