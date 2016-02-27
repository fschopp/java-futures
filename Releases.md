# The Release Process

1. The current SNAPSHOT build is pushed to the upstream repository and passes
   all tests. Configure the environment so that the gpg2 default key is the one
   that is to be used for signing the artifacts later. That is,
   `gpg2 --list-keys` should list this key first. If necessary, set the
   `GNUPGHOME` environment variable to the appropriate location.
   Also make sure that the `settings.xml` (typically in `~/.m2`) contains a
   `server` entry with id `ossrh`. For instance:

   ```xml
   <server>
       <id>ossrh</id>
       <username>abc</username>
       <password>xyz</password>
   </server>
   ```

   Finally, the Maven GPG Plugin uses property
   [`gpg.executable`](https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html#executable)
   to locate the GPG executable. If GPG is available under a non-default name
   (e.g., `gpg2`), make sure to set it in your `settings.xml`. For instance:

   ```xml
   <profile>
       <id>ossrh</id>
       <activation>
           <activeByDefault>true</activeByDefault>
       </activation>
       <properties>
           <gpg.executable>gpg2</gpg.executable>
       </properties>
   </profile>
   ```

2. Run `mvn release:clean release:prepare`. This will ask about the next
   SNAPSHOT version number and ultimately create new local (and only local)
   git commits.
3. Run `mvn release:perform` to deploy the artifacts to the Sonatype
   Open-Source Repository Hosting (OSSRH). This will not yet sync with the
   Central Repository. Instead, the artifacts can be verified by logging in to
   [OSSRH](https://oss.sonatype.org/).
4. Push the local git commits upstream.
5. Release artifact by clicking on “Close” and then “Release” in
   [OSSRH](https://oss.sonatype.org/). See the
   [OSSRH documentation](http://central.sonatype.org/pages/releasing-the-deployment.html)
   for screen shots.
6. Check out the release commit created in step 2. Run
   `.scripts/publish_site.sh` in order to generate documentation for the new
   release. This will create a new local (and only local) git commit in branch
   `gh-pages`.
7. Push the local git commit to branch `gh-pages` upstream.
