import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import util.BasePiperTest
import util.JenkinsMockStepRule
import util.JenkinsReadYamlRule
import util.JenkinsStepRule
import util.Rules

class NpmExecuteEndToEndTests extends BasePiperTest {

    private JenkinsStepRule stepRule = new JenkinsStepRule(this)
    private JenkinsMockStepRule npmExecuteScriptsRule = new JenkinsMockStepRule(this, 'npmExecuteScripts')

    @Rule
    public RuleChain ruleChain = Rules
        .getCommonRules(this)
        .around(stepRule)
        .around(npmExecuteScriptsRule)

    @Before
    void init() {}

    @Test
    void testSomething() {}
}
