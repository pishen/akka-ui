package akka.ui

import akka.actor.Actor
import akka.actor.Props
import scala.reflect.ClassTag

class ElementWriter[V: ClassTag](setter: V => Unit) extends Actor {
  def receive = {
    case v: V => setter(v)
    case _ => // do nothing
  }
}

object ElementWriter {
  case object Completed
  def props[V: ClassTag](setter: V => Unit) = Props(new ElementWriter(setter))
}
