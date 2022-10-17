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

object DependencyResolver {

  // copied from functionality
  // perform some dependency operations
  {

    // val groupedDependencies = Dependency.groupByRepository(dependencies)
    
    // // TODO get remote repositories, pass to dependency.prepare?
    // // groupedDependencies.foreach(r => r.fetch)

    // dependencies.foreach(d => d.prepare())


    // println(s"grouped dependencies: $groupedDependencies")
  }




  // Download the repo and return the path to the local dir where it is stored
  def cacheRepo(repo: Repository): Path = { Paths.get("") }

  // Modify the config so all of the dependencies are available locally
  def modifyConfig(config: Config): Config = {

    // Check all fun.repositories have valid names
    val repositories = config.functionality.repositories
    require(repositories.isEmpty || repositories.groupBy(r => r.name).map{ case(k, l) => l.length }.max == 1, "Repository names should be unique")
    require(repositories.filter(r => r.name.isEmpty()).length == 0, "Repository names can't be empty")


    // Convert all fun.dependency.repository with sugar syntax to full repositories
    // val repoRegex = raw"(\w+)://([A-Za-z0-9/_\-\.]+)@([A-Za-z0-9]+)".r  // TODO improve regex
    val repoRegex = raw"(\w+://[A-Za-z0-9/_\-\.]+@[A-Za-z0-9]*)".r
    val config1 = config.copy(functionality = config.functionality.copy(
      dependencies = config.functionality.dependencies.map(d => 
        d.repository match {
          case Left(repoRegex(s)) => d.copy(repository = Right(Repository.fromSugarSyntax(s)))
          case _ => d
        }

    )))

    // Check all remaining fun.dependency.repository names (Left) refering to fun.repositories can be matched
    val dependencyRepoNames = config1.functionality.dependencies.flatMap(_.repository.left.toOption)
    val definedRepoNames = config1.functionality.repositories.map(_.name)
    dependencyRepoNames.foreach(name =>
      require(definedRepoNames.contains(name), s"Named dependency repositories should exist in the list of repositories. '$name' not found.")
      )

    // Match repositories defined in dependencies by name to the list of repositories, fill in repository in dependency
    val config2 = config1.copy(
      functionality = config1.functionality.copy(
        dependencies = config1.functionality.dependencies.map(d => 
          d.repository match {
            case Left(name) => d.copy(repository = Right(config1.functionality.repositories.find(r => r.name == name).get))
            case _ => d
          }
        )
    ))

    // val actualRepo = 
    //   rawRepo match {
    //     case Left(repoRegex(protocol, repo, tag)) => 
    //       Repo(protocol, repo, tag)
    //     case Left(id) if id.matches("\\w+") =>
    //       throw new NotImplementedError("define lens")
    //     case Left(s) => throw new RuntimeException("unrecognised repo format " + s)
    //     case Right(r) => r
    //   }

    // get caches and store in repository classes
    config2.copy(
      // provide local cache for all repositories
      functionality = config2.functionality.copy(
        dependencies = config2.functionality.dependencies.map{ d =>
          // This should always be Either Right (Repository)
          val repo = d.repository.right.get
          val localRepoPath = cacheRepo(repo)
          d.copy(repository = Right(repo.copyRepo(localPath = localRepoPath.toString)))
        })
      )
  }
}
