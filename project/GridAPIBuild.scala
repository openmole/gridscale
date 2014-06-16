
import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys._
import com.typesafe.sbt.osgi.SbtOsgi._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

object GridAPIBuild extends Build with Libraries with Modules with Examples with Bundles


trait Settings <: Build {

  override def settings = super.settings ++ Seq (
    scalaVersion := "2.11.1",
    crossScalaVersions := Seq("2.10.4", "2.11.1")
  )



  lazy val defaultSettings =
    settings ++
      scalariformSettings ++ Seq(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(RewriteArrowSymbols, true),
    organization := "fr.iscpif.gridscale",
    resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    publishTo <<= isSnapshot { snapshot =>
      val nexus = "https://oss.sonatype.org/"
      if (snapshot) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository := { _ => false},
    licenses := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/")),
    homepage := Some(url("https://github.com/romainreuillon/gridscale")),
    scmInfo := Some(ScmInfo(url("https://github.com/romainreuillon/gridscale.git"), "scm:git:git@github.com:romainreuillon/gridscale.git")),
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
          <id>jonathanpasserat</id>
          <name>Jonathan Passert</name>
          <url>https://github.com/jopasserat/</url>
        </developer>
      </developers>
    }
  )

}


trait Examples <: Modules with Settings{
  lazy val gliteExample = Project(id = "gliteexample", base = file("examples/glite"), settings = defaultSettings) dependsOn (gridscaleGlite)
  lazy val diracExample = Project(id = "diracexample", base = file("examples/dirac"), settings = defaultSettings) dependsOn (gridscaleDIRAC)
  lazy val condorExample = Project(id = "condorexample", base = file("examples/condor"), settings = defaultSettings) dependsOn (gridscaleCondor)
  lazy val slurmExample = Project(id = "slurmexample", base = file("examples/slurm"), settings = defaultSettings) dependsOn (gridscaleSLURM)

}

trait Bundles <: Modules with Settings {
  self: Build =>

  lazy val gridscaleOsgiSettings =
    osgiSettings ++
      Seq(
        importPackage := Seq("*;resolution:=optional"),
        privatePackage := Seq(s"fr.iscpif.gridscale.${name.value}.*", "!fr.iscpif.gridscale.*", "!scala.*", "!org.bouncycastle.*", "!org.apache.log4j.*", "!org.slf4j.*", "!org.apache.commons.logging.*", "*"),
        organization := "fr.iscpif.gridscale.bundle",
        bundleSymbolicName := s"fr.iscpif.gridscale.${name.value}",
        exportPackage := Seq(s"${bundleSymbolicName.value}.*")
      )


  lazy val noSSH = privatePackage :=
    Seq("!net.schmizz.*", "!fr.iscpif.gridscale.ssh.*") ++ privatePackage.value

