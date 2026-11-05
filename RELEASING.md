# Releasing Maven artifacts

1. Set `version` in `gradle.properties` to a new stable MAJOR.MINOR.PATCH.
2. Merge and verify the build, including the template integration workflow.
3. Run **Release Maven** with the same version and `publish` disabled to build
   and verify without accessing R2. This does not verify remote write permission.
4. Run from `main` with `publish` enabled. The `maven-release` GitHub environment
   must exist. Configure its allowed branches and required reviewers in GitHub.

Configure the environment secrets `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`,
restricted to the Maven bucket, and environment variable `R2_ENDPOINT` containing
its S3 API URL. Credentials are passed only to the upload step. The endpoint is
read from GitHub environment/repository **Variables**, not Secrets.

The build version comes from `gradle.properties`; a differing workflow input or
version tag is rejected. Build artifacts are uploaded on Linux and Windows;
Linux artifacts are published. Existing release files may only be reused when
byte-identical. Prefer **Re-run failed jobs** to reuse the same verified artifact
set after an interrupted upload. Javadoc timestamps are disabled, but this alone
does not prove reproducibility across different machines or JDK patch versions.

The integration workflow lives in RetrospectumModTemplate. Before publishing
coordinated changes, make that workflow available and run it manually with
explicit `loom-ref` and `mixin-ref` commit SHAs. Automatic callers test their
commit against the other repositories' `main` branches.
