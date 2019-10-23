package com.plugin.component.extension.option.sdk

import com.plugin.component.Logger
import com.plugin.component.Runtimes
import com.plugin.component.extension.PublicationManager
import com.plugin.component.utils.ProjectUtil
import com.plugin.component.utils.PublicationUtil
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.util.ConfigureUtil

class SdkOption {

    Project project
    int compileSdkVersion                           //编译版本
    CompileOptions compileOption                    //编译选项
    Action<? super RepositoryHandler> configure     //仓库配置

    SdkOption(Project project) {
        this.project = project
        compileOption = new CompileOptions()
    }


    /**
     * 编译sdk
     * @param version
     */
    void compileSdkVersion(int version) {
        compileSdkVersion = version
    }


    /**
     * 配置选项
     * @param closure
     */
    void compileOptions(Closure closure) {
        ConfigureUtil.configure(closure, compileOption)
    }

    /**
     * 仓库配置
     * @param configure
     */
    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

    /**
     * 申明组件sdk
     * @param closure
     */
    void configuration(Closure closure) {

        NamedDomainObjectContainer<PublicationOption> publications = project.container(PublicationOption)
        ConfigureUtil.configure(closure, publications)
        publications.each {
            it.isSdk = true
            it.name = ProjectUtil.getProjectName(it.name)
            Project childProject = ProjectUtil.getProject(project, it.name)
            if (childProject == null) {
                Logger.buildOutput("publication's target[" + it.name + "] does not exist!")
            } else {
                Logger.buildOutput("publication's sdk[" + it.name + "] is " + it.groupId + ":" + it.artifactId)
                PublicationUtil.initPublication(childProject, it)
                PublicationManager.getInstance().addDependencyGraph(it.name, it)
                Runtimes.addSdkPublication(childProject.name, it)
            }
        }
    }

//    /**
//     * 未开放
//     * @param closure
//     */
//    void impl(Closure closure) {
//        NamedDomainObjectContainer<PublicationOption> publications = project.container(PublicationOption)
//        ConfigureUtil.configure(closure, publications)
//        publications.each {
//            it.isSdk = false
//            it.name = ProjectUtil.getProjectName(it.name)
//            Project childProject = ProjectUtil.getProject(project, it.name)
//            if (childProject == null) {
//                Logger.buildOutput("publication's target[" + it.name + "] does not exist!")
//            } else {
//                Logger.buildOutput("publication's impl[" + it.name + "] is " + it.groupId + ":" + it.artifactId)
//                //todo 预留后续逻辑
//            }
//        }
//    }
}
