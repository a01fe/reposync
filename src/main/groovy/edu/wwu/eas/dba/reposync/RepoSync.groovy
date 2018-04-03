package edu.wwu.eas.dba.reposync

import java.nio.file.Paths
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.gitlab.api.GitlabAPI

@Slf4j
class RepoSync {

    static final String ellucianUrl = "git@banner-src.ellucian.com"
    static final String  ellucianRemote = "origin"

    static final String wwuUrl = "https://git.eas.wwu.edu"
    static final String wwuSshUrl = "git@git.eas.wwu.edu"
    static final String wwuGroup = "Ellucian"

    def options

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
        return repos.grep(~/\s[CRW ]+\s*[^*]*/)*.replaceFirst(/\s[CRW ]+\s*([^\s]+)\s*/, '$1').collect { new File(it + ".git") }
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
            if (options.v) log.error "command failed: ${args.cmd}"
            if (!args.continueOnError) System.exit(1)
        }
        return result
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

        GitlabAPI api = GitlabAPI.connect(wwuUrl, options.a)
        def wwuGroupId = api.groups.find { group -> group.name == wwuGroup }?.id
        def repoDir = Paths.get(options.r)
    
        gitoliteRepos(ellucianUrl).each { repo ->
            log.debug "Processing ${repo}"
            def repoPath = repoDir.resolve(repo.toPath()).toFile()
            if (!repoPath.exists()) {
                repoPath.getParentFile().mkdirs()
                exec(cmd: "git clone --bare ${ellucianUrl}:${repo} ${repo}", dir: repoDir.toFile(), continueOnError: true)
            }
            // git clone may fail, only process repos that actually exist
            if (repoPath.exists()) {

                // Create WWU repository if necessary
                def wwuRepoName = repo.toString().replace("/", "-").replaceFirst(/\.git$/, "")
                def wwuRepoPath = "$wwuGroup/$wwuRepoName"
                def wwuRepoExists = false
                try {
                    api.getProject(wwuRepoPath.toString())
                    wwuRepoExists = true
                }
                catch (Exception e) {
                }
                if (! wwuRepoExists) {
                    if (options.v) println "Creating wwu repository: $wwuRepoPath"
                    if (!options.n) {
                        api.createProject(wwuRepoName, wwuGroupId, repo.toString(), false, false, false, false, false, false, null, null)
                    }
                }

                // Fetch from Ellucian
                exec(cmd: "git fetch ${ellucianUrl}:${repo} +refs/*:refs/*", dir: repoPath)

                // Push to WWU
                exec(cmd: "git push ${wwuSshUrl}:${wwuRepoPath}.git +refs/*:refs/*", dir: repoPath)
            }
        }
    }
}