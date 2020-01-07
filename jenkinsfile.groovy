//def COMPANY_BU
//def AWS_ACCOUNT
//def AWS_IAM_ROLE

pipeline {
    agent {
        docker {
            image 'hashicorp/terraform:latest'
            label 'slave'
            args  '--entrypoint="" -u root -v /opt/jenkins/.aws:/root/.aws --network host'
        }
    }
    options {
        ansiColor('xterm')
        timestamps()
    }
    parameters {
        choice(
            choices: ['plan' , 'apply' , 'show', 'plan-destroy' , 'destroy'],
            description: 'Terraform action to apply',
            name: 'action')
        choice(
            choices: ['BU-01' , 'BU-02' , 'BU-03'],
            description: 'Choose Company BU workdir',
            name: 'COMPANY_BU')
        choice(
            choices: ['aws-account-01' , 'aws-account-02' , 'aws-account-03'],
            description: 'Choose AWS Account',
            name: 'AWS_ACCOUNT')
        choice(
            choices: ['arn:aws:iam::****:role/terraform-role' , 'arn:aws:iam::****:role/terraform-role' , 'arn:aws:iam::****:role/terraform-role'],
            description: 'Choose AWS IAM Role',
            name: 'AWS_IAM_ROLE')
    }
    stages {
        stage('Terraform Workspace') {
            steps {
               script {
                   env.COMPANY_BU = sh (
                   script: '''git diff --dirstat=files,0 HEAD~1 | sed 's/^[ 0-9.]\\+% //g' | cut -d '/' -f2''',
                   returnStdout: true
                   ).trim()
               } //
               echo "${env.COMPANY_BU}"
               script {
                   env.AWS_ACCOUNT = sh (
                   script: '''git diff --dirstat=files,0 HEAD~1 | sed 's/^[ 0-9.]\\+% //g' | cut -d '/' -f3''',
                   returnStdout: true
                   ).trim()
               } //
               echo "${env.AWS_ACCOUNT}"
               }
        }
        stage('init') {
            steps {
                withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'aws --version'
                    sh 'aws sts get-caller-identity'
                    sh 'terraform version'
                    sh 'terraform init -backend-config="bucket=terraform-aws-account-01" -backend-config="key=${COMPANY_BU}/${AWS_ACCOUNT}/terraform.tfstate"'
                    }
                }
            }
        }
        stage('validate') {
            when {
                expression { params.action == 'plan' || params.action == 'apply' || params.action == 'destroy' }
            }
            steps {
               withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'ls -l'
                    sh 'terraform validate'
                    }
                }
            }
        }
        stage('plan') {
            when {
                expression { params.action == 'plan' }
            }
            steps {
               withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'ls -l'
                    sh 'terraform plan'
                    }
                }
            }
        }
        stage('apply') {
            when {
                expression { params.action == 'apply' }
                branch 'master'
            }
            steps {
               withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'terraform plan -out=plan'
                    sh 'terraform apply -auto-approve plan'
                    }
                }
            }
        }
        stage('show') {
            when {
                expression { params.action == 'show' }
            }
            steps {
                withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'terraform show'
                    }
                }
            }
        }
        stage('plan-destroy') {
            when {
                expression { params.action == 'plan-destroy' }
            }
            steps {
                withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'terraform plan -destroy'
                    }
                }
            }
        }
        stage('destroy') {
            when {
                expression { params.action == 'destroy' }
                branch 'master'
            }
            steps {
               withAWS(role: "${AWS_IAM_ROLE}") {
                    dir("company-bu/${env.COMPANY_BU}/${env.AWS_ACCOUNT}") {
                    sh 'terraform destroy -force'
                    }
                }
            }
        }
    }
}
