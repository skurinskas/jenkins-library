void call(Map parameters = [:]) {
    handlePipelineStepErrors(stepName: 'runClosures', stepParameters: [script: parameters.script]) {
        echo "Executing $parameters.label"
        if (parameters.parallelExecution) {
            echo "Executing $parameters.label in parallel"
            parallel parameters.closures
        } else {
            echo "Executing $parameters.label in sequence"
            def closuresToRun = parameters.closures.values().asList()
            for (int i = 0; i < closuresToRun.size(); i++) {
                (closuresToRun[i] as Closure)()
            }
        }
    }
}
