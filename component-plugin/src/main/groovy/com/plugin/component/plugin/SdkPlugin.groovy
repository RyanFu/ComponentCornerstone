package com.plugin.component.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestedExtension
import com.plugin.component.Constants
import com.plugin.component.Logger
import com.plugin.component.Runtimes
import com.plugin.component.extension.PublicationManager
import com.plugin.component.extension.module.ProjectInfo
import com.plugin.component.extension.option.debug.DebugDependenciesOption
import com.plugin.component.extension.option.sdk.PublicationDependenciesOption
import com.plugin.component.extension.option.sdk.PublicationOption
import com.plugin.component.transform.ComponentTransform
import com.plugin.component.utils.JarUtil
import com.plugin.component.utils.ProjectUtil
import com.plugin.component.utils.PublicationUtil
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.AppliedPlugin

class SdkPlugin extends BasePlugin {


    @Override
    void evaluate(Project project, boolean isRoot) {
        if (isRoot) {
            Runtimes.sSdkDir = new File(project.projectDir, Constants.SDK_DIR)
            Runtimes.sImplDir = new File(project.projectDir, Constants.IMPL_DIR)
            Logger.buildOutput("sdk目录 File[" + Runtimes.sSdkDir.name + "]")
            Logger.buildOutput("impl目录 File[" + Runtimes.sSdkDir.name + "]")

            if (!Runtimes.sSdkDir.exists()) {
                Runtimes.sSdkDir.mkdirs()
                Logger.buildOutput("create File[" + Runtimes.sSdkDir.name + "]")
            }

            if (!Runtimes.sImplDir.exists()) {
                Runtimes.sImplDir.mkdirs()
                Logger.buildOutput("create File[" + Runtimes.sImplDir.name + "]")
            }

            ProjectUtil.getTasks(project).each {
                if (it == Constants.CLEAN) {
                    if (!Runtimes.sSdkDir.deleteDir()) {
                        throw new RuntimeException("unable to delete dir " + Runtimes.sSdkDir.absolutePath)
                    }
                    if (!Runtimes.sImplDir.deleteDir()) {
                        throw new RuntimeException("unable to delete dir " + Runtimes.sImplDir.absolutePath)
                    }
                    Runtimes.sSdkDir.mkdirs()
                    Logger.buildOutput("reset File[" + Runtimes.sSdkDir.name + "]")

                    Runtimes.sImplDir.mkdirs()
                    Logger.buildOutput("reset File[" + Runtimes.sImplDir.name + "]")
                }
            }
            project.repositories {
                flatDir {
                    dirs Runtimes.sSdkDir.absolutePath
                    Logger.buildOutput("flatDir Dir[" + Runtimes.sSdkDir.absolutePath + "]")

                    dirs Runtimes.sImplDir.absolutePath
                    Logger.buildOutput("flatDir Dir[" + Runtimes.sImplDir.absolutePath + "]")
                }
            }

            Logger.buildOutput("读取 sdk/impl manifest 配置文件...")
            PublicationManager.getInstance().loadManifest(project)

            Logger.buildOutput("读取 component.gradle 信息...")

            //todo sdk中依赖sdk，需要特别区分，预留后续逻辑
            PublicationDependenciesOption.metaClass.component { String value ->
                return Constants.COMPONENT_PRE + value
            }

            DebugDependenciesOption.metaClass.component { String value ->
                return Constants.DEBUG_COMPONENT_PRE + value
            }
        } else {
            ProjectInfo projectInfo = Runtimes.getProjectInfo(project.name)

            //解析： component
            //example ：component(':library')
            //规则：
            // 1.  component 对象必须为实现 component 插件的project
            // 2.  component(<project>) project 逻辑上被划分为 impl / debug / sdk，其中 sdk 通过 api 暴露给上层，impl 直接打包
            project.dependencies.metaClass.component { String value ->
                return PublicationUtil.parseComponent(projectInfo, value)
            }

            //project 需要使用 api 依赖并暴露 sdk，解决 project 被依赖的时候，依赖者可以引用到 project sdk
            List<PublicationOption> publications = PublicationManager.getInstance().getPublicationByProject(project)
            project.dependencies {
                publications.each {
                    api PublicationUtil.getPublication(it)
                }
            }

            /**
             * Syncing Gradle will evaluate the build files by comparing the current files to the project state that Gradle and Android Studio maintain.
             * If it finds any changes it will execute just those specific tasks.
             */
            //project 需要依赖 sdk 中声明的依赖，意味着同步的时候，component.gradle 中 sdk { dependencies { <xxxxx> }} 内容需要同步迁移到 project
            if (projectInfo.isSync()) {
                Logger.buildOutput("Syncing gradle...")
                publications.each {
                    PublicationUtil.addPublicationDependencies(projectInfo, it)
                }
            }
        }
    }

