/*
 * The MIT License
 *
 * Copyright 2014 Rusty Gerard
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.logstash.persistence;

import hudson.model.*;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenReport;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprintAction;
//import hudson.plugins.git.GitTagAction;
import jenkins.plugins.logstash.LogstashConfiguration;

import java.util.*;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * POJO for mapping build info to JSON.
 *
 * @author Rusty Gerard
 * @since 1.0.0
 */
public class BuildInfo {
  // ISO 8601 date format
  private final static Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

  public static class GitData{
    private String scmName, revision;
    private Set<String> remoteUrls;

    public GitData() {
      this(null);
    }
    public GitData(Action action) {
      BuildData gitBuildData = null;
      if (action instanceof BuildData) {
        gitBuildData = (BuildData) action;
      }

      if (gitBuildData == null) {
        scmName = revision = null;
        remoteUrls = Collections.emptySet();
        return;
      }

      scmName = gitBuildData.scmName;
      remoteUrls = gitBuildData.remoteUrls;
      revision = gitBuildData.lastBuild.getRevision().toString();
    }

    public String getScmName() {
      return scmName;
    }

    public String getRevision() {
      return revision;
    }

    public Set<String> getRemoteUrls() {
      return remoteUrls;
    }

  }

  public static class MavenData{
    private List<MavenArtifact> deployedArtifacts;
    private List<MavenArtifact> generatedArtifacts;
    private List<String> downstreamJobs;
    private List<String> upstreamJobs;
    private List<MavenDependency> dependencies;
    private Map<MavenArtifact, Collection<Job>> downstreamJobsByArtifact;

    public MavenData() {
      this(null);
    }
    public MavenData(Action action) {
      MavenReport mavenBuildData = null;
      if (action instanceof MavenReport) {
        mavenBuildData = (MavenReport) action;
      }

      if (mavenBuildData == null) {
        deployedArtifacts = Collections.emptyList();
        generatedArtifacts = Collections.emptyList();
        downstreamJobs = Collections.emptyList();
        upstreamJobs = Collections.emptyList();
        downstreamJobsByArtifact = Collections.emptyMap();
        dependencies = Collections.emptyList();
        return;
      }
      deployedArtifacts = new ArrayList(mavenBuildData.getDeployedArtifacts());
      generatedArtifacts = new ArrayList(mavenBuildData.getGeneratedArtifacts());
      downstreamJobs = new ArrayList(mavenBuildData.getDownstreamJobs());
      upstreamJobs = new ArrayList(mavenBuildData.getUpstreamBuilds());
      dependencies = new ArrayList(mavenBuildData.getDependencies());
      downstreamJobsByArtifact = mavenBuildData.getDownstreamJobsByArtifact();

    }

    public List<MavenArtifact> getMavenDeployedArtifacts() {
      return deployedArtifacts;
    }
    public List<MavenArtifact> getMavenGeneratedArtifacts() {
      return generatedArtifacts;
    }
    public List<String> getDownstreamJobs() {return downstreamJobs;}
    public List<String> getUpstreamJobs() {return upstreamJobs;}
    public List<MavenDependency> getDependencies() {return dependencies;}
    public Map<MavenArtifact, Collection<Job>> getDownstreamJobsByArtifact(){return downstreamJobsByArtifact;}

  }

  public static class DockerData{
    private Set<String> imageIDs;

    public DockerData() {
      this(null);
    }
    public DockerData(Action action) {
      DockerFingerprintAction dockerBuildData = null;
      if (action instanceof DockerFingerprintAction) {
        dockerBuildData = (DockerFingerprintAction) action;
      }

      if (dockerBuildData == null) {
        imageIDs = Collections.emptySet();
        return;
      }

      imageIDs = dockerBuildData.getImageIDs();
    }

    public Set<String> getDockerArtifacts() {
      return imageIDs;
    }

  }

  public static class TestData {
    private int totalCount, skipCount, failCount, passCount;
    private List<FailedTest> failedTestsWithErrorDetail;
    private List<String> failedTests;

    public static class FailedTest {
      private final String fullName, errorDetails;
      public FailedTest(String fullName, String errorDetails) {
        super();
        this.fullName = fullName;
        this.errorDetails = errorDetails;
      }

      public String getFullName()
      {
        return fullName;
      }

      public String getErrorDetails()
      {
        return errorDetails;
      }
    }

    public TestData() {
      this(null);
    }

