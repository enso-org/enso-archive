import PolyglotHelpers.Module
import org.enso.polyglot.Module
import org.graalvm.polyglot.{Context, Source, Value}

object Constants {
  final val LANGUAGE_ID: String = "enso"
}

object PolyglotHelpers {

}

object Main extends App {
  private def runMain(mainModule: Module): Value = {
    val mainCons = mainModule.getAssociatedConstructor
    val mainFun  = mainModule.getMethod(mainCons, "main")
    mainFun.execute(mainCons.newInstance())
  }
  val context = Context
    .newBuilder("enso")
    .in(System.in)
    .out(System.out)
    .build()

  val mainModule = context.eval("enso", "main = IO.println \"foo\"")
  runMain(new Module(mainModule))
}
