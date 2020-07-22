package net.ltgt.gradle.j2cl.internal

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal val MOSHI = Moshi.Builder()
    .add(KotlinJsonAdapterFactory()).build()
