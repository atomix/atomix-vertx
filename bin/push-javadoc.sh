#!/bin/sh
# run from top level dir

PROJECT=atomix-vertx

mvn javadoc:javadoc -Djv=$apiVersion
rm -rf target/docs
git clone git@github.com:atomix/$PROJECT.git target/docs -b gh-pages
cd target/docs
git rm -rf api
mkdir -p api
mv -v ../site/apidocs/* api
git add -A -f api
git commit -m "Updated JavaDocs"
git push -fq origin gh-pages > /dev/null

echo "Published JavaDocs"