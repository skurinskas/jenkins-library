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
     * Each element of appUrls must be a map containing a property url, an optional property credentialId, and an optional property parameters.
     * The optional property parameters can be used to pass additional parameters to the end-to-end test deployment reachable via the given application URL.
     * These parameters must be a list of strings, where each string corresponds to one element of the parameters.
     * For example, if the parameter `--tag scenario1` should be passed to the test, specify parameters: ["--tag", "scenario1"].
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

        if (!config.appUrls) {
            error "[${STEP_NAME}] The execution failed, since no appUrls are defined. Please provide appUrls as a list of maps."
        }
        if (!(config.appUrls instanceof List)) {
            error "[${STEP_NAME}] The execution failed, since appUrls is not a list. Please provide appUrls as a list of maps."
        }
        if (!config.runScript) {
            error "[${STEP_NAME}] No runScript was defined."
        }

        for (int i = 0; i < config.appUrls.size(); i++) {
            List credentials = []

            def appUrl = config.appUrls[i]

            if (!(appUrl instanceof Map)) {
                error "[${STEP_NAME}] The element ${appUrl} is not of type map. Please provide appUrls as a list of maps."
            }

            if (!appUrl.url) {
                error "[${STEP_NAME}] No url property was defined for the following element in appUrls: ${appUrl}"
            }

            println("Thats the appUrl: ${appUrl}")
            if (appUrl.credentialId) {
                println("We'll add the credentials to the credentials list now.")
                credentials.add([$class: 'UsernamePasswordMultiBinding', credentialsId: appUrl.credentialId, passwordVariable: 'e2e_password', usernameVariable: 'e2e_username'])
                println("Thats the credentials list now: ${credentials}")
            }
            Closure e2eTest = {
                Utils utils = new Utils()
                utils.unstashStageFiles(script, stageName)
                try {
                    println("now withCredentials: ${credentials}")
                    withCredentials(credentials) {
                        if (appUrl.parameters) {
                            if (appUrl.parameters instanceof List) {
                                println("appUrl.parameters is set")
                                npmExecuteScripts(script: script, parameters: npmParameters, install: false, runScripts: [config.runScript], scriptOptions: ["--launchUrl=${appUrl.url}", appUrl.parameters])
                            } else {
                                error "[${STEP_NAME}] The parameters property is not of type list. Please provide parameters as a list of strings."
                            }

                        }
                        println("Thats the URL: ${appUrl.url}")
                        println("appUrl.parameters is not set")
                        npmExecuteScripts(script: script, parameters: npmParameters, install: false, runScripts: [config.runScript], scriptOptions: ["--launchUrl=${appUrl.url}"])
                    }

                } catch (Exception e) {
                   error "[${STEP_NAME}] The execution failed with error: ${e.getMessage()}"
                } finally {
                    //TODO: Fix Report handling

                    //archiveArtifacts artifacts: "${s4SdkGlobals.endToEndReports}/**", allowEmptyArchive: true

                    List cucumberFiles = findFiles(glob: "**/e2e/*.json")
                    List junitFiles = findFiles(glob: "**/e2e/*.xml")

                    if(cucumberFiles.size()>0){
                        // TODO what about archiveArtifacts?
                        step($class: 'CucumberTestResultArchiver', testResults: "**/e2e/*.json")
                    }
                    else if(junitFiles.size()>0){
                        testsPublishResults script: script, junit: [updateResults: true, archive: true]

                    } else {
                        error("No JUnit or cucumber report files found in ${s4SdkGlobals.endToEndReports}")
                    }
                    //testsPublishResults script: script, junit: [updateResults: true]
                    //println("Implement the report handling please")
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
        runClosures(e2ETests, config)
    }
}

def runClosures(Map toRun, Map config) {
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

