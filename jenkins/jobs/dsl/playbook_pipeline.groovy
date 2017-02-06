import base.AnsibleCartridge

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def variables = [
    gitUrl                  : 'ssh://jenkins@gerrit:29418/${GERRIT_PROJECT}',
    gitBranch               : 'master',
    gitCredentials          : 'adop-jenkins-master',
    gerritTriggerRegExp     : (projectFolderName + '/.*playbook.*').replaceAll("/", "\\\\/"),
    buildSlave              : 'docker',
    artifactName            : 'PLAYBOOK',
    sshAgentName            : 'adop-jenkins-master',
    logRotatorNum           : 10,
    logRotatorArtifactNum   : 3,
    logRotatorDays          : -1,
    logRotatorArtifactDays  : -1,
    projectFolderName       : projectFolderName,
    workspaceFolderName     : workspaceFolderName,
    absoluteJenkinsHome     : '/var/lib/docker/volumes/jenkins_home/_data',
    absoluteJenkinsSlaveHome: '/var/lib/docker/volumes/jenkins_slave_home/_data',
    absoluteWorkspace       : '${ABSOLUTE_JENKINS_SLAVE_HOME}/${JOB_NAME}/',
]

// Jobs
def getPlaybookJob = AnsibleCartridge.getBuildFromSCMJob(
    AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Get_Playbook', variables),
    variables + [
        'artifactDefaultValue'  : '',
        'triggerDownstreamJob'  : projectFolderName + '/Playbook_Sanity_Test',
    ]
)

def sanityTestJob = AnsibleCartridge.getSanityTestJob(
    AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Playbook_Sanity_Test', variables),
    variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_Playbook',
        'triggerDownstreamJob': projectFolderName + '/Playbook_Integration_Test'
    ]
)

def integrationTestJob = AnsibleCartridge.getIntegrationTestJob(
    AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Playbook_Integration_Test', variables),
    variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_Playbook',
        'triggerDownstreamJob': projectFolderName + '/Run_Playbook'
    ]
)

integrationTestJob.with {
    publishers {
        buildPipelineTrigger(projectFolderName + "/Run_Playbook") {
            parameters {
                predefinedProp('B', '${B}')
                predefinedProp('PARENT_BUILD', '${JOB_NAME}')
            }
        }
    }
}

def runPlaybookJob = AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Run_Playbook', variables)

// Views
def playbookPipelineView = AnsibleCartridge.basePipelineView(this, projectFolderName + '/Playbook_Pipeline', projectFolderName + '/Get_Playbook', 'Playbook Pipeline')

// Setup Playbook jobs
runPlaybookJob.with {
    description("Run Ansible Playbook against list of hosts from inventory")
    parameters {
        stringParam('B', '', 'Parent build number')
        stringParam('PARENT_BUILD', '', 'Parent build name')
        stringParam('TAGS', '', 'Limit execution on specific hosts by tags')
        stringParam('INVENTORY', 'hosts', 'Hosts invetory file')
    }
    steps {
        copyArtifacts(projectFolderName + '/Get_Playbook') {
            buildSelector {
                buildNumber('${B}')
            }
        }
        conditionalSteps {
            condition {
                fileExists('requirements.yml', BaseDir.WORKSPACE)
            }
            runner('Fail')
            steps {
                shell('''|#!/bin/bash
                            |set +ex
                            |
                            |ANSIBLE_UID=1000
                            |chown -R ${ANSIBLE_UID}:${ANSIBLE_UID} ${WORKSPACE}
                            |
                            |docker run --rm -t --net=${DOCKER_NETWORK_NAME} \\
                            |   -v ${ABSOLUTE_JENKINS_HOME}/.ssh:/home/ansible/.ssh \\
                            |   -v ${ABSOLUTE_WORKSPACE}:/ansible \\
                            |   -e GIT_SSH_COMMAND='ssh -o KexAlgorithms=+diffie-hellman-group1-sha1' \\
                            |   iniweb/ansible:0.3.0 \\
                            |   sh -c "ansible-galaxy install -r requirements.yml -p /ansible/roles"
                            '''.stripMargin())
            }
        }
        shell('''#!/bin/bash
            |
            |ANSIBLE_UID=1000
            |chown -R ${ANSIBLE_UID}:${ANSIBLE_UID} ${WORKSPACE}
            |
            |docker run --rm -t \\
            |   -v ${ABSOLUTE_JENKINS_HOME}/.ssh:/home/ansible/.ssh \\
            |   -v ${ABSOLUTE_WORKSPACE}:/ansible \\
            |   -e ANSIBLE_HOST_KEY_CHECKING=False \\
            |   iniweb/ansible:0.3.0 \\
            |   sh -c "ansible-playbook -i ${INVENTORY} playbook.yml"
            '''.stripMargin())
    }
}
