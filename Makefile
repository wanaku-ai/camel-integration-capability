early-build:
	gh workflow run early-access -f currentDevelopmentVersion=`cat target/classes/version.txt`