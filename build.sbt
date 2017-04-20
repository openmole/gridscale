
import com.typesafe.sbt.osgi
import osgi.OsgiKeys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import scalariform.formatter.preferences._


//lazy val root = (project in file(".")).settings(settings: _*).
//  aggregate(
//    gridscale,
//    gridscaleEGI,
//    gridscaleHTTP,
//    gridscaleSSH,
//    gridscaleCondor,
//    gridscalePBS,
//    gridscaleSLURM,
//    gridscaleSGE,
//    gridscaleOAR,
//    gridscaleBundle,
//    egiBundle,
//    httpBundle,
//    sshBundle,
//    condorBundle,
//    pbsBundle,
//    slurmBundle,
//    sgeBundle,
//    oarBundle
//  ) settings(
//    name := "gridscale-root",
//    publishArtifact := false
//  ) disablePlugins(AssemblyPlugin)


organization in ThisBuild := "fr.iscpif"
name := "gridscale"

scalaVersion in ThisBuild := "2.12.2"
crossScalaVersions in ThisBuild := Seq("2.11.11", "2.12.2")
licenses in ThisBuild := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/"))
homepage in ThisBuild := Some(url("https://github.com/openmole/gridscale"))

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")}

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

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)

scalariformSettings

lazy val javaByteCodeVersion = SettingKey[String]("javaByteCodeVersion")

def settings = Seq (
  javaByteCodeVersion :=
    (scalaVersion.value.split('.').take(2).mkString(".") match {
      case "2.10" | "2.11" => "1.7"
      case "2.12" => "1.8"
      case _ => sbt.fail("unknown scala version " + scalaVersion.value)
    }),
  javacOptions in (Compile, compile) ++= Seq("-source", javaByteCodeVersion.value, "-target", javaByteCodeVersion.value),
  scalacOptions += s"-target:jvm-${javaByteCodeVersion.value}",
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  test in assembly := {}
)


def exportSettings = Seq(exportJars := true)

lazy val publishDir = settingKey[File]("Publishing directory")
lazy val publishIpfs = taskKey[Unit]("Publish to IPFS")

lazy val defaultSettings =
  settings ++
    scalariformSettings ++ Seq(
  ScalariformKeys.preferences :=
    ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(RewriteArrowSymbols, true),

  organization := "fr.iscpif.gridscale",

  publishDir := {
    import java.io.File
    val dir = new File("/export/ivy/")
    dir.mkdirs()
    dir
  }

  //publishMavenStyle := false,
  //publishTo := Some(Resolver.file("ipfs", publishDir.value)(Resolver.ivyStylePatterns)),
)

lazy val examples = (project in file("examples")).settings(settings: _*).
  aggregate(
    egiCreamExample,
    egiWebDAVExample,
    egiDiracExample,
    httpExample,
    sshExample,
    condorExample,
//    pbsExample,
    slurmExample,
    sgeExample,
    oarExample
  ) settings(
    name := "gridscale-examples",
    publishArtifact := false,
    aggregate in assembly := true // one separate jar per aggregated project
  )


