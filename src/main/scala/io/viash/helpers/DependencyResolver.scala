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

package io.viash.helpers

import java.nio.file.{ Path, Paths }
import io.viash.functionality.dependencies.Repository
import io.viash.config.Config
import io.viash.lenses.ConfigLenses._
import io.viash.lenses.FunctionalityLenses._
import io.viash.lenses.RepositoryLens._
import io.viash.functionality.dependencies.GithubRepository
import java.nio.file.Files
import java.io.IOException
import java.io.UncheckedIOException
import io.viash.platforms.Platform
import io.viash.helpers.IO

object DependencyResolver {

  // Modify the config so all of the dependencies are available locally
  def modifyConfig(config: Config, maxRecursionDepth: Integer = 10): Config = {

    // Check recursion level
    require(maxRecursionDepth >= 0, "Not all dependencies evaluated as the recursion is too deep")

    // Check all fun.repositories have valid names
    val repositories = config.functionality.repositories
    require(repositories.isEmpty || repositories.groupBy(r => r.name).map{ case(k, l) => l.length }.max == 1, "Repository names should be unique")
    require(repositories.filter(r => r.name.isEmpty()).length == 0, "Repository names can't be empty")


    // Convert all fun.dependency.repository with sugar syntax to full repositories
    // val repoRegex = raw"(\w+)://([A-Za-z0-9/_\-\.]+)@([A-Za-z0-9]+)".r  // TODO improve regex
    val repoRegex = raw"(\w+://[A-Za-z0-9/_\-\.]+@[A-Za-z0-9]*)".r
    val config1 = composedDependenciesLens.modify(_.map(d =>
        d.repository match {
          case Left(repoRegex(s)) => d.copy(repository = Right(Repository.fromSugarSyntax(s)))
          case _ => d
        }
      ))(config)

    // Check all remaining fun.dependency.repository names (Left) refering to fun.repositories can be matched
    val dependencyRepoNames = composedDependenciesLens.get(config1).flatMap(_.repository.left.toOption)
    val definedRepoNames = composedRepositoriesLens.get(config1).map(_.name)
    dependencyRepoNames.foreach(name =>
      require(definedRepoNames.contains(name), s"Named dependency repositories should exist in the list of repositories. '$name' not found.")
    )

    // Match repositories defined in dependencies by name to the list of repositories, fill in repository in dependency
    val config2 = composedDependenciesLens.modify(_
      .map(d => 
        d.repository match {
          case Left(name) => d.copy(repository = Right(composedRepositoriesLens.get(config1).find(r => r.name == name).get))
          case _ => d
        }
      )
      )(config1)

    // get caches and store in repository classes
    val config3 = composedDependenciesLens.modify(_
      .map{d =>
        val repo = d.repository.toOption.get
        val localRepoPath = Repository.cache(repo)
        d.copy(repository = Right(localRepoPath))
      }
      )(config2)

    // find the referenced config in the locally cached repository
    val config4 = composedDependenciesLens.modify(_
      .map{dep =>
        val repo = dep.workRepository.get
        // val dependencyConfig = findConfig(repo.localPath, dep.name)
        // val configPath = dependencyConfig.flatMap(_.info).map(_.config)
        // dep.copy(foundConfigPath = configPath, workConfig = dependencyConfig)
        // TODO match platform
        val targetPath = Paths.get(repo.localPath.stripPrefix("/"), repo.path.getOrElse("").stripPrefix("/"))
        val config = findConfig2(targetPath.toString(), dep.name)
        dep.copy(foundConfigPath = config.map(_._1), configInfo = config.map(_._2).getOrElse(Map.empty))
      }
      )(config3)

    config4
    
    // recurse through our dependencies to solve their dependencies
    // composedDependenciesLens.modify(_
    //   .map{dep =>
    //     dep.workConfig match {
    //       case Some(depConf) =>
    //         dep.copy(workConfig = Some(modifyConfig(depConf, maxRecursionDepth - 1)))
    //       case _ =>
    //         dep
    //     }
    //   }
    //   )(config4)
  }

