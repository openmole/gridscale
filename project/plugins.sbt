addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.7.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("org.scala-sbt.plugins" % "sbt-onejar" % "0.8")

resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/"

resolvers += Resolver.sonatypeRepo("public")

