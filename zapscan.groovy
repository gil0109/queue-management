def owaspPodLabel = "owasp-zap-${UUID.randomUUID().toString()}"
podTemplate(
    label: owaspPodLabel, 
    name: owaspPodLabel, 
    serviceAccount: 'jenkins', 
    cloud: 'openshift', 
    containers: [ containerTemplate(
        name: 'jnlp',
        image: '172.50.0.2:5000/openshift/jenkins-slave-zap',
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
                script: "/zap/zap-baseline.py -r index.html -t http://dev-qmsappointment.apps.silver.devops.gov.bc.ca"
            )
            publishHTML([
                allowMissing: false, 
                alwaysLinkToLastBuild: false, 
                keepAll: true, 
                reportDir: '/zap/wrk', 
                reportFiles: 'index.html', 
                reportName: 'OWASP Zed Attack Proxy', 
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