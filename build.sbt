name := "smtpserver"

version := "0.0.1"

organization := "de.choffmeister.akka"

scalaVersion := "2.10.4"

scalacOptions := Seq("-encoding", "utf8")

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "com.typesafe" % "config" % "1.2.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.5",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.5"
)

packSettings

packMain := Map("smtpserver" -> "de.choffmeister.akka.smtpserver.Application")

packExtraClasspath := Map("smtpserver" -> Seq("${PROG_HOME}/config"))
