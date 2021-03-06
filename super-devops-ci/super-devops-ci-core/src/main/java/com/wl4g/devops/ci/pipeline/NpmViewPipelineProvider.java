/*
 * Copyright 2017 ~ 2025 the original author or authors. <wanglsir@gmail.com, 983708408@qq.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.devops.ci.pipeline;

import com.wl4g.devops.ci.pipeline.job.NpmViewPipeTransferJob;
import com.wl4g.devops.ci.pipeline.model.PipelineInfo;
import com.wl4g.devops.ci.utils.GitUtils;
import com.wl4g.devops.common.bean.ci.Project;
import com.wl4g.devops.common.bean.ci.TaskHistory;
import com.wl4g.devops.common.bean.share.AppInstance;

import org.springframework.util.Assert;

import java.io.File;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * NPM/(VUE/AngularJS/ReactJS...) standard deployments provider.
 *
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0 2019年5月22日
 * @since
 */
public class NpmViewPipelineProvider extends AbstractPipelineProvider {

	public NpmViewPipelineProvider(PipelineInfo info) {
		super(info);
	}

	// --- NPM building. ---

	@Override
	public void execute() throws Exception {
		// build
		build();
	}

	@Override
	public void rollback() throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Runnable newTransferJob(AppInstance instance) {
		Object[] args = { this, getPipelineInfo().getProject(), instance, getPipelineInfo().getTaskHistoryDetails() };
		return beanFactory.getBean(NpmViewPipeTransferJob.class, args);
	}

	private void build() throws Exception {
		// TODO
		// step1: git clone/pull
		getSources(false);
		// step2: npm install & npm run build ==> run build command
		npmBuild();
		// step3: tar -c
		pkg();
		// step4 scp ==> tar -x

		// Startup pipeline jobs.
		doTransferInstances();

		if (log.isInfoEnabled()) {
			log.info("Npm pipeline execution successful!");
		}
	}

	private void getSources(boolean isRollback) throws Exception {
		log.info("Pipeline building for projectId={}", getPipelineInfo().getProject().getId());
		Project project = getPipelineInfo().getProject();
		Assert.notNull(project, "project not exist");
		// Obtain project source from VCS.
		String projectDir = config.getProjectDir(project.getProjectName()).getAbsolutePath();
		if (isRollback) {
			String sha = getPipelineInfo().getTaskHistory().getShaGit();
			if (GitUtils.checkGitPath(projectDir)) {
				GitUtils.rollback(config.getVcs().getGitlab().getCredentials(), projectDir, sha);
			} else {
				GitUtils.clone(config.getVcs().getGitlab().getCredentials(), project.getGitUrl(), projectDir,
						getPipelineInfo().getBranch());
				GitUtils.rollback(config.getVcs().getGitlab().getCredentials(), projectDir, sha);
			}
		} else {
			if (GitUtils.checkGitPath(projectDir)) {// 若果目录存在则chekcout分支并pull
				GitUtils.checkout(config.getVcs().getGitlab().getCredentials(), projectDir, getPipelineInfo().getBranch());
			} else { // 若目录不存在: 则clone 项目并 checkout 对应分支
				GitUtils.clone(config.getVcs().getGitlab().getCredentials(), project.getGitUrl(), projectDir,
						getPipelineInfo().getBranch());
			}
		}
	}

	private void npmBuild() throws Exception {
		Project project = getPipelineInfo().getProject();
		TaskHistory taskHistory = getPipelineInfo().getTaskHistory();
		File logPath = config.getJobLog(getPipelineInfo().getTaskHistory().getId());
		String projectDir = config.getProjectDir(project.getProjectName()).getAbsolutePath();
		// Building.
		if (isBlank(taskHistory.getBuildCommand())) {
			doBuildWithDefaultCommand(projectDir, logPath, taskHistory.getId());
		} else {
			// Obtain temporary command file.
			File tmpCmdFile = config.getJobTmpCommandFile(taskHistory.getId(), project.getId());
			String buildCommand = resolvePlaceholderVariables(taskHistory.getBuildCommand(), projectDir);
			processManager.execFile(String.valueOf(taskHistory.getId()), buildCommand, tmpCmdFile, logPath, 300000);
		}
	}

	private void doBuildWithDefaultCommand(String projectDir, File logPath, Integer taskId) throws Exception {
		Project project = getPipelineInfo().getProject();
		TaskHistory taskHistory = getPipelineInfo().getTaskHistory();
		File tmpCmdFile = config.getJobTmpCommandFile(taskHistory.getId(), project.getId());
		String buildCommand = "cd " + projectDir + "\nnpm install\nnpm run build\n";
		processManager.execFile(String.valueOf(taskHistory.getId()), buildCommand, tmpCmdFile, logPath, 300000);
	}

	/**
	 * tar -cvf ***.tar -C /home/ci/view * tar -xvf ***.tar -C /opt/apps/view
	 */
	private void pkg() throws Exception {
		Project project = getPipelineInfo().getProject();
		TaskHistory taskHistory = getPipelineInfo().getTaskHistory();
		String projectDir = config.getProjectDir(project.getProjectName()).getAbsolutePath();
		// tar
		String tarCommand = "cd " + projectDir + "/dist\n" + "tar -zcvf "
				+ config.getJobBackup(getPipelineInfo().getTaskHistory().getId()) + "/" + project.getProjectName() + ".tar.gz  *";
		processManager.execFile(String.valueOf(taskHistory.getId()), tarCommand,
				config.getJobTmpCommandFile(taskHistory.getId(), -1),
				config.getJobLog(getPipelineInfo().getTaskHistory().getId()), 300000);
	}

}