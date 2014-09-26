package de.choffmeister.akka.smtpserver

import akka.util.ByteString

object SmtpProtocol {
  val CRLF = ByteString("\r\n")

  sealed trait Reply {
    val code: Int
    val message: String
  }

  object Reply {
    def apply(code: Int, message: String): ByteString = {
      if (message.length > 0) ByteString(code.toString + " " + message) ++ CRLF
      else ByteString(code.toString) ++ CRLF
    }

    def unapply(raw: ByteString): Option[(Int, String)] = extractCode(raw) match {
      case Some(code) ⇒
        if (raw.endsWith(CRLF)) Some((code, raw.drop(4).take(raw.length - 6).utf8String))
        else None
      case _ ⇒ None
    }

    private def extractCode(b: ByteString): Option[Int] = {
      if (b.length >= 3 &&
        b(0).toChar.isDigit &&
        b(1).toChar.isDigit &&
        b(2).toChar.isDigit) Some(b.take(3).utf8String.toInt)
      else None
    }
  }

  sealed trait Command {
    val code: String
    val message: String
  }

  object Command {
    def apply(code: String, message: String): ByteString = {
      if (message.length > 0) ByteString(code + " " + message + "\r\n")
      else ByteString(code + "\r\n")
    }

    def unapply(raw: ByteString): Option[(String, String)] = extractCode(raw) match {
      case Some(code) ⇒
        if (raw.endsWith(CRLF)) Some((code, raw.drop(5).take(raw.length - 7).utf8String))
        else None
      case _ ⇒ None
    }

    private def extractCode(b: ByteString): Option[String] = {
      if (b.length >= 4 &&
        b(0).toChar.isLetter && b(0).toChar.isUpper &&
        b(1).toChar.isLetter && b(1).toChar.isUpper &&
        b(2).toChar.isLetter && b(2).toChar.isUpper &&
        b(3).toChar.isLetter && b(3).toChar.isUpper) Some(b.take(4).utf8String)
      else None
    }
  }
}
