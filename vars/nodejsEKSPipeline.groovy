def call(Map configMap){
    pipeline{
        agent{
            label 'AGENT-1'
        }
        environment{
            ACC_ID = "513993748676"
            appVersion = ''
            REGION = "us-east-1"
            PROJECT = configMap.get('project')
            COMPONENT = configMap.get('component')
        }   
        options{
            disableConcurrentBuilds()
        }
        parameters{
            booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
        }
        stages{
            stage('Read JSON'){
                steps{
                    script {
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "Package version: ${appVersion}"
                    }
                }
            }
            stage('Install dependencies'){
                steps{
                    script{
                        sh """
                            npm audit fix --force
                            npm install
                            
                            """
                    }
                }
            }
            /* stage('Sonar Scan') {
                environment {
                    scannerHome = tool 'sonar-7.2'
                }
                steps {
                    script {
                    // Sonar Server envrionment
                    withSonarQubeEnv(installationName: 'sonar-7.2') {
                            sh "${scannerHome}/bin/sonar-scanner"
                    }
                    }
                }
            } */
            /* stage('Quality Gate') {
                steps {
                    timeout(time: 1, unit: 'MINUTES') {
                        // This will pause the pipeline until SonarQube analysis is done
                        // and fail the build if Quality Gate fails
                        waitForQualityGate abortPipeline: true
                    }
                }
            } */
            /* stage('Check Dependabot Alerts') {
                environment { 
                    GITHUB_TOKEN = credentials('github-token')
                }
                steps {
                    script {
                        // Fetch alerts from GitHub
                        def response = sh(
                            script: """
                                curl -s -H "Accept: application/vnd.github+json" \
                                    -H "Authorization: token ${GITHUB_TOKEN}" \
                                    https://api.github.com/repos/daws-84s/${COMPONENT}/dependabot/alerts
                            """,
                            returnStdout: true
                        ).trim()

                        // Parse JSON
                        def json = readJSON text: response

                        // Filter alerts by severity
                        def criticalOrHigh = json.findAll { alert ->
                            def severity = alert?.security_advisory?.severity?.toLowerCase()
                            def state = alert?.state?.toLowerCase()
                            return (state == "open" && severity == "critical" || severity == "high")
                            }

                        if (criticalOrHigh.size() > 0) {
                            error "❌ Found ${criticalOrHigh.size()} HIGH/CRITICAL Dependabot alerts. Failing pipeline!"
                        } else {
                            echo "✅ No HIGH/CRITICAL Dependabot alerts found."
                        }
                    }
                }
            } */
            stage('Docker Build'){
                steps{
                    script{
                        withAWS(credentials: 'aws-creds', region: 'us-east-1') {
                            sh """
                                aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                                docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                REPO_NAME="${PROJECT}/${COMPONENT}"

                                if ( ! aws ecr describe-repositories --repository-names "$REPO_NAME" --region "$REGION" ); then
                                    echo "Repository $REPO_NAME does not exist. Creating..."
                                    aws ecr create-repository --repository-name "$REPO_NAME" --region "$REGION"
                                else
                                    echo "Repository $REPO_NAME already exists. Skipping creation."
                                fi

                                docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                                """
                        }      
                    }
                }
            }
            stage('Trigger Deploy'){
                when{
                        expression{ params.deploy }
                        }
                steps {
                    script{
                        build job: '${COMPONENT}-cd',
                        parameters: [
                            string(name: 'appVersion', value: "${appVersion}"),
                            string(name: 'deploy_to', value: 'dev')
                            ],
                        wait: false
                    }
                }
        }
        }
        post{
            always{
                echo 'Hello World'
                deleteDir()
            }
            success{
                echo 'Code Success'
            } 
            failure{
                echo 'Code Failed'
            }
        }   
    }
}