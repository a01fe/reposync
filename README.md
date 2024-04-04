# reposync

`reposync` is a Scala application that maintains local clones of Ellucian's Banner git repositories.

It iterates over each repository in Ellucian's [Gitolite](https://gitolite.com/gitolite/index.html) instance and creates or updates the corresponding repository in our GitLab instance.

`reposync` puts these repositories in the _eas/ellucian_ group in our GitLab instance.
For example, this Ellucian repository:

> `git@banner-src.ellucian.com:banner/plugins/banner_student_attendance_tracking.git`

is mirrored to this in our GitLab instance:

> `git@git.eas.wwu.edu:eas/ellucian/banner/plugins/banner_student_attendance_tracking.git`

## Scheduled CI pipeline

`reposync` is run daily at 4am by a GitLab scheduled CI pipeline, and can be run manually.

The `run` job will fail if Ellucian revises history in their repositories. If this happens, manually run the CI pipeline with the `REPOSYNC_OPTS` CI variable set to `--force -v`. With `--force`, `reposync` will force push to GitLab.

The CI pipeline depends on these secret variables in GitLab CI settings:

1. A GitLab user's private token used by `reposync` to make changes on behalf of that user.  The `GITLAB_API_KEY` CI variable must specify the private token of a GitLab user that has the ability to create repositories and push to protected branches in the _Ellucian_ group (master or owner).

1. An SSH key pair registered with Ellucian to access the Ellucian Gitolite instance. The `SSH_PRIVATE_KEY` CI variable must specify the private ssh key and it must not have a passphrase.

1. SSH host keys for local and Ellucian git remotes in the `SSH_SERVER_HOSTKEYS` CI variable.

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

`reposync` is built as a Gradle Scala application. To build a distribution, run:

```bash
./gradlew distTar
```

## Usage

```plain text
reposync 1.3.0
Usage: reposync [options]

  -c, --config <file>      override built-in configuration file
  -d, --debug              display debugging output
  --debug-gitlab-api       enable GitLab API request and response logging
  -h, --help               display this usage message
  -f, --force              use --force on push to local repositories
  -i, --ignore-snubbies    try to mirror repositories on the snubbies list
  -m, --mirror             use --mirror on push to local repositories
  -n, --no-change          no changes, display actions only
  -r, --repo-dir <directory>
                           repo cache directory
  -t, --token <token>      GitLab API token
  -v, --verbose            display verbose output
```
