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

package io.viash.functionality.arguments

import io.viash.helpers.Circe.OneOrMore
import io.viash.schemas._

@description("An `integer` type argument has a numeric value without decimal points.")
@example(
  """arguments:
    |  - name: --core_amount
    |    type: integer
    |    default: 16
    |    description: Amount of CPU cores to use
    |    alternatives: ["-c"]
    |""".stripMargin,
    "yaml")
case class IntegerArgument(
  @description(
    """The name of the argument. Can be in the formats `--foo`, `-f` or `foo`. The number of dashes determines how values can be passed:  
      |
      |  - `--foo` is a long option, which can be passed with `executable_name --foo=value` or `executable_name --foo value`
      |  - `-f` is a short option, which can be passed with `executable_name -f value`
      |  - `foo` is an argument, which can be passed with `executable_name value`  
      |""".stripMargin)
  name: String,

  @description("List of alternative format variations for this argument.")
  alternatives: OneOrMore[String] = Nil,

  @description("A description of the argument. This will be displayed with `--help`.")
  description: Option[String] = None,

  @description("An example value for this argument. If no [`default`](#default) property was specified, this will be used for that purpose.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  example: 100
      |""".stripMargin,
      "yaml")
  example: OneOrMore[Int] = Nil,

  @description("The default value when no argument value is provided. This will not work if the [`required`](#required) property is enabled.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  default: 100
      |""".stripMargin,
      "yaml")
  default: OneOrMore[Int] = Nil,

  @description("Make the value for this argument required. If set to `true`, an error will be produced if no value was provided. `false` by default.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  required: true
      |""".stripMargin,
      "yaml")
  required: Boolean = false,

  @description("Limit the amount of valid values for this argument to those set in this list. When set and a value not present in the list is provided, an error will be produced.")
  @example(
    """- name: --memory
      |  type: integer
      |  choices: [1024, 2048, 4096]
      |""".stripMargin,
      "yaml")
  choices: List[Int] = Nil,

  @description("Minimum allowed value for this argument. If set and the provided value is lower than the minimum, an error will be produced. Can be combined with [`max`](#max) to clamp values.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  min: 50
      |""".stripMargin,
      "yaml")
  min: Option[Int] = None,

  @description("Maximum allowed value for this argument. If set and the provided value is higher than the maximum, an error will be produced. Can be combined with [`min`](#min) to clamp values.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  max: 150
      |""".stripMargin,
      "yaml")  
  max: Option[Int] = None,


  direction: Direction = Input,

  @description("Treat the argument value as an array. Arrays can be passed using the delimiter `--foo=1:2:3` or by providing the same argument multiple times `--foo 1 --foo 2`. You can use a custom delimiter by using the [`multiple_sep`](#multiple_sep) property. `false` by default.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  multiple: true
      |""".stripMargin,
      "yaml")
  @exampleWithDescription("my_component --my_integer=10:80:152", "bash", "Here's an example of how to use this:")
  multiple: Boolean = false,

  @description("The delimiter character for providing [`multiple`](#multiple) values. `:` by default.")
  @example(
    """- name: --my_integer
      |  type: integer
      |  multiple: true
      |  multiple_sep: ","
      |""".stripMargin,
      "yaml")
  @exampleWithDescription("my_component --my_integer=10:80:152", "bash", "Here's an example of how to use this:")
  multiple_sep: Char = ':',

  `type`: String = "integer"
) extends Argument[Int] {
  def copyArg(
    `type`: String, 
    name: String, 
    alternatives: OneOrMore[String],
    description: Option[String],
    example: OneOrMore[Int],
    default: OneOrMore[Int],
    required: Boolean,
    direction: Direction,
    multiple: Boolean,
    multiple_sep: Char
  ): Argument[Int] = {
    copy(name, alternatives, description, example, default, required, this.choices, this.min, this.max, direction, multiple, multiple_sep, `type`)
  }
}
