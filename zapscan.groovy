def owaspPodLabel = "jenkins-agent-zap"
podTemplate(
    label: owaspPodLabel, 
    name: owaspPodLabel, 
    serviceAccount: 'jenkins', 
    cloud: 'openshift', 
    containers: [ containerTemplate(
        name: 'jenkins-agent-zap',
        image: 'image-registry.openshift-image-registry.svc:5000/5c0dde-tools/jenkins-agent-zap:latest',
        resourceRequestCpu: '500m',
        resourceLimitCpu: '1000m',
        resourceRequestMemory: '3Gi',
        resourceLimitMemory: '4Gi',
        workingDir: '/home/jenkins',
        command: '',
        args: '${computer.jnlpmac} ${computer.name}'
    )]
) {
    node(owaspPodLabel) {
        stage('ZAP Security Scan') {          
            def retVal = sh (
                returnStatus: true, 
                script: "/zap/zap-baseline.py -r index.html -t https://dev-qmsappointments.apps.silver.devops.gov.bc.ca"
            )
            publishHTML([
                allowMissing: false, 
                alwaysLinkToLastBuild: false, 
                keepAll: true, 
                reportDir: '/zap/wrk', 
                reportFiles: 'index.html', 
                reportName: 'OWASPReport', 
            ])
            echo "Return value is: ${retVal}"

            script {
                if (retVal != 0) {
                    echo "MARKING BUILD AS UNSTABLE"
                    currentBuild.result = 'UNSTABLE'
                }
            }
        }
    }
  }