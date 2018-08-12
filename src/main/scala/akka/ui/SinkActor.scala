package akka.ui

import akka.actor.Actor
import akka.actor.Props
import scala.reflect.ClassTag
import SinkActor._

class SinkActor[V: ClassTag](setter: V => Unit) extends Actor {
  def receive = {
    case v: V      => setter(v)
    case Completed => // do nothing
  }
}

object SinkActor {
  case object Completed
  def props[V: ClassTag](setter: V => Unit) = {
    Props(new SinkActor(setter))
  }
}
