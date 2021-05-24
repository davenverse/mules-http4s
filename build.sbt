import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val catsV = "2.1.0"
val catsEffectV = "2.1.4"
val fs2V = "2.2.2"
val scodecV = "1.11.7"
val scodecCatsV = "1.0.0"
val http4sV = "0.21.6"
val circeV = "0.13.0"
val specs2V = "4.10.2"

val mulesV = "0.4.0"

val kindProjectorV = "0.11.0"
val betterMonadicForV = "0.3.1"

// Projects
lazy val `mules-http4s` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, scodec)

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "mules-http4s"
  )

lazy val scodec = project.in(file("scodec"))
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "mules-http4s-scodec",
    libraryDependencies ++= Seq(
    "org.scodec"                  %% "scodec-core"                % scodecV,
    "org.scodec"                  %% "scodec-cats"                % scodecCatsV,
    )
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings{
    import microsites._
    Seq(
      micrositeName := "mules-http4s",
      micrositeDescription := "Http4s Caching Implementation",
      micrositeAuthor := "Christopher Davenport",
      micrositeGithubOwner := "ChristopherDavenport",
      micrositeGithubRepo := "mules-http4s",
      micrositeBaseUrl := "/mules-http4s",
      micrositeDocumentationUrl := "https://www.javadoc.io/doc/io.chrisdavenport/mules-http4s_2.12",
      micrositeGitterChannelUrl := "ChristopherDavenport/libraries", // Feel Free to Set To Something Else
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      micrositeCompilingDocsTool := WithMdoc,
      scalacOptions in Tut --= Seq(
        "-Xfatal-warnings",
        "-Ywarn-unused-import",
        "-Ywarn-numeric-widen",
        "-Ywarn-dead-code",
        "-Ywarn-unused:imports",
        "-Xlint:-missing-interpolator,_"
      ),
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
          file("CODE_OF_CONDUCT.md")  -> ExtraMdFileConfig("code-of-conduct.md",   "page", Map("title" -> "code of conduct",   "section" -> "code of conduct",   "position" -> "100")),
          file("LICENSE")             -> ExtraMdFileConfig("license.md",   "page", Map("title" -> "license",   "section" -> "license",   "position" -> "101"))
      ),
      libraryDependencies ++= Seq(
        "io.chrisdavenport" %% "mules-caffeine" % mulesV,
        "org.http4s" %% "http4s-async-http-client" % http4sV,
      )
    )
  }

// General Settings
lazy val commonSettings = Seq(
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.10"),

  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),

  libraryDependencies ++= Seq(
    "org.typelevel"               %% "cats-core"                  % catsV,
    "org.typelevel"               %% "cats-effect"                % catsEffectV,
    
    "co.fs2"                      %% "fs2-core"                   % fs2V,
    "co.fs2"                      %% "fs2-io"                     % fs2V,

    "org.http4s"                  %% "http4s-server"              % http4sV,
    "org.http4s"                  %% "http4s-client"              % http4sV,

    "io.chrisdavenport"            %% "mules"                      % mulesV,
    "io.chrisdavenport"            %% "cats-effect-time"           % "0.1.0",
    "org.specs2"                  %% "specs2-core"                % specs2V       % Test,
    "org.specs2"                  %% "specs2-scalacheck"          % specs2V       % Test,
    "io.chrisdavenport"           %% "cats-scalacheck"            % "0.3.0"       % Test,
    "com.codecommit"              %% "cats-effect-testing-specs2" % "0.5.4"       %  Test,
    "org.http4s"                  %% "http4s-dsl"                 % http4sV       % Test,
  )
)

// General Settings
inThisBuild(List(
  organization := "io.chrisdavenport",
  developers := List(
    Developer("ChristopherDavenport", "Christopher Davenport", "chris@christopherdavenport.tech", url("https://github.com/ChristopherDavenport"))
  ),

  homepage := Some(url("https://github.com/ChristopherDavenport/mules-http4s")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),

  pomIncludeRepository := { _ => false},
  scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url", "https://github.com/ChristopherDavenport/mules-http4s/blob/v" + version.value + "€{FILE_PATH}.scala"
  )
))
