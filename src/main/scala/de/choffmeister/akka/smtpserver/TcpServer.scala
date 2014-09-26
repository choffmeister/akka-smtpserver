package de.choffmeister.akka.smtpserver

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Tcp._
import akka.io._

class TcpServer(bind: InetSocketAddress, handler: ActorRef ⇒ Props) extends Actor with ActorLogging {
  implicit val system = context.system
  IO(Tcp) ! Bind(self, bind)

  def receive = {
    case Bound(local) ⇒
      log.debug("Bound to {}", local)

    case CommandFailed(_: Bind) ⇒
      log.error("Unable to bind to {}", bind)
      context.stop(self)

    case Connected(remote, local) ⇒
      log.debug("New connection from {}", remote)
      val connection = sender()
      context.actorOf(handler(connection))
  }
}
