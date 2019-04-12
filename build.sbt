import com.typesafe.sbt.packager.docker.{ExecCmd, Cmd}

name := """play-spark"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.8.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.7",
  "org.apache.spark" % "spark-core_2.11" % "2.2.1",
  "org.apache.spark" % "spark-sql_2.11" % "2.2.1",
  "org.apache.spark" % "spark-mllib_2.11" % "2.2.1",
  "org.webjars" %% "webjars-play" % "2.5.0-1",
  "org.webjars" % "bootstrap" % "3.3.6",
  "com.adrianhurt" %% "play-bootstrap" % "1.0-P25-B3",
  "org.codehaus.janino" % "janino" % "3.0.8"
  //"com.typesafe.akka" % "akka-actor_2.11" % "2.3.7"

)

dockerCommands := Seq(
  Cmd("FROM","java:openjdk-8-jre"),
  Cmd("MAINTAINER","hluu"),
  Cmd("EXPOSE","9000"),
  Cmd("ADD","stage /"),
  Cmd("WORKDIR","/opt/docker"),
  Cmd("RUN","[\"chown\", \"-R\", \"daemon\", \".\"]"),
  Cmd("RUN","[\"chmod\", \"+x\", \"bin/play-spark\"]"),
  Cmd("USER","daemon"),
  Cmd("ENTRYPOINT","[\"bin/play-spark\", \"-J-Xms128m\", \"-J-Xmx512m\", \"-J-server\"]"),
  ExecCmd("CMD")
)


