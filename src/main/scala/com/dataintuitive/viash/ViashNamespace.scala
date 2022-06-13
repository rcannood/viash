/*
 * Copyright (C) 2020  Data Intuitive
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dataintuitive.viash

import java.nio.file.{Paths, Files, StandardOpenOption}
import com.dataintuitive.viash.ViashTest.{ManyTestOutput, TestOutput}
import config.Config
import helpers.IO
import com.dataintuitive.viash.helpers.MissingResourceFileException
import com.dataintuitive.viash.helpers.BuildStatus._
import java.nio.file.Path

object ViashNamespace {
  def build(
    configs: List[Either[Config, BuildStatus]],
    target: String,
    setup: Option[String] = None,
    push: Boolean = false,
    parallel: Boolean = false,
    writeMeta: Boolean = true,
    flatten: Boolean = false
  ) {
    val configs2 = if (parallel) configs.par else configs

    val results = configs2.map { config =>
      config match {
        case Right(_) => config
        case Left(conf) =>
          val funName = conf.functionality.name
          val platformId = conf.platform.get.id
          val out =
            if (!flatten) {
              conf.functionality.namespace
                .map( ns => target + s"/$platformId/$ns/$funName").getOrElse(target + s"/$platformId/$funName")
            } else {
              target
            }
          val namespaceOrNothing = conf.functionality.namespace.map( s => "(" + s + ")").getOrElse("")
          println(s"Exporting $funName $namespaceOrNothing =$platformId=> $out")
          ViashBuild(
            config = conf,
            output = out,
            namespace = conf.functionality.namespace,
            setup = setup,
            push = push,
            writeMeta = writeMeta
          )
          Right(helpers.BuildStatus.Success)
        }
      }

    printResults(results.map(r => r.fold(fa => helpers.BuildStatus.Success, fb => fb)).toList)
  }

  def test(
    configs: List[Either[Config, BuildStatus]],
    parallel: Boolean = false,
    keepFiles: Option[Boolean] = None,
    tsv: Option[String] = None,
    append: Boolean = false
  ): List[Either[(Config, ManyTestOutput), BuildStatus]] = {
    val configs2 = if (parallel) configs.par else configs

    // run all the component tests
    val tsvPath = tsv.map(Paths.get(_))

    // remove if not append
    for (tsv <- tsvPath if !append) {
      Files.deleteIfExists(tsv)
    }

    val tsvExists = tsvPath.exists(Files.exists(_))
    val tsvWriter = tsvPath.map(Files.newBufferedWriter(_, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND))

    val parentTempPath = IO.makeTemp("viash_ns_test")
    if (keepFiles.getOrElse(true)) {
      printf("The working directory for the namespace tests is %s\n", parentTempPath.toString())
    }
    
    try {
      // only print header if file does not exist
      for (writer <- tsvWriter if !tsvExists) {
        writer.write(
          List(
            "namespace",
            "functionality",
            "platform",
            "test_name",
            "exit_code",
            "duration",
            "result"
          ).mkString("\t") + sys.props("line.separator"))
        writer.flush()
      }
      printf(
        s"%s%20s %20s %20s %20s %9s %8s %20s%s\n",
        "",
        "namespace",
        "functionality",
        "platform",
        "test_name",
        "exit_code",
        "duration",
        "result",
        Console.RESET
      )

      val results = configs2.map { config =>
        config match {
          case Right(status) => Right(status)
          case Left(conf) =>
            // get attributes
            val namespace = conf.functionality.namespace.getOrElse("")
            val funName = conf.functionality.name
            val platName = conf.platform.get.id

            // print start message
            printf(s"%s%20s %20s %20s %20s %9s %8s %20s%s\n", "", namespace, funName, platName, "start", "", "", "", Console.RESET)

            // run tests
            // TODO: it would actually be great if this component could subscribe to testresults messages

            val ManyTestOutput(setupRes, testRes) = try {
              ViashTest(
                config = conf,
                keepFiles = keepFiles,
                quiet = true,
                parentTempPath = Some(parentTempPath)
              )
            } catch {
              case e: MissingResourceFileException => 
                System.err.println(s"${Console.YELLOW}viash ns: ${e.getMessage}${Console.RESET}")
                ManyTestOutput(None, List())
            }

            val testResults =
              if (setupRes.isDefined && setupRes.get.exitValue > 0) {
                Nil
              } else if (testRes.isEmpty) {
                List(TestOutput("tests", -1, "no tests found", "", 0L))
              } else {
                testRes
              }

            // print messages
            val results = setupRes.toList ::: testResults
            for (test ← results) {
              val (col, msg) = {
                if (test.exitValue > 0) {
                  (Console.RED, "ERROR")
                } else if (test.exitValue < 0) {
                  (Console.YELLOW, "MISSING")
                } else {
                  (Console.GREEN, "SUCCESS")
                }
              }

              // print message
              printf(s"%s%20s %20s %20s %20s %9s %8s %20s%s\n", col, namespace, funName, platName, test.name, test.exitValue, test.duration, msg, Console.RESET)

              // write to tsv
              tsvWriter.foreach{writer =>
                writer.append(List(namespace, funName, platName, test.name, test.exitValue, test.duration, msg).mkString("\t") + sys.props("line.separator"))
                writer.flush()
              }
            }

            // return output
            Left((conf, ManyTestOutput(setupRes, testRes)))
          }

      }.toList

      val testResults = results.flatMap(r => r.fold(fa => 
        {
          val setupRes = fa._2.setup
          val testRes = fa._2.tests
          val testStatus =
              if (setupRes.isDefined && setupRes.get.exitValue > 0) {
                Nil
              } else if (testRes.isEmpty) {
                List(TestMissing)
              } else {
                testRes.map(to => if (to.exitValue == 0) Success else TestError)
              }
          val setupStatus = setupRes.toList.map(to => if (to.exitValue == 0) Success else TestError)
          setupStatus ::: testStatus
        },
        fb => List(fb)))
      printResults(testResults)

      results
    } catch {
      case e: Exception => 
        println(e.getMessage())
        Nil
    } finally {
      tsvWriter.foreach(_.close())

      // Delete temp path if empty, otherwise fail quietly and keep.
      // (tests should have cleaned themselves according to the overall 'keep' value)
      if (!keepFiles.getOrElse(false)) {
        parentTempPath.toFile().delete()
      }

    }
  }

  def list(configs: List[Either[Config, BuildStatus]], format: String = "yaml") {
    val configs2 = configs.flatMap(_.left.toOption)
    ViashConfig.viewMany(configs2, format)
  }

  def printResults(statuses: Seq[BuildStatus]) {
    val parseErrors = statuses.count(_ == helpers.BuildStatus.ParseError)
    val disableds = statuses.count(_ == helpers.BuildStatus.Disabled)
    val buildErrors = statuses.count(_ == helpers.BuildStatus.BuildError)
    val testErrors = statuses.count(_ == helpers.BuildStatus.TestError)
    val testMissing = statuses.count(_ == helpers.BuildStatus.TestMissing)
    val successes = statuses.count(_ == helpers.BuildStatus.Success)

    if (successes != statuses.length) {
      println(s"${Console.YELLOW}Not all configs built successfully${Console.RESET}")
      if (parseErrors > 0) println(s"  ${Console.RED}${parseErrors}/${statuses.length} configs encountered parse errors${Console.RESET}")
      if (disableds > 0) println(s"  ${Console.YELLOW}${disableds}/${statuses.length} configs were disabled${Console.RESET}")
      if (buildErrors > 0) println(s"  ${Console.RED}${buildErrors}/${statuses.length} configs built failures${Console.RESET}") // Not currently implemented
      if (testErrors > 0) println(s"  ${Console.RED}${testErrors}/${statuses.length} tests failed${Console.RESET}")
      if (testMissing > 0) println(s"  ${Console.YELLOW}${testMissing}/${statuses.length} tests missing${Console.RESET}")
      if (successes > 0) println(s"  ${Console.GREEN}${successes}/${statuses.length} configs built successfully${Console.RESET}")
    }
    else {
      println(s"${Console.GREEN}All ${successes} configs built successfully${Console.RESET}")
    }
  }
}
