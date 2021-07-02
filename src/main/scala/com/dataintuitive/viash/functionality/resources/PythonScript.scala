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

package com.dataintuitive.viash.functionality.resources

import com.dataintuitive.viash.functionality._
import com.dataintuitive.viash.functionality.dataobjects._

import java.net.URI

case class PythonScript(
  path: Option[String] = None,
  text: Option[String] = None,
  dest: Option[String] = None,
  is_executable: Option[Boolean] = Some(true),
  parent: Option[URI] = None,
  oType: String = "python_script"
) extends Script {
  val meta = PythonScript
  def copyResource(path: Option[String], text: Option[String], dest: Option[String], is_executable: Option[Boolean], parent: Option[URI]): Resource = {
    copy(path = path, text = text, dest = dest, is_executable = is_executable, parent = parent)
  }

  def generatePlaceholder(functionality: Functionality): String = {
    val params = functionality.arguments.filter(d => d.direction == Input || d.isInstanceOf[FileObject])

    val par_set = params.map { par =>
      val env_name = par.VIASH_PAR

      val parse = par match {
        case o: BooleanObject if o.multiple =>
          s"""list(map(lambda x: (x.lower() == 'true'), '$$$env_name'.split('${o.multiple_sep}')))"""
        case o: IntegerObject if o.multiple =>
          s"""list(map(int, '$$$env_name'.split('${o.multiple_sep}')))"""
        case o: DoubleObject if o.multiple =>
          s"""list(map(float, '$$$env_name'.split('${o.multiple_sep}')))"""
        case o: FileObject if o.multiple =>
          s"""'$$$env_name'.split('${o.multiple_sep}')"""
        case o: StringObject if o.multiple =>
          s"""'$$$env_name'.split('${o.multiple_sep}')"""
        case _: BooleanObject => s"""'$$$env_name'.lower() == 'true'"""
        case _: IntegerObject => s"""int('$$$env_name')"""
        case _: DoubleObject => s"""float('$$$env_name')"""
        case _: FileObject => s"""'$$$env_name'"""
        case _: StringObject => s"""'$$$env_name'"""
      }

      s"""'${par.plainName}': $$VIASH_DOLLAR$$( if [ ! -z $${$env_name+x} ]; then echo "$parse"; else echo None; fi )"""
    }
    s"""par = {
       |  ${par_set.mkString(",\n  ")}
       |}
       |
       |resources_dir = '$$VIASH_RESOURCES_DIR'
       |""".stripMargin
  }
}

object PythonScript extends ScriptObject {
  val commentStr = "#"
  val extension = "py"
  val oType = "python_script"

  def command(script: String): String = {
    "python \"" + script + "\""
  }

  def commandSeq(script: String): Seq[String] = {
    Seq("python", script)
  }
}