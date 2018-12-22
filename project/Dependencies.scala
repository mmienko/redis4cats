import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "1.1.0"
    val fs2        = "1.0.2"
    val lettuce    = "5.1.3.RELEASE"
    val log4cats   = "0.2.0"
    val logback    = "1.1.3"

    val betterMonadicFor = "0.3.0-M4"
    val kindProjector    = "0.9.9"

    val scalaTest  = "3.0.6-SNAP5"
    val scalaCheck = "1.14.0"
  }

  object Libraries {
    lazy val redisClient = "io.lettuce"    % "lettuce-core"   % Versions.lettuce
    lazy val catsEffect  = "org.typelevel" %% "cats-effect"   % Versions.catsEffect
    lazy val fs2Core     = "co.fs2"        %% "fs2-core"      % Versions.fs2

    lazy val log4cats      = "io.chrisdavenport" %% "log4cats-core"  % Versions.log4cats
    lazy val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j" % Versions.log4cats
    lazy val logback       = "ch.qos.logback" % "logback-classic" % Versions.logback

    // Compiler plugins
    lazy val betterMonadicFor = "com.olegpy"     %% "better-monadic-for" % Versions.betterMonadicFor
    lazy val kindProjector    = "org.spire-math" %% "kind-projector"     % Versions.kindProjector

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"  %% "scalatest"  % Versions.scalaTest
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck
  }

}
