# End to end test for the release note script

This is the end-to-end test for the release note script. 

## Requirements
- Java 11+
- GitHub ersonal access token (PAT) with enough permissions
  - create/read project, create/read repo, create/read pull request, etc.

## Usage
Go to the `e2e` directory. and run the test.sh.
```console
Usage: ./test.sh <owner> <repository> <projectPrefix> <version> <PAT>

Example:
    ./test.sh @me scalardb ScalarDB 4.0.0 ghp_xxxx

!!CAUTION!!
This tool creates a project and repo on the GitHub. So please do not use with the product repository and organization.
```

Example
```console
$ git clone https://github.com/scalar-labs/actions.git
$ cd actions/release-note-script/e2e
$./test.sh @me test-repo test 0.1.0 ghp_xxxx
...
INFO: Processing PR: 1
INFO: Processing PR: 2
INFO: Processing PR: 3
INFO: Processing PR: 4
INFO: Processing PR: 5
INFO: Processing PR: 6
INFO: Processing PR: 7
INFO: Processing PR: 8
[OK]
$
```
