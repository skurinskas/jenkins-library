import com.sap.piper.ConfigurationHelper
import com.sap.piper.DownloadCacheUtils
import com.sap.piper.GenerateDocumentation
import com.sap.piper.k8s.ContainerMap
import groovy.transform.Field
import com.sap.piper.Utils

import static com.sap.piper.Prerequisites.checkScript

@Field String STEP_NAME = getClass().getName()

@Field Set GENERAL_CONFIG_KEYS = [
    /** Executes the deployments in parallel.*/
    'parallelExecution'
]
@Field Set STEP_CONFIG_KEYS = GENERAL_CONFIG_KEYS.plus([
    /**
     * The URLs under which the app is available after deployment.
     * Each appUrl can be a string with the URL or a map containing a property url, a property credentialId, and an optional property parameters.
     * The optional property parameters can be used to pass additional parameters to the end-to-end test deployment reachable via the given application URL.
     * These parameters are appended to the npm command during execution.
     */
    'appUrls',
    /**
     * Script to be executed from package.json.
     */
    'runScript'])
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS
/**
 *
 */

@GenerateDocumentation
void call(Map parameters = [:]) {
    handlePipelineStepErrors(stepName: STEP_NAME, stepParameters: parameters) {
        def script = checkScript(this, parameters) ?: this
        def stageName = parameters.stageName?:env.STAGE_NAME

        Map config = ConfigurationHelper.newInstance(this)
            .loadStepDefaults()
            .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
            .mixinStepConfig(script.commonPipelineEnvironment, STEP_CONFIG_KEYS)
            .mixinStageConfig(script.commonPipelineEnvironment, stageName, STEP_CONFIG_KEYS)
            .mixin(parameters, PARAMETER_KEYS)
            .use()

        // telemetry reporting
        new Utils().pushToSWA([
            step: STEP_NAME,
            stepParamKey1: 'scriptMissing',
            stepParam1: parameters?.script == null
        ], config)
        println("Here we have the config: ${config}")
        def e2ETests = [:]
        def index = 1

        // TODO: test downloadCache usage
        def npmParameters = [:]
        npmParameters.dockerOptions = ['--shm-size 512MB']

        // TODO: add config validation

        for (def appUrl : config.appUrls) {
            List credentials = []

            if (appUrl.credentialId) {
                println("We'll add the credentials to the credentials list now.")
                credentials.add([$class: 'UsernamePasswordMultiBinding', credentialsId: appUrl.credentialId, passwordVariable: 'e2e_password', usernameVariable: 'e2e_username'])
                println("Thats the credentials list now: ${credentials}")
            }
            Closure e2eTest = {
                Utils utils = new Utils()
                utils.unstashStageFiles(script, stageName)
                try {
                    withCredentials(credentials) {
                        // TODO move up to config validation
                        if (appUrl instanceof Map && appUrl.url) {
                            if (appUrl.parameters) {
                                // TODO: See what happens with the appUrl.parameters property (problem of having a string which should atually be a list of parameters handed to the execrunner in the go step later)
                                println("appUrl.parameters is set")
                                npmExecuteScripts(script: script, parameters: npmParameters, install: false, runScripts: ["$config.runScript"], scriptOptions: ["--launchUrl=${appUrl}", appUrl.parameters])
                            }
                            println("appUrl.parameters is not set")
                            npmExecuteScripts(script: script, parameters: npmParameters, install: false, runScripts: ["$config.runScript"], scriptOptions: ["--launchUrl=${appUrl}"])
                        } else {
                            error("Each appUrl in the configuration must be a Map containing a property url. Optionally it can contain a property credentialId and parameters.")
                        }
                    }

                } catch (Exception e) {
                   error "[${STEP_NAME}] The test execution failed with error: ${e.getMessage()}"
                } finally {
                    //TODO: Fix Report handling
                    /*
                    archiveArtifacts artifacts: "${s4SdkGlobals.endToEndReports}/**", allowEmptyArchive: true

                    List cucumberFiles = findFiles(glob: "${s4SdkGlobals.endToEndReports}/*.json")
                    List junitFiles = findFiles(glob: "${s4SdkGlobals.endToEndReports}/*.xml")

                    if(cucumberFiles.size()>0){
                        step($class: 'CucumberTestResultArchiver', testResults: "${s4SdkGlobals.endToEndReports}/*.json")
                    }
                    else if(junitFiles.size()>0){
                        junit allowEmptyResults: true, testResults: "${s4SdkGlobals.endToEndReports}/*.xml"
                    } else {
                        error("No JUnit or cucumber report files found in ${s4SdkGlobals.endToEndReports}")
                    }*/
                    println("Implement the report handling please")
                    utils.stashStageFiles(script, parameters.stage)
                }
            }
            e2ETests["E2E Tests ${index > 1 ? index : ''}"] = {
                if (env.POD_NAME) {
                    dockerExecuteOnKubernetes(script: script, containerMap: ContainerMap.instance.getMap().get(parameters.stage) ?: [:]) {
                        e2eTest.call()
                    }
                } else {
                    node(env.NODE_NAME) {
                        e2eTest.call()
                    }
                }
            }
            index++
        }
        runClosures(e2ETests)
    }
}

def runClosures(Map toRun) {
    echo "Executing end to end tests"
    if (config.parallelExecution) {
        echo "Executing end to end tests in parallel"
        parallel toRun
    } else {
        echo "Executing end to end tests in sequence"
        def closuresToRun = toRun.values().asList()
        for (int i = 0; i < closuresToRun.size(); i++) {
            (closuresToRun[i] as Closure)()
        }
    }
}

