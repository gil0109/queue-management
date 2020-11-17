stage('OWASP Scan') {
  agent {
      node {
          label "jenkins-agent-zap"
      }
  }
  steps {
      sh '''
          /zap/zap-baseline.py -r index.html -t http://dev-qmsappointment.apps.silver.devops.gov.bc.ca || return_code=$?
          echo "exit value was  - " $return_code
      '''
  }
  post {
    always {
      // publish html
      publishHTML target: [
          allowMissing: false,
          alwaysLinkToLastBuild: false,
          keepAll: true,
          reportDir: '/zap/wrk',
          reportFiles: 'index.html',
          reportName: 'OWASP Zed Attack Proxy'
        ]
    }
  }
}