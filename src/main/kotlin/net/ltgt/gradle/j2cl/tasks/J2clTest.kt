package net.ltgt.gradle.j2cl.tasks

import net.ltgt.gradle.j2cl.internal.createSimpleHttpServer
import net.ltgt.gradle.j2cl.internal.url
import net.ltgt.gradle.j2cl.internal.use
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.inject.Inject

@CacheableTask
abstract class J2clTest : DefaultTask() {
    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val reportsDirectory: DirectoryProperty

    // TODO: add test filtering, and make it configurable as an @Option

    @get:Classpath
    abstract val webdriverClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute() {
        if (testsDirectory.asFileTree.matching { include("*/index.html") }.isEmpty) {
            // XXX: fail? add a failOnNoMatchingTests?
            didWork = false
            return
        }

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(webdriverClasspath)
        }

        // Use a single worker and do our own parallelism so we can manage the DriverService lifecycle
        workQueue.submit(WebdriverWorker::class) {
            testsDirectory.set(this@J2clTest.testsDirectory)
            reportsDirectory.set(this@J2clTest.reportsDirectory)
        }
    }
}

private interface WebdriverParameters : WorkParameters {
    val testsDirectory: DirectoryProperty
    val reportsDirectory: DirectoryProperty
}

private abstract class WebdriverWorker : WorkAction<WebdriverParameters> {
    override fun execute() {
        createSimpleHttpServer(parameters.testsDirectory.get().asFile).use { server ->
            val testUrls = mutableMapOf<String, String>()
            parameters.testsDirectory.asFileTree.matching { include("*/index.html") }.visit {
                if (isDirectory) return@visit
                testUrls[file.parentFile.name] = server.url("/$path")
            }
            val service = ChromeDriverService.createDefaultService()
            try {
                testUrls.entries.parallelStream().forEach { (testName, testUrl) ->
                    val testResult = parameters.reportsDirectory.file("$testName.txt").get().asFile.apply {
                        createNewFile()
                    }
                    try {
                        createDriver(service).use(
                            {
                                // Inspired by rules_closure's phantomjs_test
                                // Copyright 2016 The Closure Rules Authors. All rights reserved.
                                get(testUrl)
                                manage().timeouts().setScriptTimeout(1, TimeUnit.MINUTES)
                                val isSuccess = executeAsyncScript(
                                    """
                                    var callback = arguments[arguments.length - 1];
                                    window.setInterval(function() {
                                      if (!window['G_testRunner']) {
                                        console.log('ERROR: G_testRunner not defined. ' +
                                            'Did you remember to goog.require(\'goog.testing.jsunit\')?');
                                        callback(false);
                                        return;
                                      }
                                      if (window['G_testRunner'].isFinished()) {
                                        callback(window['G_testRunner'].isSuccess());
                                      }
                                    }, 200);
                                    """.trimIndent()
                                ) as Boolean
                                testResult.writeText(manage().logs().get(LogType.BROWSER).joinToString(separator = "\n"))
                                if (!isSuccess) {
                                    println("Test failed: $testName")
                                }
                            },
                            WebDriver::quit
                        )
                    } catch (e: Exception) {
                        testResult.printWriter().use { e.printStackTrace(it) }
                    }
                }
                // TODO: fail the task when a test fails
            } finally {
                service.stop()
            }
        }
    }

    private fun createDriver(service: ChromeDriverService) =
        ChromeDriver(
            service,
            ChromeOptions().apply {
                setHeadless(true)
                setCapability(
                    "goog:loggingPrefs",
                    LoggingPreferences().apply {
                        enable(LogType.BROWSER, Level.ALL)
                    }
                )
            }
        )
}
