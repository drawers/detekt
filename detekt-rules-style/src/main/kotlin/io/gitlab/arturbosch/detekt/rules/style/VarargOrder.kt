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
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

/**
 * Detects vararg arguments supplied out of alphabetical order for functions like [listOf] and [setOf]
 * where there is a single parameter marked `vararg`.
 *
 * Keeping vararg arguments in order, where appropriate, allows for easy scanning of a large set of arguments
 * and makes for smaller diffs and less merge conflicts in pull requests.
 *
 * The rule can be configured by declaring an annotation in your project with [AnnotationTarget.EXPRESSION]. You can
 * use this annotation to decorate only the specific calls to [listOf] etc. that you want to check for order.
 *
 *  <noncompliant>
 *  val fruits = @@Alphabetical listOf("banana", "apple")
 *  </noncompliant>
 *  <compliant>
 *  val fruits = @@Alphabetical listOf("apple", "banana")
 *  </compliant>
 *
 */
@RequiresTypeResolution
class VarargOrder(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = "VarargOrder",
        Severity.Style,
        "Vararg arguments are not passed in alphabetical order.",
        debt = Debt.FIVE_MINS
    )

    @Configuration(
        "A list of fully-qualified names of annotations that can decorate a call expression with vararg."
    )
    private val includeAnnotations: List<String> by config(
        listOf("io.gitlab.arturbosch.detekt.annotations.Alphabetical")
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val annotatedExpression = expression.parent as? KtAnnotatedExpression ?: return

        if (!annotatedExpression.anyAnnotationInConfig()) {
            return
        }

        if (expression.valueArguments.isEmpty()) return

        val callableDescriptor =
            expression.calleeExpression.getResolvedCall(bindingContext)?.resultingDescriptor ?: return

        // keep rule simple by restricting it to calls with a single vararg parameter
        if (callableDescriptor.valueParameters.size > 1) {
            return
        }

        if (!callableDescriptor.valueParameters.first().isVararg) {
            return
        }

        val zipped =
            expression.valueArguments
                .sortedBy { it.text }
                .zip(expression.valueArguments) { expected, actual ->
                    Arg(expected, actual)
                }

        val firstOutOfOrder = zipped.firstOrNull { it.expected.text != it.actual.text } ?: return

        report(
            CodeSmell(
                issue,
                Entity.from(firstOutOfOrder.actual),
                "Arguments to `${callableDescriptor.name.asString()}` are not in alphabetical order. " +
                    "Reorder so that `${firstOutOfOrder.expected.text}` is before `${firstOutOfOrder.actual.text}`.",
            )
        )
    }

    private fun KtAnnotatedExpression.anyAnnotationInConfig() =
        annotationEntries.any {
            it.getResolvedCall(bindingContext)
                ?.resultingDescriptor // the <init> constructor of the annotation
                ?.returnType
                ?.fqNameOrNull()
                ?.asString() in includeAnnotations
        }

    private class Arg(
        val expected: KtValueArgument,
        val actual: KtValueArgument
    ) {

        override fun toString(): String {
            return "Arg(expected=${expected.text}, actual=${actual.text})"
        }
    }

    companion object {
        const val INCLUDE_ANNOTATIONS = "includeAnnotations"
    }
}
