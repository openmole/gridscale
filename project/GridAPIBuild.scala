
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


trait Examples <: Modules with Settings{
  lazy val egicreamExample  = Project(id = "egicreamexample", base = file("examples/egi/cream"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val egiWebDAVExample  = Project(id = "egiwebdavexample", base = file("examples/egi/webdav"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val egiDiracExample  = Project(id = "egidiracexample", base = file("examples/egi/dirac"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleEGI)
  lazy val condorExample = Project(id = "condorexample", base = file("examples/condor"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleCondor)
  lazy val slurmExample  = Project(id = "slurmexample", base = file("examples/slurm"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSLURM)
  lazy val sgeExample    = Project(id = "sgeexample", base = file("examples/sge"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSGE)
  lazy val sshExample  = Project(id = "sshexample", base = file("examples/ssh"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleSSH)
  lazy val oarExample  = Project(id = "oarexample", base = file("examples/oar"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleOAR)
  lazy val httpExample  = Project(id = "httpexample", base = file("examples/http"), settings = defaultSettings ++ exportSettings) dependsOn (gridscaleHTTP)

  mainClass in SbtOneJar.oneJar := Some("fr.iscpif.gridscale.examples.Main")
}

trait Bundles <: Modules with Settings {self: Build =>

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
    name := "gridscale")

  lazy val egiBundle = Project(id = "egibundle", base = file("bundles/egi"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleEGI, gridscaleBundle, gridscaleHTTP) settings(
    name := "egi",
    importPackage := Seq("!org.glassfish.grizzly.*", "!org.jboss.*", "!com.google.protobuf.*", "!javax.*", "!com.google.common.util.*", "!sun.misc", "!org.tukaani.xz.*", "!org.apache.tools.ant.*", "*"),
    privatePackage := Seq("fr.iscpif.gridscale.libraries.*", "fr.iscpif.gridscale.globushttp.*", "!org.apache.http.*", "!org.apache.commons.codec.*") ++ privatePackage.value,
    exportPackage := exportPackage.value ++ Seq("org.glite.*", "org.globus.*", "org.ogf.*"))

  lazy val httpBundle = Project(id = "httpbundle", base = file("bundles/http"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleHTTP, gridscaleBundle) settings (
    name := "http",
    libraryDependencies += "org.apache.httpcomponents" % "httpcore-osgi" % "4.4.4",
    libraryDependencies += "org.osgi" % "org.osgi.compendium" % "4.2.0",
    importPackage := Seq("!javax.*", "!org.apache.tools.ant.*", "*"),
    privatePackage := Seq("org.apache.http.entity.mime.*", "!org.apache.http.*", "!org.apache.commons.codec.*") ++ privatePackage.value)

  lazy val sshBundle = Project(id = "sshbundle", base = file("bundles/ssh"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleSSH, gridscaleBundle) settings(
    name := "ssh",
    importPackage := Seq("!javax.*", "*"),
    exportPackage := Seq("!net.schmizz.sshj.*", "!fr.iscpif.gridscale.ssh.impl.*") ++ exportPackage.value)

  lazy val condorBundle = Project(id = "condorbundle", base = file("bundles/condor"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleCondor, gridscaleBundle) settings(
    name := "condor", noSSH)

  lazy val pbsBundle = Project(id = "pbsbundle", base = file("bundles/pbs"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscalePBS, gridscaleBundle) settings(
    name := "pbs", noSSH)

  lazy val slurmBundle = Project(id = "slurmbundle", base = file("bundles/slurm"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*) dependsOn (gridscaleSLURM, gridscaleBundle) settings(
    name := "slurm", noSSH)

  lazy val sgeBundle = Project(id = "sgebundle", base = file("bundles/sge"), settings = defaultSettings) enablePlugins(SbtOsgi)  settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleSGE, gridscaleBundle) settings(
    name := "sge", noSSH)

  lazy val oarBundle = Project(id = "oarbundle", base = file("bundles/oar"), settings = defaultSettings) enablePlugins(SbtOsgi) settings(gridscaleOsgiSettings:_*)  dependsOn (gridscaleOAR, gridscaleBundle) settings(
    name := "oar", noSSH)

}

trait Modules <: Libraries with Settings {

  lazy val httpComponentsVersion = "4.5.2"

  lazy val gridscale = Project(id = "gridscale", base = file("modules/gridscale"), settings = defaultSettings ++ exportSettings) settings(
    libraryDependencies += scalaTest,
    libraryDependencies += "org.scala-stm" %% "scala-stm" % "0.7"
    )

  lazy val gridscaleEGI = Project(id = "egi", base = file("modules/gridscale-egi"), settings = defaultSettings ++ exportSettings) dependsOn(gridscale, gliteSecurityVoms, gridscaleHTTP) settings (
    libraryDependencies += "fr.iscpif.jglobus" % "io" % jglobusVersion,
    libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.4.0",
    libraryDependencies += "org.apache.commons" % "commons-compress" % "1.10",
    libraryDependencies += "com.google.guava" % "guava" % "19.0"
    )

  lazy val gridscaleHTTP = Project(id = "http", base = file("modules/gridscale-http"), settings = defaultSettings ++ exportSettings) dependsOn (gridscale) settings (
    libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1",
    libraryDependencies += "com.github.lookfirst" % "sardine" % "5.6" excludeAll (ExclusionRule("org.apache.httpcomponents")),
    libraryDependencies += "org.apache.httpcomponents" % "httpclient-osgi" % httpComponentsVersion,
    libraryDependencies += "org.apache.httpcomponents" % "httpmime" % httpComponentsVersion)

  lazy val gridscaleSSH = Project(id = "ssh", base = file("modules/gridscale-ssh"), settings = defaultSettings ++ exportSettings) dependsOn (gridscale) settings (
    libraryDependencies += "net.schmizz" % "sshj" % "0.10.0",
    libraryDependencies += "com.jcraft" % "jzlib" % "1.1.3"
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

  lazy val jglobusVersion = "2.2.0-20160826"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "2.2.1" % "test"

  lazy val mockito = "org.mockito" % "mockito-all" % "1.8.4" % "test"

  lazy val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.50"
  lazy val log4j = "log4j" % "log4j" % "1.2.17"

  lazy val gliteSecurityVoms = Project(id = "glite-security-voms", base = file("libraries/glite-security-voms"), settings = defaultSettings) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j,
    libraryDependencies += "fr.iscpif.jglobus" % "myproxy" % jglobusVersion,
    libraryDependencies += "commons-lang" % "commons-lang" % "2.3",
    libraryDependencies += "commons-logging" % "commons-logging" % "1.1",
    libraryDependencies += "commons-cli" % "commons-cli" % "1.1")

}
