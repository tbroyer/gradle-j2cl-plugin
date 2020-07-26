package net.ltgt.gradle.j2cl.tasks

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

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
