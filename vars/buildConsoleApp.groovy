

def call(body){
	def params = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = params
	body()
    if (!params.containsKey("UnitTests")) {
        params.put("UnitTests", []);
    }

    if (!params.containsKey("IntegrationTests")) {
        params.put("IntegrationTests", []);
    }

    if (!params.containsKey("RegressionTests")) {
        params.put("RegressionTests", []);
    }

    // GLOBAL CONSTANTS
    def Deployed = false
    def TagName = ""
    def TestCategoryFilter = "TestCategory=All"
    def BranchName = ""

    // GLOBALS: MSBUILD PARAMS
    def BuildConfig = "Debug"
    def SolutionBuildPlatform = "\"Any CPU\""
    def ProjectBuildPlatform = "\"AnyCPU\""
    def SolutionBuildString = "/p:Configuration=${BuildConfig} /p:Platform=${SolutionBuildPlatform}"
    def ProjectBuildString = "/p:Configuration=${BuildConfig} /p:Platform=${ProjectBuildPlatform}"

    // GLOBALS: PARTIAL PATHS
    def MainProjectFolder = ""

    // GLOBALS: PATHS
    def MainSolutionPath = ""
    def MainProjectPath = ""
    def versionNumber = ""

    pipeline {
        agent any

        stages {
            stage ('Init') {
                steps {
                    script {
                        BranchName = env.BRANCH_NAME

                        if (BranchName == "master" || BranchName == "main") {
                            BuildConfig = "Release"
                            SolutionBuildString = "/p:Configuration=${BuildConfig} /p:Platform=${SolutionBuildPlatform}"
                            ProjectBuildString = "/p:Configuration=${BuildConfig} /p:Platform=${ProjectBuildPlatform}"
                        }

                        // on any non-master build, we also want to run automated tests in the "Dev" category
                        if (BranchName != "master" && BranchName != "main") {
                            TestCategoryFilter = TestCategoryFilter + "|TestCategory=Dev"
                        }

                        // build project paths
                        MainProjectFolder = "${env.WORKSPACE}\\${params.MainProjectName}"
                        if (params.containsKey("MainProjectFolder")) {
                            MainProjectFolder = params.MainProjectFolder
                        }
                        
                        MainSolutionPath = "\"${env.WORKSPACE}\\${params.SolutionName}.sln\""
                        if (params.containsKey("MainSolutionPath")) {
                            MainSolutionPath = params.MainSolutionPath
                        }
                        
                        MainProjectPath = "\"${MainProjectFolder}\\${params.MainProjectName}.csproj\""
                        if (params.containsKey("MainProjectPath")) {
                            MainProjectPath = params.MainProjectPath
                        }

                        versionNumberOutput = bat (
                            script: "${env.BuildScriptsFolder}AssemblyVersionGet.bat ${MainProjectFolder}",
                            returnStdout: true
                        ).trim()

                        try{
                            versionNumber = versionNumberOutput.split()[-1]
                        }
                        catch(Exception ex){
                            
                        }
                        echo "Version Number: ${versionNumber}"
                    }

                    echo "BranchName: ${BranchName}"
                    echo "BuildConfig: ${BuildConfig}"
                    echo "MainProjectName: ${params.MainProjectName}"

                    // prints all environment variables, for debugging purposes
                    // bat "set"
                }
            }
    	}

        post {
        	
            failure {
                script {
                    def emailTo = ""

                    if (BranchName == 'master' || BranchName == 'develop') {
                        emailTo = params.ProjectLeadUsername
                    }

                    emailext (
                        to: emailTo,
                        subject: "${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - FAILED",
                        recipientProviders: [
                            [$class: 'CulpritsRecipientProvider'],
                            [$class: 'RequesterRecipientProvider']
                        ],
                        attachLog: true,
                        body: """
                            Check console output at ${env.BUILD_URL}console to view the results.
                        """
                    )
                }
            }
        }
    }
}
