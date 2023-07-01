package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class SealedSubclassOrderSpec(private val env: KotlinCoreEnvironment) {

    private val subject = SealedSubclassOrder(
        TestConfig(
            EnumEntryOrder.INCLUDE_ANNOTATIONS to listOf("com.example.Alphabetical")
        )
    )

    @Language("Kotlin")
    private val annotation = """
        package com.example
        
        annotation class Alphabetical
    """.trimIndent()

    @Nested
    inner class `annotation on sealed class ` {
        @Test
        fun `reports out of order`() {
            val code = """
                package com.example                

                import com.example.Alphabetical
        
                @Alphabetical
                sealed class Fruit {
                    object Banana: Fruit()
                    object Apple: Fruit()
                }         
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code, annotation)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasSourceLocation(7, 5)
            assertThat(findings[0]).hasMessage(
                "Sealed subclasses for class `Fruit` are not declared in alphabetical order. " +
                    "Reorder so that `Apple` is before `Banana`."
            )
        }

        @Test
        fun `does not report in order`() {
            val code = """
                import com.example.Alphabetical
    
                @Alphabetical
                sealed class Fruit {
                    object Apple: Fruit()
                    object Banana: Fruit()
                }         
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation)).isEmpty()
        }

        @Test
        fun `does not report empty sealed class`() {
            val code = """
                import com.example.Alphabetical
        
                @Alphabetical
                sealed class Fruit

                @Alphabetical
                sealed class Vegetable {
                    val vitamin: String
                    
                    abstract fun eat()
                }
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation))
                .isEmpty()
        }

        @Test
        fun `does not report nested sealed class`() {
            val code = """
                import com.example.Alphabetical
                
                @Alphabetical
                sealed class Meal {
                    object Breakfast: Meal()
                    object Lunch: Meal()
                    object Supper: Meal()
                    
                    sealed class Fruit {
                        object Banana: Fruit()
                        object Apple: Fruit()
                    }
                }                
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation))
                .isEmpty()
        }

        @Test
        fun `reports emoji entries out of order`() {
            // https://en.wikipedia.org/wiki/Emoji#Unicode_blocks

            @Suppress("ClassName")
            val code = """
                import com.example.Alphabetical
        
                @Alphabetical
                sealed class Fruit {
                    object `🍒`: Fruit()
                    object `🍎`: Fruit()
                }         
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation))
                .hasSize(1)
                .hasTextLocations("object `🍒`: Fruit()")
        }

        @Test
        fun `reports names in backticks out of order`() {
            @Suppress("ClassName")
            val code = """
                import com.example.Alphabetical
    
                @Alphabetical
                sealed class Fruit {
                    object `yellow banana`: Fruit()
                    object apple: Fruit()
                }         
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code, annotation)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Sealed subclasses for class `Fruit` are not declared in alphabetical order. " +
                    "Reorder so that `apple` is before `yellow banana`."
            )
        }

        @Test
        fun `reports keyword names out of order`() {
            @Suppress("ClassName")
            val code = """
                import com.example.Alphabetical
        
                @Alphabetical
                sealed class Fruit {
                    object `null`: Fruit()
                    object NULL: Fruit()
                }         
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation))
                .hasSize(1)
        }

        @Test
        fun `reports out of order for more complex sealed`() {
            val code = """
                import com.example.Alphabetical
        
                @Alphabetical
                sealed class Fruit(val id: String) {                
                    class Banana: Fruit("banana") {
                        override val calories: Int = 100
                        override fun eat() {
                            println("Eating a banana!")
                        }
                    }
                    class Apple: Fruit("apple") {
                        override val calories: Int = 50
                        override fun eat() {
                            println("Eating an apple!")
                        }
                    }
            
                    abstract val calories: Int
            
                    abstract fun eat()
            
                    fun eatAll() {
                        println("Eating all the fruit!")
                    }
                }         
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation))
                .hasSize(1)
        }
    }

    @Nested
    inner class `annotation on supertype` {
        @Test
        fun `reports out of order for super interface`() {
            val code = """
                package com.example
                                
                @Alphabetical
                interface Identifiable {
                    val id: String
                }
                
                sealed class Fruit(override val id: String) : Identifiable {
                    class Banana: Fruit("banana")
                    class Apple: Fruit("apple")
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code, annotation)
            assertThat(findings).hasSize(1)
            assertThat(findings[0]).hasMessage(
                "Sealed subclasses for class `Fruit` (which implements `Identifiable`) are not declared in alphabetical " +
                    "order. Reorder so that `Apple` is before `Banana`."
            )
        }

        @Test
        fun `reports out of order for super super interface`() {
            val code = """
                package com.example                
                
                @Alphabetical
                interface Identifiable {
                    val id: String
                }
                
                interface Delicious

                interface PrettyPrintable : Identifiable {
                    val prettyString: String
                }
                
                sealed class Fruit(override val prettyString: String, override val id: String) : PrettyPrintable, Delicious {
                    object Banana: Fruit("Yellow banana", "banana")
                    object Apple: Fruit("Red apple", "apple")
                }
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation)).hasSize(1)
        }

        @Test
        fun `reports interface with recursive type parameters out of order`() {
            val code = """
                package com.example                
                                
                @Alphabetical
                interface Tree<T : Tree<T>> {
                    fun getChildren(): List<T>
                }
                
                sealed class BinaryTree : Tree<BinaryTree> {
                    object Node: BinaryTree() {
                        override fun getChildren(): List<BinaryTree> = listOf(LEAF, LEAF)
                    }
                    object Leaf: BinaryTree() {
                        override fun getChildren(): List<BinaryTree> = emptyList()
                    }
                }
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code, annotation)).hasSize(1)
        }
    }
}
