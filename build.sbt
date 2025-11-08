
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
//import scalariform.formatter.preferences._
//import com.typesafe.sbt.SbtScalariform.ScalariformKeys

ThisBuild / organization := "org.openmole.gridscale"
name := "gridscale"

def scalaVersionValue = "3.7.2"

ThisBuild / scalaVersion := scalaVersionValue
//ThisBuild / crossScalaVersions := Seq("2.13.8", "3.1.2")

ThisBuild / licenses := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/"))
ThisBuild / homepage := Some(url("https://github.com/openmole/gridscale"))

ThisBuild / pomIncludeRepository := { _ => false}
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/openmole/gridscale.git"), "scm:git:git@github.com:openmole/gridscale.git"))

ThisBuild / publishTo := localStaging.value

lazy val root = (project in file(".")).settings (
  publishArtifact := false
)

ThisBuild / developers := List(
  Developer(
    id    = "romainreuillon",
    name  = "Romain Reuillon",
    email = "",
    url   = url("https://github.com/romainreuillon/")
  ),
  Developer(
    id    = "jopasserat",
    name  = "Jonathan Passerat-Palmbach",
    email = "",
    url   = url("https://github.com/jopasserat/")
  ),
  Developer(
    id    = "justeraimbault",
    name  = "Juste Raimbault",
    email = "",
    url   = url("https://github.com/JusteRaimbault/")
  )
)

releaseVersionBump := sbtrelease.Version.Bump.Minor
releaseTagComment    := s"Releasing ${(ThisBuild / version).value}"
releaseCommitMessage := s"Bump version to ${(ThisBuild / version).value}"
//sonatypeProfileName := "org.openmole"

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  //runTest,
  setReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("publishSigned"),
  releaseStepCommand("sonaRelease"),
  setNextVersion,
  commitNextVersion,
  //releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

def settings = Seq (
  resolvers += Resolver.sonatypeCentralSnapshots,
  resolvers += "jitpack" at "https://jitpack.io",
  javacOptions ++= Seq("-source", "11", "-target", "11"),
  scalacOptions ++= Seq("-Xtarget:11", "-language:higherKinds"),
  scalacOptions ++= Seq("-language:postfixOps", "-source:3.7")
)


def exportSettings = Seq(exportJars := true)

lazy val publishDir = settingKey[File]("Publishing directory")
lazy val publishIpfs = taskKey[Unit]("Publish to IPFS")

def defaultSettings =
  settings ++
    Seq(
      scalaVersion := scalaVersionValue,
      shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
    )

/* ---------------- Libraries --------------------*/

lazy val httpComponentsVersion = "4.5.14"
lazy val httpComponents = Seq("httpclient", "httpmime").map(
  "org.apache.httpcomponents" % _ % httpComponentsVersion)

lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.9" % "test"

lazy val betterFile = "com.github.pathikrit" %% "better-files" % "3.9.2" cross(CrossVersion.for3Use2_13)

val circeVersion = "0.14.6"

lazy val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)


lazy val compress = "org.apache.commons" % "commons-compress" % "1.28.0"

val json4sVersion = "4.0.7"

/* -------------- gridscale dsl ------------------ */

def dslSettings = defaultSettings ++ Seq(
  libraryDependencies += "org.typelevel"  %% "squants"  % "1.8.3"
)

lazy val gridscale = Project(id = "gridscale", base = file("gridscale")) settings(dslSettings) settings(
  libraryDependencies += scalaTest
)

lazy val local = Project(id = "local", base = file("local")) settings(dslSettings) dependsOn (gridscale)

lazy val ssh = Project(id = "ssh", base = file("ssh")) settings(dslSettings) dependsOn (gridscale) settings (
  libraryDependencies += "com.hierynomus" % "sshj" % "0.40.0",
  libraryDependencies += "com.jcraft" % "jzlib" % "1.1.3"
)

lazy val cluster = Project(id = "cluster", base = file("cluster")) settings(dslSettings: _*) dependsOn (ssh, local)

