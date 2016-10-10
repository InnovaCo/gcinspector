organization := "eu.inn"

name := "gc-info-experiment"

version := "0.1"

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-encoding", "UTF-8",
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

packSettings

packMain := Map("main" -> "eu.inn.gc.Main")

val params = Seq(
  "-Xms128m",
  "-Xmx128m",
//  "-XX:+UseG1GC",
  "-XX:+UseConcMarkSweepGC",
  "-XX:+UseParNewGC",
  "-XX:+UseGCLogFileRotation",
  "-XX:NumberOfGCLogFiles=10",
  "-XX:GCLogFileSize=50M",
  "-verbose:gc",
  "-Xloggc:gc.log",
  "-XX:+PrintGCDateStamps",
//  "-XX:+PrintGCDetails",
//  "-XX:+PrintHeapAtGC",
//  "-XX:+PrintTenuringDistribution",
  "-XX:+PrintGCApplicationStoppedTime"
//  "-XX:+PrintPromotionFailure",
//  "-XX:PrintFLSStatistics=1",
//  "-XX:+PrintSafepointStatistics"
)

packJvmOpts := Map("main" -> params)

packJarNameConvention := "full"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
)