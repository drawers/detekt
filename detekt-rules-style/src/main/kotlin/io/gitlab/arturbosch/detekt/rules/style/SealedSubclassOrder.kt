package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.Configuration
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import io.gitlab.arturbosch.detekt.rules.identifierName
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces

/**
 * Detects sealed subclasses declared out of alphabetical order when either the sealed class itself or one of its super types
 * is decorated with the @Alphabetical annotation. Keeping sealed subclasses in order, where appropriate, allows for
 * easy scanning of a large sealed class and makes for smaller diffs and less merge conflicts in pull requests.
 *
 *  <noncompliant>
 *  @@Alphabetical
 *  sealed class Fruit {
 *      object Banana: Fruit()
 *      object Apple: Fruit()
 *  }
 *  </noncompliant>
 *  <compliant>
 *  @@Alphabetical
 *  sealed class Fruit {
 *      object Apple: Fruit()
 *      object Banana: Fruit()
 *  }
 *  </compliant>
 *  <noncompliant>
 *  @@Alphabetical
 *  interface Edible {
 *      val calories: Int
 *  }
 *  sealed class Fruit(override val calories: Int): Edible {
 *     object Banana: Fruit(100),
 *     object Apple: Fruit(75)
 *  }
 *  </noncompliant>
 *  <compliant>
 *  @@Alphabetical
 *  interface Edible {
 *      val calories: Int
 *  }
 *  sealed class Fruit(override val calories: Int): Edible {
 *     object Apple: Fruit(75)
 *     object Banana: Fruit(100),
 *  }
 *  </noncompliant>
 */
@RequiresTypeResolution
class SealedSubclassOrder(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = "SealedSubclassOrder",
        Severity.Style,
        "Sealed subclasses are not declared in alphabetical order.",
        debt = Debt.FIVE_MINS
    )

    @Configuration(
        "A list of fully-qualified names of annotations that can decorate a sealed class or one of its super types."
    )
    private val includeAnnotations: List<String> by config(
        listOf("io.gitlab.arturbosch.detekt.annotations.Alphabetical")
    )

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        if (!classOrObject.hasModifier(KtTokens.SEALED_KEYWORD)) {
            return
        }

        val sealedSuperTypeDescriptor = bindingContext[BindingContext.CLASS, classOrObject] ?: return
        val sealedSuperTypeName = sealedSuperTypeDescriptor.fqNameOrNull() ?: return

        // use BFS because we assume the annotation is more likely to be found in shallower nodes
        // when we start searching from the enum class descriptor itself
        val classWithAnnotation =
            sealedSuperTypeDescriptor.breadthFirstSearchInSuperInterfaces { it.anyIncludeAnnotations() } ?: return

        // search for annotation
        val sealedSubclassDeclarations = classOrObject.declarations
            .filterIsInstance<KtClassOrObject>()
            .filter {
                it.getSuperTypeList()?.entries?.any { superType ->
                    superType.getResolvedCall(bindingContext)
                        ?.resultingDescriptor
                        ?.returnType
                        ?.fqNameOrNull() == sealedSuperTypeName
                } ?: false
            }

        val zipped = sealedSubclassDeclarations.sortedBy {
            it.name.orEmpty()
        }.zip(sealedSubclassDeclarations) { expected: KtClassOrObject, actual: KtClassOrObject ->
            SubClassPair(expected = expected, actual = actual)
        }

        val firstOutOfOrder = zipped.firstOrNull {
            it.actual.name != it.expected.name
        } ?: return

        report(
            CodeSmell(
                issue,
                Entity.from(firstOutOfOrder.actual),
                buildMessage(classOrObject, classWithAnnotation, firstOutOfOrder)
            )
        )
    }

    private fun ClassDescriptor.breadthFirstSearchInSuperInterfaces(
        predicate: (ClassDescriptor) -> Boolean
    ): ClassDescriptor? {
        val deque = ArrayDeque(listOf(this))
        while (deque.isNotEmpty()) {
            val head = deque.pop()
            if (predicate(head)) {
                return head
            }
            for (superInterface in head.getSuperInterfaces()) {
                deque.push(superInterface)
            }
        }
        return null
    }

    private fun ClassDescriptor.anyIncludeAnnotations() =
        annotations.any { it.fqName?.asString() in includeAnnotations }

    private class SubClassPair(
        val expected: KtClassOrObject,
        val actual: KtClassOrObject,
    ) {
        override fun toString(): String {
            return "SubClassPair(expected=${expected.identifierName()}, actual=${actual.identifierName()})"
        }
    }

    private fun buildMessage(
        sealedClass: KtClassOrObject,
        classWithAnnotation: ClassDescriptor,
        firstOutOfOrder: SubClassPair
    ) = buildString {
        append("Sealed subclasses for class `${sealedClass.identifierName()}` ")
        if (sealedClass.fqName != classWithAnnotation.fqNameOrNull()) {
            append("(which implements `${classWithAnnotation.name.identifier}`) ")
        }
        append("are not declared in alphabetical order. ")
        append("Reorder so that `${firstOutOfOrder.expected.name}` ")
        append("is before `${firstOutOfOrder.actual.name}`.")
    }

    companion object {
        const val INCLUDE_ANNOTATIONS = "includeAnnotations"
    }
}
