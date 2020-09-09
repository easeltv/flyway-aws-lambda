
lazy val flywayAwsLambda = (project in file(".")).settings(
  organization := "easeltv",
  name := "flyway-aws-lambda",
  version := "1.0.0",
  scalaVersion := "2.12.0",

  assemblyJarName := s"${name.value}-${version.value}.jar",
  test in assembly := {},

  libraryDependencies ++= Seq(
    // Flyway
    "org.flywaydb" % "flyway-core" % "6.0.7",
    "org.postgresql" % "postgresql" % "42.1.4",

    // AWS
    "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.799",

    // Json
    "io.spray" %%  "spray-json" % "1.3.2",
  )
)
