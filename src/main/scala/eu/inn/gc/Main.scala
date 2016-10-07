package eu.inn.gc

import scala.collection.JavaConversions._
import java.lang.management.ManagementFactory
import java.util
import java.util.concurrent.{Executors, ScheduledThreadPoolExecutor, TimeUnit}
import scala.concurrent.ExecutionContext

object Main extends App {

  private val leak = new util.ArrayList[Object]()
  @volatile
  private var sink: Object = _

  val scheduler = Executors.newScheduledThreadPool(1)
  scheduler.scheduleAtFixedRate(
    new Runnable {
      override def run(): Unit = {
        ManagementFactory.getGarbageCollectorMXBeans foreach { gc ⇒
          //    stats.newGauge(s"gc.${gc.getName.replaceAll("[^\\w]+", "-")}.count")(gc.getCollectionCount)
          //    stats.newGauge(s"gc.${gc.getName.replaceAll("[^\\w]+", "-")}.time")(gc.getCollectionTime)
          println(s"gc.${gc.getName.replaceAll("[^\\w]+", "-")}.count", gc.getCollectionCount)
          println(s"gc.${gc.getName.replaceAll("[^\\w]+", "-")}.time", gc.getCollectionTime)
        }
      }
    },
    0,
    1,
    TimeUnit.SECONDS
  )

  GCInspector.register()

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
