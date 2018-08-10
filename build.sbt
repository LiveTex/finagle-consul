scalaVersion := "2.11.8"
crossScalaVersions := Seq("2.11.8")

organization := "ru.livetex"

name := "finagle-consul"

version := "1.0.8-SNAPSHOT"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

resolvers += "twttr" at "http://maven.twttr.com/"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

testOptions in Test += Tests.Argument("-oF")

val finagleVer = "18.4.0"
val jacksonVer = "2.9.5"

libraryDependencies ++= Seq(
  "com.twitter"    %% "finagle-core"    % finagleVer,
  "com.twitter"    %% "finagle-http"    % finagleVer,
  "commons-codec"  %  "commons-codec"   % "1.10",

  "org.slf4j" % "slf4j-api" % "1.7.21",

  "com.fasterxml.jackson.core"   %  "jackson-databind"     % jacksonVer,
  "com.fasterxml.jackson.core"   %  "jackson-annotations"  % jacksonVer,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVer,

  "org.scalatest"  %% "scalatest"       % "2.2.4"   % "test"
)


val nexusUrl = "http://sonatype-nexus.livetex.ru"
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
publishTo := {
  if (isSnapshot.value)
    Some("snapshots" at nexusUrl + "/nexus/content/repositories/snapshots/")
  else
    Some("releases"  at nexusUrl + "/nexus/content/repositories/releases/")
}
