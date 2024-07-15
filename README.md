# reposync

`reposync` is a Scala application that maintains local clones of Ellucian's Banner git repositories.

It iterates over each repository in Ellucian's [Gitolite](https://gitolite.com/gitolite/index.html) instance and creates or updates the corresponding repository in our GitLab instance.

`reposync` puts these repositories in the _eas/ellucian_ group in our GitLab instance.
For example, this Ellucian repository:

> `git@banner-src.ellucian.com:banner/plugins/banner_student_attendance_tracking.git`

is mirrored to this repository in our GitLab instance:

> `git@git.eas.wwu.edu:eas/ellucian/banner/plugins/banner_student_attendance_tracking.git`

To save time and network bandwidth, `reposync` clones repositories into a local directory (specified by `-r`) that should be preserved across runs.

## Scheduled cron job

`reposync` is run daily at 4am by a cron job in our `ops` cluster.

The cron job will fail if Ellucian revises history in their repositories. If this happens, add `--force` to container arguments. With `--force`, `reposync` will force push to GitLab.

The cron job depends on secrets that are built from external Gradle properties when `reposync` is deployed:

1. A GitLab user's private token used by `reposync` to make changes on behalf of that user.  The `gitlabApiKey` property must specify the private token of a GitLab user that has the ability to create repositories and push to protected branches in the _Ellucian_ group (master or owner).

1. An ssh key pair (without a passphrase) registered with Ellucian to access the Ellucian Gitolite instance. The `sshPrivateKey` property must specify the base 64 encoded private ssh key.

1. Base 64 encoded ssh host keys for local and Ellucian git remotes in the `sshHostKeys` property.

## Snubbies

Ellucian's customer facing gitolite instance lists some repositories as available, but `git clone` fails with permission errors.
To avoid wasting time attempting to clone them, `reposync` will skip repositories in the `snubbies` list in `src/main/resources/config.yaml`.
Pass `--ignore-snubbies` to `reposync` to have it try to mirror all repositories that Ellucian's gitolite instance lists.

## Mirroring

When `--mirror` is set, `reposync` runs `git push --mirror` to update repositories in our GitLab instance,
insuring that our repositories are identical replicas of Ellucian repositories.
This is useful when we have accidentally pushed changes and wish to restore them.

Unfortunately, some Ellucian repositories have remote branches that GitLab will not accept, which causes `git push --mirror` to fail.
As a workaround, `reposync` will remove these bad remote branches when run with `--mirror --force` options.

If `--mirror` is not used, `reposync` runs `git push` with refspec patterns that avoid pushing bad remote branches.
This will preserve extraneous commits in our repositories that are not in Ellucian repositories.

## Building

`reposync` is built as a Gradle Scala application. To build and push an image, run:

```bash
./gradlew buildImage
```

## Deploying

Create a property file named `~/.gradle/prod-reposync.properties` that contains secrets as described above:

```properties
gitlabApiKey=<your-GitLab-API-key>
sshPrivateKey=<base-64-encoded-ssh-private-key>
sshHostKeys=<base-64-encoded-ssh-host-keys>
```

You can generate base64 encoded ssh keys with:

```bash
echo "sshPrivateKey=$(base64 -w 0 /path/to/ssh/private/key)"
echo "sshHostKeys=$(ssh-keyscan banner-src.ellucian.com 2>/dev/null | base64 -w 0)"
```

To deploy a `reposync` cron job, run:

```bash
./gradlew deployProd
```

## Usage

```plain text
reposync 1.3.1
Usage: reposync [options]

  -c, --config <file>      override built-in configuration file
  -d, --debug              display debugging output
  --debug-gitlab-api       enable GitLab API request and response logging
  -h, --help               display this usage message
  -f, --force              use --force on push to local repositories
  -i, --ignore-snubbies    try to mirror repositories on the snubbies list
  -l, --list-repo_age      list date of last commit in local mirror repos
  -m, --mirror             use --mirror on push to local repositories
  -n, --no-change          no changes, display actions only
  -r, --repo-dir <directory>
                           repo cache directory
  -t, --token <token>      GitLab API token
  -v, --verbose            display verbose output```
