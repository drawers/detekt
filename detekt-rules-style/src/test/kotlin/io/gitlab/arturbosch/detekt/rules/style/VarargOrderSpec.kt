package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class VarargOrderSpec(private val env: KotlinCoreEnvironment) {

    private val subject = VarargOrder(
        TestConfig(
            VarargOrder.INCLUDE_ANNOTATIONS to listOf("com.example.Alphabetical")
        )
    )

    @Nested
    inner class `without configured annotation` {

        @Test
        fun `reports simple out of order`() {
            val code = """
                package com.example.alphabetical

                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                annotation class Alphabetical

                val fruits = @Alphabetical listOf("banana", "apple", "cherry")
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Arguments to `listOf` are not in alphabetical order. " +
                    "Reorder so that `\"apple\"` is before `\"banana\"`."
            )
        }

        @Test
        fun `reports out of order for setOf`() {
            val code = """
                val fruits = setOf("banana", "apple", "cherry")
            """.trimIndent()
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
        }

        @Test
        fun `reports out of order for user function`() {
            val code = """
                fun <T> trimmedSetOf(vararg elements: T) = setOf(elements).dropLast(1)
                val fruits = trimmedSetOf("banana", "apple", "cherry")
            """.trimIndent()
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
        }

        @Test
        fun `reports out of order for enums`() {
            val code = """
                enum class Fruit {
                    APPLE, BANANA
                }
                
                val fruits = setOf(Fruit.BANANA, Fruit.APPLE)
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Arguments to `setOf` are not in alphabetical order. " +
                    "Reorder so that `Fruit.APPLE` is before `Fruit.BANANA`."
            )
        }

        @Test
        fun `reports out of order with backticks`() {
            val code = """
                enum class Fruit {
                    `Amazing apple`, BANANA
                }
                
                val fruits = setOf(Fruit.BANANA, Fruit.`Amazing apple`)
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Arguments to `setOf` are not in alphabetical order. " +
                    "Reorder so that `Fruit.APPLE` is before `Fruit.BANANA`."
            )
        }

        @Test
        fun `reports out of order for mix of variables as arguments`() {
            val code = """
                val a = "a"
                val b = "b"
                val letters = setOf(b, "a")
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Arguments to `setOf` are not in alphabetical order. " +
                    "Reorder so that `\"a\"` is before `b`."
            )
        }

        @Test
        fun `does not report in order`() {
            val code = """        
                val fruits = listOf("apple", "banana", "cherry")
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }
    }

    @Nested
    inner class `with configured annotation` {
        @Test
        fun `reports out of order when annotation is present`() {
            val code = """
                package com.example
    
                @Retention(AnnotationRetention.SOURCE)
                @Target(AnnotationTarget.EXPRESSION)
                annotation class Alphabetical
    
                val fruits = @Alphabetical listOf("banana", "apple", "cherry")
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Arguments to `listOf` are not in alphabetical order. " +
                    "Reorder so that `\"apple\"` is before `\"banana\"`."
            )
        }

        @Test
        fun `does not report out of order when annotation is absent`() {
            val code = """
                package com.example
        
                val fruits = listOf("banana", "apple", "cherry")
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }
    }
}
