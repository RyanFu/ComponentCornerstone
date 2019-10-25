package com.plugin.component.plugin

import com.plugin.component.Logger
import com.plugin.component.Runtimes
import com.plugin.component.extension.module.ProjectInfo
import com.plugin.component.utils.ProjectUtil
import org.gradle.api.Project

class DebugPlugin extends BasePlugin {

    @Override
    void afterEvaluate(Project project, boolean isRoot) {
        ProjectInfo projectInfo = Runtimes.getProjectInfo(project.name)
        //调整debugModule结构
        if (ProjectUtil.isProjectSame(projectInfo.name, Runtimes.getDebugModuleName())) {
            Logger.buildOutput("")
            Logger.buildOutput(" =====> project[" + projectInfo.name + "] is debugModel,modifying DebugSets <=====")
            ProjectUtil.modifyDebugSets(projectInfo.project.rootProject, projectInfo)
            Logger.buildOutput(" =====> project[" + projectInfo.name + "] is debugModel,modifying DebugSets <=====")
            Logger.buildOutput("")
        }
    }
}