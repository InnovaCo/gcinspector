package eu.inn.gc

import com.sun.management.GcInfo
import java.lang.management.MemoryUsage
import scala.collection.JavaConversions._

case class GCNotification(gcName: String, stwPauseDuration: Long, gcInfo: GcInfo) {

  val duration = gcInfo.getDuration

  lazy val totalBytesCollected = {
    val beforeMemoryUsage = gcInfo.getMemoryUsageBeforeGc
    val afterMemoryUsage = gcInfo.getMemoryUsageAfterGc

    gcPoolNames(gcInfo).foldLeft(0L) { (bytes, poolName) ⇒
      val before: MemoryUsage = beforeMemoryUsage.get(poolName)
      val after: MemoryUsage = afterMemoryUsage.get(poolName)
      if (after != null && after.getUsed != before.getUsed) {
        bytes + (before.getUsed - after.getUsed)
      } else {
        bytes
      }
    }
  }

  private def gcPoolNames(gcInfo: GcInfo): Set[String] = {
    gcInfo.getMemoryUsageBeforeGc.keySet().toSet
  }
}
