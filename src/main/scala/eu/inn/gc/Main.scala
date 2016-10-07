package eu.inn.gc

import java.util

object Main extends App {

  private val leak = new util.ArrayList[Object]()
  @volatile
  private var sink: Object = _

  while(true) {
    try {
      leak.add(new Array[Byte](1024 * 1024))
      sink = new Array[Byte](1024 * 1024)
    } catch {
      case _: OutOfMemoryError ⇒
        leak.clear()
    }
  }
}
