// This Jenkins build requires a configmap called jenkin-config with the following in it:
// namespace=<openshift project namespace>

def WAIT_TIMEOUT = 10
def TAG_NAMES = ['dev', 'test', 'prod']
def BUILDS = ['queue-management-api', 'queue-management-npm-build', 'queue-management-frontend', 'appointment-npm-build', 'appointment-frontend','send-appointment-reminder-crond']
def DEP_ENV_NAMES = ['dev', 'test', 'prod']
def label = "mypod-${UUID.randomUUID().toString()}"
def API_IMAGE_HASH = ""
def FRONTEND_IMAGE_HASH = ""
def APPOINTMENT_IMAGE_HASH = ""
def REMINDER_IMAGE_HASH = ""

String getNameSpace() {
    def NAMESPACE = sh (
        script: 'oc describe configmap jenkin-config | awk  -F  "=" \'/^namespace/{print $2}\'',
        returnStdout: true
    ).trim()
    return NAMESPACE
}

// Get an image's hash tag
String getImageTagHash(String imageName, String tag = "") {

  if(!tag?.trim()) {
    tag = "latest"
  }

  def istag = openshift.raw("get istag ${imageName}:${tag} -o template --template='{{.image.dockerImageReference}}'")
  return istag.out.tokenize('@')[1].trim()
}

podTemplate(
    label: label, 
    name: 'jenkins-nodejs', 
    serviceAccount: 'jenkins', 
    cloud: 'openshift', 
    containers: [
        containerTemplate(
            name: 'jnlp',
            image: '172.50.0.2:5000/openshift/jenkins-slave-python3nodejs',
            resourceRequestCpu: '1000m',
            resourceLimitCpu: '2000m',
            resourceRequestMemory: '2Gi',
            resourceLimitMemory: '4Gi',
            workingDir: '/tmp',
            command: '',
            args: '${computer.jnlpmac} ${computer.name}'
        )
    ]
){
    node(label) {

        stage('Checkout Source') {
            echo "checking out source"
            checkout scm
        }
        stage('SonarQube Analysis') {
            echo ">>> Performing static analysis <<<"
            SONAR_ROUTE_NAME = 'sonarqube'
            SONAR_ROUTE_NAMESPACE = sh (
                script: 'oc describe configmap jenkin-config | awk  -F  "=" \'/^namespace/{print $2}\'',
                returnStdout: true
            ).trim()
            SONAR_PROJECT_NAME = 'Queue Management'
            SONAR_PROJECT_KEY = 'queue-management'
            SONAR_PROJECT_BASE_DIR = '../'
            SONAR_SOURCES = './'

            SONARQUBE_PWD = sh (
                script: 'oc set env dc/sonarqube --list | awk  -F  "=" \'/SONARQUBE_KEY/{print $2}\'',
                returnStdout: true
            ).trim()

            SONARQUBE_URL = sh (
                script: 'oc get routes -o wide --no-headers | awk \'/sonarqube/{ print match($0,/edge/) ?  "https://"$2 : "http://"$2 }\'',
                returnStdout: true
            ).trim()

            echo "PWD: ${SONARQUBE_PWD}"
            echo "URL: ${SONARQUBE_URL}"

            dir('sonar-runner') {
                sh (
                    returnStdout: true,
                    script: "./gradlew sonarqube --stacktrace --info \
                        -Dsonar.verbose=true \
                        -Dsonar.host.url=${SONARQUBE_URL} \
                        -Dsonar.projectName='${SONAR_PROJECT_NAME}' \
                        -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                        -Dsonar.projectBaseDir=${SONAR_PROJECT_BASE_DIR} \
                        -Dsonar.login=${SONARQUBE_PWD} \
                        -Dsonar.sources=${SONAR_SOURCES}"
                )
            }
        }
    }
}