#!/usr/bin/env groovy
def app="meta-migration"
def date=""
def gitShortCommit=""
pipeline {
    agent {
        kubernetes {
            defaultContainer 'main'
            yamlFile "ci/jenkins/pod/meta-migration.yaml"
            customWorkspace '/home/jenkins/agent/workspace'
        }
    }

    options {
        timestamps()
        timeout(time: 36, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        HARBOR_REPO = "harbor.milvus.io"
        CI_DOCKER_CREDENTIAL_ID="harbor-milvus-io-registry"
    }

    stages {
            stage('Publish Meta Migration Images') {
                steps {
                    container(name: 'build',shell: '/bin/sh') {
                        script {
                            sh "make meta-migration"
                        }
                    }
                    container('main'){
                        script {
                            date=sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                            gitShortCommit=sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                            sh 'printenv'
                            def tag="${date}-${gitShortCommit}"
                            def image="${env.HARBOR_REPO}/milvus/${app}:${tag}"
                            withCredentials([usernamePassword(credentialsId: "${env.CI_DOCKER_CREDENTIAL_ID}", usernameVariable: 'CI_REGISTRY_USERNAME', passwordVariable: 'CI_REGISTRY_PASSWORD')]){
                                    sh "docker login ${env.HARBOR_REPO} -u '${CI_REGISTRY_USERNAME}' -p '${CI_REGISTRY_PASSWORD}'"
                                    sh """
                                        docker build -t  ${image} -f build/docker/meta-migration/Dockerfile .
                                        docker push ${image}
                                        docker logout
                                    """
                            }
                        }
                    }
                }

            }
    }
    post {
        unsuccessful {
            container('jnlp') {
                script {
                    emailext subject: '$DEFAULT_SUBJECT',
                    body: '$DEFAULT_CONTENT',
                    recipientProviders: [developers(), culprits()],
                    replyTo: '$DEFAULT_REPLYTO',
                    // to: "${authorEmail},qa@zilliz.com,devops@zilliz.com"
                    to: "jing.li@zilliz.com"
                }
            }
        }
    }
}