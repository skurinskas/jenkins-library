import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.RuleChain
import util.BasePiperTest
import util.JenkinsCredentialsRule
import util.JenkinsMockStepRule
import util.JenkinsReadYamlRule
import util.JenkinsStepRule
import util.Rules

import static org.junit.Assert.assertFalse

class NpmExecuteEndToEndTestsTest extends BasePiperTest {

    private JenkinsStepRule stepRule = new JenkinsStepRule(this)
    private ExpectedException thrown = ExpectedException.none()
    private JenkinsMockStepRule npmExecuteScriptsRule = new JenkinsMockStepRule(this, 'npmExecuteScripts')
    private JenkinsCredentialsRule credentialsRule = new JenkinsCredentialsRule(this)
    private JenkinsReadYamlRule readYamlRule = new JenkinsReadYamlRule(this)

    @Rule
    public RuleChain ruleChain = Rules
        .getCommonRules(this)
        .around(thrown)
        .around(readYamlRule)
        .around(credentialsRule)
        .around(stepRule)
        .around(npmExecuteScriptsRule)

    @Before
    void init() {
        helper.registerAllowedMethod("deleteDir", [], null)

        credentialsRule.reset()
            .withCredentials('testCred', 'test_cf', '********')
            .withCredentials('testCred2', 'test_other', '**')
    }

    //TODO Add tests for config validation, and other error code paths

    @Test
    void e2eTestNoAppUrl() {
        thrown.expect(hudson.AbortException)
        thrown.expectMessage('[npmExecuteEndToEndTests] The execution failed, since no appUrls are defined. Please provide appUrls as a list of maps.')

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )
    }

    @Test
    void e2eTestNoRunScript() {
        def appUrl = [url: "http://my-url.com"]

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: [appUrl]
        ]]]

        thrown.expect(hudson.AbortException)
        thrown.expectMessage('[npmExecuteEndToEndTests] No runScript was defined.')

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage"
        )
    }

    @Test
    void e2eTestAppUrlsNoList() {
        def appUrl = "http://my-url.com"

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: appUrl
        ]]]

        thrown.expect(hudson.AbortException)
        thrown.expectMessage("[npmExecuteEndToEndTests] The execution failed, since appUrls is not a list. Please provide appUrls as a list of maps.")

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )
    }

    @Test
    void e2eTestAppUrlsNoMap() {
        def appUrl = "http://my-url.com"

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: [appUrl]
        ]]]

        thrown.expect(hudson.AbortException)
        thrown.expectMessage("[npmExecuteEndToEndTests] The element ${appUrl} is not of type map. Please provide appUrls as a list of maps.")

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )
    }

    @Test
    void e2eTestWithOneAppUrl() {
        def appUrl = [url: "http://my-url.com"]

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: [appUrl]
        ]]]

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )

        assert npmExecuteScriptsRule.hasParameter('script', nullScript)
        assert npmExecuteScriptsRule.hasParameter('parameters', [dockerOptions: ['--shm-size 512MB']])
        assert npmExecuteScriptsRule.hasParameter('install', false)
        assert npmExecuteScriptsRule.hasParameter('runScripts', ["ci-e2e"])
        assert npmExecuteScriptsRule.hasParameter('scriptOptions', ["--launchUrl=${appUrl.url}"])
    }

    @Test
    void e2eTestWithOneAppUrlWithCredentials() {
        def appUrl = [url: "http://my-url.com", credentialId: 'testCred']

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: [appUrl]
        ]]]

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )

        assert npmExecuteScriptsRule.hasParameter('script', nullScript)
        assert npmExecuteScriptsRule.hasParameter('parameters', [dockerOptions: ['--shm-size 512MB']])
        assert npmExecuteScriptsRule.hasParameter('install', false)
        assert npmExecuteScriptsRule.hasParameter('runScripts', ["ci-e2e"])
        assert npmExecuteScriptsRule.hasParameter('scriptOptions', ["--launchUrl=${appUrl.url}"])
    }

    @Test
    void e2eTestWithTwoAppUrlWithCredentials() {
        def appUrl = [url: "http://my-url.com", credentialId: 'testCred']
        def appUrl2 = [url: "http://my-second-url.com", credentialId: 'testCred2']

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: [appUrl, appUrl2]
        ]]]

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )

        assert npmExecuteScriptsRule.hasParameter('script', nullScript)
        assert npmExecuteScriptsRule.hasParameter('parameters', [dockerOptions: ['--shm-size 512MB']])
        assert npmExecuteScriptsRule.hasParameter('install', false)
        assert npmExecuteScriptsRule.hasParameter('runScripts', ["ci-e2e"])
        assert npmExecuteScriptsRule.hasParameter('scriptOptions', ["--launchUrl=${appUrl.url}"])
        assert npmExecuteScriptsRule.hasParameter('scriptOptions', ["--launchUrl=${appUrl2.url}"])
    }

    @Test
    void e2eTestWithOneAppUrlWithCredentialsAndParameters() {
        def appUrl = [url: "http://my-url.com", credentialId: 'testCred', parameters: '--tag scenario1 --NIGHTWATCH_ENV=chrome']

        nullScript.commonPipelineEnvironment.configuration = [stages: [myStage:[
            appUrls: [appUrl]
        ]]]

        stepRule.step.npmExecuteEndToEndTests(
            script: nullScript,
            stageName: "myStage",
            runScript: "ci-e2e"
        )

        assert npmExecuteScriptsRule.hasParameter('script', nullScript)
        assert npmExecuteScriptsRule.hasParameter('parameters', [dockerOptions: ['--shm-size 512MB']])
        assert npmExecuteScriptsRule.hasParameter('install', false)
        assert npmExecuteScriptsRule.hasParameter('runScripts', ["ci-e2e"])
        assert npmExecuteScriptsRule.hasParameter('scriptOptions', ["--launchUrl=${appUrl.url}", appUrl.parameters])
    }
}
