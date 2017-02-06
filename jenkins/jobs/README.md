# Jenkins Jobs
The Jenkins jobs section of the cartridge loader will perform the following actions in this order:

 * If present, source 'generate.sh'
 * Load all XML files present in the xml directory
  * This directory can be used to store template files that 'generate.sh' can convert to XML files ready for this step
 * Load all Job DSL Groovy scripts present in the dsl directory (or any sub-directories)
  * This can be used to define pipeline structures that use Job DSL to define empty jobs for building, code analysis, deploying and testing
 * Once all the jobs have been loaded, the following steps will need to be carried to begin development.
  * The build and regression test jobs will need to be updated with the correct repositories
  * Relevant steps to build will need to be added for each job - this will include shell scripts, SonarQube, archiving artifacts for downstream jobs as well as copying artifacts from upstream jobs
