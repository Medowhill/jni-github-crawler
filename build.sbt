ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "info.hjaem"
ThisBuild / organizationName := "hjaem"

lazy val root = (project in file("."))
  .settings(
    name := "github",
    libraryDependencies += "commons-io" % "commons-io" % "2.6",
    libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
    libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"
  )
