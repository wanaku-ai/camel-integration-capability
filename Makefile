early-build:
	gh workflow run early-access -f currentDevelopmentVersion=`cat camel-integration-capability-common/target/classes/cic-version.txt`