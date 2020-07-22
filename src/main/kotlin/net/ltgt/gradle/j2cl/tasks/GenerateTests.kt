package net.ltgt.gradle.j2cl.tasks

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import javax.inject.Inject
import javax.tools.JavaCompiler
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider

@CacheableTask
abstract class GenerateTests : DefaultTask(), PatternFilterable {

    private val patterns: PatternFilterable = PatternSet()

    @get:Internal
    abstract val testClassesDirs: ConfigurableFileCollection

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    protected val testClasses by lazy {
        testClassesDirs.asFileTree.matching(patterns)
    }

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val sourceCompatibility: Property<JavaVersion>

    @get:CompileClasspath
    abstract val annotationClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val annotationProcessorPath: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val bootstrapClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun execute() {
        // TODO: process incrementally (only changed test classes)
        // (we could assume that test classes will change if one of their dependencies have changed too)
        val testClassNames = mutableSetOf<String>()
        testClasses.visit {
            if (isDirectory) return@visit
            // XXX: No need to worry about binary vs source name as J2clTestingProcessor only accepts top-level classes
            testClassNames.add(path.removeSuffix(".class").replace('/', '.'))
        }
        val generatedTestSuite = temporaryDir.resolve("GeneratedTestSuiteJ2clTestInput.java")
        generatedTestSuite.writeText(
            """
            package javatests;
            
            import com.google.j2cl.junit.apt.J2clTestInput;
            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;
            import org.junit.runners.Suite.SuiteClasses;

            @J2clTestInput(GeneratedTestSuiteJ2clTestInput.class)
            @RunWith(Suite.class)
            @SuiteClasses({${testClassNames.sorted().joinToString { "$it.class" }}})
            public class GeneratedTestSuiteJ2clTestInput {}
            """.trimIndent()
        )

        val destinationDir = destinationDirectory.get().asFile.path
        val args = mutableListOf(
            "-sourcepath", "",
            "-proc:only", // we don't need to compile the generated class
            // we don't need to distinguish between generated sources or resources
            // and the Vertispan fork changes the output locations anyway,
            // so this supports both vanilla J2CL and the Vertispan fork.
            "-d", destinationDir,
            "-s", destinationDir,
            "-classpath", layout.files(testClassesDirs, annotationClasspath, classpath).asPath,
            "-bootclasspath", bootstrapClasspath.asPath,
            "-processorpath", annotationProcessorPath.asPath,
            "-processor", "com.google.j2cl.junit.apt.J2clTestingProcessor"
        )
        sourceCompatibility.orNull?.let {
            args.add("-source")
            args.add(it.toString())
        }

        val compiler = ToolProvider.getSystemJavaCompiler()
        val fileManager = createFileManager(compiler)
        val compileTask =
            compiler.getTask(null, fileManager, null, args, null, fileManager.getJavaFileObjects(generatedTestSuite))
        if (!compileTask.call()) {
            throw GenerateTestsException()
        }
    }

    // PatternFilterable

    override fun exclude(excludes: Iterable<String>): PatternFilterable {
        patterns.exclude(excludes)
        return this
    }

    override fun exclude(excludeSpec: Closure<*>): PatternFilterable {
        patterns.exclude(excludeSpec)
        return this
    }

    override fun exclude(excludeSpec: Spec<FileTreeElement>): PatternFilterable {
        patterns.exclude(excludeSpec)
        return this
    }

    override fun exclude(vararg excludes: String?): PatternFilterable {
        patterns.exclude(*excludes)
        return this
    }

    @Internal
    override fun getExcludes(): MutableSet<String> = patterns.excludes

    @Internal
    override fun getIncludes(): MutableSet<String> = patterns.includes

    override fun include(includeSpec: Spec<FileTreeElement>): PatternFilterable {
        patterns.include(includeSpec)
        return this
    }

    override fun include(includes: Iterable<String>): PatternFilterable {
        patterns.include(includes)
        return this
    }

    override fun include(includeSpec: Closure<*>): PatternFilterable {
        patterns.include(includeSpec)
        return this
    }

    override fun include(vararg includes: String?): PatternFilterable {
        patterns.include(*includes)
        return this
    }

    override fun setExcludes(excludes: Iterable<String>): PatternFilterable {
        patterns.setExcludes(excludes)
        return this
    }

    override fun setIncludes(includes: Iterable<String>): PatternFilterable {
        patterns.setIncludes(includes)
        return this
    }

    private fun createFileManager(compiler: JavaCompiler): StandardJavaFileManager {
        // see org.gradle.api.internal.tasks.compile.JdkJavaCompiler
        // and org.gradle.api.internal.tasks.compile.reflect.GradleStandardJavaFileManager
        val delegate = compiler.getStandardFileManager(null, null, Charsets.UTF_8)
        if (!JavaVersion.current().isJava9Compatible) {
            return delegate
        }
        return object : StandardJavaFileManager by delegate {
            override fun hasLocation(location: JavaFileManager.Location) = when (location) {
                StandardLocation.SOURCE_PATH -> false
                else -> delegate.hasLocation(location)
            }

            override fun list(
                location: JavaFileManager.Location,
                packageName: String,
                kinds: Set<JavaFileObject.Kind>,
                recurse: Boolean
            ) = delegate.list(
                location,
                packageName,
                when (location) {
                    StandardLocation.CLASS_PATH -> kinds - JavaFileObject.Kind.SOURCE
                    else -> kinds
                },
                recurse
            )
        }
    }
}

private class GenerateTestsException :
    RuntimeException("Generating tests failed; see the error output for details.")
