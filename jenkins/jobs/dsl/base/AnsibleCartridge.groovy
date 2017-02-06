package base

/**
 * Ansible Cartridge base class for both pipelines role and playbook
 */
class AnsibleCartridge {

    static baseCartridgeJob(dslFactory, jobName, variables) {
        dslFactory.freeStyleJob(jobName) {
            label(variables.buildSlave)
            environmentVariables {
                env('WORKSPACE_NAME', variables.workspaceFolderName)
                env('PROJECT_NAME', variables.projectFolderName)
                env('ABSOLUTE_JENKINS_HOME', variables.absoluteJenkinsHome)
                env('ABSOLUTE_JENKINS_SLAVE_HOME', variables.absoluteJenkinsSlaveHome)
                env('ABSOLUTE_WORKSPACE', variables.absoluteWorkspace)
            }
            wrappers {
                sshAgent(variables.sshAgentName)
                timestamps()
                maskPasswords()
                colorizeOutput()
                preBuildCleanup()
                injectPasswords {
                    injectGlobalPasswords(false)
                    maskPasswordParameters(true)
                }
            }
            logRotator {
                numToKeep(variables.logRotatorNum)
                artifactNumToKeep(variables.logRotatorArtifactNum)
                daysToKeep(variables.logRotatorDays)
                artifactDaysToKeep(variables.logRotatorArtifactDays)
            }
        }
    }

    static basePipelineView(dslFactory, viewName, jobName, viewTitle) {
        dslFactory.buildPipelineView(viewName) {
            title(viewTitle)
            displayedBuilds(5)
            refreshFrequency(5)
            selectedJob(jobName)
            showPipelineParameters()
            showPipelineDefinitionHeader()
        }
    }