    @Override
    void afterEvaluate(Project project,boolean isRoot) {
        if(isRoot){
            Logger.buildOutput("")
            Logger.buildOutput("=====> 处理 sdk/impl jar <=====")
            List<String> topSort = PublicationManager.getInstance().dependencyGraph.topSort()
            Collections.reverse(topSort)
            topSort.each {
                PublicationOption publication = PublicationManager.getInstance().publicationDependencies.get(it)
                if (publication == null) {
                    return
                }
                Project childProject = project.findProject(publication.project)
                PublicationUtil.filterPublicationDependencies(publication)
                if (publication.version != null) {
                    JarUtil.handleMavenJar(childProject, publication)
                } else {
                    JarUtil.handleLocalJar(childProject, publication)
                }
                PublicationManager.getInstance().hitPublication(publication)
            }
            Logger.buildOutput("=====> 处理 sdk/impl jar <=====")
            Logger.buildOutput("")

            project.allprojects.each {
                if (it == project) return
                if (!Runtimes.shouldApplyComponentPlugin(it)) return
                Project childProject = it
                Logger.buildOutput("")
                Logger.buildOutput("=====> project[" + childProject.name + "]配置信息 <=====")
                ProjectInfo projectInfo = new ProjectInfo(childProject)
                childProject.repositories {
                    flatDir {
                        dirs Runtimes.sSdkDir.absolutePath
                        Logger.buildOutput("add flatDir Dir[" + Runtimes.sSdkDir.absolutePath + "]")
                        dirs Runtimes.sImplDir.absolutePath
                        Logger.buildOutput("add flatDir Dir[" + Runtimes.sImplDir.absolutePath + "]")
                    }
                }
                Runtimes.addProjectInfo(childProject.name, projectInfo)
                Logger.buildOutput("compileModuleName", projectInfo.compileModuleName)
                Logger.buildOutput("projectName", projectInfo.name)
                Logger.buildOutput("isDebugModule", projectInfo.isDebugModule())
                Logger.buildOutput("taskNames", projectInfo.taskNames)
                Logger.buildOutput("isSyncTask", projectInfo.isSync())
                Logger.buildOutput("isAssemble", projectInfo.isAssemble)
                Logger.buildOutput("isDebug", projectInfo.isDebug)
                Logger.buildOutput("=====> project[" + childProject.name + "]配置信息 <=====")
                Logger.buildOutput("")

                childProject.plugins.all {
                    Class extensionClass
                    if (it instanceof AppPlugin) {
                        extensionClass = AppExtension
                    } else if (it instanceof LibraryPlugin) {
                        extensionClass = LibraryExtension
                    } else {
                        return
                    }

                    //plugin afterEvaluate 之后调用
                    childProject.extensions.configure(extensionClass, new Action<? extends TestedExtension>() {
                        @Override
                        void execute(TestedExtension testedExtension) {
                            Logger.buildOutput("")
                        }
                    })
                }

                /**
                 * plugin afterEvaluate 之后调用
                 */
                childProject.plugins.whenObjectAdded {
                    if (it instanceof AppPlugin || it instanceof LibraryPlugin) {
                        Logger.buildOutput("")
                        Logger.buildOutput("=====> project[" + childProject.name + "]注入插件 <=====")
                        Logger.buildOutput("project[" + childProject.name + "] whenObjectAdded(AppPlugin or LibraryPlugin)")
                        Logger.buildOutput("apply plugin: com.android.component")
                        childProject.pluginManager.apply(Constants.PLUGIN_COMPONENT)
                        childProject.dependencies {
                            Logger.buildOutput("add dependency: " + Constants.CORE_DEPENDENCY)
                            implementation Constants.CORE_DEPENDENCY
//                            implementation childProject.project(":component-core")
                        }
                        if (it instanceof AppPlugin) {
                            if (projectInfo.isDebugModule() || projectInfo.isCompileModuleAndAssemble()) {
                                Logger.buildOutput("plugin is AppPlugin")
                                Logger.buildOutput("registerTransform", "ScanCodeTransform")
                                Logger.buildOutput("registerTransform", "InjectCodeTransform")
                                childProject.extensions.findByType(BaseExtension.class).registerTransform(new ComponentTransform(childProject))
                            }
                        }
                        Logger.buildOutput("=====> project[" + childProject.name + "]注入插件 <=====")
                        Logger.buildOutput("")
                    }
                }

                /**
                 * 介于 evaluate 与 afterEvaluate 中间
                 */
                childProject.getPluginManager().withPlugin("com.android.library", new Action<AppliedPlugin>() {
                    @Override
                    void execute(AppliedPlugin appliedPlugin) {
                        Logger.buildOutput("")
                    }
                })

                childProject.getPluginManager().withPlugin("com.android.application", new Action<AppliedPlugin>() {
                    @Override
                    void execute(AppliedPlugin appliedPlugin) {
                        Logger.buildOutput("")
                    }
                })

                //评估之后，after之前
                childProject.getPluginManager().withPlugin("com.android.component", new Action<AppliedPlugin>() {
                    @Override
                    void execute(AppliedPlugin appliedPlugin) {
                        Logger.buildOutput("")
                    }
                })
            }
        }else{
            ProjectInfo projectInfo = Runtimes.getProjectInfo(project.name)
            ProjectUtil.modifySourceSets(projectInfo)

            //todo 发布
//            List<PublicationOption> publicationList = PublicationManager.getInstance().getPublicationByProject(project)
//            List<PublicationOption> publicationPublishList = new ArrayList<>()
//            publicationList.each {
//                if (it.version != null) {
//                    publicationPublishList.add(it)
//                }
//            }
//
//            if (publicationPublishList.size() > 0) {
//                project.plugins.apply(Constants.PLUGIN_MAVEN_PUBLISH)
//                def publishing = project.extensions.getByName(Constants.PUBLISHING)
//                if (mComponentExtension != null && mComponentExtension.configure != null) {
//                    publishing.repositories mComponentExtension.configure
//                }
//
//                publicationPublishList.each {
//                    PublicationUtil.createPublishTask(project, it)
//                }
//            }
        }
    }


