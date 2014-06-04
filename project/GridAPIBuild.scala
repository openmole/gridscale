
import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys._
import com.typesafe.sbt.osgi.SbtOsgi._

object GridAPIBuild extends Build with Libraries with Modules with Examples with Bundles {

  override def settings = super.settings ++ Seq(
    resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/",
    organization := "fr.iscpif.gridscale",
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository := { _ => false},
    licenses := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/")),
    homepage := Some(url("https://github.com/romainreuillon/gridscale")),
    scmInfo := Some(ScmInfo(url("https://github.com/romainreuillon/gridscale.git"), "scm:git:git@github.com:romainreuillon/gridscale.git")),
    pomExtra := (
      <developers>
        <developer>
          <id>romainreuillon</id>
          <name>Romain Reuillon</name>
        </developer>
        <developer>
          <id>jopasserat</id>
          <name>Jonathan Passerat</name>
        </developer>
      </developers>
      )
  )

}

trait Examples <: Modules {
  lazy val gliteExample = Project(id = "gliteexample", base = file("examples/glite")) dependsOn (gridscaleGlite)
  lazy val diracExample = Project(id = "diracexample", base = file("examples/dirac")) dependsOn (gridscaleDIRAC)
  lazy val condorExample = Project(id = "condorexample", base = file("examples/condor")) dependsOn (gridscaleCondor)
  lazy val slurmExample = Project(id = "slurmexample", base = file("examples/slurm")) dependsOn (gridscaleSLURM)

}

trait Bundles <: Modules {
  self: Build =>

  lazy val gridscaleOsgiSettings =
    osgiSettings ++
      Seq(
        importPackage := Seq("*;resolution:=optional"),
        privatePackage := Seq(s"fr.iscpif.gridscale.${name.value}", "!fr.iscpif.gridscale.*", "!scala.*", "!org.bouncycastle.*", "!org.apache.log4j.*", "!org.slf4j.*", "!org.apache.commons.logging.*", "*"),
        organization := "fr.iscpif.gridscale.bundle",
        bundleSymbolicName := s"fr.iscpif.gridscale.${name.value}",
        exportPackage := Seq(s"${bundleSymbolicName.value}.*")
      )


  lazy val noSSH = privatePackage :=
    Seq("!net.schmizz.*", "!fr.iscpif.gridscale.ssh.*") ++ privatePackage.value

