package com.squareup.anvil.compiler.internal.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.contributesMultibindingFqName
import org.jetbrains.kotlin.name.FqName

public fun KSClassDeclaration.checkNotMoreThanOneQualifier(
  annotationFqName: FqName,
) {
  val annotationsList = resolvableAnnotations.toList()
  // The class is annotated with @ContributesBinding, @ContributesMultibinding, or another Anvil annotation.
  // If there is less than 2 further annotations, then there can't be more than two qualifiers.
  if (annotationsList.size <= 2) return

  val qualifierCount = annotationsList.count { it.isQualifier() }
  if (qualifierCount > 1) {
    throw KspAnvilException(
      message = "Classes annotated with @${annotationFqName.shortName()} may not use more " +
        "than one @Qualifier.",
      node = this,
    )
  }
}

public inline fun KSClassDeclaration.checkClassIsPublic(message: () -> String) {
  if (getVisibility() != PUBLIC) {
    throw KspAnvilException(
      message = message(),
      node = this,
    )
  }
}

public fun KSClassDeclaration.checkNotMoreThanOneMapKey() {
  // The class is annotated with @ContributesMultibinding. If there is less than 2 further
  // annotations, then there can't be more than two map keys.
  val annotationsList = resolvableAnnotations.toList()
  if (annotationsList.size <= 2) return

  val mapKeysCount = annotationsList.count { it.isMapKey() }

  if (mapKeysCount > 1) {
    throw KspAnvilException(
      message = "Classes annotated with @${contributesMultibindingFqName.shortName()} may not " +
        "use more than one @MapKey.",
      node = this,
    )
  }
}

public fun KSClassDeclaration.checkSingleSuperType(
  annotationFqName: FqName,
  resolver: Resolver,
) {
  // If the bound type exists, then you're allowed to have multiple super types. Without the bound
  // type there must be exactly one super type.
  val hasExplicitBoundType = getKSAnnotationsByQualifiedName(annotationFqName.asString())
    .firstOrNull()
    ?.boundTypeOrNull() != null
  if (hasExplicitBoundType) return

  if (superTypesExcludingAny(resolver, shallow = true).count() != 1) {
    throw KspAnvilException(
      message = "${qualifiedName?.asString()} contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @${annotationFqName.shortName()} annotation.",
      node = this,
    )
  }
}

public fun KSClassDeclaration.checkClassExtendsBoundType(
  annotationFqName: FqName,
  resolver: Resolver,
) {
  val boundType = getKSAnnotationsByQualifiedName(annotationFqName.asString())
    .firstOrNull()
    ?.boundTypeOrNull()
    ?: superTypesExcludingAny(resolver, shallow = true).singleOrNull()
    ?: throw KspAnvilException(
      message = "Couldn't find the bound type.",
      node = this,
    )

  // The boundType is declared explicitly in the annotation. Since all classes extend Any, we can
  // stop here.
  if (boundType == resolver.builtIns.anyType) return

  if (!boundType.isAssignableFrom(asType(emptyList()))) {
    throw KspAnvilException(
      message = "${this.qualifiedName?.asString()} contributes a binding " +
        "for ${boundType.declaration.qualifiedName?.asString()}, but doesn't " +
        "extend this type.",
      node = this,
    )
  }
}

public fun KSClassDeclaration.superTypesExcludingAny(
  resolver: Resolver,
  shallow: Boolean,
): Sequence<KSType> {
  val supertypeSequence = if (shallow) {
    superTypes.map { it.resolve() }
  } else {
    getAllSuperTypes()
  }
  return supertypeSequence
    .filterNot { it == resolver.builtIns.anyType }
}

public fun KSClassDeclaration.isInterface(): Boolean {
  return classKind == ClassKind.INTERFACE
}
