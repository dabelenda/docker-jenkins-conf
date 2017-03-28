import jenkins.model.Jenkins
import jenkins.plugins.git.GitSCMSource;
import hudson.model.FreeStyleProject
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.libs.*
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import groovy.json.JsonSlurper;

def env = System.getenv()

def pipeline_default_token = env['JENKINS_GITHUB_PIPELINE_DEFAULT_TOKEN']
def pipeline_config_json = env['JENKONS_PIPELINE_CONFIG']
def parser = new JsonSlurper()
def pipeline_config = parser.parseText(pipeline_config_json)

def textCredIdDefault = "pipeline-library-default-token"

def libconfiglist = []

for (libconf in pipeline_config) {
  def libname = libconf.key
  def settings = libconf.value
  def ref = "master"
  def textCredId = textCredIdDefault
  def username = "pipeline-library-${libname}"

  if ( ! settings.get("token") || pipeline_default_token ) {
    println "No token defined for pipeline library ${libname}"
    textCredId = "pipeline-library-token-${libname}"
    continue
  }
  if ( ! settings.get("repository") ) {
    println "No repository defined for pipeline library ${libname}"
    continue
  }
  if (settings.get("username") ) {
      username = settings.get("username")
  }
  if (settings.get("token")) {
    def pipeline_token = settings.get("token")
    def pwCredId = "pipeline-library-token-${libname}"

    Credentials pwcglob = (Credentials) new UsernamePasswordCredentialsImpl(
      CredentialsScope.GLOBAL,
      pwCredId,
      "Read access on pipeline shared library ${libname}",
      username,
      pipeline_token
    )

    println "Add Github Default token for pipeline library in GLOBAL scope"
    SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), pwcglob)
  }

  def repository = settings.get("repository")

  if ( settings.get("reference") ) {
    ref = settings.get("reference")
  }

  GitSCMSource scm = new GitSCMSource(
    libname,
    repository,
    textCredId,
    "*/${ref}",
    "",
    false
  )

  SCMSourceRetriever retriever = new SCMSourceRetriever(scm)

  LibraryConfiguration libconfig = new LibraryConfiguration(
    libname,
    retriever
  )
  libconfig.setDefaultVersion(ref)
  libconfig.setImplicit(false)

  libconfiglist << libconfig
}

if ( Jenkins.instance.pluginManager.activePlugins.find { it.shortName == "workflow-cps-global-lib" } != null ) {
  println "--> setting shared pipeline library"

  def inst = Jenkins.getInstance()
  def desc = inst.getDescriptor("org.jenkinsci.plugins.workflow.libs.GlobalLibraries")

  desc.get().setLibraries(libconfiglist)
  desc.save()
}
