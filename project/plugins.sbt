
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.0.15")

//addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scala3-migrate" % "0.4.5")

resolvers += Resolver.sonatypeRepo("public")

