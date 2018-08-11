package akka.ui

import akka.actor._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import ChildrenWriter._

class ChildrenWriter(n: Node) extends Actor {
  def receive = {
    case ReplaceAll(children) =>
      children.foreach(child => n.appendChild(child))
      n.childNodes
        .dropRight(children.size)
        .foreach { child =>
          // remove the bindings
          sourceBindings.get(child)
            .foreach(actors => actors.foreach(_ ! PoisonPill))
          sourceBindings -= child
          sinkBindings.get(child)
            .foreach(actors => actors.foreach(_ ! PoisonPill))
          sinkBindings -= child
          // remove the node
          n.removeChild(child)
        }
    case Completed => // do nothing
  }
}

object ChildrenWriter {
  case class ReplaceAll(children: Seq[Node])
  case object Completed
  def props(n: Node) = Props(new ChildrenWriter(n))
}
