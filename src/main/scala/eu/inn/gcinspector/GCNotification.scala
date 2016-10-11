package eu.inn.gcinspector

import com.sun.management.GcInfo
import java.lang.management.MemoryUsage
import scala.collection.JavaConversions._

class GCNotification(val gcName: String, val gcAction: String, val gcCause: String, val stwPauseDuration: Long, val gcInfo: GcInfo) {

  val id = gcInfo.getId

  val duration = gcInfo.getDuration

  lazy val totalBytesCollected = {
    val beforeMemoryUsage = gcInfo.getMemoryUsageBeforeGc
    val afterMemoryUsage = gcInfo.getMemoryUsageAfterGc

    val poolNames = beforeMemoryUsage.keySet().toSet

    poolNames.foldLeft(0L) { (bytes, poolName) ⇒
      val before: MemoryUsage = beforeMemoryUsage.get(poolName)
      val after: MemoryUsage = afterMemoryUsage.get(poolName)
      if (after != null && after.getUsed != before.getUsed) {
        bytes + (before.getUsed - after.getUsed)
      } else {
        bytes
      }
    }
  }

  override def toString() = s"GC $gcName #$id. Action $gcAction. Cause: $gcCause. Total duration: $duration ms. STW time: $stwPauseDuration ms. $totalBytesCollected bytes collected"
}
