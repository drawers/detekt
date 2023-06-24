package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class VarargOrderSpec(private val env: KotlinCoreEnvironment) {

    private val subject = VarargOrder(
        TestConfig(
            VarargOrder.INCLUDE_ANNOTATIONS to listOf("com.example.Alphabetical")
        )
    )

    @Language("kotlin")
    private val annotation = """
        package com.example

        @Target(AnnotationTarget.EXPRESSION)
        @Retention(AnnotationRetention.SOURCE)
        annotation class Alphabetical

        val fruits = @Alphabetical listOf("banana", "apple", "cherry")
    """.trimIndent()

    @Test
    fun `reports simple out of order`() {
        val code = """
            import com.example.Alphabetical

            val fruits = @Alphabetical listOf("banana", "apple", "cherry")
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code, annotation)
        assertThat(findings).hasSize(1)
        assertThat(findings[0]).hasMessage(
            "Arguments to `listOf` are not in alphabetical order. " + "Reorder so that `\"apple\"` is before `\"banana\"`."
        )
    }

    @Test
    fun `reports out of order for setOf`() {
        val code = """
            import com.example.Alphabetical

            val fruits = @Alphabetical setOf("banana", "apple", "cherry")
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code, annotation)).hasSize(1)
    }

    @Test
    fun `reports out of order for user function`() {
        val code = """
            import com.example.Alphabetical

            fun <T> trimmedSetOf(vararg elements: T) = setOf(elements).dropLast(1)

            val fruits = @Alphabetical trimmedSetOf("banana", "apple", "cherry")
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code, annotation)).hasSize(1)
    }

    @Test
    fun `reports out of order for enums`() {
        val code = """
            import com.example.Alphabetical

            enum class Fruit {
                APPLE, BANANA
            }
            
            val fruits = @Alphabetical setOf(Fruit.BANANA, Fruit.APPLE)
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code, annotation)
        assertThat(findings).hasSize(1)
        assertThat(findings[0]).hasMessage(
            "Arguments to `setOf` are not in alphabetical order. " +
                "Reorder so that `Fruit.APPLE` is before `Fruit.BANANA`."
        )
    }

    @Test
    fun `doesn't report when name identifiers are in order`() {
        @Suppress("EnumEntryName")
        val code = """
            import com.example.Alphabetical            

            enum class Fruit {
                `Amazing apple`, BANANA
            }
            
            val fruits = @Alphabetical setOf(Fruit.BANANA, Fruit.`Amazing apple`)
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code, annotation)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports out of order for mix of variables as arguments`() {
        val code = """
            import com.example.Alphabetical            

            val a = "a"
            val b = "b"
            val letters = @Alphabetical setOf(b, "a") // `"a"` in quotation marks is before `b`
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code, annotation)
        assertThat(findings).hasSize(1)
        assertThat(findings[0]).hasMessage(
            "Arguments to `setOf` are not in alphabetical order. " + "Reorder so that `\"a\"` is before `b`."
        )
    }

    @Test
    fun `does not report in order`() {
        val code = """
            import com.example.Alphabetical
            
            val fruits = listOf("apple", "banana", "cherry")
        """.trimIndent()

        assertThat(subject.compileAndLintWithContext(env, code, annotation)).isEmpty()
    }
}
