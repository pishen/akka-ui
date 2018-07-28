name := "akka-ui"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.6"

enablePlugins(ScalaJSPlugin)

libraryDependencies ++= Seq(
  "org.akka-js" %%% "akkajsactorstream" % "1.2.5.13",
  "org.scala-js" %%% "scalajs-dom" % "0.9.2"
)

organization := "net.pishen"
