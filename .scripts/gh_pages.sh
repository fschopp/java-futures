#!/bin/bash

# -----------------------------------------------------------------------------
# Run mvn site and add output as new commit to branch 'gh-pages'
#
# Usage:
# .scripts/gh_pages.sh
# -----------------------------------------------------------------------------

set -e # Fail on error
set -u # Fail on uninitialized

if [ ! -f pom.xml ]; then
    echo "Must run from root directory (containing file 'pom.xml')"
    exit 1
fi

echo "Will build site and commit to branch 'gh-pages'..."

# save the version
version=$(mvn org.apache.maven.plugins:maven-help-plugin:2.2:evaluate \
    -Dexpression=project.version 2> /dev/null | egrep -v '^\[')
localrepo=$(pwd)
newsite=${localrepo}/target/site
email=$(git config user.email)
name=$(git config user.name)

# Now build the site.
mvn clean verify site

# Clone existing gh-pages branch to target/gh-pages
rm -rf target/gh-pages
mkdir -p target
cd target
git clone --quiet --branch=gh-pages --single-branch \
    "${localrepo}" gh-pages
cd gh-pages
git config --local user.email "${email}"
git config --local user.name "${name}"

# Copy contents of the site directory rather than the directory itself
if [[ "${version}" != *SNAPSHOT* ]]; then
    git rm -r --force --ignore-unmatch "${version}"
    cp -Rf "${newsite}/" "${version}/"
else
    git rm -r --force --ignore-unmatch snapshot
    cp -Rf "${newsite}/" "snapshot/"
fi

# Add site and commit
git add --force --all
git commit -m "New site for version '${version}'."
git push ${localrepo} gh-pages > /dev/null

echo "Successfully committed new site to branch 'gh-pages'."