    @Override
    void afterAllEvaluate() {
        ProjectInfo compileProject = Runtimes.getCompileProjectWhenAssemble()
        if (compileProject != null) {
            Logger.buildOutput("")
            Logger.buildOutput("=====> 处理循环依赖 <=====")
            if (Runtimes.sAssembleModules.size() > 1) {
                Logger.buildOutput("task has one more assemble module,skip transforming [component] to [project]")
            } else {
                Logger.buildOutput("assemble project", compileProject.name)
                Set<String> hasResolve = new HashSet<>()
                Set<String> currentDependencies = new HashSet<>()
                Set<String> nextDependencies = new HashSet<>()
                currentDependencies.addAll(compileProject.componentDependencies)
                Logger.buildOutput("project[" + compileProject.name + "] component 依赖", compileProject.getComponentDependenciesString())

                while (!currentDependencies.isEmpty()) {
                    for (String string : currentDependencies) {
                        ProjectInfo projectInfo = Runtimes.getProjectInfo(string)
                        String name = projectInfo.name
                        if (!hasResolve.contains(name)) {
                            hasResolve.add(name)
                            nextDependencies.addAll(projectInfo.componentDependencies)
                            Logger.buildOutput("project[" + projectInfo.name + "] component 依赖", projectInfo.getComponentDependenciesString())
                        }
                    }
                    currentDependencies.clear()
                    currentDependencies.addAll(nextDependencies)
                    nextDependencies.clear()
                }

                if (!hasResolve.isEmpty()) {
                    StringBuilder stringBuilder = new StringBuilder()
                    for (String realDependency : hasResolve) {
                        stringBuilder.append(" :project(")
                        stringBuilder.append(realDependency)
                        stringBuilder.append(")")
                        compileProject.project.dependencies {
                            implementation compileProject.project.project(":" + realDependency)
                        }
                    }
                    Logger.buildOutput("application[" + compileProject.name + "] component 合并依赖", stringBuilder.toString())
                }
            }
            Logger.buildOutput("=====> 处理循环依赖 <=====")
            Logger.buildOutput("")
        }
    }
}