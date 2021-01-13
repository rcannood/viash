package com.dataintuitive.viash.platforms

import com.dataintuitive.viash.functionality._
import com.dataintuitive.viash.functionality.dataobjects._
import com.dataintuitive.viash.functionality.resources._
import com.dataintuitive.viash.platforms.requirements._
import com.dataintuitive.viash.helpers.Bash
import com.dataintuitive.viash.config.Version
import com.dataintuitive.viash.wrapper.{BashWrapper, BashWrapperMods}
import com.dataintuitive.viash.platforms.docker._

case class DockerPlatform(
  id: String = "docker",
  image: String,
  version: Option[Version] = None,
  target_image: Option[String] = None,
  target_registry: Option[String] = None,
  target_tag: Option[Version] = None,
  resolve_volume: DockerResolveVolume = Automatic,
  chown: Boolean = true,
  port: Option[List[String]] = None,
  workdir: Option[String] = None,
  apk: Option[ApkRequirements] = None,
  apt: Option[AptRequirements] = None,
  r: Option[RRequirements] = None,
  python: Option[PythonRequirements] = None,
  docker: Option[DockerRequirements] = None,
  setup: List[Requirements] = Nil
) extends Platform {
  val `type` = "docker"

  assert(version.isEmpty || target_tag.isEmpty, "docker platform: version and target_tag should not both be defined")

  val requirements: List[Requirements] =
    setup :::
      apk.toList :::
      apt.toList :::
      r.toList :::
      python.toList :::
      docker.toList

  def modifyFunctionality(functionality: Functionality): Functionality = {
    // collect docker args
    val dockerArgs = "-i --rm" + {
      port.getOrElse(Nil).map(" -p " + _).mkString("")
    }

    // create setup
    val (effectiveID, setupCommands) = processDockerSetup(functionality)

    // generate automount code
    val dmVol = processDockerVolumes(functionality)

    // add ---debug flag
    val debuggor = s"""docker run --entrypoint=bash $dockerArgs -v "$$(pwd)":/pwd --workdir /pwd -t $effectiveID"""
    val dmDebug = addDockerDebug(debuggor)

    // add ---chown flag
    val dmChown = addDockerChown(functionality, dockerArgs, dmVol.extraParams, effectiveID)

    val dmDockerfile = BashWrapperMods(
      parsers =
        """
          |        ---dockerfile)
          |            ViashDockerfile
          |            exit 0
          |            ;;""".stripMargin
    )

    // compile modifications
    val dm = dmVol ++ dmDebug ++ dmChown ++ dmDockerfile

    // make commands
    val entrypointStr = functionality.mainScript.get match {
      case _: Executable => "--entrypoint='' "
      case _ => "--entrypoint=bash "
    }
    val workdirStr = workdir.map("--workdir " + _ + " ").getOrElse("")
    val executor = s"""eval docker run $entrypointStr$workdirStr$dockerArgs${dm.extraParams} $effectiveID"""

    // add extra arguments to the functionality file for each of the volumes
    val fun2 = functionality.copy(
      arguments = functionality.arguments ::: dm.inputs
    )

    // create new bash script
    val bashScript = BashScript(
      dest = Some(functionality.name),
      text = Some(BashWrapper.wrapScript(
        executor = executor,
        functionality = fun2,
        setupCommands = setupCommands,
        mods = dm
      ))
    )

    fun2.copy(
      resources = Some(bashScript :: fun2.resources.getOrElse(Nil).tail)
    )
  }

  private val tagRegex = "(.*):(.*)".r

  private def processDockerSetup(functionality: Functionality) = {
    // get dependencies
    val runCommands = requirements.flatMap(_.dockerCommands)

    // if no extra dependencies are needed, the provided image can just be used,
    // otherwise need to construct a separate docker container

    // get imagename and tag
    val (imageRegistry, imageName, imageTag) =
      if (runCommands.isEmpty) {
        image match {
          case tagRegex(a, b) => (None, a, b)
          case _ => (None, image, "latest")
        }
      } else {
        (
          target_registry,
          target_image.getOrElse(functionality.namespace.map(_ + "/").getOrElse("") + functionality.name),
          (version orElse target_tag).map(_.toString).getOrElse("latest")
        )
      }

    val effectiveID = imageRegistry.map(_ + "/").getOrElse("") + imageName + ":" + imageTag

    val (viashDockerFile, viashSetup) =
      if (runCommands.isEmpty) {
        ("  :", s"  docker image inspect $effectiveID >/dev/null 2>&1 || docker pull $effectiveID")
      } else {
        val dockerFile =
          s"FROM $image\n\n" +
            runCommands.mkString("\n")

        val dockerRequirements =
          requirements.flatMap {
            case d: DockerRequirements => Some(d)
            case _ => None
          }
        val buildArgs = dockerRequirements.map(_.build_args.map(" --build-arg " + _).mkString).mkString("")

        val vdf =
          s"""cat << 'VIASHDOCKER'
             |$dockerFile
             |VIASHDOCKER""".stripMargin

        val vs =
          s"""  # create temporary directory to store temporary dockerfile in
             |
             |  tmpdir=$$(mktemp -d "$$VIASH_TEMP/viash_setupdocker-${functionality.name}-XXXXXX")
             |  function clean_up {
             |    rm -rf "\\$$tmpdir"
             |  }
             |  trap clean_up EXIT
             |  ViashDockerfile > $$tmpdir/Dockerfile
             |  # if [ ! -z $$(docker images -q $effectiveID) ]; then
             |  #   echo "Image exists locally or on Docker Hub"
             |  # else
             |    # Quick workaround to have the resources available in the current dir
             |    cp $$${BashWrapper.var_resources_dir}/* $$tmpdir
             |    # Build the container
             |    echo "> docker build -t $effectiveID$buildArgs $$tmpdir"
             |    docker build -t $effectiveID$buildArgs $$tmpdir
             |  #fi""".stripMargin

        (vdf, vs)
      }

    val setupCommands =
      s"""# ViashDockerFile: print the dockerfile to stdout
         |# return : dockerfile required to run this component
         |# examples:
         |#   ViashDockerFile
         |function ViashDockerfile {
         |$viashDockerFile
         |}
         |
         |# ViashSetup: build a docker container
         |# if available on docker hub, the image will be pulled
         |# from there instead.
         |# examples:
         |#   ViashSetup
         |function ViashSetup {
         |$viashSetup
         |}""".stripMargin

    (effectiveID, setupCommands)
  }

  private val extraMountsVar = "VIASH_EXTRA_MOUNTS"

  private def processDockerVolumes(functionality: Functionality) = {
    val args = functionality.argumentsAndDummies

    val preParse =
      s"""
         |${Bash.ViashAbsolutePath}
         |${Bash.ViashAutodetectMount}
         |${Bash.ViashExtractFlags}
         |# initialise autodetect mount variable
         |$extraMountsVar=''""".stripMargin


    val parsers =
      s"""
         |        ---v|---volume)
         |            ${Bash.save(extraMountsVar, Seq("-v \"$2\""))}
         |            shift 2
         |            ;;
         |        ---volume=*)
         |            ${Bash.save(extraMountsVar, Seq("-v $(ViashRemoveFlags \"$2\")"))}
         |            shift 1
         |            ;;""".stripMargin

    val extraParams = s" $$$extraMountsVar"

    val postParseVolumes =
      if (resolve_volume == Automatic) {
        "\n\n# detect volumes from file arguments" +
          args.flatMap {
            case arg: FileObject if arg.multiple =>
              // resolve arguments with multiplicity different from singular args
              val viash_temp = "VIASH_TEST_" + arg.plainName.toUpperCase()
              Some(
                s"""
                   |if [ ! -z "$$${arg.VIASH_PAR}" ]; then
                   |  IFS="${arg.multiple_sep}"
                   |  for var in $$${arg.VIASH_PAR}; do
                   |    unset IFS
                   |    $extraMountsVar="$$$extraMountsVar $$(ViashAutodetectMountArg "$$var")"
                   |    ${BashWrapper.store(viash_temp, "\"$(ViashAutodetectMount \"$var\")\"", Some(arg.multiple_sep)).mkString("\n    ")}
                   |  done
                   |  ${arg.VIASH_PAR}="$$$viash_temp"
                   |fi""".stripMargin)
            case arg: FileObject =>
              Some(
                s"""
                   |if [ ! -z "$$${arg.VIASH_PAR}" ]; then
                   |  $extraMountsVar="$$$extraMountsVar $$(ViashAutodetectMountArg "$$${arg.VIASH_PAR}")"
                   |  ${arg.VIASH_PAR}=$$(ViashAutodetectMount "$$${arg.VIASH_PAR}")
                   |fi""".stripMargin)
            case _ => None
          }.mkString("")
      } else {
        ""
      }

    val postParse = postParseVolumes + "\n\n" +
      s"""# Always mount the resource directory
         |$extraMountsVar="$$$extraMountsVar $$(ViashAutodetectMountArg "$$${BashWrapper.var_resources_dir}")"
         |${BashWrapper.var_resources_dir}=$$(ViashAutodetectMount "$$${BashWrapper.var_resources_dir}")
         |
         |# Always mount the VIASH_TEMP directory
         |$extraMountsVar="$$$extraMountsVar $$(ViashAutodetectMountArg "$$VIASH_TEMP")"
         |VIASH_TEMP=$$(ViashAutodetectMount "$$VIASH_TEMP")""".stripMargin

    BashWrapperMods(
      preParse = preParse,
      parsers = parsers,
      postParse = postParse,
      extraParams = extraParams
    )
  }

  private def addDockerDebug(debugCommand: String) = {
    val parsers = "\n" + Bash.argStore("---debug", "VIASH_DEBUG", "yes", 1, None)
    val postParse =
      s"""
         |
         |# if desired, enter a debug session
         |if [ $${VIASH_DEBUG} ]; then
         |  echo "+ $debugCommand"
         |  $debugCommand
         |  exit 0
         |fi"""


    BashWrapperMods(
      parsers = parsers,
      postParse = postParse
    )
  }

  private def addDockerChown(
    functionality: Functionality,
    dockerArgs: String,
    volExtraParams: String,
    fullImageID: String,
  ) = {
    val args = functionality.argumentsAndDummies

    def chownCommand(value: String): String = {
      s"""eval docker run --entrypoint=chown $dockerArgs$volExtraParams $fullImageID "$$(id -u):$$(id -g)" -R $value"""
    }

    val postParse =
      if (chown) {
        // chown output files/folders
        val chownPars = args
          .filter(a => a.isInstanceOf[FileObject] && a.direction == Output)
          .map(arg => {

            // resolve arguments with multiplicity different from
            // singular args
            if (arg.multiple) {
              val viash_temp = "VIASH_TEST_" + arg.plainName.toUpperCase()
              s"""
                 |if [ ! -z "$$${arg.VIASH_PAR}" ]; then
                 |  IFS="${arg.multiple_sep}"
                 |  for var in $$${arg.VIASH_PAR}; do
                 |    unset IFS
                 |    ${chownCommand("\"$var\"")}
                 |  done
                 |  ${arg.VIASH_PAR}="$$$viash_temp"
                 |fi""".stripMargin
            } else {
              s"""
                 |if [ ! -z "$$${arg.VIASH_PAR}" ]; then
                 |  ${chownCommand("\"$" + arg.VIASH_PAR + "\"")}
                 |fi""".stripMargin
            }
          })

        val chownParStr =
          if (chownPars.isEmpty) ":"
          else chownPars.mkString("").split("\n").mkString("\n  ")

        s"""
           |
           |# change file ownership
           |function viash_perform_chown {
           |  $chownParStr
           |}
           |trap viash_perform_chown EXIT
           |""".stripMargin
      } else {
        ""
      }

    BashWrapperMods(
      postParse = postParse
    )
  }
}
