package missinglinkcli

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs

import java.io.File

import com.spotify.missinglink._
import com.spotify.missinglink.Conflict.ConflictCategory
import com.spotify.missinglink.datamodel._

import scala.collection.JavaConverters._

object MissingLink extends CaseApp[MissingLinkOptions] {

  // adapted from https://github.com/scalacenter/sbt-missinglink/blob/dbd507772f7bc563bfdf75e76efafa2434a36033/src/main/scala/ch/epfl/scala/sbtmissinglink/MissingLinkPlugin.scala#L267-L317
  private def outputConflicts(conflicts: Seq[Conflict], printLn: String => Unit): Unit = {

    val descriptions = Map(
      ConflictCategory.CLASS_NOT_FOUND -> "Class being called not found",
      ConflictCategory.METHOD_SIGNATURE_NOT_FOUND -> "Method being called not found",
    )

    // group conflict by category
    val byCategory = conflicts.groupBy(_.category())

    for ((category, conflictsInCategory) <- byCategory) {
      val desc = descriptions.getOrElse(category, category.name().replace('_', ' '))
      printLn("")
      printLn("Category: " + desc)

      // next group by artifact containing the conflict
      val byArtifact = conflictsInCategory.groupBy(_.usedBy())

      for ((artifactName, conflictsInArtifact) <- byArtifact) {
        printLn("  In artifact: " + artifactName.name())

        // next group by class containing the conflict
        val byClassName = conflictsInArtifact.groupBy(_.dependency().fromClass())

        for ((classDesc, conflictsInClass) <- byClassName) {
          printLn("    In class: " + classDesc.toString())

          for (conflict <- conflictsInClass) {
            def optionalLineNumber(lineNumber: Int): String =
              if (lineNumber != 0) ":" + lineNumber else ""

            val dep = conflict.dependency()
            printLn(
              "      In method:  " +
                dep.fromMethod().prettyWithoutReturnType() +
                optionalLineNumber(dep.fromLineNumber())
            )
            printLn("      " + dep.describe())
            printLn("      Problem: " + conflict.reason())
            if (conflict.existsIn() != ConflictChecker.UNKNOWN_ARTIFACT_NAME)
              printLn("      Found in: " + conflict.existsIn().name())
            // this could be smarter about separating each blob of warnings by method, but for
            // now just output a new line always
            printLn("")
          }
        }
      }
    }
  }

  private def loadArtifact(f: File): Artifact = {
    val artifactLoader = new ArtifactLoader
    System.err.println(s"Loading $f")
    try artifactLoader.load(f)
    finally System.gc()
  }

  private def loadBootstrapArtifacts(): Seq[Artifact] =
    Option(System.getProperty("sun.boot.class.path")) match {
      case Some(bootstrapClasspath) =>
        val cp = bootstrapClasspath
          .split(System.getProperty("path.separator"))
          .toSeq
          .map(new File(_))
          .filter(_.exists())
        cp.map(loadArtifact)
      case None =>
        System.err.println(s"Loading boot class path")
        Java9ModuleLoader.getJava9ModuleArtifacts((s, _) => System.err.println(s"Warning: $s"))
          .asScala.toList
    }

  def run(options: MissingLinkOptions, args: RemainingArgs): Unit = {

    if (args.all.nonEmpty) {
      System.err.println(s"Unexpected arguments: ${args.all.mkString(", ")}")
      sys.exit(1)
    }

    val (mainJar, dependencyJars) = options.finalClassPath match {
      case Nil =>
        System.err.println(s"No class path specified (--cp)")
        sys.exit(1)
      case h :: t => (new File(h), t.map(new File(_)))
    }

    for (f <- mainJar +: dependencyJars) {
      if (!f.exists()) {
        System.err.println(s"$f not found")
        sys.exit(1)
      }
      if (!f.isFile) {
        System.err.println(s"$f is not a file")
        sys.exit(1)
      }
    }

    val conflictChecker = new ConflictChecker

    val projectArtifact = loadArtifact(mainJar)
    val dependencyArtifacts = dependencyJars.map(loadArtifact)
    val bootArtifacts = loadBootstrapArtifacts()

    System.err.println("Analyzing dependencies")
    val conflicts = conflictChecker
      .check(
        projectArtifact,
        (projectArtifact +: dependencyArtifacts).asJava,
        (bootArtifacts ++ (projectArtifact +: dependencyArtifacts)).asJava
      )
      .asScala
      .toVector

    if (conflicts.isEmpty)
      System.err.println("No conflict found")
    else {
      val conflictCounts = conflicts
        .groupBy(_.usedBy)
        .mapValues(_.length)
        .toVector
        .sortBy(_._1.name)

      for ((name, count) <- conflictCounts)
        System.err.println(s"Found $count conflicts in ${name.name}")

      outputConflicts(conflicts, line => System.err.println(line))
      sys.exit(1)
    }
  }
}
