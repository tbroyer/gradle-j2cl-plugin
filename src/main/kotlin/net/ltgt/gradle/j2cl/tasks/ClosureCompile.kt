package net.ltgt.gradle.j2cl.tasks

import net.ltgt.gradle.j2cl.internal.ClosureCompilerWorker
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class ClosureCompile : SourceTask() {

    @get:Input
    abstract val entrypoints: ListProperty<String>

    // This could be NONE if we didn't have source maps as inputs
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree = super.getSource()

    @get:Nested
    abstract val options: ClosureOptions

    fun options(configure: Action<ClosureOptions>) {
        configure.execute(options)
    }

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Input
    abstract val initialScriptFilename: Property<String>

    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute() {
        // XXX: allow using processIsolation with configurable forkOptions
        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(this@ClosureCompile.compilerClasspath)
        }

        workQueue.submit(ClosureCompilerWorker::class) {
            entrypoints.set(this@ClosureCompile.entrypoints)
            files.from(source)
            destinationDirectory.set(this@ClosureCompile.destinationDirectory)
            initialScriptFilename.set(this@ClosureCompile.initialScriptFilename)
            options.set(this@ClosureCompile.options)
            temporaryDirectory.set(temporaryDir)
        }
    }
}