lazy val pbs = Project(id = "pbs", base = file("pbs")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val slurm = Project(id = "slurm", base = file("slurm")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val condor = Project(id = "condor", base = file("condor")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val oar = Project(id = "oar", base = file("oar")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val sge = Project(id = "sge", base = file("sge")) settings(dslSettings: _*) dependsOn(gridscale, cluster)


lazy val qarnot = Project(id = "qarnot", base = file("qarnot")) dependsOn(gridscale, http) settings(
  dslSettings,
  libraryDependencies ++= circe
)

lazy val miniclust = Project(id = "miniclust", base = file("miniclust")) settings(dslSettings) dependsOn(gridscale) settings(
  libraryDependencies += "com.github.openmole.miniclust" %% "submit" % "28e2a76751fc5da0739f5e36c97172ba4dd71296",
)

lazy val http = Project(id = "http", base = file("http")) settings(dslSettings: _*) dependsOn(gridscale) settings (
  libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1",
  libraryDependencies += "com.squareup.okhttp3" % "okhttp" % "5.3.0",
  libraryDependencies ++= httpComponents
)

lazy val webdav = Project(id = "webdav", base = file("webdav")) settings(dslSettings: _*) dependsOn(gridscale, http)

lazy val dirac =  Project(id = "dirac", base = file("dirac")) settings(dslSettings: _*) dependsOn(gridscale, http) settings (
  libraryDependencies += "org.json4s" %% "json4s-jackson" % json4sVersion,
  libraryDependencies += compress
)

lazy val egi = Project(id = "egi", base = file("egi")) settings(dslSettings: _*) dependsOn(gridscale, http, webdav) settings (
  libraryDependencies += "org.json4s" %% "json4s-jackson" % json4sVersion,
  libraryDependencies += "org.bouncycastle" % "bcpkix-jdk18on" % "1.82",
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.4.0"
)

lazy val ipfs = Project(id = "ipfs", base = file("ipfs")) settings(dslSettings: _*) dependsOn(gridscale, http) settings (
  libraryDependencies ++= circe,
  libraryDependencies += compress)


/* -------------- examples ------------------ */

def exampleSettings = 
  defaultSettings ++ exportSettings ++ Seq(
    publish / skip := true
  )

lazy val egiCreamExample  = Project(id = "example-egi-cream", base = file("examples/egi/cream")) settings(exampleSettings) dependsOn egi
lazy val egiWebDAVExample  = Project(id = "example-egi-webdav", base = file("examples/egi/webdav")) settings(exampleSettings) dependsOn (egi, webdav)
lazy val egiDiracExample  = Project(id = "example-egi-dirac", base = file("examples/egi/dirac")) settings(exampleSettings) dependsOn (egi, dirac)
lazy val condorExample = Project(id = "example-condor", base = file("examples/condor")) settings(exampleSettings) dependsOn condor
lazy val pbsExample  = Project(id = "example-pbs", base = file("examples/pbs")) settings(exampleSettings) dependsOn pbs
lazy val slurmExample  = Project(id = "example-slurm", base = file("examples/slurm")) settings(exampleSettings) dependsOn slurm
lazy val sgeExample    = Project(id = "example-sge", base = file("examples/sge")) settings(exampleSettings) dependsOn sge
lazy val sshExample  = Project(id = "example-ssh", base = file("examples/ssh")) settings(exampleSettings) dependsOn ssh
lazy val oarExample  = Project(id = "example-oar", base = file("examples/oar")) settings(exampleSettings) dependsOn oar
lazy val qarnotExample  = Project(id = "example-qarnot", base = file("examples/qarnot")) settings(exampleSettings) dependsOn qarnot
lazy val httpExample  = Project(id = "example-http", base = file("examples/http")) settings(exampleSettings) dependsOn http
lazy val localExample  = Project(id = "example-local", base = file("examples/local")) settings(exampleSettings) dependsOn (local, cluster)
lazy val miniclustExample  = Project(id = "example-miniclust", base = file("examples/miniclust")) settings(exampleSettings) dependsOn miniclust


lazy val ipfsExample  = Project(id = "ipfsexample", base = file("examples/ipfs")) settings(exampleSettings) dependsOn ipfs settings(
  libraryDependencies += betterFile
)
