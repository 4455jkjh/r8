/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

// ***********************************************************************************
// MAINTAINED AND TESTED IN THE R8 REPO. PLEASE MAKE CHANGES THERE AND REPLICATE.
// ***********************************************************************************

package androidx.annotation.keep

import kotlin.reflect.KClass

/**
 * The annotated code uses reflection to inspect a runtime visible annotation on a class/interface,
 * or its subclasses / interface implementers.
 *
 * This annotation indicates to optimizers or shrinkers that the specified runtime annotation should
 * be retained on matching classes that remain in the final application build, if the annotated code
 * is reachable. Matching classes may still be shrunk, optimized, or renamed if not otherwise kept.
 *
 * @see UsesReflectionToConstruct
 * @see UsesReflectionToAccessMethod
 * @see UsesReflectionToAccessField
 */
@Retention(AnnotationRetention.BINARY)
@Repeatable
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
public annotation class UsesReflectionToReadClassAnnotation(

    /**
     * Annotation class read by reflection.
     *
     * Either `annotationConstant` or [annotationName] must be specified.
     *
     * Mutually exclusive with [annotationName].
     */
    val annotationConstant: KClass<*> = Unspecified::class,

    /**
     * Annotation class name read by reflection.
     *
     * Either [annotationConstant] or `annotationName` must be specified.
     *
     * Mutually exclusive with [annotationConstant].
     */
    val annotationName: String = "",

    /**
     * Class (and its subclasses / interface implementers) on which the annotation is read by
     * reflection.
     *
     * If neither `classConstant` nor `className` is specified then the annotation is retained on
     * all annotated classes.
     *
     * Mutually exclusive with [className].
     */
    val classConstant: KClass<*> = Unspecified::class,

    /**
     * Class name (and its subclasses / interface implementers) on which the annotation is read by
     * reflection.
     *
     * If neither `classConstant` nor `className` is specified then the annotation is retained on
     * all annotated classes.
     *
     * Mutually exclusive with [classConstant].
     */
    val className: String = "",
)
