package akka.ui

import akka.actor.Actor
import akka.actor.Props
import scala.reflect.ClassTag
import PropertyWriter._

class PropertyWriter[V: ClassTag](setter: V => Unit) extends Actor {
  def receive = {
    case v: V => setter(v)
    case Completed => // do nothing
  }
}

object PropertyWriter {
  case object Completed
  def props[V: ClassTag](setter: V => Unit) = Props(new PropertyWriter(setter))
}
