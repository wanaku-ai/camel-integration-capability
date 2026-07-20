early-build:
	gh workflow run early-access --ref $$(git rev-parse --abbrev-ref HEAD) -f currentDevelopmentVersion=`cat camel-integration-capability-common/target/classes/cic-version.txt`