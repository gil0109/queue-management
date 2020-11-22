podTemplate(label: 'jenkins-agent-zap', name: 'jenkins-agent-zap', serviceAccount: 'jenkins', cloud: 'openshift', containers: [
  containerTemplate(
    name: 'jnlp',
    image: 'image-registry.openshift-image-registry.svc:5000/5c0dde-tools/jenkins-agent-zap:latest',
    resourceRequestCpu: '500m',
    resourceLimitCpu: '1000m',
    resourceRequestMemory: '3Gi',
    resourceLimitMemory: '4Gi',
    workingDir: '/home/jenkins',
    command: '',
    args: '${computer.jnlpmac} ${computer.name}'
  )
]) {
  stage('OWASP Scan') {
    node('jenkins-agent-zap') {
        //the checkout is mandatory
        echo "checking out source"
        echo "Build: ${BUILD_ID}"
        checkout scm
        dir('zap') {
            def retVal = sh returnStatus: true, script: '/zap/zap-baseline.py -r index.html -t https://dev-theq.apps.silver.devops.gov.bc.ca/'
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: '/zap/wrk', reportFiles: 'index.html', reportName: 'ZAP Full Scan', reportTitles: 'ZAP Full Scan'])
            echo "Return value is: ${retVal}"
            }
    }
  }
}