  def copyDependencies(config: Config, output: String, platform: Option[Platform]): Config = {
    composedDependenciesLens.modify(_.map(dep => {
      // copy the dependency to the output folder
      val dependencyOutputPath = Paths.get(output, dep.name)
      if (dependencyOutputPath.toFile().exists())
        IO.deleteRecursively(dependencyOutputPath)
      Files.createDirectories(dependencyOutputPath)
      
      val platformId = platform.map(_.id).getOrElse("")
      // val dependencyRepoPath = Paths.get(dep.workRepository.get.localPath.stripPrefix("/"), dep.workRepository.get.path.getOrElse("").stripPrefix("/"), "target", platformId, dep.name)
      val dependencyRepoPath = Paths.get(dep.foundConfigPath.get).getParent()

      IO.copyFolder(dependencyRepoPath, dependencyOutputPath)

      // more recursion for the dependencies of dependencies
      // copyDependencies(dep.workConfig.get, output, platform)

      // Store location of the copied files
      dep.copy(writtenPath = Some(dependencyOutputPath.toString()))
    }))(config)
  }

  def findConfig(path: String, name: String): Option[Config] = {
    // search for configs in the repository and filter by namespace/name
    val configs = Config.readConfigs(
          source = path,
          query = Some(s"^$name$$"),
          queryNamespace = None,
          queryName = None,
          configMods = Nil,
          addOptMainScript = false
        )
    configs.flatMap(_.swap.toOption).headOption
  }

  def findConfig2(path: String, name: String): Option[(String, Map[String, String])] = {
    val scriptFiles = IO.find(Paths.get(path), (path, attrs) => {
      path.toString.contains(".vsh.") &&
        path.toFile.getName.startsWith(".") &&
        attrs.isRegularFile
    })

    val scriptInfo = scriptFiles.map(scriptPath => {
      val info = getSparseConfigInfo(scriptPath.toString())
      (scriptPath, info)
    })

    val script = scriptInfo.filter{
        case(scriptPath, info) => 
          (info("functionalityNamespace"), info("functionalityName")) match {
            case (ns, n) if !ns.isEmpty() => s"$ns/$n" == name
            case (_, n) => n == name
          }
      }.headOption

    script.map(t => (t._1.toString(), t._2))
  }

  def getSparseConfigInfo(configPath: String): Map[String, String] = {

    import io.circe.yaml.parser
    import io.circe.Json
    import io.viash.helpers.circe._

    /* STRING */
    // read yaml as string
    val (yamlText, _) = Config.readYAML(configPath)
    
    /* JSON 0: parsed from string */
    // parse yaml into Json
    def parsingErrorHandler[C](e: Exception): C = {
      Console.err.println(s"${Console.RED}Error parsing '${configPath}'.${Console.RESET}\nDetails:")
      throw e
    }
    val json0 = parser.parse(yamlText).fold(parsingErrorHandler, identity)

    /* JSON 1: after inheritance */
    // apply inheritance if need be
    val json1 = json0.inherit(IO.uri(configPath))


    def getFunctionalityName(json: Json): Option[String] = {
      json.hcursor.downField("functionality").downField("name").as[String].toOption
    }
    def getFunctionalityNamespace(json: Json): Option[String] = {
      json.hcursor.downField("functionality").downField("namespace").as[String].toOption
    }
    def getInfo(json: Json): Option[Map[String, String]] = {
      json.hcursor.downField("info").as[Map[String, String]].toOption
    }

    val functionalityName = getFunctionalityName(json1)
    val functonalityNamespace = getFunctionalityNamespace(json1)
    val info = getInfo(json1).getOrElse(Map.empty) +
      ("functionalityName" -> functionalityName.getOrElse("")) +
      ("functionalityNamespace" -> functonalityNamespace.getOrElse(""))

    println(s"name: $functionalityName, namespace: $functonalityNamespace, info:")
    info.foreach{ case (k, v) => println(s"  $k -> $v")}

    info
  }
}
