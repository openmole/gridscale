
import com.typesafe.sbt.pgp.PgpKeys._

organization := "fr.iscpif"

name := "gridscale"

packagedArtifacts in file(".") := Map.empty

publish in file(".") := {}

publishSigned := {}

scalariformSettings

