package net.ltgt.gradle.j2cl.tasks

import com.google.j2cl.tools.gwtincompatible.GwtIncompatibleStripper
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

abstract class GwtIncompatibleStrip : IncrementalSourceTask() {

    @get:PathSensitive(PathSensitivity.RELATIVE)
    override val stableSources: ConfigurableFileCollection
        get() = super.stableSources

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Classpath
    abstract val stripperClasspath: ConfigurableFileCollection

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        // XXX: allow using processIsolation with configurable forkOptions?
        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(this@GwtIncompatibleStrip.stripperClasspath)
        }

        inputChanges.getFileChanges(stableSources).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach

            val targetFile = destinationDirectory.file(change.normalizedPath)
            if (change.changeType == ChangeType.REMOVED) {
                project.delete(targetFile)
            } else {
                workQueue.submit(GwtIncompatibleStripperWorker::class) {
                    sourceFile.set(change.file)
                    destinationFile.set(targetFile)
                }
            }
        }
    }
}

private interface GwtIncompatibleStripperParameters : WorkParameters {
    val sourceFile: RegularFileProperty
    val destinationFile: RegularFileProperty
}

private abstract class GwtIncompatibleStripperWorker : WorkAction<GwtIncompatibleStripperParameters> {

    override fun execute() {
        val strippedContent = GwtIncompatibleStripper.strip(parameters.sourceFile.get().asFile.readText())
        parameters.destinationFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(strippedContent)
        }
    }
}
