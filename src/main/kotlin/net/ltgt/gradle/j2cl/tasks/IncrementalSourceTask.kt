package net.ltgt.gradle.j2cl.tasks

import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.model.ReplacedBy
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.compile.JavaCompile
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * The standard [SourceTask] does not support incremental sources
 * because its [getSource] doesn't return a stable value.
 *
 * This version, inspired by what's done in [JavaCompile] tasks et al,
 * replaces the [getSource] with a stable property.
 */
abstract class IncrementalSourceTask : SourceTask() {
    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    protected open val stableSources = objectFactory.fileCollection().from(
        Callable {
            return@Callable source
        }
    )

    @ReplacedBy("stableSources")
    override fun getSource(): FileTree = super.getSource()
}
