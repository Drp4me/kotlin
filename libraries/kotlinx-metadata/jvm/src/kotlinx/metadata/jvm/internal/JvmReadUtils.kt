/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.metadata.jvm.internal

import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmLambda
import kotlinx.metadata.KmPackage
import kotlinx.metadata.internal.toKmClass
import kotlinx.metadata.internal.toKmLambda
import kotlinx.metadata.internal.toKmPackage
import kotlinx.metadata.jvm.KmModule
import kotlinx.metadata.jvm.KmPackageParts
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.UnstableMetadataApi
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMetadataVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping

internal object JvmReadUtils {
    internal fun readKmClass(annotationData: Metadata): KmClass {
        val (strings, proto) = JvmProtoBufUtil.readClassDataFrom(annotationData.requireNotEmpty(), annotationData.data2)
        return proto.toKmClass(strings)
    }

    internal fun readKmPackage(annotationData: Metadata): KmPackage {
        val (strings, proto) = JvmProtoBufUtil.readPackageDataFrom(annotationData.requireNotEmpty(), annotationData.data2)
        return proto.toKmPackage(strings)
    }

    internal fun readKmLambda(annotationData: Metadata): KmLambda? {
        val functionData =
            annotationData.data1.takeIf(Array<*>::isNotEmpty)?.let { data1 ->
                JvmProtoBufUtil.readFunctionDataFrom(data1, annotationData.data2)
            } ?: return null
        val (strings, proto) = functionData
        return proto.toKmLambda(strings)
    }

    internal fun readMetadataImpl(annotationData: Metadata, lenient: Boolean): KotlinClassMetadata {
        checkMetadataVersionForRead(annotationData, lenient)

        return wrapIntoMetadataExceptionWhenNeeded {
            when (annotationData.kind) {
                KotlinClassMetadata.CLASS_KIND -> KotlinClassMetadata.Class(annotationData, lenient)
                KotlinClassMetadata.FILE_FACADE_KIND -> KotlinClassMetadata.FileFacade(annotationData, lenient)
                KotlinClassMetadata.SYNTHETIC_CLASS_KIND -> KotlinClassMetadata.SyntheticClass(annotationData, lenient)
                KotlinClassMetadata.MULTI_FILE_CLASS_FACADE_KIND -> KotlinClassMetadata.MultiFileClassFacade(annotationData, lenient)
                KotlinClassMetadata.MULTI_FILE_CLASS_PART_KIND -> KotlinClassMetadata.MultiFileClassPart(annotationData, lenient)
                else -> KotlinClassMetadata.Unknown(annotationData, lenient)
            }
        }
    }

    @UnstableMetadataApi
    internal fun readModuleMetadataImpl(data: ModuleMapping): KmModule {
        val v = KmModule()
        for ((fqName, parts) in data.packageFqName2Parts) {
            val (fileFacades, multiFileClassParts) = parts.parts.partition { parts.getMultifileFacadeName(it) == null }
            v.packageParts[fqName] = KmPackageParts(
                fileFacades.toMutableList(),
                multiFileClassParts.associateWith { parts.getMultifileFacadeName(it)!! }.toMutableMap()
            )
        }

        for (annotation in data.moduleData.annotations) {
            @Suppress("DEPRECATION_ERROR")
            v.annotations.add(KmAnnotation(annotation, emptyMap()))
        }

        for (classProto in data.moduleData.optionalAnnotations) {
            v.optionalAnnotationClasses.add(classProto.toKmClass(data.moduleData.nameResolver))
        }
        return v
    }

    private fun checkMetadataVersionForRead(annotationData: Metadata, lenient: Boolean) {
        if (annotationData.metadataVersion.isEmpty())
            throw IllegalArgumentException("Provided Metadata instance does not have metadataVersion in it and therefore is malformed and cannot be read.")
        val jvmMetadataVersion = JvmMetadataVersion(
            annotationData.metadataVersion,
            (annotationData.extraInt and (1 shl 3)/* see JvmAnnotationNames.METADATA_STRICT_VERSION_SEMANTICS_FLAG */) != 0
        )
        throwIfNotCompatible(jvmMetadataVersion, lenient)
    }

    internal fun throwIfNotCompatible(jvmMetadataVersion: JvmMetadataVersion, lenient: Boolean) {
        val isAtLeast110 = jvmMetadataVersion.isAtLeast(1, 1, 0)
        val isCompatible = if (lenient) isAtLeast110 else jvmMetadataVersion.isCompatibleWithCurrentCompilerVersion()
        if (!isCompatible) {
            // Kotlin 1.0 produces classfiles with metadataVersion = 1.1.0, while 1.0.0 represents unsupported pre-1.0 Kotlin (see JvmMetadataVersion.kt:39)
            val postfix =
                if (!isAtLeast110) "while minimum supported version is 1.1.0 (Kotlin 1.0)."
                else "while maximum supported version is ${if (jvmMetadataVersion.isStrictSemantics) JvmMetadataVersion.INSTANCE else JvmMetadataVersion.INSTANCE_NEXT}. To support newer versions, update the kotlinx-metadata-jvm library."
            throw IllegalArgumentException("Provided Metadata instance has version $jvmMetadataVersion, $postfix")
        }
    }
}
