

addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.2.0-SNAPSHOT")

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.7.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.2.1")

resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/"

resolvers += Resolver.sonatypeRepo("public")

scalariformSettings

