package net.ltgt.gradle.j2cl.tasks

import com.google.j2cl.tools.gwtincompatible.GwtIncompatibleStripperCommandLineRunner
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

abstract class GwtIncompatibleStrip : SourceTask() {

    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource() = super.getSource()

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Classpath
    abstract val stripperClasspath: ConfigurableFileCollection

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute() {
        // XXX: allow using processIsolation with configurable forkOptions?
        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(this@GwtIncompatibleStrip.stripperClasspath)
        }

        // TODO: process incrementally (only changed files)
        // GwtIncompatibleStripper currently outputs a ZIP file unconditionally
        // with entry paths based on the input paths, which we pass as absolute paths
        // because we can't be sure of the current working directory,
        // so we need to map those paths to the expected paths when unzipping the result.
        // https://github.com/google/j2cl/issues/98
        val filesMapping = mutableMapOf<String, String>()
        source.visit {
            if (isDirectory) return@visit
            filesMapping[getJavaPath(file.rootlessPath)] = path
        }

        workQueue.submit(GwtIncompatibleStripperWorker::class) {
            files.from(source)
            this.filesMapping.set(filesMapping)
            destinationDirectory.set(this@GwtIncompatibleStrip.destinationDirectory)
            temporaryDir.set(this@GwtIncompatibleStrip.temporaryDir)
        }
    }

    private val File.rootlessPath: String
        get() = if (isRooted) toPath().let { it.root.relativize(it).toString() } else path
}

private interface GwtIncompatibleStripperParameters : WorkParameters {
    val files: ConfigurableFileCollection
    val filesMapping: MapProperty<String, String>
    val destinationDirectory: DirectoryProperty
    val temporaryDir: DirectoryProperty
}

private abstract class GwtIncompatibleStripperWorker : WorkAction<GwtIncompatibleStripperParameters> {

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    override fun execute() {
        val filesMapping = parameters.filesMapping.get()

        val srcjar = parameters.temporaryDir.get().asFile.resolve("j2cl_stripped-src.jar")
        val args = mutableListOf<String>("-d", srcjar.path)
        parameters.files.mapTo(args) { it.path }
        try {
            val exitCode = GwtIncompatibleStripperCommandLineRunner.run(args.toTypedArray())
            if (exitCode != 0) {
                throw GwtIncompatibleStripperException(exitCode)
            }

            fileSystemOperations.copy {
                from(archiveOperations.zipTree(srcjar))
                into(parameters.destinationDirectory)
                eachFile {
                    // GwtIncompatibleStripper is supposed to strip leading components from absolute paths
                    // up to "java roots" (per Bazel conventions), but at the time of writing it does not;
                    // so we fallback to computing that path if looking it up directly doesn't find a match.
                    path = filesMapping[sourcePath] ?: filesMapping[getJavaPath(sourcePath)] ?: path
                }
                includeEmptyDirs = false
            }
        } finally {
            srcjar.delete()
        }
    }
}

// This is the same algorithm as com.google.j2cl.common.FrontendUtils.getJavaPath
private fun getJavaPath(path: String): String =
    path.findAnyOf(listOf("/java/", "/javatests/"))?.let { (i, s) -> path.substring(i + s.length) } ?: path

private class GwtIncompatibleStripperException(exitCode: Int) :
    RuntimeException("GwtIncompatible stripping failed with exit code $exitCode; see the stripper error output for details.")
