plugins {
  id("conventions.kmp-library")
  id("conventions.publish")
}

publish {
  configurePom(
    artifactId = "annotations",
    pomName = "Anvil Annotations",
    pomDescription = "Annotations used to mark classes and methods for code generation in Anvil",
    overrideArtifactId = false,
  )
}
