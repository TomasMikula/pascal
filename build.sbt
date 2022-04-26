name := "pascal"
organization := "com.github.tomasmikula"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
homepage := Some(url("http://github.com/TomasMikula/pascal"))

scalaVersion := crossScalaVersions.value.head
crossScalaVersions := Seq("2.12.15", "2.13.8")
crossVersion := CrossVersion.full
crossTarget := {
  // workarond for https://github.com/sbt/sbt/issues/5097
  target.value / s"scala-${scalaVersion.value}"
}

Compile / unmanagedSourceDirectories += {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2L, v)) if v >= 13 =>
      (Compile / baseDirectory).value / s"src/main/scala-2.13+"
    case _ =>
      (Compile / baseDirectory).value / s"src/main/scala-2.13-"
  }
}

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  scalaOrganization.value % "scala-compiler" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)

scalacOptions ++= Seq(
  //"-Xfatal-warnings",
  "-Xlint",
  "-feature",
  "-language:higherKinds",
  "-deprecation",
  "-unchecked"
)

List(Compile, Test) flatMap { config =>
  Seq(
    // Notice this is :=, not += - all the warning/lint options are simply
    // impediments in the repl.
    config / console / scalacOptions := Seq(
      "-language:_",
      "-Xplugin:" + (Compile / packageBin).value
    )
  )
}

Test / scalacOptions ++= {
  val jar = (Compile / packageBin).value
  Seq(s"-Xplugin:${jar.getAbsolutePath}", s"-Jdummy=${jar.lastModified}") // ensures recompile
}

Test / scalacOptions += "-Yrangepos"

Test / fork := true


/******************
 *** Publishing ***
 ******************/

import ReleaseTransformations._

releaseCrossBuild := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
Test / publishArtifact := false
publishMavenStyle := true
pomIncludeRepository := Function.const(false)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <scm>
    <url>git@github.com:TomasMikula/pascal.git</url>
    <connection>scm:git:git@github.com:TomasMikula/pascal.git</connection>
  </scm>
  <developers>
    <developer>
      <id>TomasMikula</id>
      <name>Tomas Mikula</name>
      <url>http://github.com/TomasMikula/</url>
    </developer>
  </developers>
)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges)