    public TestData(Action action) {
      AbstractTestResultAction<?> testResultAction = null;
      if (action instanceof AbstractTestResultAction) {
        testResultAction = (AbstractTestResultAction<?>) action;
      }

      if (testResultAction == null) {
        totalCount = skipCount = failCount = 0;
        failedTests = Collections.emptyList();
        failedTestsWithErrorDetail = Collections.emptyList();
        return;
      }

      totalCount = testResultAction.getTotalCount();
      skipCount = testResultAction.getSkipCount();
      failCount = testResultAction.getFailCount();
      passCount = totalCount - skipCount - failCount;

      failedTests = new ArrayList<String>();
      failedTestsWithErrorDetail = new ArrayList<FailedTest>();
      for (TestResult result : testResultAction.getFailedTests()) {
          failedTests.add(result.getFullName());
          failedTestsWithErrorDetail.add(new FailedTest(result.getFullName(),result.getErrorDetails()));
      }
    }

    public int getTotalCount()
    {
        return totalCount;
    }

    public int getSkipCount()
    {
        return skipCount;
    }

    public int getFailCount()
    {
        return failCount;
    }

    public int getPassCount()
    {
        return passCount;
    }

    public List<FailedTest> getFailedTestsWithErrorDetail()
    {
        return failedTestsWithErrorDetail;
    }

    public List<String> getFailedTests()
    {
        return failedTests;
    }
  }

  private String id;
  private String result;
  private String projectName;
  private String fullProjectName;
  private String displayName;
  private String fullDisplayName;
  private String description;
  private String url;
  private String buildHost;
  private String buildLabel;
  private int buildNum;
  private long buildDuration;
  private transient String timestamp; // This belongs in the root object
  private transient Run<?, ?> build;
  private String rootProjectName;
  private String rootFullProjectName;
  private String rootProjectDisplayName;
  private int rootBuildNum;
  private Map<String, String> buildVariables;
  private Set<String> sensitiveBuildVariables;
  private TestData testResults = null;
  private GitData gitInfo = null;
  private MavenData mavenInfo = null;
  private DockerData dockerInfo = null;

  // Freestyle project build
  public BuildInfo(AbstractBuild<?, ?> build, Date currentTime, TaskListener listener) {
    initData(build, currentTime);

    // build.getDuration() is always 0 in Notifiers
    rootProjectName = build.getRootBuild().getProject().getName();
    rootFullProjectName = build.getRootBuild().getProject().getFullName();
    rootProjectDisplayName = build.getRootBuild().getDisplayName();
    rootBuildNum = build.getRootBuild().getNumber();
    buildVariables = build.getBuildVariables();
    sensitiveBuildVariables = build.getSensitiveBuildVariables();

    // Get environment build variables and merge them into the buildVariables map
    Map<String, String> buildEnvVariables = new HashMap<String, String>();
    List<Environment> buildEnvironments = build.getEnvironments();
    if (buildEnvironments != null) {
      for (Environment env : buildEnvironments) {
        if (env == null) {
          continue;
        }

        env.buildEnvVars(buildEnvVariables);
        if (!buildEnvVariables.isEmpty()) {
          buildVariables.putAll(buildEnvVariables);
          buildEnvVariables.clear();
        }
      }
    }
    try {
      buildVariables.putAll(build.getEnvironment(listener));
    } catch (Exception e) {
      // no base build env vars to merge
      LOGGER.log(WARNING,"Unable update logstash buildVariables with EnvVars from " + build.getDisplayName(),e);
    }
    for (String key : sensitiveBuildVariables) {
      buildVariables.remove(key);
    }
  }

  // Pipeline project build
  public BuildInfo(Run<?, ?> build, Date currentTime, TaskListener listener) {
    initData(build, currentTime);

    rootProjectName = projectName;
    rootFullProjectName = fullProjectName;
    rootProjectDisplayName = displayName;
    rootBuildNum = buildNum;

    try {
      // TODO: sensitive variables are not filtered, c.f. https://stackoverflow.com/questions/30916085
      buildVariables = build.getEnvironment(listener);
    } catch (IOException | InterruptedException e) {
      LOGGER.log(WARNING,"Unable to get environment for " + build.getDisplayName(),e);
      buildVariables = new HashMap<String, String>();
    }
  }

