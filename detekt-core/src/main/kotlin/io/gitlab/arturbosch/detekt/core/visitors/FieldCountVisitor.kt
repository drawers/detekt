package io.gitlab.arturbosch.detekt.core.visitors

import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.core.NUMBER_OF_FIELDS_KEY
import io.gitlab.arturbosch.detekt.core.visitors.util.collectByType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

class FieldCountVisitor : DetektVisitor() {

	override fun visitKtFile(file: KtFile) {
		super.visitKtFile(file)
		file.putUserData(NUMBER_OF_FIELDS_KEY, file.collectByType<KtProperty>().size)
	}
}
