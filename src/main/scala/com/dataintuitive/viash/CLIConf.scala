package com.dataintuitive.viash

import org.rogach.scallop.{ScallopConf, Subcommand}

trait ViashCommand {
  _: ScallopConf =>
  val platform = opt[String](
    short = 'p',
    default = None,
    descr =
      "Specifies which platform amongst those specified in the config to use. " +
      "If this is not provided, the first platform will be used. " +
      "If no platforms are defined in the config, the native platform will be used. " +
      "In addition, the path to a platform yaml file can also be specified.",
    required = false
  )
  val platformid = opt[String](
    short = 'P',
    default = None,
    descr = "[deprecated] passthrough option for platform.",
    required = false,
    hidden = true
  )
  val config = trailArg[String](
    descr = "A viash config file (example: config.vsh.yaml). This argument can also be a script with the config as a header.",
    default = Some("config.vsh.yaml"),
    required = true
  )
}
trait ViashNs {
  _: ScallopConf =>
  val namespace = opt[String](
    name = "namespace",
    short = 'n',
    descr = "Filter which namespaces get selected. Can be a regex. Example: \"build|run\".",
    default = None
  )
  val src = opt[String](
    name = "src",
    short = 's',
    descr = " A source directory containing viash config files, possibly structured in a hierarchical folder structure. Default: src/.",
    default = Some("src")
  )
  val platform = opt[String](
    short = 'p',
    descr =
      "Acts as a regular expression to filter the platform ids specified in the found config files. " +
        "If this is not provided, all platforms will be used. " +
        "If no platforms are defined in a config, the native platform will be used. " +
        "In addition, the path to a platform yaml file can also be specified.",
    default = None,
    required = false
  )
  val platformid = opt[String](
    short = 'P',
    descr = "[deprecated] passthrough option for platform.",
    default = None,
    required = false,
    hidden = true
  )
  val parallel = opt[Boolean](
    name = "parallel",
    short = 'l',
    default = Some(false),
    descr = "Whether or not to run the process in parallel."
  )
}
trait WithTemporary {
  _: ScallopConf =>
  val keep = opt[String](
    name = "keep",
    short = 'k',
    default = None,
    descr = "Whether or not to keep temporary files. By default, files will be deleted if all goes well but remain when an error occurs." +
      " By specifying --keep true, the temporary files will always be retained, whereas --keep false will always delete them." +
      " The temporary directory can be overwritten by setting defining a VIASH_TEMP directory."
  )
}

class CLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version(s"${Main.name} ${Main.version} (c) 2020 Data Intuitive, All Rights Reserved")

  appendDefaultToDescription = true

  banner(
    s"""
       |viash is a spec and a tool for defining execution contexts and converting execution instructions to concrete instantiations.
       |
       |Usage:
       |  viash run config.vsh.yaml -- [arguments for script]
       |  viash build config.vsh.yaml
       |  viash test config.vsh.yaml
       |  viash ns build
       |  viash ns test
       |
       |Check the help of a subcommand for more information, or the API available at:
       |  https://www.data-intuitive.com/viash_docs
       |
       |Arguments:""".stripMargin)

  val run = new Subcommand("run") with ViashCommand with WithTemporary {
    banner(
      s"""viash run
         |Executes a viash component from the provided viash config file. viash generates a temporary executable and immediately executes it with the given parameters.
         |
         |Usage:
         |  viash run config.vsh.yaml [-p docker] [-k true/false]  -- [arguments for script]
         |
         |Arguments:""".stripMargin)

    footer(
      s"""  -- param1 param2 ...    Extra parameters to be passed to the component itself.
         |                          -- is used to separate viash arguments from the arguments
         |                          of the component.
         |
         |The temporary directory can be altered by setting the VIASH_TEMP directory. Example:
         |  export VIASH_TEMP=/home/myuser/.viash_temp
         |  viash run config.vsh.yaml""".stripMargin)
  }

  val build = new Subcommand("build") with ViashCommand {
    banner(
      s"""viash build
         |Build an executable from the provided viash config file.
         |
         |Usage:
         |  viash build config.vsh.yaml -o output [-p docker] [-m] [-s]
         |
         |Arguments:""".stripMargin)

    val meta = opt[Boolean](
      name = "meta",
      short = 'm',
      default = Some(false),
      descr = "Print out some meta information at the end."
    )
    val output = opt[String](
      descr = "Path to directory in which the executable and any resources is built to. Default: \"output/\".",
      default = Some("output/"),
      required = true
    )
    val setup = opt[Boolean](
      name = "setup",
      default = Some(false),
      descr = "Whether or not to set up the platform environment after building the executable."
    )
  }

  val test = new Subcommand("test") with ViashCommand with WithTemporary {
    banner(
      s"""viash test
         |Test the component using the tests defined in the viash config file.
         |
         |Usage:
         |  viash test config.vsh.yaml [-p docker [-k true/false]
         |
         |Arguments:""".stripMargin)

    footer(
      s"""
         |The temporary directory can be altered by setting the VIASH_TEMP directory. Example:
         |  export VIASH_TEMP=/home/myuser/.viash_temp
         |  viash run meta.vsh.yaml""".stripMargin)
  }

  val namespace = new Subcommand("ns") {

    val build = new Subcommand("build") with ViashNs{
      banner(
        s"""viash ns build
           |Build a namespace from many viash config files.
           |
           |Usage:
           |  viash ns build [-n nmspc] [-s src] [-t target] [-p docker] [--setup] [--parallel]
           |
           |Arguments:""".stripMargin)
      val target = opt[String](
        name = "target",
        short = 't',
        descr = "A target directory to build the executables into. Default: target/.",
        default = Some("target")
      )
      val setup = opt[Boolean](
        name = "setup",
        default = Some(false),
        descr = "Whether or not to set up the platform environment after building the executable."
      )
    }

    val test = new Subcommand("test") with ViashNs with WithTemporary {
      banner(
        s"""viash ns test
           |Test a namespace containing many viash config files.
           |
           |Usage:
           |  viash ns test [-n nmspc] [-s src] [-p docker] [--parallel] [--tsv file.tsv]
           |
           |Arguments:""".stripMargin)
      val tsv = opt[String](
        name = "tsv",
        short = 't',
        descr = "Path to write a summary of the test results to."
      )
    }

    addSubcommand(build)
    addSubcommand(test)
    requireSubcommand()

    shortSubcommandsHelp(true)
  }

  addSubcommand(run)
  addSubcommand(build)
  addSubcommand(test)
  addSubcommand(namespace)

  shortSubcommandsHelp(true)

  verify()
}
