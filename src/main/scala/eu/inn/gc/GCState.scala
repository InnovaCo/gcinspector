package eu.inn.gc

import com.sun.management.GarbageCollectionNotificationInfo
import java.lang.management.GarbageCollectorMXBean

class GCState (val gcBean: GarbageCollectorMXBean, val assumeGCIsPartiallyConcurrent: Boolean, val assumeGCIsOldGen: Boolean) {

  var keys: Array[String] = null
  var lastGcTotalDuration: Long = 0

  def keys(info: GarbageCollectionNotificationInfo): Array[String] = {
    if (keys != null) return keys
    keys = info.getGcInfo.getMemoryUsageBeforeGc.keySet.toArray(new Array[String](0)).sorted
    keys
  }
}
