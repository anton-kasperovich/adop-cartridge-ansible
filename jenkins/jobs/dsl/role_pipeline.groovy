import base.AnsibleCartridge

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def variables = [
    gitUrl                  : 'ssh://jenkins@gerrit:29418/${GERRIT_PROJECT}',
    gitBranch               : 'master',
    gitCredentials          : 'adop-jenkins-master',
    gerritTriggerRegExp     : (projectFolderName + '/.*role.*').replaceAll("/", "\\\\/"),
    buildSlave              : 'docker',
    artifactName            : 'ROLE',
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
def getRoleJob = AnsibleCartridge.getBuildFromSCMJob(
    AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Get_Role', variables),
    variables + [
        'artifactDefaultValue': 'adop-cartridge-ansible-reference-role',
        'triggerDownstreamJob': projectFolderName + '/Role_Sanity_Test',
    ]
)

def sanityTestJob = AnsibleCartridge.getSanityTestJob(
    AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Role_Sanity_Test', variables),
    variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_Role',
        'triggerDownstreamJob': projectFolderName + '/Role_Integration_Test'
    ]
)

def integrationTestJob = AnsibleCartridge.getIntegrationTestJob(
    AnsibleCartridge.baseCartridgeJob(this, projectFolderName + '/Role_Integration_Test', variables),
    variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_Role'
    ]
)

// Views
def rolePipelineView = AnsibleCartridge.basePipelineView(
    this,
    projectFolderName + '/Role_Pipeline',
    projectFolderName + '/Get_Role',
    'Role Pipeline'
)
