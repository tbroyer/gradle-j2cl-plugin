package net.ltgt.gradle.j2cl.tasks

import com.google.j2cl.transpiler.J2clCommandLineRunner
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class J2clTranspile : SourceTask() {

    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource() = super.getSource()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeJsSources: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Classpath
    abstract val transpilerClasspath: ConfigurableFileCollection

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute() {
        // XXX: allow using processIsolation with configurable forkOptions?
        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(transpilerClasspath)
        }

        // TODO: process incrementally (requires tracking dependencies between files)
        // https://github.com/google/j2cl/issues/99
        workQueue.submit(J2clTranspilerWorker::class) {
            this.sources.from(this@J2clTranspile.source)
            this.nativeJsSources.from(this@J2clTranspile.nativeJsSources)
            this.classpath.from(this@J2clTranspile.classpath)
            this.destinationDirectory.set(this@J2clTranspile.destinationDirectory)
        }
    }
}

private interface J2clTranspilerParameters : WorkParameters {
    val sources: ConfigurableFileCollection
    val nativeJsSources: ConfigurableFileCollection
    val classpath: ConfigurableFileCollection
    val destinationDirectory: DirectoryProperty
}

private abstract class J2clTranspilerWorker : WorkAction<J2clTranspilerParameters> {
    override fun execute() {
        val args = mutableListOf<String>(
            "-d",
            parameters.destinationDirectory.get().asFile.path
        )
        if (!parameters.classpath.isEmpty) {
            args.add("-classpath")
            args.add(parameters.classpath.asPath)
        }
        if (!parameters.nativeJsSources.isEmpty) {
            args.add("-nativesourcepath")
            args.add(parameters.nativeJsSources.asPath)
        }
        args.addAll(parameters.sources.map { it.path })
        val exitCode = J2clCommandLineRunner.run(args.toTypedArray())
        if (exitCode != 0) {
            throw J2clTranspilerException(exitCode)
        }
    }
}

private class J2clTranspilerException(exitCode: Int) :
    RuntimeException("J2CL transpiling failed with exit code $exitCode; see the transpiler error output for details.")
