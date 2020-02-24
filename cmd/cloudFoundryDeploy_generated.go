// Code generated by piper's step-generator. DO NOT EDIT.

package cmd

import (
	"fmt"
	"os"
	"time"

	"github.com/SAP/jenkins-library/pkg/config"
	"github.com/SAP/jenkins-library/pkg/log"
	"github.com/SAP/jenkins-library/pkg/telemetry"
	"github.com/spf13/cobra"
)

type cloudFoundryDeployOptions struct {
	APIEndpoint string `json:"apiEndpoint,omitempty"`
}

// CloudFoundryDeployCommand Performs cf deployment
func CloudFoundryDeployCommand() *cobra.Command {
	metadata := cloudFoundryDeployMetadata()
	var stepConfig cloudFoundryDeployOptions
	var startTime time.Time

	var createCloudFoundryDeployCmd = &cobra.Command{
		Use:   "cloudFoundryDeploy",
		Short: "Performs cf deployment",
		Long:  `Performs cf deployment`,
		PreRunE: func(cmd *cobra.Command, args []string) error {
			startTime = time.Now()
			log.SetStepName("cloudFoundryDeploy")
			log.SetVerbose(GeneralConfig.Verbose)
			return PrepareConfig(cmd, &metadata, "cloudFoundryDeploy", &stepConfig, config.OpenPiperFile)
		},
		Run: func(cmd *cobra.Command, args []string) {
			telemetryData := telemetry.CustomData{}
			telemetryData.ErrorCode = "1"
			handler := func() {
				telemetryData.Duration = fmt.Sprintf("%v", time.Since(startTime).Milliseconds())
				telemetry.Send(&telemetryData)
			}
			log.DeferExitHandler(handler)
			defer handler()
			telemetry.Initialize(GeneralConfig.NoTelemetry, "cloudFoundryDeploy")
			cloudFoundryDeploy(stepConfig, &telemetryData)
			telemetryData.ErrorCode = "0"
		},
	}

	addCloudFoundryDeployFlags(createCloudFoundryDeployCmd, &stepConfig)
	return createCloudFoundryDeployCmd
}

func addCloudFoundryDeployFlags(cmd *cobra.Command, stepConfig *cloudFoundryDeployOptions) {
	cmd.Flags().StringVar(&stepConfig.APIEndpoint, "apiEndpoint", os.Getenv("PIPER_apiEndpoint"), "The api endpoint")

	cmd.MarkFlagRequired("apiEndpoint")
}

// retrieve step metadata
func cloudFoundryDeployMetadata() config.StepData {
	var theMetaData = config.StepData{
		Spec: config.StepSpec{
			Inputs: config.StepInputs{
				Parameters: []config.StepParameters{
					{
						Name:        "apiEndpoint",
						ResourceRef: []config.ResourceReference{},
						Scope:       []string{"PARAMETERS", "STAGES", "STEPS"},
						Type:        "string",
						Mandatory:   true,
						Aliases:     []config.Alias{},
					},
				},
			},
		},
	}
	return theMetaData
}