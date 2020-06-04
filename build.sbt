
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

organization in ThisBuild := "org.openmole.gridscale"
name := "gridscale"

scalaVersion in ThisBuild := "2.12.11"
crossScalaVersions in ThisBuild := Seq("2.12.11", "2.13.2")

licenses in ThisBuild := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/"))
homepage in ThisBuild := Some(url("https://github.com/openmole/gridscale"))

publishTo in ThisBuild := sonatypePublishToBundle.value


/*publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")}*/

pomIncludeRepository in ThisBuild := { _ => false}
scmInfo in ThisBuild := Some(ScmInfo(url("https://github.com/openmole/gridscale.git"), "scm:git:git@github.com:openmole/gridscale.git"))

pomExtra in ThisBuild := {
  <!-- Developer contact information -->
    <developers>
      <developer>
        <id>romainreuillon</id>
        <name>Romain Reuillon</name>
        <url>https://github.com/romainreuillon/</url>
      </developer>
      <developer>
        <id>jopasserat</id>
        <name>Jonathan Passerat-Palmbach</name>
        <url>https://github.com/jopasserat/</url>
      </developer>
    </developers>
}


releaseVersionBump := sbtrelease.Version.Bump.Minor

releaseTagComment    := s"Releasing ${(version in ThisBuild).value}"

releaseCommitMessage := s"Bump version to ${(version in ThisBuild).value}"

sonatypeProfileName := "org.openmole"


releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  //releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

def priorTo2_13(scalaVersion: String): Boolean =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, minor)) if minor < 13 => true
    case _                              => false
  }

def settings = Seq (
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
  // macro paradise doesn't work with scaladoc
  //sources in (Compile, doc) := Nil,
  resolvers += Resolver.sonatypeRepo("snapshots"),
  scalacOptions ++= (
    if (priorTo2_13(scalaVersion.value)) Nil else Seq("-Ymacro-annotations", "-language:postfixOps")
    ),
  libraryDependencies ++=
    (if (priorTo2_13(scalaVersion.value))
      Seq(
        compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch))
      )
    else Nil),
  libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6"
)


def exportSettings = Seq(exportJars := true)

lazy val publishDir = settingKey[File]("Publishing directory")
lazy val publishIpfs = taskKey[Unit]("Publish to IPFS")

def defaultSettings =
  settings ++
    scalariformSettings(autoformat = true) ++ Seq(
  ScalariformKeys.preferences :=
    ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(RewriteArrowSymbols, true),

  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },

  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
)



/* ---------------- Libraries --------------------*/

lazy val httpComponentsVersion = "4.5.10"
lazy val httpComponents = Seq("httpclient", "httpmime").map(
  "org.apache.httpcomponents" % _ % httpComponentsVersion)

lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0" % "test"

lazy val betterFile = "com.github.pathikrit" %% "better-files" % "3.8.0"

val circeVersion = "0.12.3"

lazy val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)


lazy val compress = "org.apache.commons" % "commons-compress" % "1.19"

/* -------------- gridscale dsl ------------------ */

val monocleVersion = "2.0.0"

def dslSettings = defaultSettings ++ Seq(
  //scalacOptions += "-Ypartial-unification",

  libraryDependencies += "org.typelevel"  %% "squants"  % "1.6.0",
  libraryDependencies += "com.beachape" %% "enumeratum" % "1.5.15",

  //scalacOptions += "-Xplugin-require:macroparadise",

  //resolvers += Resolver.sonatypeRepo("snapshots"),
  // rename to avoid conflict with publishTo resolver
  //resolvers +=
  //  "Sonatype OSS Stagings" at "https://oss.sonatype.org/content/repositories/staging",

  resolvers += "jitpack" at "https://jitpack.io"

)

lazy val effect = Project(id = "effect", base = file("effect")) settings(dslSettings: _*)

lazy val gridscale = Project(id = "gridscale", base = file("gridscale")) settings(dslSettings: _*) settings(
  libraryDependencies += scalaTest
) dependsOn(effect)

