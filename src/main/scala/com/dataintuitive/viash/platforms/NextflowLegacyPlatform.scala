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

package com.dataintuitive.viash.platforms

import com.dataintuitive.viash.config.Config
import com.dataintuitive.viash.functionality._
import com.dataintuitive.viash.functionality.resources._
import com.dataintuitive.viash.functionality.arguments._
import com.dataintuitive.viash.config.Version
import com.dataintuitive.viash.helpers.{Docker, Bash}
import com.dataintuitive.viash.helpers.Circe._
import com.dataintuitive.viash.helpers.description
import com.dataintuitive.viash.helpers.example

/**
 * / * Platform class for generating NextFlow (DSL2) modules.
 */
@description("Run a Viash component as a Nextflow module.")
case class NextflowLegacyPlatform(
  @description("Every platform can be given a specific id that can later be referred to explicitly when running or building the Viash component.")
  id: String = "nextflow",

  @description("""If no image attributes are configured, Viash will use the auto-generated image name from the Docker platform:
                 |
                 |```
                 |[<namespace>/]<name>:<version>
                 |```
                 |It’s possible to specify the container image explicitly with which to run the module in different ways:
                 |
                 |```
                 |image: dataintuitive/viash:0.4.0
                 |```
                 |Exactly the same can be obtained with
                 |
                 |```
                 |image: dataintuitive/viash
                 |registry: index.docker.io/v1/
                 |tag: 0.4.0
                 |```
                 |Specifying the attribute(s) like this will use the container `dataintuitive/viash:0.4.0` from Docker hub (registry).
                 |
                 |If no tag is specified Viash will use `functionality.version` as the tag.
                 |
                 |If no registry is specified, Viash (and NextFlow) will assume the image is available locally or on Docker Hub. In other words, the `registry: ...` attribute above is superfluous. No other registry is checked automatically due to a limitation from Docker itself.
                 |""".stripMargin)
  image: Option[String],

  @description("Specify a Docker image based on its tag.")
  @example("tag: 4.0", "yaml")
  tag: Option[Version] = None,
  version: Option[Version] = None,

  @description("The URL to the a [custom Docker registry](https://docs.docker.com/registry/).")
  @example("registry: https://my-docker-registry.org", "yaml")
  registry: Option[String] = None,

  @description("Name of a container’s [organization](https://docs.docker.com/docker-hub/orgs/).")
  @example("organization: viash-io", "yaml")
  organization: Option[String] = None,

  @description("The default namespace separator is \"_\".")
  @example("namespace_separator: \"+\"", "yaml")
  namespace_separator: String = "_",
  executor: Option[String] = None,

  @description("""NextFlow uses the autogenerated `work` dirs to manage process IO under the hood. In order effectively output something one can publish the results a module or step in the pipeline. In order to do this, add `publish: true` to the config:
                 |
                 | - publish is optional
                 | - Default value is false
                 |This attribute simply defines if output of a component should be published yes or no. The output location has to be provided at pipeline launch by means of the option `--publishDir ...` or as `params.publishDir` in `nextflow.config`:
                 |```
                 |params.publishDir = "..."
                 |```
                 |""".stripMargin)
  publish: Option[Boolean] = None,

  @description("""By default, a subdirectory is created corresponding to the unique ID that is passed in the triplet. Let us illustrate this with an example. The following code snippet uses the value of `--input` as an input of a workflow. The input can include a wildcard so that multiple samples can run in parallel. We use the parent directory name (`.getParent().baseName`) as an identifier for the sample. We pass this as the first entry of the triplet:
                 |
                 |```
                 |Channel.fromPath(params.input) \
                 |    | map{ it -> [ it.getParent().baseName , it ] } \
                 |    | map{ it -> [ it[0] , it[1], params ] }
                 |    | ...
                 |```
                 |Say the resulting sample names are `SAMPLE1` and `SAMPLE2`. The next step in the pipeline will be published (at least by default) under:
                 |```
                 |<publishDir>/SAMPLE1/
                 |<publishDir>/SAMPLE2/
                 |```
                 |These per-ID subdirectories can be avoided by setting:
                 |```
                 |per_id: false
                 |```
                 |""".stripMargin)
  per_id: Option[Boolean] = None,

  @description("Separates the outputs generated by a Nextflow component with multiple outputs as separate events on the channel. Default value: `true`.")
  @example("separate_multiple_outputs: false", "yaml")
  separate_multiple_outputs: Boolean = true,

  @description("""When `publish: true`, this attribute defines where the output is written relative to the `params.publishDir` setting. For example, `path: processed` in combination with `--output s3://some_bucket/` will store the output of this component under
                 |```
                 |s3://some_bucket/processed/
                 |```
                 |This attribute gives control over the directory structure of the output. For example:
                 |```
                 |path: raw_data
                 |```
                 |Or even:
                 |```
                 |path: raw_data/bcl
                 |```
                 |Please note that `per_id` and `path` can be combined.""")
  path: Option[String] = None,

  @description("""When running the module in a cluster context and depending on the cluster type, [NextFlow allows for attaching labels](https://www.nextflow.io/docs/latest/process.html#label) to the process that can later be used as selectors for associating resources to this process.
                 |
                 |In order to attach one label to a process/component, one can use the `label: ...` attribute, multiple labels can be added using `labels: [ ..., ... ]` and the two can even be mixed.
                 |
                 |In the main `nextflow.config`, one can now use this label:
                 |
                 |process {
                 |  ...
                 |  withLabel: bigmem {
                 |     maxForks = 5
                 |     ...
                 |  }
                 |}
                 |""".stripMargin)
  @example("label: highmem labels: [ highmem, highcpu ]", "yaml")
  label: Option[String] = None,

  @description("""When running the module in a cluster context and depending on the cluster type, [NextFlow allows for attaching labels](https://www.nextflow.io/docs/latest/process.html#label) to the process that can later be used as selectors for associating resources to this process.
                 |
                 |In order to attach one label to a process/component, one can use the `label: ...` attribute, multiple labels can be added using `labels: [ ..., ... ]` and the two can even be mixed.
                 |
                 |In the main `nextflow.config`, one can now use this label:
                 |
                 |process {
                 |  ...
                 |  withLabel: bigmem {
                 |     maxForks = 5
                 |     ...
                 |  }
                 |}
                 |""".stripMargin)
  @example("label: highmem labels: [ highmem, highcpu ]", "yaml")
  labels: OneOrMore[String] = Nil,

  @description("""By default NextFlow will create a symbolic link to the inputs for a process/module and run the tool at hand using those symbolic links. Some applications do not cope well with this strategy, in that case the files should effectively be copied rather than linked to. This can be achieved by using `stageInMode: copy`.
                 |This attribute is optional, the default is `symlink`.
                 |""".stripMargin)
  @example("stageInMode: copy", "yaml")
  stageInMode: Option[String] = None,
  directive_cpus: Option[Integer] = None,
  directive_max_forks: Option[Integer] = None,
  directive_time: Option[String] = None,
  directive_memory: Option[String] = None,
  directive_cache: Option[String] = None,
  `type`: String = "nextflow",
  variant: String = "legacy"
) extends NextflowPlatform {
  assert(version.isEmpty, "nextflow platform: attribute 'version' is deprecated")

  private val nativePlatform = NativePlatform(id = id)

  def modifyFunctionality(config: Config, testing: Boolean): Functionality = {
    val functionality = config.functionality
    import NextFlowUtils._
    implicit val fun: Functionality = functionality

    val fname = functionality.name

    // get image info
    val imageInfo = Docker.getImageInfo(
      functionality = Some(functionality),
      registry = registry,
      organization = organization,
      name = image,
      tag = tag.map(_.toString),
      namespaceSeparator = namespace_separator
    )

    // get main script/binary
    val mainResource = functionality.mainScript
    val executionCode = mainResource match {
      case Some(e: Executable) => e.path.get
      case _ => fname
    }

    val allPars = functionality.allArguments

    val outputs = allPars
      .filter(_.isInstanceOf[FileArgument])
      .count(_.direction == Output)

    // All values for arguments/parameters are defined in the root of
    // the params structure. the function name is prefixed as a namespace
    // identifier. A "__" is used to separate namespace and arg/option.
    //
    // Required arguments also get a params.<argument> entry so that they can be
    // called using --param value when using those standalone.

    /**
      * A representation of viash's functionality.arguments is converted into a
      * nextflow.config data structure under params.<function>.arguments.
      *
      * A `value` attribute is added that points to params.<function>__<argument>.
      * In turn, a pointer is configured to params.argument.
      *
      * The goal is to have a configuration file that works both when running
      * the module standalone as well as in a pipeline.
      */
    val namespacedParameters: List[ConfigTuple] = {
      functionality.allArguments.flatMap { argument => (argument.required, argument.default.toList) match {
        case (true, _) =>
          // println(s"> ${argument.plainName} in $fname is set to be required.")
          // println(s"> --${argument.plainName} <...> has to be specified when running this module standalone.")
          Some(
            namespacedValueTuple(
              argument.plainName.replace("-", "_"),
              "viash_no_value"
            )(fun)
          )
        case (false, Nil) =>
          Some(
            namespacedValueTuple(
              argument.plainName.replace("-", "_"),
              "no_default_value_configured"
            )(fun)
          )
        case (false, li) =>
          Some(
            namespacedValueTuple(
              argument.plainName.replace("-", "_"),
              Bash.escape(li.mkString(argument.multiple_sep.toString), backtick = false, newline = true, quote = true)
            )(fun)
          )
      }}
    }

    val argumentsAsTuple: List[ConfigTuple] =
      if (functionality.allArguments.nonEmpty) {
        List(
          "arguments" → NestedValue(functionality.allArguments.map(argumentToConfigTuple(_)))
        )
      } else {
        Nil
      }

    val mainParams: List[ConfigTuple] = List(
      "name" → functionality.name,
      "container" → imageInfo.name,
      "containerTag" -> imageInfo.tag,
      "containerRegistry" -> imageInfo.registry.getOrElse(""),
      "containerOrganization" -> imageInfo.organization.getOrElse(""),
      "command" → executionCode
    )

    // fetch test information
    val tests = functionality.test_resources
    val testPaths = tests.map(test => test.path.getOrElse("/dev/null"))
    val testScript: List[String] = {
        tests.flatMap{
          case test: Script => Some(test.filename)
          case _ => None
        }
    }

            // If no tests are defined, isDefined is set to FALSE
    val testConfig: List[ConfigTuple] = List("tests" -> NestedValue(
        List(
          tupleToConfigTuple("isDefined" -> tests.nonEmpty),
          tupleToConfigTuple("testScript" -> testScript.headOption.getOrElse("NA")),
          tupleToConfigTuple("testResources" -> testPaths)
        )
      ))

    /**
     * A few notes:
     * 1. input and output are initialized as empty strings, so that no warnings appear.
     * 2. id is initialized as empty string, which makes sense in test scenarios.
     */
    val asNestedTuples: List[ConfigTuple] = List(
      "docker.enabled" → true,
      "def viash_temp = System.getenv(\"VIASH_TEMP\") ?: \"/tmp/\"\n  docker.runOptions" → "-i -v ${baseDir}:${baseDir} -v $viash_temp:$viash_temp",
      "process.container" → "dataintuitive/viash",
      "params" → NestedValue(
        namespacedParameters :::
        List(
          tupleToConfigTuple("id" → ""),
          tupleToConfigTuple("testScript" -> testScript.headOption.getOrElse("")), // TODO: what about when there are multiple tests?
          tupleToConfigTuple("testResources" -> testPaths),
          tupleToConfigTuple(functionality.name → NestedValue(
            mainParams :::
            testConfig :::
            argumentsAsTuple
          ))
        )
    ))

    val setup_nextflowconfig = PlainFile(
      dest = Some("nextflow.config"),
      text = Some(listMapToConfig(asNestedTuples))
    )

    val setup_main_header =
      s"""nextflow.enable.dsl=2
        |
        |params.test = false
        |params.debug = false
        |params.publishDir = "./"
        |""".stripMargin

    val setup_main_outputFilters: String = {
      if (separate_multiple_outputs) {
        allPars
          .filter(_.isInstanceOf[FileArgument])
          .filter(_.direction == Output)
          .map(par =>
            s"""
               |// A process that filters out ${par.plainName} from the output Map
               |process filter${par.plainName.capitalize} {
               |
               |  input:
               |    tuple val(id), val(input), val(_params)
               |  output:
               |    tuple val(id), val(output), val(_params)
               |  when:
               |    input.keySet().contains("${par.plainName}")
               |  exec:
               |    output = input["${par.plainName}"]
               |
               |}""".stripMargin
          ).mkString("\n")
      } else {
        ""
      }
    }

    val setup_main_check =
      s"""
        |// A function to verify (at runtime) if all required arguments are effectively provided.
        |def checkParams(_params) {
        |  _params.arguments.collect{
        |    if (it.value == "viash_no_value") {
        |      println("[ERROR] option --$${it.name} not specified in component $fname")
        |      println("exiting now...")
        |        exit 1
        |    }
        |  }
        |}
        |
        |""".stripMargin


    val setup_main_utils =
      s"""
        |def escape(str) {
        |  return str.replaceAll('\\\\\\\\', '\\\\\\\\\\\\\\\\').replaceAll("\\"", "\\\\\\\\\\"").replaceAll("\\n", "\\\\\\\\n").replaceAll("`", "\\\\\\\\`")
        |}
        |
        |def renderArg(it) {
        |  if (it.flags == "") {
        |    return "\'" + escape(it.value) + "\'"
        |  } else if (it.type == "boolean_true") {
        |    if (it.value.toLowerCase() == "true") {
        |      return it.flags + it.name
        |    } else {
        |      return ""
        |    }
        |  } else if (it.type == "boolean_false") {
        |    if (it.value.toLowerCase() == "true") {
        |      return ""
        |    } else {
        |      return it.flags + it.name
        |    }
        |  } else if (it.value == "no_default_value_configured") {
        |    return ""
        |  } else {
        |    def retVal = it.value in List && it.multiple ? it.value.join(it.multiple_sep): it.value
        |    return it.flags + it.name + " \'" + escape(retVal) + "\'"
        |  }
        |}
        |
        |def renderCLI(command, arguments) {
        |  def argumentsList = arguments.collect{renderArg(it)}.findAll{it != ""}
        |
        |  def command_line = command + argumentsList
        |
        |  return command_line.join(" ")
        |}
        |
        |def effectiveContainer(processParams) {
        |  def _organization = params.containsKey("containerOrganization") ? params.containerOrganization : processParams.containerOrganization
        |  def _registry = params.containsKey("containerRegistry") ? params.containerRegistry : processParams.containerRegistry
        |  def _name = processParams.container
        |  def _tag = params.containsKey("containerTag") ? params.containerTag : processParams.containerTag
        |
        |  return (_registry == "" ? "" : _registry + "/") + (_organization == "" ? "" : _organization + "/") + _name + ":" + _tag
        |}
        |
        |// Convert the nextflow.config arguments list to a List instead of a LinkedHashMap
        |// The rest of this main.nf script uses the Map form
        |def argumentsAsList(_params) {
        |  def overrideArgs = _params.arguments.collect{ key, value -> value }
        |  def newParams = _params + [ "arguments" : overrideArgs ]
        |  return newParams
        |}
        |
        |""".stripMargin

    val setup_main_outFromIn = 
        s"""
          |// Use the params map, create a hashmap of the filenames for output
          |// output filename is <sample>.<method>.<arg_name>[.extension]
          |def outFromIn(_params) {
          |
          |  def id = _params.id
          |
          |  _params
          |    .arguments
          |    .findAll{ it -> it.type == "file" && it.direction == "Output" }
          |    .collect{ it ->
          |      // If an 'example' attribute is present, strip the extension from the filename,
          |      // If a 'dflt' attribute is present, strip the extension from the filename,
          |      // Otherwise just use the option name as an extension.
          |      def extOrName =
          |        (it.example != null)
          |          ? it.example.split(/\\./).last()
          |          : (it.dflt != null)
          |            ? it.dflt.split(/\\./).last()
          |            : it.name
          |      // The output filename is <sample> . <modulename> . <extension>
          |      // Unless the output argument is explicitly specified on the CLI
          |      def newValue =
          |        (it.value == "viash_no_value")
          |          ? "$fname." + it.name + "." + extOrName
          |          : it.value
          |      def newName =
          |        (id != "")
          |          ? id + "." + newValue
          |          : it.name + newValue
          |      it + [ value : newName ]
          |    }
          |
          |}
          |""".stripMargin

    val setup_main_overrideIO =
      """
        |
        |def overrideIO(_params, inputs, outputs) {
        |
        |  // `inputs` in fact can be one of:
        |  // - `String`,
        |  // - `List[String]`,
        |  // - `Map[String, String | List[String]]`
        |  // Please refer to the docs for more info
        |  def overrideArgs = _params.arguments.collect{ it ->
        |    if (it.type == "file") {
        |      if (it.direction == "Input") {
        |        (inputs in List || inputs in HashMap)
        |          ? (inputs in List)
        |            ? it + [ "value" : inputs.join(it.multiple_sep)]
        |            : (inputs[it.name] != null)
        |              ? (inputs[it.name] in List)
        |                ? it + [ "value" : inputs[it.name].join(it.multiple_sep)]
        |                : it + [ "value" : inputs[it.name]]
        |              : it
        |          : it + [ "value" : inputs ]
        |      } else {
        |        (outputs in List || outputs in HashMap)
        |          ? (outputs in List)
        |            ? it + [ "value" : outputs.join(it.multiple_sep)]
        |            : (outputs[it.name] != null)
        |              ? (outputs[it.name] in List)
        |                ? it + [ "value" : outputs[it.name].join(it.multiple_sep)]
        |                : it + [ "value" : outputs[it.name]]
        |              : it
        |          : it + [ "value" : outputs ]
        |      }
        |    } else {
        |      it
        |    }
        |  }
        |
        |  def newParams = _params + [ "arguments" : overrideArgs ]
        |
        |  return newParams
        |
        |}
        |""".stripMargin

    def formatDirective(id: String, value: Option[String], delim: String): String = {
      value.map(v => s"\n  $id $delim$v$delim").getOrElse("")
    }

    /**
     * Some (implicit) conventions:
     * - `params.output/` is where the output data is published
     * - per_id is for creating directories per (sample) ID, default is true
     * - path is for modifying the layout of the output directory, default is no changes
     */
    val setup_main_process = {

      val per_idParsed = per_id.getOrElse(true)
      val pathParsed = path.map(_.split("/").mkString("/") + "/").getOrElse("")

      // If id is the empty string, the subdirectory is not created
      val publishDirString =
        if (per_idParsed) {
          s"$${params.publishDir}/$pathParsed$${id}/"
        } else {
          s"$${params.publishDir}/$pathParsed"
        }

      val publishDirStr = publish match {
        case Some(true) => s"""  publishDir "$publishDirString", mode: 'copy', overwrite: true, enabled: !params.test"""
        case _ => ""
      }

      val directives =
        labels.map(l => formatDirective("label", Some(l), "'")).mkString +
          formatDirective("label", label, "'") +
          formatDirective("cpus", directive_cpus.map(_.toString), "") +
          formatDirective("maxForks", directive_max_forks.map(_.toString), "") +
          formatDirective("time", directive_time, "'") +
          formatDirective("memory", directive_memory, "'") +
          formatDirective("cache", directive_cache, "'")

      val stageInModeStr = stageInMode match {
        case Some("copy") => "copy"
        case _ => "symlink"
      }

      s"""
        |process ${fname}_process {$directives
        |  tag "$${id}"
        |  echo { (params.debug == true) ? true : false }
        |  stageInMode "$stageInModeStr"
        |  container "$${container}"
        |$publishDirStr
        |  input:
        |    tuple val(id), path(input), val(output), val(container), val(cli), val(_params)
        |  output:
        |    tuple val("$${id}"), path(output), val(_params)
        |  stub:
        |    \"\"\"
        |    # Adding NXF's `$$moduleDir` to the path in order to resolve our own wrappers
        |    export PATH="$${moduleDir}:\\$$PATH"
        |    STUB=1 $$cli
        |    \"\"\"
        |  script:
        |    def viash_temp = System.getenv("VIASH_TEMP") ?: "/tmp/"
        |    if (params.test)
        |      \"\"\"
        |      # Some useful stuff
        |      export NUMBA_CACHE_DIR=/tmp/numba-cache
        |      # Running the pre-hook when necessary
        |      # Pass viash temp dir
        |      export VIASH_TEMP="$${viash_temp}"
        |      # Adding NXF's `$$moduleDir` to the path in order to resolve our own wrappers
        |      export PATH="./:$${moduleDir}:\\$$PATH"
        |      ./$${params.$fname.tests.testScript} | tee $$output
        |      \"\"\"
        |    else
        |      \"\"\"
        |      # Some useful stuff
        |      export NUMBA_CACHE_DIR=/tmp/numba-cache
        |      # Running the pre-hook when necessary
        |      # Pass viash temp dir
        |      export VIASH_TEMP="$${viash_temp}"
        |      # Adding NXF's `$$moduleDir` to the path in order to resolve our own wrappers
        |      export PATH="$${moduleDir}:\\$$PATH"
        |      $$cli
        |      \"\"\"
        |}
        |""".stripMargin
    }

    val emitter =
      if (separate_multiple_outputs) {
        s"""  result_
          |     | filter { it[1].keySet().size() > 1 }
          |     | view{">> Be careful, multiple outputs from this component!"}
          |
          |  emit:
          |  result_.flatMap{ it ->
          |    (it[1].keySet().size() > 1)
          |      ? it[1].collect{ k, el -> [ it[0], [ (k): el ], it[2] ] }
          |      : it[1].collect{ k, el -> [ it[0], el, it[2] ] }
          |  }""".stripMargin
      } else {
        s"""  emit:
           |  result_.flatMap{ it ->
           |    (it[1].keySet().size() > 1)
           |      ? [ it ]
           |      : it[1].collect{ k, el -> [ it[0], el, it[2] ] }
           |  }""".stripMargin
      }

    val setup_main_workflow =
      s"""
        |workflow $fname {
        |
        |  take:
        |  id_input_params_
        |
        |  main:
        |
        |  def key = "$fname"
        |
        |  def id_input_output_function_cli_params_ =
        |    id_input_params_.map{ id, input, _params ->
        |
        |      // Start from the (global) params and overwrite with the (local) _params
        |      def defaultParams = params[key] ? params[key] : [:]
        |      def overrideParams = _params[key] ? _params[key] : [:]
        |      def updtParams = defaultParams + overrideParams
        |      // Convert to List[Map] for the arguments
        |      def newParams = argumentsAsList(updtParams) + [ "id" : id ]
        |
        |      // Generate output filenames, out comes a Map
        |      def output = outFromIn(newParams)
        |
        |      // The process expects Path or List[Path], Maps need to be converted
        |      def inputsForProcess =
        |        (input in HashMap)
        |          ? input.collect{ k, v -> v }.flatten()
        |          : input
        |      def outputsForProcess = output.collect{ it.value }
        |
        |      // For our machinery, we convert Path -> String in the input
        |      def inputs =
        |        (input in List || input in HashMap)
        |          ? (input in List)
        |            ? input.collect{ it.name }
        |            : input.collectEntries{ k, v -> [ k, (v in List) ? v.collect{it.name} : v.name ] }
        |          : input.name
        |      outputs = output.collectEntries{ [(it.name): it.value] }
        |
        |      def finalParams = overrideIO(newParams, inputs, outputs)
        |
        |      checkParams(finalParams)
        |
        |      new Tuple6(
        |        id,
        |        inputsForProcess,
        |        outputsForProcess,
        |        effectiveContainer(finalParams),
        |        renderCLI([finalParams.command], finalParams.arguments),
        |        finalParams
        |      )
        |    }
        |
        |  result_ = ${fname}_process(id_input_output_function_cli_params_)
        |    | join(id_input_params_)
        |    | map{ id, output, _params, input, original_params ->
        |        def parsedOutput = _params.arguments
        |          .findAll{ it.type == "file" && it.direction == "Output" }
        |          .withIndex()
        |          .collectEntries{ it, i ->
        |            // with one entry, output is of type Path and array selections
        |            // would select just one element from the path
        |            [(it.name): (output in List) ? output[i] : output ]
        |          }
        |        new Tuple3(id, parsedOutput, original_params)
        |      }
        |
        |${emitter.replaceAll("\n", "\n|")}
        |}
        |""".stripMargin

    val resultParseBlocks: List[String] =
      if (separate_multiple_outputs && outputs >= 2) {
        allPars
          .filter(_.isInstanceOf[FileArgument])
          .filter(_.direction == Output)
          .map(par =>
            s"""
              |  result \\
              |    | filter${par.plainName.capitalize} \\
              |    | view{ "Output for ${par.plainName}: " + it[1] }
              |""".stripMargin
          )
      } else {
        List("  result.view{ it[1] }")
      }

    val setup_main_entrypoint =
      s"""
        |workflow {
        |  def id = params.id
        |  def fname = "$fname"
        |
        |  def _params = params
        |
        |  // could be refactored to be FP
        |  for (entry in params[fname].arguments) {
        |    def name = entry.value.name
        |    if (params[name] != null) {
        |      params[fname].arguments[name].value = params[name]
        |    }
        |  }
        |
        |  def inputFiles = params.$fname
        |    .arguments
        |    .findAll{ key, par -> par.type == "file" && par.direction == "Input" }
        |    .collectEntries{ key, par -> [(par.name): file(params[fname].arguments[par.name].value) ] }
        |
        |  def ch_ = Channel.from("").map{ s -> new Tuple3(id, inputFiles, params)}
        |
        |  result = $fname(ch_)
        |${resultParseBlocks.mkString("\n").replaceAll("\n", "\n|")}
        |}
        |""".stripMargin

    val setup_test_entrypoint =
      s"""
        |// This workflow is not production-ready yet, we leave it in for future dev
        |// TODO
        |workflow test {
        |
        |  take:
        |  rootDir
        |
        |  main:
        |  params.test = true
        |  params.$fname.output = "$fname.log"
        |
        |  Channel.from(rootDir) \\
        |    | filter { params.$fname.tests.isDefined } \\
        |    | map{ p -> new Tuple3(
        |        "tests",
        |        params.$fname.tests.testResources.collect{ file( p + it ) },
        |        params
        |    )} \\
        |    | $fname
        |
        |  emit:
        |  $fname.out
        |}""".stripMargin

    val setup_main = PlainFile(
      dest = Some("main.nf"),
      text = Some(setup_main_header +
        setup_main_check +
        setup_main_utils +
        setup_main_outFromIn +
        setup_main_outputFilters +
        setup_main_overrideIO +
        setup_main_process +
        setup_main_workflow +
        setup_main_entrypoint +
        setup_test_entrypoint)
    )

    val additionalResources = mainResource match {
      case None => Nil
      case Some(_: Executable) => Nil
      case Some(_: Script) =>
        nativePlatform.modifyFunctionality(config, false).resources
    }

    functionality.copy(
      resources =
        additionalResources ::: List(setup_nextflowconfig, setup_main)
    )
  }
}

