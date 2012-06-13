import java.util.Properties
import sbt._
import sbt.Keys._

object ScctPlugin extends Plugin {

  val scctReportDir = SettingKey[File]("scct-report-dir")

  lazy val Scct = config("scct")
  lazy val ScctTest = config("scct-test") extend Scct

  lazy val instrumentSettings =
    inConfig(Scct)(Defaults.compileSettings) ++
    inConfig(ScctTest)(Defaults.testSettings) ++
    Seq(
      scctReportDir <<= crossTarget / "coverage-report",

      ivyConfigurations ++= Seq(Scct, ScctTest),
      //resolvers += "scct repository" at "http://mtkopone.github.com/scct/maven-repo",

      libraryDependencies += "reaktor" %% "scct" % "0.2-SNAPSHOT" % "scct",

      sources in Scct <<= (sources in Compile),
      sourceDirectory in Scct <<= (sourceDirectory in Compile),
      scalacOptions in Scct <++= (name in Scct, baseDirectory in Scct) map { (n,b) => Seq(
        "-Xplugin:"+scctJarPath,
        "-P:scct:projectId:"+n,
        "-P:scct:basedir:"+b
      )},

      sources in ScctTest <<= (sources in Test),
      sourceDirectory in ScctTest <<= (sourceDirectory in Test),
      externalDependencyClasspath in ScctTest <<= Classpaths.concat(externalDependencyClasspath in ScctTest, externalDependencyClasspath in Test),

      internalDependencyClasspath in Scct <<= (internalDependencyClasspath in Compile),
      internalDependencyClasspath in ScctTest <<= (internalDependencyClasspath in Test, internalDependencyClasspath in ScctTest, classDirectory in Compile) map { (testDeps, scctDeps, oldClassDir) =>
        scctDeps ++ testDeps.filter(_.data != oldClassDir)
      },

      testOptions in ScctTest <+= (name in Scct, baseDirectory in Scct, scalaSource in Scct, classDirectory in ScctTest, scctReportDir) map { (n, base, src, testClassesDir, reportDir) =>
        Tests.Setup { () =>
          val props = new Properties()
          props.setProperty("scct.basedir", base.getAbsolutePath)
          props.setProperty("scct.report.hook", "system.property")
          props.setProperty("scct.project.name", n)
          props.setProperty("scct.report.dir", reportDir.getAbsolutePath)
          props.setProperty("scct.source.dir", src.getAbsolutePath)
          val out = testClassesDir / "scct.properties"
          IO.write(props, "Env for scct test run and report generation", out)
        }
      },
      testOptions in ScctTest <+= (state, name in Scct) map { (s, n) =>
        Tests.Cleanup { () =>
          val reportProperty = "scct.%s.fire.report".format(n)
          System.setProperty(reportProperty, "true")
          val maxSleep = compat.Platform.currentTime + 60L*1000L
          while (sys.props(reportProperty) != "done" && compat.Platform.currentTime < maxSleep) Thread.sleep(200L)
          if (sys.props(reportProperty) != "done") println("Timed out waiting for scct coverage report")
        }
      },

      // Sugar: copy test from ScctTest to Scct so you can use scct:test
      test in Scct <<= (test in ScctTest)
    )

  def scctJarPath = {
    val url = classOf[reaktor.scct.ScctInstrumentPlugin].getProtectionDomain().getCodeSource().getLocation()
    new File(url.toURI).getAbsolutePath
  }

}