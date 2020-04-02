package com.dataintuitive.viash.targets

import com.dataintuitive.viash.functionality.{Functionality, Resource, StringObject}
import com.dataintuitive.viash.functionality.platforms.NativePlatform
import com.dataintuitive.viash.targets.environments._
import com.dataintuitive.viash.functionality.{DataObject, FileObject, Direction}
import com.dataintuitive.viash.functionality.{Input, Output}
import java.nio.file.Paths

import scala.reflect.ClassTag

/**
 * Target class for generating NextFlow (DSL2) modules.
 * Most of the functionality is derived from the DockerTarget and we fall back to it.
 * That also means the syntax needs to be compatible.
 *
 * Extra fields:
 * - executor: the type of 'process' to use, explicitly added to the source files for consistency
 */
case class NextFlowTarget(
  image: String,
  volumes: Option[List[Volume]] = None,
  port: Option[List[String]] = None,
  workdir: Option[String] = None,
  apt: Option[AptEnvironment] = None,
  r: Option[REnvironment] = None,
  python: Option[PythonEnvironment] = None,
  executor: Option[String]
) extends Target {
  val `type` = "nextflow"

  val dockerTarget = DockerTarget(image, volumes, port, workdir, apt, r, python)

  def modifyFunctionality(functionality: Functionality) = {

    val dockerFunctionality = dockerTarget.modifyFunctionality(functionality)

    val resourcesPath = "/app"

    def quote(str:String) = '"' + str + '"'

    // get main script/binary
    // Only the Native platform case is covered for the moment
    val mainResource = functionality.mainResource.get
    val mainPath = Paths.get(resourcesPath, mainResource.name).toFile().getPath()
    val executionCode = functionality.platform match {
      case Some(NativePlatform) => mainResource.path.getOrElse("echo No command provided")
      case _    => { println("Not implemented yet"); mainPath}
    }

    // Use DataObject.tag to know if there is an input file because it should be handled differently
    // val inputFileObject:Option[FileObject] = allArgs.collect{ case x:FileObject => x }.headOption

    def dataObjectToTuples[T](dataObject:DataObject[T]):List[(String, Any)] = List(
      dataObject.name.map(x => ("name", x.toString)),
      dataObject.short.map(x => ("short", x.toString)),
      dataObject.description.map(x => ("description", x.toString)),
      dataObject.default.map(x => ("value", x.toString)),
      dataObject.required.map(x => ("required", x)),
      dataObject.direction.map(x => ("direction", x))
    ).flatMap(x => x)

    def nameOrShort[T](dataObject:DataObject[T]):String =
      (dataObject.name, dataObject.short) match {
        case (Some(n), None) => n
        case (None, Some(c)) => c.toString
        case _ => "HELP"
      }

    val paramsAsTuple =
      ("options",
        functionality.options.map(x => (nameOrShort(x), dataObjectToTuples(x)))
        )

    val argumentsAsTuple =
      ("arguments",
        functionality.arguments.map(x => (nameOrShort(x), dataObjectToTuples(x)))
        )

    /**
     * Some (implicit) conventions:
     * - `out/` is where the output data is published
     * - For multiple samples, an additional subdir `id` can be created, but its blank by default
     */
    val asNestedTuples:List[(String, Any)] = List(
      ("docker.enabled", true),
      ("process.container", "dataintuitive/portash"),
      ("params",
        List(
          ("id", ""),
          ("outDir", "out"),
          ("input", "test.md"),
          (functionality.name,
            List(
              ("name", functionality.name),
              ("container", image),
              ("command", executionCode),
              paramsAsTuple,
              argumentsAsTuple
            )
            )
          )
        )
      )

    // println(functionality)
    // println(asNestedTuples)

    def convertBool(b: Boolean):String = if (b) "true" else "false"

    def mapToConfig(m:(String, Any), indent:String = ""):String = m match {
        case (k:String, v: List[(String, Any)]) =>
          indent + k + " {\n" + v.map(x => mapToConfig(x, indent + "  ")).mkString("\n") + "\n" + indent + "}"
        case (k:String, v: String) => indent + k + " = " + quote(v)
        case (k:String, v: Boolean) => indent + k + " = " + convertBool(v)
        case (k:String, v: Direction) => indent + k + " = " + quote(v.toString)
        case _ => indent + "Parsing ERROR - Not implemented yet " + m
    }

    def listMapToConfig(m:List[(String, Any)]) = m.map(x => mapToConfig(x)).mkString("\n")

    val setup_nextflowconfig = Resource(
      name = "nextflow.config",
      code = Some(
        listMapToConfig(asNestedTuples)
      )
    )

    val fname = functionality.name

    val setup_main_header = s"""nextflow.preview.dsl=2
        |import java.nio.file.Paths
        |
        """.stripMargin('|')

    val setup_main_utils = s"""
        |
        |// TODO: Support for short options
        |def renderCLI(command, arguments, options) {
        |
        |    def argumentsList = []
        |    def optionsList = []
        |    def argumentsMap = arguments
        |    argumentsMap.each{ it -> argumentsList << it.value }
        |    def optionsMap = options
        |    optionsMap.each{ it -> optionsList << "--" + it.name + " " + it.value }
        |
        |    def command_line = command + argumentsList + optionsList
        |
        |    return command_line.join(" ")
        |}
        """.stripMargin('|')

    /**
     * What should the output filename be, in terms of the input?
     * This is irrelevant for simple one-step function calling, but it is crucial a in a pipeline.
     * This uses the function type, but there is no check on it yet!
     */
    val setup_main_outFromInF = """
        |def outFromIn(inputStr) {
        |
        |    def pattern = ~/^(\w+)\.(\w+)$/
        |    def newFileName = inputStr.replaceFirst(pattern) { _, prefix, ext -> "${prefix}.html" }
        |    return newFileName
        |
        |}
        |
    """.stripMargin('|')
    val setup_main_overrideInput = """
        |// In: Hashmap key -> DataqObjects
        |// Out: Arrays of DataObjects
        |def overrideInput(params, str) {
        |
        |    def overrideOptions = []
        |    def overrideArgs = []
        |
        |    def update = [ "value" : str ]
        |
        |    params.arguments.each{
        |        it -> (it.value.direction == "Input") ? overrideArgs << it.value + update  : overrideArgs << it.value
        |        }
        |    params.options.each{
        |        it -> (it.value.direction == "Input") ? overrideOptions << it.value + update : overrideOptions << it.value
        |        }
        |
        |    def newParams = params + [ "options" : overrideOptions] + [ "arguments" : overrideArgs ]
        |
        |    return newParams
        |
        |}
        """.stripMargin('|')

    val setup_main_overrideOutput = """
        |def overrideOutput(params, str) {
        |
        |    def overrideOptions = []
        |    def overrideArgs = []
        |
        |    def update = [ "value" : str ]
        |
        |    params.arguments.each{
        |        it -> (it.direction == "Output") ? overrideArgs << it + update  : overrideArgs << it
        |        }
        |    params.options.each{
        |        it -> (it.direction == "Output") ? overrideOptions << it + update : overrideOptions << it
        |        }
        |
        |    def newParams = params + [ "options" : overrideOptions] + [ "arguments" : overrideArgs ]
        |
        |    return newParams
        |
        |}
        """.stripMargin('|')

    val setup_main_process = s"""
        |
        |process simpleBashExecutor {
        |  container "$${container}"
        |  publishDir "$${params.outDir}/$${id}", mode: 'copy', overwrite: true
        |  input:
        |    tuple val(id), path(input), val(output), val(container), val(cli)
        |  output:
        |    tuple val("$${id}"), path("$${output}")
        |  script:
        |    \"\"\"
        |    echo Running: $$cli
        |    $$cli
        |    \"\"\"
        |
        |}
        """.stripMargin('|')

    val setup_main_workflow = s"""
        |workflow $fname {
        |
        |    take:
        |    id_input_params_
        |
        |    main:
        |
        |    key = "$fname"
        |
        |    def id_input_output_function_cli_ =
        |        id_input_params_.map{ id, input, _params ->
        |            def defaultParams = params[key] ? params[key] : [:]
        |            def overrideParams = _params ? _params : [:]
        |            def updtParams = defaultParams + overrideParams
        |            // now, switch to arrays instead of hashes...
        |            def updtParams1 = overrideInput(updtParams, input.toString())
        |            def updtParams2 = overrideOutput(updtParams1, outFromIn(input.toString()))
        |            new Tuple5(
        |                id,
        |                input,
        |                outFromIn(input.toString()),
        |                updtParams2.container,
        |                renderCLI([updtParams2.command], updtParams2.arguments, updtParams2.options)
        |            )
        |        }
        |
        |    simpleBashExecutor(id_input_output_function_cli_)
        |
        |}
        """.stripMargin('|')

    val setup_main_entrypoint = s"""
        |workflow {
        |
        |   id = params.id
        |   inputPath = Paths.get(params.input)
        |   ch_ = Channel.from(inputPath).map{ s -> new Tuple3(id, s, params.pandoc)}
        |
        |   $fname(ch_)
        |}
        """.stripMargin('|')

    val setup_main = Resource(
      name = "main.nf",
      code = Some(setup_main_header +
                  setup_main_utils +
                  setup_main_outFromInF +
                  setup_main_overrideInput +
                  setup_main_overrideOutput +
                  setup_main_process +
                  setup_main_workflow +
                  setup_main_entrypoint)
    )

    dockerFunctionality.copy(
        resources =
          dockerFunctionality.resources ::: List(setup_nextflowconfig, setup_main)
    )
  }

}
