
import SonatypeKeys._

import com.typesafe.sbt.pgp.PgpKeys._

sonatypeSettings

organization := "fr.iscpif"

name := "gridscale"

packagedArtifacts in file(".") := Map.empty

publish in file(".") := {}

publishSigned := {}

scalariformSettings

