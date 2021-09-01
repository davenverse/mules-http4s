import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / crossScalaVersions := Seq("2.12.14", "2.13.6")

val catsV = "2.6.1"
val catsEffectV = "3.2.1"
val fs2V = "3.0.6"
val scodecCatsV = "1.1.0"
val http4sV = "0.23.2"
val circeV = "0.14.1"
val specs2V = "4.12.3"

val mulesV = "0.5.0-M1"

// Projects
lazy val `mules-http4s` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, scodec)

lazy val core = project.in(file("core"))
  .settings(
    name := "mules-http4s",
    libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,
      
      "co.fs2"                      %% "fs2-core"                   % fs2V,
      "co.fs2"                      %% "fs2-io"                     % fs2V,

      "org.http4s"                  %% "http4s-server"              % http4sV,
      "org.http4s"                  %% "http4s-client"              % http4sV,

      "io.chrisdavenport"            %% "mules"                      % mulesV,
      "io.chrisdavenport"            %% "cats-effect-time"           % "0.2.0",
      "com.comcast"            %% "ip4s-test-kit"             % "3.0.3"
    ) ++ testingDeps
  )

lazy val scodec = project.in(file("scodec"))
  .dependsOn(core % "compile->compile, test->test")
  .settings(
    name := "mules-http4s-scodec",
    libraryDependencies ++= Seq(
      "org.scodec"             %% "scodec-core"      % (if (isDotty.value) "2.0.0" else "1.11.8"),
      "org.scodec"                  %% "scodec-cats"                % scodecCatsV,
    ) ++ testingDeps
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DavenverseMicrositePlugin)
  .dependsOn(core)
  .settings{
    Seq(
      micrositeDescription := "Http4s Caching Implementation",
      libraryDependencies ++= Seq(
        "io.chrisdavenport" %% "mules-caffeine" % mulesV,
        "org.http4s" %% "http4s-async-http-client" % http4sV,
      )
    )
  }

lazy val testingDeps = Seq(
  "org.specs2"                  %% "specs2-core"                % specs2V       % Test,
  "org.specs2"                  %% "specs2-scalacheck"          % specs2V       % Test,
  "io.chrisdavenport"           %% "cats-scalacheck"            % "0.3.1"       % Test,
  "org.typelevel"              %% "cats-effect-testing-specs2" % "1.2.0"       %  Test,
  "org.http4s"                  %% "http4s-dsl"                 % http4sV       % Test,
)