lazy val egiCreamExample  = Project(id = "egicreamexample", base = file("examples/egi/cream"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
lazy val egiWebDAVExample  = Project(id = "egiwebdavexample", base = file("examples/egi/webdav"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
lazy val egiDiracExample  = Project(id = "egidiracexample", base = file("examples/egi/dirac"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
lazy val condorExample = Project(id = "condorexample", base = file("examples/condor"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleCondor)
lazy val slurmExample  = Project(id = "slurmexample", base = file("examples/slurm"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSLURM)
lazy val sgeExample    = Project(id = "sgeexample", base = file("examples/sge"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSGE)
lazy val sshExample  = Project(id = "sshexample", base = file("examples/ssh"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSSH)
lazy val oarExample  = Project(id = "oarexample", base = file("examples/oar"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleOAR)
lazy val httpExample  = Project(id = "httpexample", base = file("examples/http"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleHTTP)


/* -------------------- OSGI ------------------------*/

lazy val gridscaleOsgiSettings =
  SbtOsgi.autoImport.osgiSettings ++ Seq(
    importPackage := Seq("*"),
    privatePackage := Seq(s"fr.iscpif.gridscale.${name.value}.*", "!fr.iscpif.gridscale.*", "!scala.*", "!org.bouncycastle.*", "!org.apache.log4j.*", "!org.slf4j.*", "!org.apache.commons.logging.*", "*"),
    organization := "fr.iscpif.gridscale.bundle",
    bundleSymbolicName := s"fr.iscpif.gridscale.${name.value}",
    exportPackage := Seq(s"${bundleSymbolicName.value}.*"),
    requireCapability := """osgi.ee;filter:="(&(osgi.ee=JavaSE)(version=1.7))"""
  )

lazy val noSSH = privatePackage :=
  Seq("!net.schmizz.*", "!fr.iscpif.gridscale.ssh.*") ++ privatePackage.value

lazy val gridscaleBundle = Project(id = "gridscalebundle", base = file("bundles/gridscale"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin) settings(gridscaleOsgiSettings:_*) dependsOn (gridscale) settings (
  privatePackage := Seq("fr.iscpif.gridscale.*", "!scala.*"),
  exportPackage := Seq("fr.iscpif.gridscale.*"),
  name := "gridscale")

lazy val egiBundle = Project(id = "egibundle", base = file("bundles/egi"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleEGI, gridscaleBundle, gridscaleHTTP) settings(
  name := "egi",
  importPackage := Seq("!org.glassfish.grizzly.*", "!org.jboss.*", "!com.google.protobuf.*", "!javax.*", "!com.google.common.util.*", "!sun.misc", "!org.tukaani.xz.*", "!org.apache.tools.ant.*", "*"),
  privatePackage := Seq("fr.iscpif.gridscale.libraries.*", "fr.iscpif.gridscale.globushttp.*", "!org.apache.http.*", "!org.apache.commons.codec.*") ++ privatePackage.value,
  exportPackage := exportPackage.value ++ Seq("org.glite.*", "org.globus.*", "org.ogf.*"))

lazy val httpBundle = Project(id = "httpbundle", base = file("bundles/http"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleHTTP, gridscaleBundle) settings (
  name := "http",
  libraryDependencies += "org.apache.httpcomponents" % "httpcore-osgi" % "4.4.4",
  libraryDependencies += "org.osgi" % "org.osgi.compendium" % "4.2.0",
  importPackage := Seq("!javax.*", "!org.apache.tools.ant.*", "*"),
  privatePackage := Seq("org.apache.http.entity.mime.*", "!org.apache.http.*", "!org.apache.commons.codec.*") ++ privatePackage.value)

lazy val sshBundle = Project(id = "sshbundle", base = file("bundles/ssh"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleSSH, gridscaleBundle) settings(
  name := "ssh",
  importPackage := Seq("!javax.*", "*"),
  exportPackage := Seq("!net.schmizz.sshj.*", "!fr.iscpif.gridscale.ssh.impl.*") ++ exportPackage.value)

lazy val condorBundle = Project(id = "condorbundle", base = file("bundles/condor"), settings = defaultSettings) enablePlugins(SbtOsgi)  disablePlugins(AssemblyPlugin) settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleCondor, gridscaleBundle) settings(
  name := "condor", noSSH)

lazy val pbsBundle = Project(id = "pbsbundle", base = file("bundles/pbs"), settings = defaultSettings) enablePlugins(SbtOsgi)  disablePlugins(AssemblyPlugin) settings(gridscaleOsgiSettings:_*) dependsOn (gridscalePBS, gridscaleBundle) settings(
  name := "pbs", noSSH)

lazy val slurmBundle = Project(id = "slurmbundle", base = file("bundles/slurm"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleSLURM, gridscaleBundle) settings(
  name := "slurm", noSSH)

lazy val sgeBundle = Project(id = "sgebundle", base = file("bundles/sge"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin)  settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleSGE, gridscaleBundle) settings(
  name := "sge", noSSH)

lazy val oarBundle = Project(id = "oarbundle", base = file("bundles/oar"), settings = defaultSettings) enablePlugins(SbtOsgi) disablePlugins(AssemblyPlugin) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleOAR, gridscaleBundle) settings(
  name := "oar", noSSH)



/* ---------------- Modules --------------------*/

lazy val httpComponentsVersion = "4.5.2"


lazy val gridscale = Project(id = "gridscale", base = file("modules/gridscale"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) settings(
  libraryDependencies += scalaTest,
  libraryDependencies += "org.scala-stm" %% "scala-stm" % "0.8"
  )

lazy val gridscaleEGI = Project(id = "egi", base = file("modules/gridscale-egi"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn(gridscale, gliteSecurityVoms, gridscaleHTTP) settings (
  libraryDependencies += "fr.iscpif.jglobus" % "io" % jglobusVersion,
  libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.5.0",
  libraryDependencies += "org.apache.commons" % "commons-compress" % "1.10",
  libraryDependencies += "com.google.guava" % "guava" % "19.0"
  )

lazy val gridscaleHTTP = Project(id = "http", base = file("modules/gridscale-http"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn (gridscale) settings (
  libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1",
  libraryDependencies += jodaTime,
  libraryDependencies ++= httpComponents)

lazy val gridscaleSSH = Project(id = "ssh", base = file("modules/gridscale-ssh"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn (gridscale) settings (
  libraryDependencies += "net.schmizz" % "sshj" % "0.10.0",
  libraryDependencies += "com.jcraft" % "jzlib" % "1.1.3"
  )

lazy val gridscaleCondor = Project(id = "condor", base = file("modules/gridscale-condor"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn(gridscale, gridscaleSSH) settings (
  libraryDependencies ++= Seq(scalaTest, mockito)
  )

lazy val gridscalePBS = Project(id = "pbs", base = file("modules/gridscale-pbs"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn(gridscale, gridscaleSSH)

lazy val gridscaleSLURM = Project(id = "slurm", base = file("modules/gridscale-slurm"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn(gridscale, gridscaleSSH)  settings (
  libraryDependencies ++= Seq(scalaTest, mockito)
  )

lazy val gridscaleSGE = Project(id = "sge", base = file("modules/gridscale-sge"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn(gridscale, gridscaleSSH)  settings (
  libraryDependencies ++= Seq(scalaTest, mockito)
  )

lazy val gridscaleOAR = Project(id = "oar", base = file("modules/gridscale-oar"), settings = defaultSettings ++ exportSettings) disablePlugins(AssemblyPlugin) dependsOn(gridscale, gridscaleSSH)



/* ---------------- Libraries --------------------*/

lazy val jglobusVersion = "2.2.0-20160826"

lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"

lazy val mockito = "org.mockito" % "mockito-all" % "1.8.4" % "test"

lazy val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.50"
lazy val log4j = "log4j" % "log4j" % "1.2.17"

lazy val httpComponents = Seq(
  "org.apache.httpcomponents" % "httpclient-osgi" % httpComponentsVersion,
  "org.apache.httpcomponents" % "httpmime" % httpComponentsVersion)

lazy val gliteSecurityVoms = Project(id = "glite-security-voms", base = file("libraries/glite-security-voms"), settings = defaultSettings) settings(
  libraryDependencies += bouncyCastle,
  libraryDependencies += log4j,
  libraryDependencies += "fr.iscpif.jglobus" % "myproxy" % jglobusVersion,
  libraryDependencies += "commons-lang" % "commons-lang" % "2.3",
  libraryDependencies += "commons-logging" % "commons-logging" % "1.1",
  libraryDependencies += "commons-cli" % "commons-cli" % "1.1")

lazy val jodaTime = "joda-time" % "joda-time" % "2.9.9"

