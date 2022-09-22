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

package io.viash.functionality.dependencies

import java.nio.file.{Path, Paths}
import io.viash.config.Config
import io.viash.config.Config._

case class Dependency(
  name: String,
  repository: Either[Repository, String]
) {
  var linkedRepository: Option[Repository] = None
  def workRepository: Repository = {
    // TODO evaluate required code for 'on the fly' creation of the repo from dependency data
    if (linkedRepository.isDefined)
      linkedRepository.get
    else
      LocalRepository()
  }

}

object Dependency {
  def groupByRepository(dependencies: Seq[Dependency]) = {
    dependencies.groupBy(_.workRepository)
  }

}