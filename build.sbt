name := "akka-ui"

version := "0.2.0"

scalaVersion := "2.12.6"

enablePlugins(ScalaJSPlugin)

libraryDependencies ++= Seq(
  "org.akka-js" %%% "akkajsactor" % "1.2.5.14",
  "org.akka-js" %%% "akkajsactorstream" % "1.2.5.14",
  "org.scala-js" %%% "scalajs-dom" % "0.9.2"
)

organization := "net.pishen"

licenses += "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")

homepage := Some(url("https://github.com/pishen/akka-ui"))

pomExtra := (
  <scm>
    <url>https://github.com/pishen/akka-ui.git</url>
    <connection>scm:git:git@github.com:pishen/akka-ui.git</connection>
  </scm>
  <developers>
    <developer>
      <id>pishen</id>
      <name>Pishen Tsai</name>
    </developer>
  </developers>
)
