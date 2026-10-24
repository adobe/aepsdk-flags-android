EXTENSION-LIBRARY-FOLDER-NAME = flags
TEST-APP-FOLDER-NAME = testapp

init:
	git config core.hooksPath .githooks

clean:
	(./code/gradlew -p code clean)

format:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessApply)
		
format-license:
	(./code/gradlew -p code licenseFormat)

checkformat:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessCheck)

checkstyle:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) checkstyle)

# Used by build and test CI workflow
lint: checkformat checkstyle

# Validate version strings match across all versioned files.
# Usage: make version-check TAG=1.0.0
# Optional: CORE=3.6.0 to also validate mavenCoreVersion in gradle.properties
version-check:
	@TAG="$(TAG)"; CORE="$(CORE)"; \
	if [ -z "$$TAG" ]; then echo "TAG is required (ex: make version-check TAG=1.0.0)"; exit 1; fi; \
	MODULE_VERSION=$$(grep -E '^moduleVersion=' code/gradle.properties | cut -d= -f2); \
	BASE_MODULE_VERSION=$${MODULE_VERSION%-SNAPSHOT}; \
	if [ "$$BASE_MODULE_VERSION" != "$$TAG" ]; then echo "gradle.properties moduleVersion ($$MODULE_VERSION) does not match TAG ($$TAG)"; exit 1; fi; \
	if [ -n "$$CORE" ]; then \
		CORE_VERSION=$$(grep -E '^mavenCoreVersion=' code/gradle.properties | cut -d= -f2); \
		if [ "$$CORE_VERSION" != "$$CORE" ]; then echo "gradle.properties mavenCoreVersion ($$CORE_VERSION) does not match CORE ($$CORE)"; exit 1; fi; \
	fi; \
	echo "Version validation passed: $$TAG"

unit-test:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) testPhoneDebugUnitTest)

unit-test-coverage:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) createPhoneDebugUnitTestCoverageReport)

functional-test:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) uninstallPhoneDebugAndroidTest)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) connectedPhoneDebugAndroidTest)

functional-test-coverage:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) uninstallPhoneDebugAndroidTest)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) createPhoneDebugAndroidTestCoverageReport)

# Used by the scheduled E2E functional test workflow.
e2e-functional-test:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) uninstallPhoneDebugAndroidTest)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) connectedPhoneDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adobe.marketing.mobile.flags.FlagFunctionalTests,com.adobe.marketing.mobile.flags.FlagEdgeIdentityFunctionalTests)

javadoc:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) javadocJar)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) dokkaJavadoc)

assemble-phone:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) assemblePhone)

assemble-phone-debug:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME)  assemblePhoneDebug)
		
assemble-phone-release:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) assemblePhoneRelease)

assemble-app:
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME)  assemble)

# Publish a JitPack-style build to Maven Local (used by the build-and-test CI workflow).
ci-publish-maven-local-jitpack: assemble-phone-release
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) publishReleasePublicationToMavenLocal -Pjitpack)

# Stage a snapshot publication (uploaded to Sonatype Central by the maven-snapshot workflow via JReleaser).
ci-publish-staging: clean
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) publish)

# Stage a release publication (uploaded to Sonatype Central by the maven-release workflow via JReleaser).
ci-publish: assemble-phone-release
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) publish -Prelease)