  lazy val gridscaleBundle = Project(id = "gridscalebundle", base = file("bundles/gridscale"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscale) settings (
    privatePackage := Seq("fr.iscpif.gridscale.*", "!scala.*"),
    exportPackage := Seq("fr.iscpif.gridscale.*"),
    name := "gridscale"
    )

  lazy val gliteBundle = Project(id = "glitebundle", base = file("bundles/glite"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscaleGlite) settings(
    name := "glite",
    privatePackage := Seq("fr.iscpif.gridscale.libraries.*", "fr.iscpif.gridscale.globushttp.*") ++ privatePackage.value,
    exportPackage := exportPackage.value ++ Seq("org.glite.*", "org.globus.*", "org.ogf.*")
    )

  lazy val httpBundle = Project(id = "httpbundle", base = file("bundles/http"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscaleHttp) settings (
    name := "http"
    )

  lazy val diracBundle = Project(id = "diracbundle", base = file("bundles/dirac"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscaleDIRAC) settings (
    name := "dirac"
    )

  lazy val sshBundle = Project(id = "sshbundle", base = file("bundles/ssh"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscaleSSH) settings(
    name := "ssh",
    exportPackage := exportPackage.value ++ Seq("net.schmizz.sshj.*")
    )

  lazy val condorBundle = Project(id = "condorbundle", base = file("bundles/condor"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscaleCondor) settings(
    name := "condor", noSSH
    )

  lazy val pbsBundle = Project(id = "pbsbundle", base = file("bundles/pbs"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscalePBS) settings(
    name := "pbs", noSSH
    )

  lazy val slurmBundle = Project(id = "slurmbundle", base = file("bundles/slurm"), settings = defaultSettings ++ gridscaleOsgiSettings) dependsOn (gridscaleSLURM) settings(
    name := "slurm", noSSH
    )


}

trait Modules <: Libraries with Settings {

  lazy val gridscale = Project(id = "gridscale", base = file("modules/gridscale"), settings = defaultSettings)

  lazy val gridscaleGlite = Project(id = "gridscaleglite", base = file("modules/gridscale-glite"), settings = defaultSettings) dependsOn(gridscale, wmsStub, lbStub, srmStub, globusHttp, gliteSecurityDelegation) settings (
    libraryDependencies += "org.jglobus" % "io" % jglobusVersion
    )

  lazy val gridscaleHttp = Project(id = "gridscalehttp", base = file("modules/gridscale-http"), settings = defaultSettings) dependsOn (gridscale) settings (
    libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1"
    )

  lazy val gridscaleDIRAC = Project(id = "gridscaledirac", base = file("modules/gridscale-dirac"), settings = defaultSettings) dependsOn (gridscale) settings(
    libraryDependencies += "io.spray" %% "spray-json" % "1.2.6",
    libraryDependencies += "org.scalaj" %% "scalaj-http" % "0.3.15"
    )

  lazy val gridscaleSSH = Project(id = "gridscalessh", base = file("modules/gridscale-ssh"), settings = defaultSettings) dependsOn (gridscale) settings (
    libraryDependencies += "net.schmizz" % "sshj" % "0.9.1-20140524"
    )

  lazy val gridscaleCondor = Project(id = "gridscalecondor", base = file("modules/gridscale-condor"), settings = defaultSettings) dependsOn(gridscale, gridscaleSSH)

  lazy val gridscalePBS = Project(id = "gridscalepbs", base = file("modules/gridscale-pbs"), settings = defaultSettings) dependsOn(gridscale, gridscaleSSH)

  lazy val gridscaleSLURM = Project(id = "gridscaleslurm", base = file("modules/gridscale-slurm"), settings = defaultSettings) dependsOn(gridscale, gridscaleSSH)

}


trait Libraries <: Settings {

  import Keys._
  import sbtscalaxb.Plugin._
  import ScalaxbKeys._

  lazy val jglobusVersion = "2.1-20140524"

  lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.1"

  lazy val xml =
    libraryDependencies ++=
      (if (!scalaVersion.value.startsWith("2.10")) Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1")
      else Seq.empty)

  lazy val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.49"
  lazy val log4j = "log4j" % "log4j" % "1.2.17"

  lazy val srmStub = Project(id = "srmstub", base = file("libraries/srmstub"), settings = defaultSettings) settings (
    scalaxbSettings ++ Seq(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.srmstub",
      libraryDependencies += dispatch, xml): _*
    )

  lazy val wmsStub = Project(id = "wmsstub", base = file("libraries/wmsstub"), settings = defaultSettings) settings (
    scalaxbSettings ++ Seq(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.wmsstub",
      libraryDependencies += dispatch, xml): _*
    )

  lazy val lbStub = Project(id = "lbstub", base = file("libraries/lbstub"), settings = defaultSettings) settings (
    scalaxbSettings ++ Seq(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.lbstub",
      wrapContents in scalaxb in Compile := Seq("{http://schemas.ogf.org/glue/2008/05/spec_2.0_d42_r01}ComputingService_t"),
      libraryDependencies += dispatch, xml): _*
    )


  lazy val globusHttp = Project(id = "globushttp", base = file("libraries/globushttp"), settings = defaultSettings) settings(
    resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/",
    libraryDependencies += "org.jglobus" % "ssl-proxies" % jglobusVersion,
    libraryDependencies += "org.jglobus" % "gss" % jglobusVersion,
    libraryDependencies += "commons-httpclient" % "commons-httpclient" % "3.1"
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