object NextFlowUtils {
  import scala.reflect.runtime.universe._

  def quote(str: String): String = '"' + str + '"'

  def quoteLong(str: String): String = str.replace("-", "_")

  trait ValueType

  case class PlainValue[A: TypeTag](v: A) extends ValueType {
    def toConfig:String = v match {
      case s: String if typeOf[String] =:= typeOf[A] =>
        quote(s)
      case b: Boolean if typeOf[Boolean] =:= typeOf[A] =>
        b.toString
      case c: Char if typeOf[Char] =:= typeOf[A]  =>
        quote(c.toString)
      case i: Int if typeOf[Int] =:= typeOf[A]  =>
        i.toString
      case l: List[_] =>
        l.map(el => quote(el.toString)).mkString("[ ", ", ", " ]")
      case _ =>
        "Parsing ERROR - Not implemented yet " + v
    }
  }

  case class ConfigTuple(tuple: (String, ValueType)) {
    def toConfig(indent: String = "  "): String = {
      val (k,v) = tuple
      v match {
        case pv: PlainValue[_] =>
          s"""$indent$k = ${pv.toConfig}"""
        case NestedValue(nv) =>
          nv.map(_.toConfig(indent + "  ")).mkString(s"$indent$k {\n", "\n", s"\n$indent}")
      }
    }
  }

