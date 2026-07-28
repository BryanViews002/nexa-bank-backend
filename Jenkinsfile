pipeline {
    agent any
    stages {
        stage('Clone Repositories') {
            parallel {
                stage('frontend') {
                    steps {
                        dir('frontend') {
                            git credentialsId: 'github-creds', url: 'https://github.com/BryanViews002/nexa-bank-suite.git', branch: 'main'
                        }
                    }
                }
                stage('backend') {
                    steps {
                        dir('backend') {
                            git credentialsId: 'github-creds', url: 'https://github.com/BryanViews002/nexa-bank-backend.git', branch: 'main'
                        }
                    }
                }
            }
        }
        stage('Build Docker Images') {
            parallel {
                stage('frontend') {
                    steps {
                        dir('frontend') {
                            sh 'docker build --no-cache --build-arg VITE_API_URL=https://symmetrical-space-potato-6946qw99r4jq35jj5-3001.app.github.dev -t localhost:5000/nexa-frontend:latest .'
                            sh 'docker push localhost:5000/nexa-frontend:latest'
                        }
                    }
                }
                stage('backend') {
                    steps {
                        dir('backend') {
                            sh 'docker build -t localhost:5000/nexa-backend:latest .'
                            sh 'docker push localhost:5000/nexa-backend:latest'
                        }
                    }
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                dir('backend') {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        sh 'kubectl apply -f k8s/'
                        sh 'kubectl rollout restart deployment/nexa-frontend'
                        sh 'kubectl rollout restart deployment/nexa-backend'
                    }
                }
            }
        }
    }
    post {
        success {
            echo 'Pipeline completed successfully!'
            mail to: 'bryanjoe0012@gmail.com',
                 subject: "✅ Nexa Bank Pipeline SUCCESS - Build #${env.BUILD_NUMBER}",
                 body: "Good news! The pipeline completed successfully.\n\nCheck it out: ${env.BUILD_URL}"
        }
        failure {
            echo 'Pipeline failed!'
            mail to: 'bryanjoe0012@gmail.com',
                 subject: "❌ Nexa Bank Pipeline FAILED - Build #${env.BUILD_NUMBER}",
                 body: "The pipeline failed.\n\nCheck the logs: ${env.BUILD_URL}"
        }
    }
}
