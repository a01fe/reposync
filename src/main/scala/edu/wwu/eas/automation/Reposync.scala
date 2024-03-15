package edu.wwu.eas.automation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import os.Path
import os.RelPath

import edu.wwu.eas.automation.GitLab.createProject
import edu.wwu.eas.automation.GitLab.isProject
import edu.wwu.eas.automation.GitOps.*

/** The location of a git repository.
  *
  * @constructor
  *   create a new [[Repo]] with a git-ssh path to a repository
  * @param name
  *   [[os.RelPath]] to the repo, e.g. `banner/apps/banner_general_ssb_app.git`
  */

case class Repo(name: RelPath):
  def clonePath(using options: Options): Path = options.repoDir / name
  def gitLabPath(using config: Config): RelPath = RelPath(config.gitLabRoot) / nameWithoutExt
  def isRemoteRepoInGitLab(using Config): Boolean = isProject(gitLabPath.toString)
  def nameWithoutExt: RelPath = name / os.up / name.baseName

object Reposync:

  @main def reposyncMain(args: String*) =
    val (options, config) = OptionParser.parse(args: _*)
    given Options = options
    given Config = config

    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    rootLogger.setLevel(Level.WARN)
    if options.verbose then rootLogger.setLevel(Level.INFO)
    if options.debug then
      rootLogger.setLevel(Level.DEBUG)
      if options.debugGitLabApi then
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINE)

    GitLab.connect(config.gitLabUrl)

    logger.info("Clone remote repos")
    val repos = getGitoliteRepositoryList(config.remoteGitUrl)
      // Filter repositories on our snubby list. These are repos that gitolite
      // says we can access but fail when we clone them
      .filterNot(n =>
        Option.unless(options.ignoreSnubbies)(config.snubbies.contains(n.toString))
          .filter(identity).map(_ => logger.info(s"Skipping ${n.toString} in snubbies"))
          .nonEmpty
      )
      .map(n => Repo(n))
      // Make a local clone if one doesn't already exist
      .tapEach(r =>
        os.makeDir.all(r.clonePath / os.up)
        if !os.isDir(r.clonePath) then
          exec(
            cmd = os.proc("git", "clone", "--bare", s"${config.remoteGitUrl}:${r.name}", r.name.toString),
            dir = options.repoDir,
            changes = true,
            continueOnError = true
          )
      )
      // Ignore repos that we couldn't clone
      .filter(r => os.isDir(r.clonePath) && os.size(r.clonePath) > 0)

    logger.info("Insure repos exist in GitLab")
    repos
      .filter(!_.isRemoteRepoInGitLab)
      .foreach(r =>
        logger.info(s"Create GitLab repository: ${r.gitLabPath.toString}")
        if !options.noChange then createProject(r.gitLabPath)
      )

    logger.info("Mirror repos in GitLab")
    repos
      .foreach(r =>
        exec(
          cmd = os.proc("git", "fetch", s"${config.remoteGitUrl}:${r.name}", "+refs/*:refs/*"),
          dir = r.clonePath,
          changes = true
        )
        exec(
          cmd = os.proc(
            "git",
            "push",
            Option.when(options.force)("--force"),
            s"${config.localGitUrl}:${r.gitLabPath}.git",
            "+refs/heads/*:refs/heads/*",
            "+refs/tags/*:refs/tags/*",
            "+refs/change/*:refs/change/*"
          ),
          dir = r.clonePath,
          changes = true
        )
      )
