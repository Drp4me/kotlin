package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.work.NormalizeLineEndings
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.sources.internal
import org.jetbrains.kotlin.gradle.utils.filesProvider
import org.jetbrains.kotlin.utils.addToStdlib.applyIf

internal class MetadataDependencyTransformationTaskInputs(
    project: Project,
    kotlinSourceSet: KotlinSourceSet,
    private val skipProjectDependencies: Boolean = false,
) {
    @Suppress("unused") // Gradle input
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:NormalizeLineEndings
    val configurationToResolve: FileCollection = kotlinSourceSet
        .internal
        .resolvableMetadataConfiguration
        .applyIf(skipProjectDependencies) { withoutProjectDependencies() }

    @Suppress("unused") // Gradle input
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:NormalizeLineEndings
    val hostSpecificMetadataConfigurationsToResolve: FileCollection = project.filesProvider {
        kotlinSourceSet.internal.compilations
            .filter { compilation -> if (compilation is KotlinNativeCompilation) compilation.konanTarget.enabledOnCurrentHost else true }
            .mapNotNull { compilation -> compilation
                .internal
                .configurations
                .hostSpecificMetadataConfiguration
                ?.applyIf(skipProjectDependencies) { withoutProjectDependencies() }
            }
    }

    @Transient // Only needed for configuring task inputs
    private val participatingSourceSetsLazy: Lazy<Set<KotlinSourceSet>>? = lazy {
        kotlinSourceSet.internal.withDependsOnClosure.toMutableSet().apply {
            if (any { it.name == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME })
                add(project.kotlinExtension.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME))
        }
    }

    private val participatingSourceSets: Set<KotlinSourceSet>
        get() = participatingSourceSetsLazy?.value
            ?: error(
                "`participatingSourceSets` is null. " +
                        "Probably it is accessed it during Task Execution with state loaded from Configuration Cache"
            )

    @Suppress("unused") // Gradle input
    @get:Input
    val inputSourceSetsAndCompilations: Map<String, Iterable<String>> by lazy {
        participatingSourceSets.associate { sourceSet ->
            sourceSet.name to sourceSet.internal.compilations.map { it.name }.sorted()
        }
    }

    @Suppress("unused") // Gradle input
    @get:Input
    val inputCompilationDependencies: Map<String, Set<List<String?>>> by lazy {
        participatingSourceSets.flatMap { it.internal.compilations }.associate {
            it.name to project.configurations.getByName(it.compileDependencyConfigurationName)
                .allDependencies
                .applyIf(skipProjectDependencies) { filterNot { it is ProjectDependency } }
                .map { listOf(it.group, it.name, it.version) }.toSet()
        }
    }
}

private fun Configuration.withoutProjectDependencies(): FileCollection {
    return incoming.artifactView { view ->
        view.componentFilter { componentIdentifier ->
            componentIdentifier !is ProjectComponentIdentifier
        }
    }.files
}
