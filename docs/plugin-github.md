# Git and GitHub Plugin
This plugin provides support for BitBucket and GitHub with two different configuration files: `.gitlink` files allow linking configuration files to an online git interface and `.github` files allow accessing GitHub information in olives.

## Git Link
It is recommended that the Shesmu configuration files and olives be stored in a git repository. If that repository is hosted on BitBucket, GitLab, or GitHub, then the Shesmu web interface can provide hyperlinks to the web interface of BitBucket, GitLab, or GitHub. Create a configuration file ending in `.gitlink` as follows:

    {
       "prefix": "/srv/shesmu/gitrepo",
       "type": "GITHUB",
       "url": "https://github.com/myorg/gitrepo"
    }

for GitHub or GitLab. For BitBucket:

    {
      "prefix": "/srv/shesmu/gitrepo",
      "type": "BITBUCKET",
      "url": "https://bitbucket.oicr.on.ca/projects/MYORG/repos/gitrepo"
    }

Any configuration files that are found in the `prefix` directory will be mapped onto the repository. Prefixes should be non-overlapping or the URLs will be selected at random.

## GitHub Repository Information
The current state of GitHub branch information can be used as an input format. Create a configuration file ending in `.github` as follows:

    {
       "repo": "myrepo",
       "owner": "myorg",
       "timeout": 10
    }

The `owner` is the GitHub user or organisation that owns the repository. Now, olives can use `Input github_branches;` to access the current state of all branches in the repository. Note that this uses the heavily rate-limited unauthenticated public API.
`timeout` defines the HTTP connection timeout for fetching
input information from GitHub, in minutes.
