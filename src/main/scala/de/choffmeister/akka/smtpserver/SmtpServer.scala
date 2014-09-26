package de.choffmeister.akka.smtpserver

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Tcp._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

class SmtpServer(connection: ActorRef, config: SmtpServer.Config) extends FSM[SmtpServer.State, SmtpServer.Data] {
  import de.choffmeister.akka.smtpserver.SmtpProtocol._
  import de.choffmeister.akka.smtpserver.SmtpServer._

  private var pipeline = new DelimitedTcpPipeline(ByteString("\r\n")).compose(new LoggingTcpPipeline("SERVER"))
  private var adapter = context.actorOf(Props(new TcpPipelineAdapter(connection, self, pipeline)))
  connection ! Register(adapter)
  self ! Register(self)

  when(State0)(PartialFunction.empty)

  when(State1) {
    case Event(Register(_, _, _), _) ⇒
      reply(220, "localhost")
      goto(State2)
  }

  when(State2) {
    // http://tools.ietf.org/html/rfc5321#section-4.1.1.1
    case Event(Received(Command("HELO", remoteName)), _) ⇒
      replyOk()
      goto(State3)

    // http://tools.ietf.org/html/rfc5321#section-4.1.1.1
    case Event(Received(Command("EHLO", remoteName)), _) ⇒
      replyOk()
      goto(State3)
  }

  when(State3) {
    // http://tools.ietf.org/html/rfc5321#section-4.1.1.2
    case Event(Received(Command("MAIL", from)), _) ⇒
      replyOk()
      goto(State4) using Envelope(from = Some(from))
  }

  when(State4) {
    // http://tools.ietf.org/html/rfc5321#section-4.1.1.3
    case Event(Received(Command("RCPT", to)), envelope: Envelope) ⇒
      replyOk()
      goto(State4) using envelope.copy(to = envelope.to ++ List(to))

    // http://tools.ietf.org/html/rfc5321#section-4.1.1.4
    case Event(Received(Command("DATA", _)), envelope: Envelope) if envelope.to == Nil ⇒
      replyError("You must provide at least one recipient")
      goto(State4)

    // http://tools.ietf.org/html/rfc5321#section-4.1.1.4
    case Event(Received(Command("DATA", _)), envelope: Envelope) ⇒
      reply(354, "Start mail input")
      goto(State5)
  }

  // http://tools.ietf.org/html/rfc5321#section-4.1.1.4
  when(State5) {
    case Event(Received(raw), envelope: Envelope) if raw == ByteString(".\r\n") ⇒
      val result = envelope
      logMail(result)
      replyOk()
      goto(State3) using Empty
    case Event(Received(raw), envelope: Envelope) ⇒
      goto(State5) using envelope.copy(body = envelope.body ++ raw)
  }

  whenUnhandled {
    // http://tools.ietf.org/html/rfc5321#section-4.1.1.6
    case Event(Received(Command("VRFY", mailbox)), _) ⇒
      // TODO: check mailbox
      replyOk()
      stay()

    // http://tools.ietf.org/html/rfc5321#section-4.1.1.9
    case Event(Received(Command("NOOP", _)), _) ⇒
      replyOk()
      stay()

    // http://tools.ietf.org/html/rfc5321#section-4.1.1.5
    case Event(Received(Command("RSET", _)), _) ⇒
      replyOk()
      goto(State3) using Empty

    // http://tools.ietf.org/html/rfc5321#section-4.1.1.10
    case Event(Received(Command("QUIT", _)), _) ⇒
      reply(221, "OK")
      adapter ! Close
      goto(State0)

    case Event(_: ConnectionClosed, _) ⇒
      log.debug("Connection closed")
      context.stop(self)
      goto(State0)

    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      replyError()
      stay()
  }

  startWith(State1, Empty)
  initialize()

  def reply(code: Int, message: String = "") = adapter ! Write(Reply(code, message))
  def replyOk(message: String = "OK") = reply(250, message)
  def replyError(message: String = "Error") = reply(500, message)

  def logMail(envelope: Envelope) = {
    log.info("Got mail")
    log.info("From {}", envelope.from)
    log.info("To {}", envelope.to)
    log.info("Body\n{}", envelope.body.utf8String)
  }
}

object SmtpServer {
  sealed trait State
  case object State0 extends State
  case object State1 extends State
  case object State2 extends State
  case object State3 extends State
  case object State4 extends State
  case object State5 extends State

  sealed trait Data
  case object Empty extends Data
  case class Envelope(from: Option[String] = None, to: List[String] = Nil, body: ByteString = ByteString.empty) extends Data

  case class Config(bind: InetSocketAddress, banner: String)
  object Config {
    def load(): Config = {
      val raw = ConfigFactory.load("application").getConfig("smtp.server")
      Config(
        bind = new InetSocketAddress(raw.getString("interface"), raw.getInt("port")),
        banner = raw.getString("banner")
      )
    }
  }
}
