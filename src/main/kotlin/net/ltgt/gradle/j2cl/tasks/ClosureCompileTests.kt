package net.ltgt.gradle.j2cl.tasks

import net.ltgt.gradle.j2cl.internal.ClosureCompilerWorker
import net.ltgt.gradle.j2cl.internal.MOSHI
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class ClosureCompileTests : SourceTask() {

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

    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute() {
        // XXX: allow using processIsolation with configurable forkOptions
        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(compilerClasspath)
        }

        // TODO: process incrementally (requires tracking dependencies between files)
        // https://github.com/google/j2cl/issues/99
        val testSummaryFile = source.matching { include("test_summary.json") }.singleFile
        val testSummary = MOSHI.adapter(TestSummary::class.java).lenient().fromJson(testSummaryFile.readText())!!
        testSummary.tests.forEach { test ->
            val testWithoutExtension = test.removeSuffix(".js")
            val testClass = testWithoutExtension.replace('/', '.')
            val testDir = this@ClosureCompileTests.destinationDirectory.dir(testClass)

            // XXX: do it in a worker? probably not worth it.
            generateHtml(testDir.get().file("index.html").asFile, testClass)

            // Rename *.testsuite to *.js
            // XXX: should GenerateTests separate its output in 2 directories/FileTrees and do that renaming itself?
            val renamedTest = temporaryDir.resolve("$testClass.js")
            source.matching { include("$testWithoutExtension.testsuite") }.singleFile
                .copyTo(renamedTest, overwrite = true)

            workQueue.submit(ClosureCompilerWorker::class) {
                // XXX: use the *.js file as the entrypoint instead of computing its known goog.module name?
                entrypoints.add("javatests.${testClass}_AdapterSuite")
                files.from(source, renamedTest)
                destinationDirectory.set(testDir)
                initialScriptFilename.set("test.js")
                options.set(this@ClosureCompileTests.options)
                temporaryDirectory.set(temporaryDir)
            }
        }
    }

    private fun generateHtml(file: File, testClass: String) {
        file.parentFile.mkdirs()
        file.writeText(
            """
            <!DOCTYPE html>
            <html>
            <head><title>$testClass</title></head>
            <body>
            <script src="test.js"></script>
            </body>
            </html>
            """.trimIndent()
        )
    }

    private class TestSummary(
        val tests: Set<String>
    )
}