  private void initData(Run<?, ?> build, Date currentTime) {

    this.build = build;
    Executor executor = build.getExecutor();
    if (executor == null) {
        buildHost = "master";
        buildLabel = "master";
    } else {
        Node node = executor.getOwner().getNode();
        if (node == null) {
          buildHost = "master";
          buildLabel = "master";
        } else {
          buildHost = StringUtils.isBlank(node.getDisplayName()) ? "master" : node.getDisplayName();
          buildLabel = StringUtils.isBlank(node.getLabelString()) ? "master" : node.getLabelString();
        }
    }

    id = build.getId();
    projectName = build.getParent().getName();
    fullProjectName = build.getParent().getFullName();
    displayName = build.getDisplayName();
    fullDisplayName = build.getFullDisplayName();
    description = build.getDescription();
    url = build.getUrl();
    buildNum = build.getNumber();
    buildDuration = currentTime.getTime() - build.getStartTimeInMillis();
    timestamp = LogstashConfiguration.getInstance().getDateFormatter().format(build.getTimestamp().getTime());
    updateResult();
  }

  public void updateResult()
  {
    if (result == null && build.getResult() != null)
    {
      Result result = build.getResult();
      this.result = result == null ? null : result.toString();
    }
    Action testResultAction = build.getAction(AbstractTestResultAction.class);
    if (testResults == null && testResultAction != null) {
      testResults = new TestData(testResultAction);
    }
    Action GitBuildData = build.getAction(BuildData.class);
    if (gitInfo == null && GitBuildData != null) {
      gitInfo = new GitData(GitBuildData);
    }

    Action MavenBuildData = build.getAction(MavenReport.class);
    if (mavenInfo == null && MavenBuildData != null) {
      mavenInfo = new MavenData(MavenBuildData);
    }

    Action DockerBuildData = build.getAction(DockerFingerprintAction.class);
    if (dockerInfo == null && DockerBuildData != null) {
      dockerInfo = new DockerData(DockerBuildData);
    }
  }

  @Override
  public String toString() {
    Gson gson = new GsonBuilder().create();
    return gson.toJson(this);
  }

  public JSONObject toJson() {
    String data = toString();
    return JSONObject.fromObject(data);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getResult() {
    return result;
  }

  public void setResult(Result result) {
    this.result = result.toString();
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getFullProjectName() {
    return fullProjectName;
  }

  public void setFullProjectName(String fullProjectName) {
    this.fullProjectName = fullProjectName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getFullDisplayName() {
    return fullDisplayName;
  }

  public void setFullDisplayName(String fullDisplayName) {
    this.fullDisplayName = fullDisplayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getBuildHost() {
    return buildHost;
  }

  public void setBuildHost(String buildHost) {
    this.buildHost = buildHost;
  }

  public String getBuildLabel() {
    return buildLabel;
  }

  public void setBuildLabel(String buildLabel) {
    this.buildLabel = buildLabel;
  }

  public int getBuildNum() {
    return buildNum;
  }

  public void setBuildNum(int buildNum) {
    this.buildNum = buildNum;
  }

  public long getBuildDuration() {
    return buildDuration;
  }

  public void setBuildDuration(long buildDuration) {
    this.buildDuration = buildDuration;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Calendar timestamp) {
    this.timestamp = LogstashConfiguration.getInstance().getDateFormatter().format(timestamp.getTime());
  }

  public String getRootProjectName() {
    return rootProjectName;
  }

  public void setRootProjectName(String rootProjectName) {
    this.rootProjectName = rootProjectName;
  }

  public String getRootFullProjectName() {
    return rootFullProjectName;
  }

  public void setRootFullProjectName(String rootFullProjectName) {
    this.rootFullProjectName = rootFullProjectName;
  }

  public String getRootProjectDisplayName() {
    return rootProjectDisplayName;
  }

  public void setRootProjectDisplayName(String rootProjectDisplayName) {
    this.rootProjectDisplayName = rootProjectDisplayName;
  }

  public int getRootBuildNum() {
    return rootBuildNum;
  }

  public void setRootBuildNum(int rootBuildNum) {
    this.rootBuildNum = rootBuildNum;
  }

  public Map<String, String> getBuildVariables() {
    return buildVariables;
  }

  public void setBuildVariables(Map<String, String> buildVariables) {
    this.buildVariables = buildVariables;
  }

  public Set<String> getSensitiveBuildVariables() {
    return sensitiveBuildVariables;
  }

  public void setSensitiveBuildVariables(Set<String> sensitiveBuildVariables) {
    this.sensitiveBuildVariables = sensitiveBuildVariables;
  }

  public TestData getTestResults() {
    return testResults;
  }

  public void setTestResults(TestData testResults) {
    this.testResults = testResults;
  }
}
