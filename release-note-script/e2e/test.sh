#!/usr/bin/env bash

source $(dirname $0)/common.sh

if [ $# != 5 ];then
    usage
    exit 1
fi

OWNER=$1
REPO=$2
PROJ_PREFIX=$3
PROJ_VERSION=$4
PAT=$5

export GH_TOKEN=$PAT

#### Test execution

CURRENT_DIR=$(pwd)

# createWorkDir
cd $WORK_DIR

# Create a project.
PROJ_NUMBER=$(createProject "$OWNER" "$PROJ_PREFIX $PROJ_VERSION")

# Trap EXIT to clean up the temporary resources when this script end.
trap 'cleanUp $WORK_DIR $OWNER $REPO $PROJ_NUMBER' EXIT

if [ ! -d $PR_DIR ];then
    mkdir -p $PR_DIR
fi

# Create a repository.
createRepo $REPO
cd $REPO

# Create labels for the repository.
createLabels $OWNER $REPO "backward-incompatible enhancement improvement bugfix"

# Create 'main' branch.
createMainBranch

# Create pull requests with/without release note text.
## With release note text
createBranchAndCommit "enhancement-01"
PR_NUM=$(createPRWithReleaseNote $PROJ_PREFIX $PROJ_VERSION "enhancement-01" "enhancement-01" "This is an enhancement-01" "enhancement")
mergePullRequest $PR_NUM $OWNER $REPO

createBranchAndCommit "enhancement-02"
PR_NUM=$(createPRWithReleaseNote $PROJ_PREFIX $PROJ_VERSION "enhancement-02" "enhancement-02" "Same as #$PR_NUM" "enhancement")
mergePullRequest $PR_NUM $OWNER $REPO

createBranchAndCommit "improvement-01"
PR_NUM=$(createPRWithReleaseNote $PROJ_PREFIX $PROJ_VERSION "improvement-01" "improvement-01" "This is an improvement-01" "improvement")
mergePullRequest $PR_NUM $OWNER $REPO

createBranchAndCommit "bugfix-01"
PR_NUM=$(createPRWithReleaseNote $PROJ_PREFIX $PROJ_VERSION "bugfix-01" "bugfix-01" "This is a bugfix-01" "bugfix")
mergePullRequest $PR_NUM $OWNER $REPO

## Without release note text
createBranchAndCommit "excluded-enhancement"
PR_NUM=$(createPRWithoutReleaseNote $PROJ_PREFIX $PROJ_VERSION "excluded-enhancement" "This is an excluded-enhancement" "enhancement")
mergePullRequest $PR_NUM $OWNER $REPO

createBranchAndCommit "excluded-improvement"
PR_NUM=$(createPRWithoutReleaseNote $PROJ_PREFIX $PROJ_VERSION "excluded-improvement" "This is an excluded-improvement" "improvement")
mergePullRequest $PR_NUM $OWNER $REPO

createBranchAndCommit "excluded-bugfix"
PR_NUM=$(createPRWithoutReleaseNote $PROJ_PREFIX $PROJ_VERSION "excluded-bugfix" "This is an excluded-bugfix" "bugfix")
mergePullRequest $PR_NUM $OWNER $REPO

## With a large pull request body (64KiB)
# NOTE:
# The Java Process class has a limited buffer size for input and output
# streams (typically 64KB). If the buffer reaches its limit, the process
# becomes blocked until space is freed by reading from or writing to the
# streams. The current implementation of the release note script uses
# Process to execute the gh command for GitHub operations. However, some
# pull requests created by Dependabot occasionally exceed this limit
# (over 64KB in the body). To prevent blocking, the script has been
# modified accordingly. I added this test case to verify that the script
# functions correctly with large PRs.
createBranchAndCommit "bugfix-with-large-pr"
largeBody=$(cat $FIXTURE_DIR/large-pr-body/prbody-64kib)
PR_NUM=$(createPRWithReleaseNote $PROJ_PREFIX $PROJ_VERSION "bugfix-with-large-pr" $largeBody "This is a bugfix-with-large-pr" "bugfix")
mergePullRequest $PR_NUM $OWNER $REPO

cd $CURRENT_DIR

# Create release note body
java $RN_SCRIPT $OWNER $PROJ_PREFIX $PROJ_VERSION $REPO > $WORK_DIR/rnbody.md
RET=$?

# Verify the result
if [ $RET != 0 ];then
    echo -e "\033[31m[NG]\033[0m Failed to execute the release note script"
fi

diff $WORK_DIR/rnbody.md $FIXTURE_DIR/expected/normal.md
RET=$?
if [ $RET != 0 ];then
    echo -e "\033[31m[NG]\033[0m the release note body doesn't match the expected one."
else
    echo -e "\033[32m[OK]\033[0m"
fi
