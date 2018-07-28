package akka.ui

import org.scalajs.dom.raw.MouseEvent
import org.scalajs.dom.html.Button

sealed abstract class Listener[A, T, E](val name: String)

// trait Click[T <: EventTarget] extends Listener {
  // type E = MouseEvent
  // override def name = "click"
// }

sealed trait Click

// trait Click[X] extends Listener[Window, X]

object Click {
  implicit val x = new Listener[Click, Button, MouseEvent]("click") {}


  // implicit val windowClick = new Click[Window] {}
  // implicit val buttonClick = new Click[HTMLButtonElement] {}
}
