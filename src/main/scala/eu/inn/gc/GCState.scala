package eu.inn.gc

import com.sun.management.GarbageCollectionNotificationInfo
import java.lang.management.GarbageCollectorMXBean

class GCState(val gcBean: GarbageCollectorMXBean) {

  var keys: Array[String] = null

  @volatile
  var lastGcTotalDuration: Long = 0

  val assumeGCIsPartiallyConcurrent = GCState.assumeGCIsPartiallyConcurrent(gcBean)

  def keys(gcNotification: GarbageCollectionNotificationInfo): Array[String] = {
    if (keys != null) return keys
    keys = gcNotification.getGcInfo.getMemoryUsageBeforeGc.keySet.toArray(new Array[String](0)).sorted
    keys
  }
}

object GCState {

  /*
   * Assume that a GC type is at least partially concurrent and so a side channel method
   * should be used to calculate application stopped time due to the GC.
   *
   * If the GC isn't recognized then assume that is concurrent and we need to do our own calculation
   * via the the side channel.
   */
  def assumeGCIsPartiallyConcurrent(gc: GarbageCollectorMXBean): Boolean = gc.getName match {
    //First two are from the serial collector
    case "Copy" |
         "MarkSweepCompact" |
          //Parallel collector
         "PS MarkSweep" |
         "PS Scavenge" |
         "G1 Young Generation" |
          "ParNew" ⇒ false
    case "ConcurrentMarkSweep" | "G1 Old Generation" ⇒ true

    //Assume possibly concurrent if unsure
    case _ ⇒ true
  }
}