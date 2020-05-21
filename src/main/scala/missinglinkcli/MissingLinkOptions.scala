package missinglinkcli

import java.io.File

import caseapp.ExtraName
import java.util.regex.Pattern

final case class MissingLinkOptions(
  @ExtraName("cp")
    classPath: List[String] = Nil,
  classPathSeparator: String = File.pathSeparator
) {
  def finalClassPath: List[String] = {
    val sepOpt = Some(classPathSeparator).filter(_.nonEmpty)
    sepOpt match {
      case None => classPath
      case Some(sep) =>
        val pattern = Pattern.compile(sep, Pattern.LITERAL)
        classPath.flatMap(pattern.split(_).toSeq)
    }
  }
}