    static void getBuildFromSCMJob(def job, variables) {
        job.with {
            description('This job downloads a ' + variables.artifactName.toLowerCase() + '. Also job have Gerrit trigger with regexp configuration to capture events "' + variables.artifactName.toLowerCase() + '" in the repository name.')
            parameters {
                stringParam(variables.artifactName, variables.artifactDefaultValue, 'Name of the ' + variables.artifactName.toLowerCase() + ' you want to load')
            }
            environmentVariables {
                env('WORKSPACE_NAME', variables.workspaceFolderName)
                env('PROJECT_NAME', variables.projectFolderName)
                groovy('''
        if (!binding.variables.containsKey('GERRIT_PROJECT')) {
            return [GERRIT_PROJECT: "''' + variables.projectFolderName + '''/$''' + variables.artifactName + '''"]
        }'''.stripMargin())
            }
            scm {
                git {
                    remote {
                        url(variables.gitUrl)
                        credentials(variables.gitCredentials)
                    }
                    branch('*/' + variables.gitBranch)
                }
            }
            configure { node ->
                node / 'properties' / 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' / 'projectNameList' {
                    'string' '*Role*'
                    'string' '*Playbook*'
                }
            }
            steps {
                shell('''set +x
                    |git_data=$(git --git-dir "${WORKSPACE}/.git" log -1 --pretty="format:%an<br/>%s%b")
                    |echo "GIT_LOG_DATA=${git_data}" > git_log_data.properties
                    '''.stripMargin())
                environmentVariables {
                    propertiesFile('git_log_data.properties')
                }
            }
            steps {
                systemGroovyCommand('''
                                |import hudson.model.*;
                                |import hudson.util.*;
                                |
                                |// Get current build number
                                |def currentBuildNum = build.getEnvironment(listener).get('BUILD_NUMBER')
                                |println "Build Number: " + currentBuildNum
                                |
                                |// Get Git Data
                                |def gitData = build.getEnvironment(listener).get('GIT_LOG_DATA')
                                |println "Git Data: " + gitData;
                                |
                                |def currentBuild = Thread.currentThread().executable;
                                |def oldParams = currentBuild.getAction(ParametersAction.class)
                                |
                                |// Update the param
                                |def params = [ new StringParameterValue("T",gitData), new StringParameterValue("B",currentBuildNum) ]
                                |
                                |// Remove old params - Plugins inject variables!
                                |currentBuild.actions.remove(oldParams)
                                |currentBuild.addAction(new ParametersAction(params));
                                '''.stripMargin())
            }
            triggers {
                gerrit {
                    events {
                        refUpdated()
                    }
                    project('reg_exp:' + variables.gerritTriggerRegExp, 'plain:master')
                    configure { node ->
                        node / serverName('ADOP Gerrit')
                    }
                }
            }
            publishers {
                archiveArtifacts('**/*')
                downstreamParameterized {
                    trigger(variables.triggerDownstreamJob) {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('B', '${BUILD_NUMBER}')
                            predefinedProp('PARENT_BUILD', '${JOB_NAME}')
                        }
                    }
                }
            }
        }
    }

    static getSanityTestJob(def job, variables) {
        job.with {
            description('This job runs sanity checks on a playbook/role.')
            parameters {
                stringParam('B', '', 'Parent build number')
                stringParam('PARENT_BUILD', '', 'Parent build name')
            }
            steps {
                copyArtifacts(variables.copyArtifactsFromJob) {
                    buildSelector {
                        buildNumber('${B}')
                    }
                }
                shell('''#!/bin/bash
                    |set -x
                    |
                    |docker run --rm -t \\
                    |   -v ${ABSOLUTE_WORKSPACE}:/ansible \\
                    |   iniweb/ansible:0.3.0 sh -c "ansible-lint -p /ansible/*.yml"
                    '''.stripMargin())
            }
            publishers {
                downstreamParameterized {
                    trigger(variables.triggerDownstreamJob) {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('B', '${B}')
                            predefinedProp('PARENT_BUILD', '${JOB_NAME}')
                        }
                    }
                }
            }
        }

        return job
    }

    static getIntegrationTestJob(def job, variables) {
        job.with {
            description('''This job executes integration tests on the Ansible playbook/role using Test-Kitchen.
Drivers supported: ec2, docker.
Provisioners supported: Ansible.'''
            )
            parameters {
                stringParam('B', '', 'Parent build number')
                stringParam('PARENT_BUILD', '', 'Parent build name')
            }
            steps {
                copyArtifacts(variables.copyArtifactsFromJob) {
                    buildSelector {
                        buildNumber('${B}')
                    }
                }
                systemGroovyCommand('''
                                    |// Check if Role/Playbook have .kitchen.yml file
                                    |if(build.workspace.isRemote()) {
                                    |    kitchenFile = new hudson.FilePath(build.workspace.channel, build.workspace.toString() + '/.kitchen.yml')
                                    |} else {
                                    |    kitchenFile = new hudson.FilePath(new File(build.workspace.toString() + '/.kitchen.yml'))
                                    |}
                                    |
                                    |// If it doesn't have .kitchen.yml mark build as UNSTABLE
                                    |if(!kitchenFile.exists()) {
                                    |    println "Repository does not have any unit tests implemented"
                                    |    build.result = hudson.model.Result.fromString('UNSTABLE')
                                    |}
                                    |'''.stripMargin())
                conditionalSteps {
                    condition {
                        fileExists('.kitchen.yml', BaseDir.WORKSPACE)
                    }
                    runner('Fail')
                    steps {
                        shell('''|#!/bin/bash
                            |set +ex
                            |
                            |docker run --rm -i -v /var/run/docker.sock:/var/run/docker.sock \\
                            |    --net=host \\
                            |    -v ${ABSOLUTE_WORKSPACE}:/kitchen \\
                            |    iniweb/test-kitchen-ansible:0.0.1 kitchen test
                            |
                            |test_kitchen_status=$?
                            |
                            |docker run --rm -i -v /var/run/docker.sock:/var/run/docker.sock \\
                            |    --net=host \\
                            |    -v ${ABSOLUTE_WORKSPACE}:/kitchen \\
                            |    iniweb/test-kitchen-ansible:0.0.1 kitchen destroy
                            |
                            |exit ${test_kitchen_status}
                            |
                            '''.stripMargin())
                    }
                }
            }
        }

        return job
    }
}
