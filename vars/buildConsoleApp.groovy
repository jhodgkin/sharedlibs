// EXPECTED CONFIGURATION
// buildSynergyConsoleApp {
//     SolutionName = "ABBYYBridge"
//     MainProjectName = "ABBYYBridgeServiceWinform"
//     ProjectLeadUsername = "aarondavis"
//     GitRepoName = "ABBYYBridge"
//     SourceControlCredentialId = "0adde854-3187-47bc-b9c2-9ef921ae07a7"
//     Deploy = "true"
//     UnitTests = [
//         [
//             testProjectPath: "LaunchCL.Unit.Tests",
//             testDllPath: "LaunchCL.Unit.Tests.dll"
//         ]
//     ]
//     IntegrationTests = [
//          [
//              testProjectPath: "LaunchRulesEngine.Integration.Tests",
//              testDllPath: "LaunchRulesEngine.Integration.Tests.dll"
//          ]
//     ]
//     RegressionTests = [
//         [
//             testProjectPath: "Launch.Web.Selenium.Tests",
//             testDllPath: "Launch.Web.Selenium.Tests.dll",
//             runSettingsPath: "Launch.Web.Selenium.Tests\\Launch.Web.Selenium.Tests.runsettings",
//             // for running selective unit tests, see:
//             //		https://docs.microsoft.com/en-us/dotnet/core/testing/selective-unit-tests
//             // for vstest.console.exe args, see:
//             //		https://docs.microsoft.com/en-us/visualstudio/test/vstest-console-options?view=vs-2017
//             // for vstest.console.exe TestCaseFilter docs, see:
//             //		https://github.com/Microsoft/vstest-docs/blob/master/docs/filter.md
//             // NOTE: example, but no longer needed
//             // testCaseFilter: "ClassName!=Launch.Web.Selenium.Tests.Tests.SillyTests",
//             args: ""
//         ]
//     ]
// }

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

                        // versionNumberOutput = bat (
                        //     script: "${env.BuildScriptsFolder}AssemblyVersionGet.bat ${MainProjectFolder}",
                        //     returnStdout: true
                        // ).trim()

                        // try{
                        //     versionNumber = versionNumberOutput.split()[-1]
                        // }
                        // catch(Exception ex){
                            
                        // }
                        // echo "Version Number: ${versionNumber}"
                    }

                    echo "BranchName: ${BranchName}"
                    echo "BuildConfig: ${BuildConfig}"
                    echo "MainProjectName: ${params.MainProjectName}"

                    // prints all environment variables, for debugging purposes
                    // bat "set"
                }
            }

            stage('Build C#') {
                steps {
                    // restore Nuget packages
                    bat "${env.NugetTool} restore ${MainSolutionPath} ${env.NugetSources}"

                    // build
                    bat "${env.MSBuildTool} ${MainSolutionPath} ${SolutionBuildString} /t:Restore /p:RestoreSources=\"${env.NugetSources_MSBuild}\""
        			bat "${env.MSBuildTool} ${MainSolutionPath} ${SolutionBuildString}"
                }
            }

   			// stage('QC Config') {
      //           steps {
      //               script {
      //               File file = new File("${MainProjectFolder}\\bin\\${BuildConfig}\\AppSettings.xml")
      //               if(file.exists()) {
						//     echo "Checking Config ${MainProjectFolder}"
						//     echo bat (
      //                           script: "python C:\\buildscripts\\Jenkins\\billingServicesConsoleAppConfigQc.py \"${MainProjectFolder}\\bin\\${BuildConfig}\\AppSettings.xml\" ${params.MainProjectName} ${BranchName}",
      //                           returnStdout: true
      //                       )
      //                   }
      //               else{
      //                   echo "Could not find AppSettings file"
      //                   }
      //               }
      //           }
      //       }

            stage('Test C#') {
                steps {
                    script {
                        for(LinkedHashMap test: params.UnitTests) {
                            if (!test.containsKey("args")) {
                                test.args = ""
                            }

                            def testCaseFilterArg = "(${TestCategoryFilter})"
                            if (test.containsKey("testCaseFilter")) {
                                testCaseFilterArg = testCaseFilterArg + "&(${test.testCaseFilter})"
                            }

                            echo bat (
                                script: "${env.MSTestTool} \"${env.WORKSPACE}\\${test.testProjectPath}\\bin\\${BuildConfig}\\${test.testDllPath}\" /Logger:console;verbosity=normal /TestCaseFilter:\"${testCaseFilterArg}\" ${test.args}",
                                returnStdout: true
                            )
                        }

                        for(LinkedHashMap test: params.IntegrationTests) {
                            if (!test.containsKey("args")) {
                                test.args = ""
                            }

                            def testCaseFilterArg = "(${TestCategoryFilter})"
                            if (test.containsKey("testCaseFilter")) {
                                testCaseFilterArg = testCaseFilterArg + "&(${test.testCaseFilter})"
                            }

                            echo bat (
                                script: "${env.MSTestTool} \"${env.WORKSPACE}\\${test.testProjectPath}\\bin\\${BuildConfig}\\${test.testDllPath}\" /Logger:console;verbosity=normal /TestCaseFilter:\"${testCaseFilterArg}\" ${test.args}",
                                returnStdout: true
                            )
                        }
                    }
                }
            }

            stage('Tag Release') {
                when {
                    branch 'master'
                }
                steps {
                    script {
                        // https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/push-git-repo/pushGitRepo.groovy
                        withCredentials([usernamePassword(credentialsId: params.SourceControlCredentialId, passwordVariable: 'GitPassword', usernameVariable: 'GitUsername')]) {
                            // https://www.atlassian.com/git/tutorials/inspecting-a-repository/git-tag
                            TagName = "${params.ProjectName}_${env.BUILD_NUMBER}"

                            echo "TagName: ${TagName}"

                            // workaround; see: https://confluence.atlassian.com/bitbucketserverkb/ssl-certificate-problem-unable-to-get-local-issuer-certificate-816521128.html
                            echo bat (
                                script: "git config --global http.sslVerify false",
                                returnStdout: true
                            )

                            echo bat (
                                script: "git tag ${TagName} -m \"automated build #${env.BUILD_NUMBER} of ${params.DeploySiteName}\"",
                                returnStdout: true
                            )

                            echo bat (
                                script: "git push https://${GitUsername}:${GitPassword}@git.conservice.com/BillingServices/ConsoleApps/${params.GitRepoName}.git ${TagName}",
                                returnStdout: true
                            )
                        }
                    }
                }
            }

            stage('Deploy'){
                when {
                    branch 'master' || branch 'main'
                }
                steps{
                    script {
						File deployPython = new File("deploy.py")
						if(deployPython.exists()) {
							echo bat (
								script: "python \"deploy.py\" \"${env.WORKSPACE}\" \"${params.MainProjectName}\" ${BuildConfig}",
								returnStdout: true
							)
						}else{
							echo "Could not find a deploy.(py,groovy,sh) file."
							returnStdout: failure
						}
                    }
                }
            }
    	}

        // post {

        //     failure {
        //         script {
        //             def emailTo = ""

        //             if (BranchName == 'master' || BranchName == 'develop') {
        //                 emailTo = params.ProjectLeadUsername
        //             }

        //             emailext (
        //                 to: emailTo,
        //                 subject: "${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - FAILED",
        //                 recipientProviders: [
        //                     [$class: 'CulpritsRecipientProvider'],
        //                     [$class: 'RequesterRecipientProvider']
        //                 ],
        //                 attachLog: true,
        //                 body: """
        //                     Check console output at ${env.BUILD_URL}console to view the results.
        //                 """
        //             )
        //         }
        //     }
        // }
    }
}
