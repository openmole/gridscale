
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.0")
//addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.8.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

resolvers += Resolver.sonatypeRepo("public")