  case class NestedValue(v: List[ConfigTuple]) extends ValueType

  implicit def tupleToConfigTuple[A:TypeTag](tuple: (String, A)): ConfigTuple = {
    val (k,v) = tuple
    v match {
      case NestedValue(nv) => ConfigTuple((k, NestedValue(nv)))
      case _ => ConfigTuple((k, PlainValue(v)))
    }
  }

  def listMapToConfig(m: List[ConfigTuple]): String = {
    m.map(_.toConfig()).mkString("\n")
  }

  def namespacedValueTuple(key: String, value: String)(implicit fun: Functionality): ConfigTuple =
    (s"${fun.name}__$key", value)

  implicit def argumentToConfigTuple[T:TypeTag](argument: Argument[T])(implicit fun: Functionality): ConfigTuple = {
    val pointer = "${params." + fun.name + "__" + argument.plainName + "}"

    // TODO: Should this not be converted from the json?
    val default = if (argument.default.isEmpty) None else Some(argument.default.mkString(argument.multiple_sep.toString))
    val example = if (argument.example.isEmpty) None else Some(argument.example.mkString(argument.multiple_sep.toString))
    quoteLong(argument.plainName) → NestedValue(
      tupleToConfigTuple("name" → argument.plainName) ::
      tupleToConfigTuple("flags" → argument.flags) ::
      tupleToConfigTuple("required" → argument.required) ::
      tupleToConfigTuple("type" → argument.`type`) ::
      tupleToConfigTuple("direction" → argument.direction.toString) ::
      tupleToConfigTuple("multiple" → argument.multiple) ::
      tupleToConfigTuple("multiple_sep" -> argument.multiple_sep) ::
      tupleToConfigTuple("value" → pointer) ::
      default.map{ x =>
        List(tupleToConfigTuple("dflt" -> Bash.escape(x.toString, backtick = false, quote = true, newline = true)))
      }.getOrElse(Nil) :::
      example.map{x =>
        List(tupleToConfigTuple("example" -> Bash.escape(x.toString, backtick = false, quote = true, newline = true)))
      }.getOrElse(Nil) :::
      argument.description.map{x =>
        List(tupleToConfigTuple("description" → Bash.escape(x, backtick = false, quote = true, newline = true)))
      }.getOrElse(Nil)
    )
  }
}

// vim: tabstop=2:softtabstop=2:shiftwidth=2:expandtab
