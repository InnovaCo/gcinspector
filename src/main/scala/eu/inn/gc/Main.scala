package eu.inn.gc

import java.lang.Long
import scala.collection.JavaConversions._
import java.lang.management.ManagementFactory
import java.util
import java.util.concurrent.{Executors, ScheduledThreadPoolExecutor, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.util.Random

object Main extends App {

  private val leak = new util.ArrayList[Object]()
  @volatile
  private var sink: Object = _

  GCInspector.register(new GCListener {
    override def handleNotification(gcNotification: GCNotification): Unit = {
      println(gcNotification)
    }
  })

  while(true) {
    try {
      leak.add(new Array[Byte](1024 * 1024))
      sink = new Array[Byte](1024 * 1024)
      if (Random.nextInt(10) == 5) {
        Thread.sleep(1000)
      }
    } catch {
      case _: OutOfMemoryError ⇒
        leak.clear()
    }
  }
}