  lazy val gridscaleBundle = Project(id = "gridscalebundle", base = file("bundles/gridscale"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscale) settings (
    name := "griscale"
    )

  lazy val gliteBundle = Project(id = "glitebundle", base = file("bundles/glite"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscaleGlite) settings(
    name := "glite", exportPackage := exportPackage.value ++ Seq("org.glite.*", "org.globus.*", "org.ogf.*")
    )

  lazy val httpBundle = Project(id = "httpbundle", base = file("bundles/http"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscaleHttp) settings (
    name := "http"
    )

  lazy val diracBundle = Project(id = "diracbundle", base = file("bundles/dirac"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscaleDIRAC) settings (
    name := "dirac"
    )

  lazy val sshBundle = Project(id = "sshbundle", base = file("bundles/ssh"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscaleSSH) settings(
    name := "ssh",
    exportPackage := exportPackage.value ++ Seq("net.schmizz.sshj.*")
    )

  lazy val condorBundle = Project(id = "condorbundle", base = file("bundles/condor"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscaleCondor) settings(
    name := "condor", noSSH
    )

  lazy val pbsBundle = Project(id = "pbsbundle", base = file("bundles/pbs"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscalePBS) settings(
    name := "pbs", noSSH
    )

  lazy val slurmBundle = Project(id = "slurmbundle", base = file("bundles/slurm"), settings = self.settings ++ gridscaleOsgiSettings) dependsOn (gridscaleSLURM) settings(
    name := "slurm", noSSH
    )


}

trait Modules <: Libraries {

  lazy val gridscale = Project(id = "gridscale", base = file("modules/gridscale"))

  lazy val gridscaleGlite = Project(id = "gridscaleglite", base = file("modules/gridscale-glite")) dependsOn(gridscale, wmsStub, lbStub, srmStub, globusHttp, gliteSecurityDelegation) settings (
    libraryDependencies += "org.jglobus" % "io" % jglobusVersion
    )

  lazy val gridscaleHttp = Project(id = "gridscalehttp", base = file("modules/gridscale-http")) dependsOn (gridscale) settings (
    libraryDependencies += "org.htmlparser" % "htmlparser" % "2.1"
    )

  lazy val gridscaleDIRAC = Project(id = "gridscaledirac", base = file("modules/gridscale-dirac")) dependsOn (gridscale) settings(
    libraryDependencies += "io.spray" %% "spray-json" % "1.2.6",
    libraryDependencies += "org.scalaj" %% "scalaj-http" % "0.3.15"
    )

  lazy val gridscaleSSH = Project(id = "gridscalessh", base = file("modules/gridscale-ssh")) dependsOn (gridscale) settings (
    libraryDependencies += "net.schmizz" % "sshj" % "0.9.1-20140524"
    )

  lazy val gridscaleCondor = Project(id = "gridscalecondor", base = file("modules/gridscale-condor")) dependsOn(gridscale, gridscaleSSH)

  lazy val gridscalePBS = Project(id = "gridscalepbs", base = file("modules/gridscale-pbs")) dependsOn(gridscale, gridscaleSSH)

  lazy val gridscaleSLURM = Project(id = "gridscaleslurm", base = file("modules/gridscale-slurm")) dependsOn(gridscale, gridscaleSSH)

}


trait Libraries {

  import Keys._
  import sbtscalaxb.Plugin._
  import ScalaxbKeys._

  lazy val jglobusVersion = "2.1-20140524"
  lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.1"
  lazy val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.49"
  lazy val log4j = "log4j" % "log4j" % "1.2.17"

  lazy val srmStub = Project(id = "srmstub", base = file("libraries/srmstub")) settings (
    scalaxbSettings ++ Seq(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.srmstub",
      libraryDependencies += dispatch): _*
    )

  lazy val wmsStub = Project(id = "wmsstub", base = file("libraries/wmsstub")) settings (
    scalaxbSettings ++ Seq(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.wmsstub",
      libraryDependencies += dispatch
    ): _*
    )

  lazy val lbStub = Project(id = "lbstub", base = file("libraries/lbstub")) settings (
    scalaxbSettings ++ Seq(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in scalaxb in Compile := "fr.iscpif.gridscale.libraries.lbstub",
      wrapContents in scalaxb in Compile := Seq("{http://schemas.ogf.org/glue/2008/05/spec_2.0_d42_r01}ComputingService_t"),
      libraryDependencies += dispatch): _*
    )


  lazy val globusHttp = Project(id = "globushttp", base = file("libraries/globushttp")) settings(
    resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/",
    libraryDependencies += "org.jglobus" % "ssl-proxies" % jglobusVersion,
    libraryDependencies += "org.jglobus" % "gss" % jglobusVersion,
    libraryDependencies += "commons-httpclient" % "commons-httpclient" % "3.1"
    )

  lazy val gliteSecurityDelegation = Project(id = "glite-security-delegation", base = file("libraries/glite-security-delegation")) dependsOn(gliteSecurityUtil, gliteSecurityVoms) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j
    )

  lazy val gliteSecurityUtil = Project(id = "glite-security-util", base = file("libraries/glite-security-util")) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j
    )

  lazy val gliteSecurityVoms = Project(id = "glite-security-voms", base = file("libraries/glite-security-voms")) settings(
    libraryDependencies += bouncyCastle,
    libraryDependencies += log4j,
    libraryDependencies += "org.jglobus" % "myproxy" % jglobusVersion,
    libraryDependencies += "commons-lang" % "commons-lang" % "2.3",
    libraryDependencies += "commons-logging" % "commons-logging" % "1.1",
    libraryDependencies += "commons-cli" % "commons-cli" % "1.1"
    )

}