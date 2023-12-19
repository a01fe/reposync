import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.bridge.SLF4JBridgeHandler
import org.slf4j.LoggerFactory
import edu.wwu.eas.automation.ReposyncException
import os.RelPath
import edu.wwu.eas.automation.GitLab
import edu.wwu.eas.automation.GitOps.exec
import edu.wwu.eas.automation.GitOps
import org.virtuslab.yaml.*
import edu.wwu.eas.automation.Config
import edu.wwu.eas.automation.Options
import edu.wwu.eas.automation.shellEscape

val options = Options(debug = true, verbose = true, token = "68u73e6zweMyyVRQGhb7")

val config = os.read(os.resource / "config.yaml").as[Config] match
  case Right(c) => c
  case Left(e) => throw new ReposyncException(s"Cannot read configuration: ${e.msg}")

given Options = options
given Config = config

GitLab.connect(config.gitLabUrl)

GitLab.createProject(RelPath("eas/banner/aoifetest"))
