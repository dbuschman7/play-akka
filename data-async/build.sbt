name := "data-async"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  javaCore
)     

lazy val root = (project in file(".")).enablePlugins(SbtWeb).enablePlugins(play.PlayScala)