lazy val local = Project(id = "local", base = file("local")) settings(dslSettings: _*) dependsOn (gridscale)

lazy val ssh = Project(id = "ssh", base = file("ssh")) settings(dslSettings: _*) dependsOn (gridscale) settings (
  libraryDependencies += "com.hierynomus" % "sshj" % "0.27.0",
  libraryDependencies += "com.jcraft" % "jzlib" % "1.1.3"
)

lazy val cluster = Project(id = "cluster", base = file("cluster")) settings(dslSettings: _*) dependsOn (ssh, local) settings (
  libraryDependencies ++= Seq("monocle-core", "monocle-generic", "monocle-macro").map("com.github.julien-truffaut" %% _ % monocleVersion)
)

lazy val pbs = Project(id = "pbs", base = file("pbs")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val slurm = Project(id = "slurm", base = file("slurm")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val condor = Project(id = "condor", base = file("condor")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val oar = Project(id = "oar", base = file("oar")) settings(dslSettings: _*) dependsOn(gridscale, cluster)
lazy val sge = Project(id = "sge", base = file("sge")) settings(dslSettings: _*) dependsOn(gridscale, cluster)


lazy val http = Project(id = "http", base = file("http")) settings(dslSettings: _*) dependsOn(gridscale) settings (
  libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1",
  libraryDependencies += "com.squareup.okhttp3" % "okhttp" % "4.3.0",
  libraryDependencies ++= httpComponents
)

lazy val webdav = Project(id = "webdav", base = file("webdav")) settings(dslSettings: _*) dependsOn(gridscale, http)

lazy val dirac =  Project(id = "dirac", base = file("dirac")) settings(dslSettings: _*) dependsOn(gridscale, http) settings (
  libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.7",
  libraryDependencies += compress
)

lazy val egi = Project(id = "egi", base = file("egi")) settings(dslSettings: _*) dependsOn(gridscale, http, webdav) settings (
  libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.7",
  libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.64"
)

lazy val ipfs = Project(id = "ipfs", base = file("ipfs")) settings(dslSettings: _*) dependsOn(gridscale, http) settings (
  libraryDependencies ++= circe,
  libraryDependencies += compress)


/* -------------- examples ------------------ */

def exampleSettings = defaultSettings ++ exportSettings

lazy val egiCreamExample  = Project(id = "egicreamexample", base = file("examples/egi/cream")) settings(exampleSettings: _*) dependsOn egi
lazy val egiWebDAVExample  = Project(id = "egiwebdavexample", base = file("examples/egi/webdav")) settings(exampleSettings: _*) dependsOn (egi, webdav)
lazy val egiDiracExample  = Project(id = "egidiracexample", base = file("examples/egi/dirac")) settings(exampleSettings: _*) dependsOn (egi, dirac)
lazy val condorExample = Project(id = "condorexample", base = file("examples/condor")) settings(exampleSettings: _*) dependsOn condor
lazy val pbsExample  = Project(id = "pbsexample", base = file("examples/pbs")) settings(exampleSettings: _*) dependsOn pbs
lazy val slurmExample  = Project(id = "slurmexample", base = file("examples/slurm")) settings(exampleSettings: _*) dependsOn slurm
lazy val sgeExample    = Project(id = "sgeexample", base = file("examples/sge")) settings(exampleSettings: _*) dependsOn sge
lazy val sshExample  = Project(id = "sshexample", base = file("examples/ssh")) settings(exampleSettings: _*) dependsOn ssh
lazy val oarExample  = Project(id = "oarexample", base = file("examples/oar")) settings(exampleSettings: _*) dependsOn oar
lazy val httpExample  = Project(id = "httpexample", base = file("examples/http")) settings(exampleSettings: _*) dependsOn http
lazy val localExample  = Project(id = "localexample", base = file("examples/local")) settings(exampleSettings: _*) dependsOn (local, cluster)

lazy val ipfsExample  = Project(id = "ipfsexample", base = file("examples/ipfs")) settings(exampleSettings: _*) dependsOn ipfs settings(
  libraryDependencies += betterFile
)
