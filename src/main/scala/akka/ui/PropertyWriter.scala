package akka.ui

import akka.actor.Actor
import akka.actor.Props
import scala.reflect.ClassTag

class PropertyWriter[V: ClassTag](setter: V => Unit) extends Actor {
  def receive = {
    case v: V => setter(v)
  }
}

object PropertyWriter {
  case object Completed
  def props[V: ClassTag](setter: V => Unit) = Props(new PropertyWriter(setter))
}
