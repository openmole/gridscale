

addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.2.1-SNAPSHOT")

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.7.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.3")

resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/"

resolvers += Resolver.sonatypeRepo("public")

scalariformSettings

