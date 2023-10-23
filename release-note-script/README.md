# Release Note Script

These are scripts for generating the main body of release notes for Scalar products. There are two scripts available:

- ReleaseNoteCreation.java ... Creates a release note body for a target repository.
- MergeReleaseNotes.java ... Creates a merged release note body for ScalarDB (including community and enterprise edition).

These scripts are invoked in GitHub Actions workflows when releasing a new version of Scalar products. 

For the details of the release note please see [General rules](https://developers.scalar-labs.com/docs/style-guide/release-notes/).

## Prerequisites

- [GitHub CLI](https://cli.github.com/)
- Java 11+

## How to Run the Scripts

Note that primarily, these scripts are intended to be executed within GitHub Actions workflows.
So usually you don't need to execute the scripts manually.

### Create a Release Note Body for Each Repository

*Assuming the use of Java 11*
To run the script:

```shell
java ReleaseNoteCreation.java <owner> <projectTitlePrefix> <version> <repository>
```

Here, the arguments mean the following:

- *owner*: Owner of the target repository.
- *projectTitlePrefix*: Prefix for the target version project.
  - e.g., If the project name of the target is `ScalarDB 4.0.0`, then specify `ScalarDB`. 
- *version*: The target version to create the release note.
  - e.g., If the project name of the target is `ScalarDB 4.0.0`, then specify `4.0.0`. 
- *repository*: The name of the target repository.

Example: To create the release note for ScalarDB 4.0.0

```shell
java ReleaseNoteCreation.java scalar-labs ScalarDB 4.0.0 scalardb
```

The result will be output in a Markdown format to the standard output.

### Create a Merged Release Note for ScalarDB

*Assuming the use of Java 11*

Before running this script, make sure you have the release note bodies for ScalarDB, ScalarDB Cluster, ScalarDB GraphQL, and ScalarDB SQL as Markdown files for `scalardb.md`, `cluster.md`, `graphql.md`, `sql.md` respectively in the same directory.

To run the script:

```shell
java MergeReleaseNotes.java
```

The result will be output in a Markdown format to the standard output.
