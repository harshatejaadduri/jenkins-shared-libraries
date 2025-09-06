def call (Map configMap){
    pipeline{
        agent{
            label 'AGENT-1'
        }
        environment{
            ACC_ID = "513993748676"
            appVersion = '1.0.0'
            REGION = "us-east-1"
            PROJECT = "project"
            COMPONENT = "component" 
        }
        options{
            disableConcurrentBuilds()
        }
        parameters{
            string(name: 'appVersion', description: 'Image version of the application')
            choice(name: 'deploy_to', choices: ['dev','prod','qa'] , description: 'pick up an environment')
        }
        stages{
            stage('Deploy'){
            steps{
                script{
                    withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                            sh """
                                aws eks update-kubeconfig --region $REGION --name "${PROJECT}-${params.deploy_to}"
                                kubectl apply -f namespace.yaml
                                helm upgrade --install $COMPONENT -f values-${params.deploy_to}.yaml -n $PROJECT .
                                """
                            }
                    }      
                }
            }
            stage('Check Status'){
                steps{
                    script{
                        withAWS(credentials: 'aws-creds', region: 'us-east-1') {
                            def deploymentStatus = sh(returnStdout: true, script: "kubectl rollout status deployment/catalogue --timeout=30s -n $PROJECT || echo FAILED").trim()
                            if (deploymentStatus.contains("successfully rolled out")) {
                                echo "Deployment is success"
                            } 
                            else {
                                sh """
                                    helm rollback $COMPONENT -n $PROJECT
                                    sleep 20
                                """
                                def rollbackStatus = sh(returnStdout: true, script: "kubectl rollout status deployment/catalogue --timeout=30s -n $PROJECT || echo FAILED").trim()
                                if (rollbackStatus.contains("successfully rolled out")) {
                                    error "Deployment is Failure, Rollback Success"
                                }
                                else{
                                    error "Deployment is Failure, Rollback Failure. Application is not running"
                                }
                            }

                        }
                    }
                }
            }
            
        }
        post{
            always{
                echo "====++++always++++===="
            }
            success{
                echo "====++++only when successful++++===="
            }
            failure{
                echo "====++++only when failed++++===="
            }
        }
    }   
}