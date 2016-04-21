
import com.github.retronym.SbtOneJar
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.osgi.OsgiKeys._
import com.typesafe.sbt.osgi.SbtOsgi
import sbt.Keys._
import sbt._

import scalariform.formatter.preferences._

object GridAPIBuild extends Build with Libraries with Modules with Examples with Bundles


trait Settings <: Build {

  override def settings = super.settings ++ Seq (
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.11.8"),
    javacOptions in (Compile, compile) ++= Seq("-source", "1.7", "-target", "1.7"),
    scalacOptions += "-target:jvm-1.7"
  )

  def exportSettings = Seq(
    exportJars := true
  ) ++ SbtOneJar.oneJarSettings

  lazy val defaultSettings =
    settings ++
      scalariformSettings ++ Seq(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(RewriteArrowSymbols, true),
    organization := "fr.iscpif.gridscale",
    resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
    resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/",
    publishTo <<= isSnapshot { snapshot =>
      val nexus = "https://oss.sonatype.org/"
      if (snapshot) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository := { _ => false},
    licenses := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/")),
    homepage := Some(url("https://github.com/openmole/gridscale")),
    scmInfo := Some(ScmInfo(url("https://github.com/openmole/gridscale.git"), "scm:git:git@github.com:openmole/gridscale.git")),
    // To sync with Maven central, you need to supply the following information:
    pomExtra := {
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
  )

}


trait Examples <: Modules with Settings {
  lazy val egicreamExample  = Project(id = "egicreamexample", base = file("examples/egi/cream"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val egisrmExample  = Project(id = "egisrmexample", base = file("examples/egi/srm"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val egiwmsExample  = Project(id = "egiwmsexample", base = file("examples/egi/wms"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val egiWebDAVExample  = Project(id = "egiwebdavexample", base = file("examples/egi/webdav"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val egiDiracExample  = Project(id = "egidiracexample", base = file("examples/egi/dirac"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val awsExample  = Project(id = "awsexample", base = file("examples/aws"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleAWS)
  lazy val condorExample = Project(id = "condorexample", base = file("examples/condor"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleCondor)
  lazy val slurmExample  = Project(id = "slurmexample", base = file("examples/slurm"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSLURM)
  lazy val sgeExample    = Project(id = "sgeexample", base = file("examples/sge"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSGE)
  lazy val sshExample  = Project(id = "sshexample", base = file("examples/ssh"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSSH)
  lazy val oarExample  = Project(id = "oarexample", base = file("examples/oar"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleOAR)
  lazy val httpExample  = Project(id = "httpexample", base = file("examples/http"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleHTTP)

  mainClass in SbtOneJar.oneJar := Some("fr.iscpif.gridscale.examples.Main")
}

trait Bundles <: Modules with Settings {
  self: Build =>

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

  lazy val gridscaleBundle = Project(id = "gridscalebundle", base = file("bundles/gridscale"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*) dependsOn (gridscale) settings (
    privatePackage := Seq("fr.iscpif.gridscale.*", "!scala.*"),
    exportPackage := Seq("fr.iscpif.gridscale.*"),
    name := "gridscale"
    )

  lazy val egiBundle = Project(id = "egibundle", base = file("bundles/egi"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleEGI) settings(
    name := "egi",
    importPackage := Seq("!org.glassfish.grizzly.*", "!org.jboss.*", "!com.google.protobuf.*", "!javax.*", "!com.google.common.util.*", "org.tukaani.xz.*;resolution:=optional", "org.apache.tools.ant.*;resolution:=optional", "*"),
    privatePackage := Seq("fr.iscpif.gridscale.libraries.*", "fr.iscpif.gridscale.globushttp.*", "!org.apache.http.*", "!org.apache.commons.codec.*") ++ privatePackage.value,
    exportPackage := exportPackage.value ++ Seq("org.glite.*", "org.globus.*", "org.ogf.*")
    )

  lazy val httpBundle = Project(id = "httpbundle", base = file("bundles/http"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleHTTP) settings (
    name := "http",
    importPackage := Seq("org.apache.tools.ant.*;resolution:=optional", "*"),
    privatePackage := Seq("org.apache.http.entity.mime.*", "!org.apache.http.*", "!org.apache.commons.codec.*") ++ privatePackage.value
    )

  lazy val sshBundle = Project(id = "sshbundle", base = file("bundles/ssh"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleSSH) settings(
    name := "ssh",
    importPackage := Seq("!javax.*", "*"),
    exportPackage := Seq("!net.schmizz.sshj.*", "!fr.iscpif.gridscale.ssh.impl.*") ++ exportPackage.value
    )

  lazy val awsBundle = Project(id = "awsbundle", base = file("bundles/aws"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleAWS) settings(
    name := "aws", noSSH
    )

  lazy val condorBundle = Project(id = "condorbundle", base = file("bundles/condor"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleCondor) settings(
    name := "condor", noSSH
    )

  lazy val pbsBundle = Project(id = "pbsbundle", base = file("bundles/pbs"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscalePBS) settings(
    name := "pbs", noSSH
    )

  lazy val slurmBundle = Project(id = "slurmbundle", base = file("bundles/slurm"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleSLURM) settings(
    name := "slurm", noSSH
    )

  lazy val sgeBundle = Project(id = "sgebundle", base = file("bundles/sge"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleSGE) settings(
    name := "sge", noSSH
    )

  lazy val oarBundle = Project(id = "oarbundle", base = file("bundles/oar"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleOAR) settings(
    name := "oar", noSSH
    )

}

trait Modules <: Libraries with Settings {

  lazy val httpComponentsVersion = "4.5.1"

  lazy val gridscale = Project(id = "gridscale", base = file("modules/gridscale"), settings = defaultSettings ++ exportSettings) settings(libraryDependencies += scalaTest)

  lazy val gridscaleEGI = Project(id = "egi", base = file("modules/gridscale-egi"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, wmsStub, lbStub, srmStub, globusHttp, gliteSecurityDelegation, gliteSecurityVoms, gridscaleHTTP) settings (
    libraryDependencies += "org.jglobus" % "io" % jglobusVersion,
    libraryDependencies += "io.spray" %% "spray-json" % "1.2.6",
    libraryDependencies += "org.apache.commons" % "commons-compress" % "1.10"
    )

  lazy val gridscaleHTTP = Project(id = "http", base = file("modules/gridscale-http"), settings = defaultSettings ++ exportSettings) dependsOn (gridscale) settings (
    libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1",
    libraryDependencies += "com.github.lookfirst" % "sardine" % "5.6",
    libraryDependencies += "org.apache.httpcomponents" % "httpclient-osgi" % httpComponentsVersion,
    libraryDependencies += "org.apache.httpcomponents" % "httpmime" % httpComponentsVersion
     )

  lazy val gridscaleSSH = Project(id = "ssh", base = file("modules/gridscale-ssh"), settings = defaultSettings ++ exportSettings) dependsOn (gridscale) settings (
    libraryDependencies += "net.schmizz" % "sshj" % "0.10.0",
    libraryDependencies += "com.jcraft" % "jzlib" % "1.1.3"
    )

  lazy val gridscaleAWS = Project(id = "aws", base = file("modules/gridscale-aws"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, gridscaleSSH) settings (
    libraryDependencies += scalaTest,
    libraryDependencies += mockito,
    libraryDependencies += "org.apache.jclouds" % "jclouds-all" % "1.9.2",
    libraryDependencies += "org.apache.jclouds.driver" % "jclouds-sshj" % "1.9.2",
    libraryDependencies += "org.apache.jclouds.driver" % "jclouds-log4j" % "1.9.2",
    libraryDependencies += "com.jcraft" % "jsch" % "0.1.53",
    libraryDependencies += "com.jsuereth" % "scala-arm_2.11" % "2.0.0-M1"
    )

  lazy val gridscaleCondor = Project(id = "gridscalecondor", base = file("modules/gridscale-condor"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, gridscaleSSH) settings (
    libraryDependencies ++= Seq(scalaTest, mockito)
    )

  lazy val gridscalePBS = Project(id = "pbs", base = file("modules/gridscale-pbs"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, gridscaleSSH)

  lazy val gridscaleSLURM = Project(id = "slurm", base = file("modules/gridscale-slurm"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, gridscaleSSH)  settings (
    libraryDependencies ++= Seq(scalaTest, mockito)
    )

  lazy val gridscaleSGE = Project(id = "sge", base = file("modules/gridscale-sge"), settings = defaultSettings ++ exportSettings)
                          .dependsOn(gridscale, gridscaleSSH)  settings (
    libraryDependencies ++= Seq(scalaTest, mockito)
    )

  lazy val gridscaleOAR = Project(id = "oar", base = file("modules/gridscale-oar"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, gridscaleSSH)


}


trait Libraries <: Settings {

  import sbt.Keys._
  import sbtscalaxb.Plugin.ScalaxbKeys._
  import sbtscalaxb.Plugin._

  lazy val jglobusVersion = "2.2.0-20150814"

  lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.1"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "2.2.1" % "test"

  lazy val mockito = "org.mockito" % "mockito-all" % "1.8.4" % "test"

  lazy val httpClient = "commons-httpclient" % "commons-httpclient" % "3.1"

  lazy val xml =
    libraryDependencies ++=
      (if (!scalaVersion.value.startsWith("2.10")) Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1")
      else Seq.empty)

  lazy val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.50"
  lazy val log4j = "log4j" % "log4j" % "1.2.17"

  lazy val srmStub = Project(id = "srmstub", base = file("libraries/srmstub"), settings = defaultSettings) settings (
    scalaxbSettings ++ Seq(
      async in (Compile, scalaxb) := false,
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.srmstub",
      libraryDependencies += dispatch, xml): _*
    )

  lazy val wmsStub = Project(id = "wmsstub", base = file("libraries/wmsstub"), settings = defaultSettings) settings (
    scalaxbSettings ++ Seq(
      async in (Compile, scalaxb) := false,
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.wmsstub",
      libraryDependencies += dispatch, xml): _*
    )

  lazy val lbStub = Project(id = "lbstub", base = file("libraries/lbstub"), settings = defaultSettings) settings (
    scalaxbSettings ++ Seq(
      async in (Compile, scalaxb) := false,
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.lbstub",
      wrapContents in scalaxb in Compile := Seq("{http://schemas.ogf.org/glue/2008/05/spec_2.0_d42_r01}ComputingService_t"),
      libraryDependencies += dispatch, xml): _*
    )


  lazy val globusHttp = Project(id = "globushttp", base = file("libraries/globushttp"), settings = defaultSettings) settings(
    resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/",
    libraryDependencies += "org.jglobus" % "ssl-proxies" % jglobusVersion,
    libraryDependencies += "org.jglobus" % "gss" % jglobusVersion,
    libraryDependencies += httpClient
    )

  lazy val gliteSecurityDelegation = Project(id = "glite-security-delegation", base = file("libraries/glite-security-delegation"), settings = defaultSettings) dependsOn(gliteSecurityUtil, gliteSecurityVoms) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j
    )

  lazy val gliteSecurityUtil = Project(id = "glite-security-util", base = file("libraries/glite-security-util"), settings = defaultSettings) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j
    )

  lazy val gliteSecurityVoms = Project(id = "glite-security-voms", base = file("libraries/glite-security-voms"), settings = defaultSettings) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j,
    libraryDependencies += "org.jglobus" % "myproxy" % jglobusVersion,
    libraryDependencies += "commons-lang" % "commons-lang" % "2.3",
    libraryDependencies += "commons-logging" % "commons-logging" % "1.1",
    libraryDependencies += "commons-cli" % "commons-cli" % "1.1"
    )

}
