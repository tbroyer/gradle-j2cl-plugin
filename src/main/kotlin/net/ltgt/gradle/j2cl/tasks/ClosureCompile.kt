package net.ltgt.gradle.j2cl.tasks

import com.google.javascript.jscomp.CommandLineRunner
import com.google.javascript.jscomp.CompilationLevel
import com.google.javascript.jscomp.CompilerOptions
import net.ltgt.gradle.j2cl.internal.MOSHI
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
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
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
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

interface ClosureOptions {

    @get:Input
    val debug: Property<Boolean>

    @get:Input
    val exportTestFunctions: Property<Boolean>

    @get:Input
    val rewritePolyfills: Property<Boolean>

    @get:Input
    val compilationLevel: Property<String>

    @get:Input
    val jreLoggingLogLevel: Property<String>

    @get:Input
    val jreChecksCheckLevel: Property<String>

    @get:Input
    val jreClassMetadata: Property<String>

    @get:Input
    val defines: MapProperty<String, String>
}

private interface ClosureCompilerParameters : WorkParameters {
    val entrypoints: ListProperty<String>
    val files: ConfigurableFileCollection
    val destinationDirectory: DirectoryProperty
    val initialScriptFilename: Property<String>
    val options: Property<ClosureOptions>
    val temporaryDirectory: DirectoryProperty
}

private abstract class ClosureCompilerWorker : WorkAction<ClosureCompilerParameters> {

    override fun execute() {
        val entrypoints = parameters.entrypoints.get()
        val destinationDirectory by parameters.destinationDirectory
        val initialScriptFilename by parameters.initialScriptFilename
        val options by parameters.options
        val compilationLevel = CompilationLevel.fromString(options.compilationLevel.get())

        val configFile = defineJs(parameters.temporaryDirectory.get(), options)

        val args = mutableListOf<String>()
        entrypoints.flatMapTo(args) {
            listOf("--entry_point", "goog:$it")
        }
        val jsOutputFile = destinationDirectory.file(initialScriptFilename).asFile
        args.addAll(
            arrayOf(
                // from closure_js_binary
                "--js_output_file", jsOutputFile.path,
                "--create_source_map", "${jsOutputFile.path}.map",
                "--language_in", "ECMASCRIPT_2017",
                "--language_out", "ECMASCRIPT5",
                "--compilation_level", compilationLevel.toString(),
                "--dependency_mode", "PRUNE_LEGACY",
                "--warning_level", "VERBOSE",
                "--generate_exports",
                "--process_closure_primitives",
                "--define=goog.json.USE_NATIVE_JSON",
                "--hide_warnings_for=closure/goog/base.js",
                "--source_map_location_mapping", "${jsOutputFile.path}|${jsOutputFile.name}",
                if (options.debug.get()) "--debug" else "--define=goog.DEBUG=false",
                // from j2cl_application
                "--jscomp_off", "analyzerChecks",
                "--jscomp_off", "reportUnknownTypes"
            )
        )
        if (compilationLevel == CompilationLevel.ADVANCED_OPTIMIZATIONS) {
            args.add("--use_types_for_optimization")
        }
        args.add(configFile.path)
        parameters.files.flatMapTo(args) {
            when {
                it.isZip -> listOf("--jszip", it.path)
                it.isJs -> listOf(it.path)
                else -> listOf()
            }
        }
        args.add("--rewrite_polyfills=${options.rewritePolyfills.get()}")

        val runner = JsCompRunner(
            args.toTypedArray(),
            exportTestFunctions = options.exportTestFunctions.get()
        )
        if (runner.shouldRunCompiler()) {
            runner.run()
        }
        if (runner.exitCode != 0) {
            throw ClosureCompilerException(runner.exitCode)
        }
        if (runner.hasErrors()) {
            throw ClosureCompilerException()
        }
    }

    private fun defineJs(temporaryDir: Directory, options: ClosureOptions): File {
        val config = temporaryDir.asFile.resolve("config.js")

        val defines = mutableMapOf(
            "jre.checks.checkLevel" to options.jreChecksCheckLevel.get(),
            "jre.logging.logLevel" to options.jreLoggingLogLevel.get(),
            "jre.classMetadata" to options.jreClassMetadata.get()
        )
        defines.putAll(options.defines.get())

        config.writeText("var CLOSURE_DEFINES = ${MOSHI.adapter<Any>(Object::class.java).indent("  ").toJson(defines)};")

        return config
    }
}

private class JsCompRunner(
    args: Array<String>,
    private val exportTestFunctions: Boolean
) : CommandLineRunner(args) {
    var exitCode: Int = 0

    init {
        setExitCodeReceiver { this.exitCode = it!!; null }
    }

    override fun createOptions(): CompilerOptions = super.createOptions().apply {
        exportTestFunctions = this@JsCompRunner.exportTestFunctions
    }
}

private class ClosureCompilerException(exitCode: Int? = null) :
    RuntimeException("Compilation failed${exitCode?.let { " with exit code $it" } ?: ""}; see the compiler error output for details.")

private val File.isZip: Boolean
    get() = name.endsWith(".zip", ignoreCase = true) || name.endsWith(".jszip", ignoreCase = true)
private val File.isJs: Boolean
    get() = name.endsWith(".js", ignoreCase = true)
