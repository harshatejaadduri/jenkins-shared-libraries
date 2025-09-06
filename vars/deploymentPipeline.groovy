def call (Map configMap){
    pipeline{
        agent{
            label 'AGENT-1'
        }
        environment{
            ACC_ID = "513993748676"
            appVersion = ''
            REGION = "us-east-1"
            PROJECT = "project"
            COMPONENT = "component" 
        }
        options{
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
        parameters{
            string(name: 'appVersion', description: 'Image version of the application')
            choice(name: 'deploy_to', choices: ['dev','prod','qa'] , description: 'pick up an environment')
        }
        stages{
            stage('Deployment Status'){
            steps{
                script{
                    withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                        def deploymentStatus = sh( returnStdout = true, script: "kubectl rollout status deployment/catalogue --timeout=30sec -n $PROJECT || echo FAILED" ).trim()
                        if (deploymentStatus.contains("successfully rolled out" )) {
                            echo "Deployment is successful"
                        } 
                        else {
                            sh """
                                helm rollback $COMPONENT -n $PROJECT
                            """
                                    def deploymentStatus = sh( returnStdout = true, script: "kubectl rollout status deployment/catalogue --timeout=30sec -n $PROJECT|| echo FAILED" ).trim()
                                if (deploymentStatus.contains("successfully rolled out ")) {
                                    error "Deployment is failure, Rollback success"
                                }
                                else{
                                    error "" "Deployment is Failure, Rollback is Failure, Application is not running"
                                } 
                            }  
                        }          
                    }      
                }
            }
            stage('Deploy'){
            steps{
                script{
                    withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                            sh """
                                aws eks update-kubeconfig --region $REGION --name "k8-$PROJECT-${params.deploy_to}"
                                kubectl create -f namespace.yaml
                                sed -i "s/IMAGE_VERSION/${appVersion}/g" values-${params.deploy_to}.yaml
                                helm upgrade --install $COMPONENT -f values-${params.deploy_to}.yaml -n $PROJECT .
                                """
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