package edu.wwu.eas.dba.reposync

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.gitlab.api.GitlabAPI
import org.gitlab.api.models.CreateGroupRequest
import org.gitlab.api.models.GitlabGroup
import org.gitlab.api.models.GitlabVisibility

@Slf4j
class RepoSync {

    static final String ellucianUrl = "git@banner-src.ellucian.com"
    static final String ellucianRemote = "origin"

    static final String wwuUrl = "https://git.eas.wwu.edu"
    static final String wwuSshUrl = "git@git.eas.wwu.edu"
    static final String easGroupPath = "eas"
    static final String ellucianGroupPath = "ellucian"
    static final Path rootGitlabPath = Paths.get("${easGroupPath}/${ellucianGroupPath}")

    def api
    def options
    def rootTree

    static void main(String... args) {
        new RepoSync().run(args)
    }

    def gitoliteRepos(url) {
        def process = "ssh ${url} info".execute()
        process.consumeProcessErrorStream(System.err)
        def repos = process.in.readLines()
        process.waitFor()
        if (process.exitValue() != 0) {
            log.error "Fetching repo list failed"
            System.exit(1)
        }
        return repos.grep(~/\s[CRW ]+\s*[^*]*/)*.replaceFirst(/\s[CRW ]+\s*([^\s]+)\s*/, '$1').collect { Paths.get(it + ".git") }
    }

    def exec(args) {
        def result = 0

        if (options.n && !args.quiet) {
            println args.cmd
        }
        else {
            if (options.v && !args.quiet) println args.cmd
            def process = args.cmd.execute(null, args.dir)
            (options.v && !args.quiet) ? process.consumeProcessOutput(System.out, System.err) : process.consumeProcessOutput()
            process.waitFor()
            result = process.exitValue()
        }
        if (result != 0) {
            log.error "command failed: ${args.cmd}"
            if (!args.continueOnError) System.exit(1)
        }
        return result
    }

    GitlabGroup findOrCreateGroup(Path path) {
        findOrCreateGroup(path.toString().split('/'), rootTree)
    }

    def getSubgroups(GitlabGroup group) {
        def tailUrl = GitlabGroup.URL + "/" + group.id + "/subgroups" + GitlabAPI.PARAM_MAX_ITEMS_PER_PAGE
        api.retrieve().getAll(tailUrl, GitlabGroup[].class)
    }

    // Recursively walk GitLab groups looking for the specified path. Create any
    // containing groups.
    GitlabGroup findOrCreateGroup(String[] path, def tree) {
        if (path.size() <= 1) {
            return tree.group
        }
        if (tree.subgroups == null) {
            tree.subgroups = getSubgroups(tree.group).collect { [group: it, subgroups: null] }
        }
        def subgroup = tree.subgroups.find { it.group.path == path.head() }
        if (subgroup == null) {
            def request = new CreateGroupRequest(path.head())
            request.parentId = tree.group.id
            request.visibility = GitlabVisibility.PRIVATE
            def group = api.createGroup(request, null)
            tree.subgroups = getSubgroups(tree.group).collect { [group: it, subgroups: null] }
            subgroup = tree.subgroups.find { it.group.path == path.head() }
            if (subgroup == null) {
                throw new RuntimeException("new group ${group.name} not found in tree")
            }
        }
        return findOrCreateGroup(path.tail(), subgroup)
    }

    def run(String... args) {
        def name = this.class.name
        def cli = new CliBuilder(usage: "${name} [options]", stopAtNonOption: false)
        cli.a(longOpt: 'api-key', args: 1, argName: 'KEY', 'GitLab API key')
        cli.d(longOpt: 'debug', 'debug')
        cli.h(longOpt: 'help', 'help (this usage message)')
        cli.n(longOpt: 'no-change', 'no change, display only')
        cli.r(longOpt: 'repo-dir', args: 1, argName: 'DIRECTORY', 'repo cache directory')
        cli.v(longOpt: 'verbose', 'verbose')
        options = cli.parse(args)

        if (options == null || options.arguments().size() != 0 || !options.a || !options.r || options.h) {
            cli.usage()
            System.exit(1)
        }

        def root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
        if (options.v)
            root.setLevel(Level.INFO)
        if (options.d)
            root.setLevel(Level.DEBUG)

        api = GitlabAPI.connect(wwuUrl, options.a)
        def easGroup = api.groups.find { group -> group.path == easGroupPath }
        rootTree = [ group: getSubgroups(easGroup).find { group -> group.path == ellucianGroupPath }, subgroups: null ]
        log.debug "root group id: ${rootTree.group.id}"

        def repoDir = Paths.get(options.r)

        gitoliteRepos(ellucianUrl).each { repo ->
            log.debug "Processing ${repo}"
            def repoPath = repoDir.resolve(repo)
            if (Files.notExists(repoPath)) {
                Files.createDirectories(repoPath.parent)
                exec(cmd: "git clone --bare ${ellucianUrl}:${repo} ${repo}", dir: repoDir.toFile(), continueOnError: true)
            }
            // git clone may fail, only process repos that actually exist
            if (Files.exists(repoPath)) {

                // Strip off .git extension to get GitLab path
                def gitlabPath = repo.parent.resolve(repo.fileName.toString().replaceFirst(/\.git$/, ''))

                def gitlabRepoExists = false
                try {
                    api.getProject(rootGitlabPath.resolve(gitlabPath).toString())
                    gitlabRepoExists = true
                }
                catch (Exception e) {
                }
                if (! gitlabRepoExists) {
                    log.info "Creating GitLab repository: ${gitlabPath}"
                    if (!options.n) {
                        def repoGroup = findOrCreateGroup(gitlabPath)
                        api.createProject(gitlabPath.fileName.toString(), repoGroup.id, repo.toString(), false, false, false, false, false, false, null, null)
                    }
                }

                // Fetch from Ellucian
                exec(cmd: "git fetch ${ellucianUrl}:${repo} +refs/*:refs/*", dir: repoPath.toFile())

                // Push to WWU
                exec(cmd: "git push ${wwuSshUrl}:${rootGitlabPath.resolve(repo).toString()} +refs/*:refs/*", dir: repoPath.toFile())
            }
        }
    }
}
