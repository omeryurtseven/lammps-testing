@Library(value='lammps_testing', changelog=false)
import org.lammps.ci.Utils

def project_url = 'https://github.com/lammps/lammps.git'
def set_github_status = true
def send_slack = true

def lammps_testing_branch = "master"

node('atlas2') {
    def utils = new Utils()


    stage('Checkout') {
        dir('lammps') {
            branch_name = "origin-pull/pull/${env.GITHUB_PR_NUMBER}/merge"
            refspec = "+refs/pull/${env.GITHUB_PR_NUMBER}/merge:refs/remotes/origin-pull/pull/${env.GITHUB_PR_NUMBER}/merge"
            checkout changelog: true, poll: true, scm: [$class: 'GitSCM', branches: [[name: branch_name]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanCheckout'], [$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'lammps-jenkins', name: 'origin-pull', refspec: refspec, url: project_url]]]
        }

        dir('lammps-testing') {
            checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: "*/${lammps_testing_branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'lammps-jenkins', url: 'https://github.com/lammps/lammps-testing']]]
        }
    }

    if (set_github_status) {
        utils.setGitHubCommitStatus(project_url, env.JOB_NAME, env.GITHUB_PR_HEAD_SHA, 'building...', 'PENDING')
    }

    def yaml_files = findFiles glob: 'lammps-testing/scripts/*.yml'


    def configurations = yaml_files.collectEntries { yaml_file -> get_configuration(yaml_file)  }

    def jobs = [:]
    def err = null
    def ccache_dir = ".ccache"

    try {
        configurations.each { container, config ->
            if(config.unit_tests.size() > 0) {
                jobs[container] = config.unit_tests.collectEntries { build ->
                    ["${build}": launch_build("${container}/unit_tests/${build}", env.GITHUB_PR_HEAD_SHA, env.GITHUB_PR_NUMBER, env.WORKSPACE, ccache_dir)]
                }

                stage(config.display_name) {
                    echo "Running ${config.display_name}"
                    parallel jobs[container]
                }
            }
        }
    } catch (caughtErr) {
        err = caughtErr
        currentBuild.result = 'FAILURE'
    } finally {
        if (currentBuild.result == 'FAILURE') {
            if (set_github_status) {
                utils.setGitHubCommitStatus(project_url, env.JOB_NAME, env.GITHUB_PR_HEAD_SHA, 'build failed!', 'FAILURE')
            }
            if (send_slack) {
                slackSend color: 'bad', message: "Build <${env.BUILD_URL}|#${env.BUILD_NUMBER}> of ${env.JOB_NAME} failed!"
            }
        } else {
            if (set_github_status) {
                utils.setGitHubCommitStatus(project_url, env.JOB_NAME, env.GITHUB_PR_HEAD_SHA, 'build successful!', 'SUCCESS')
            }
            if (send_slack) {
                slackSend color: 'good', message: "Build <${env.BUILD_URL}|#${env.BUILD_NUMBER}> of ${env.JOB_NAME} succeeded!"
            }
        }

        if(err) {
            throw err
        }
    }
}

def get_configuration(yaml_file) {
    def name = yaml_file.name.take(yaml_file.name.lastIndexOf('.'))
    def config  = readYaml(file: yaml_file.path)

    def display_name = name
    def builds = []
    def run_tests  = []
    def regression_tests = []
    def unit_tests = []

    if(config.containsKey('display_name')) {
        display_name = config.display_name.toString()
    }

    if(config.containsKey('builds')) {
        builds = config.builds.collect({ it.toString() })
    }

    if(config.containsKey('run_tests')) {
        run_tests = config.run_tests.collect({ it.toString() })
    }

    if(config.containsKey('regression_tests')) {
        regression_tests = config.regression_tests.collect({ it.toString() })
    }

    if(config.containsKey('unit_tests')) {
        unit_tests = config.unit_tests.collect({ it.toString() })
    }

    return ["${name}": [
        "display_name": display_name,
        "builds": builds,
        "run_tests": run_tests,
        "regression_tests": regression_tests,
        "unit_tests": unit_tests,
    ]]
}

def launch_build(job_name, commit, pr, workspace, ccache_dir) {
    return {
        build job: job_name, parameters: [ string(name: 'GIT_COMMIT', value: commit), string(name: 'GITHUB_PR_NUMBER', value: pr), string(name: 'WORKSPACE_PARENT', value: workspace), string(name: 'CCACHE_DIR', value: ccache_dir) ]
    }
}
