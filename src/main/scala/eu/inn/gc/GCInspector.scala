/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *//*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.inn.gc

import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.GcInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.management.MBeanServer
import javax.management.Notification
import javax.management.NotificationListener
import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage
import java.util.HashMap
import java.util.Map
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

class GCInspector() extends NotificationListener with GCInspectorMXBean {

  val mbs: MBeanServer = ManagementFactory.getPlatformMBeanServer
  try {
    val gcName: ObjectName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*")
    import scala.collection.JavaConversions._
    for (name <- mbs.queryNames(gcName, null)) {
      val gc: GarbageCollectorMXBean = ManagementFactory.newPlatformMXBeanProxy(mbs, name.getCanonicalName, classOf[GarbageCollectorMXBean])
      gcStates.put(gc.getName, new GCState(gc))
    }
    mbs.registerMBean(this, new ObjectName(GCInspector.MBEAN_NAME))
  }
  catch {
    case e: Exception ⇒ {
      throw new RuntimeException(e)
    }
  }

  final private[gc] val state: AtomicReference[State] = new AtomicReference[State](new State)
  final private[gc] val gcStates: Map[String, GCState] = new HashMap[String, GCState]

  def handleNotification(notification: Notification, handback: Any) {
    val `type`: String = notification.getType
    if (`type` == GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION) {
      // retrieve the garbage collection notification information
      val cd: CompositeData = notification.getUserData.asInstanceOf[CompositeData]
      val info: GarbageCollectionNotificationInfo = GarbageCollectionNotificationInfo.from(cd)
      val gcName: String = info.getGcName
      val gcInfo: GcInfo = info.getGcInfo
      var duration: Long = gcInfo.getDuration
      /*
                   * The duration supplied in the notification info includes more than just
                   * application stopped time for concurrent GCs. Try and do a better job coming up with a good stopped time
                   * value by asking for and tracking cumulative time spent blocked in GC.
                   */ val gcState: GCState = gcStates.get(gcName)
      if (gcState.assumeGCIsPartiallyConcurrent) {
        val previousTotal: Long = gcState.lastGcTotalDuration
        val total: Long = gcState.gcBean.getCollectionTime
        gcState.lastGcTotalDuration_$eq(total)
        val possibleDuration: Long = total - previousTotal // may be zero for a really fast collection
        duration = Math.min(duration, possibleDuration)
      }
      var bytes: Long = 0
      val beforeMemoryUsage: Map[String, MemoryUsage] = gcInfo.getMemoryUsageBeforeGc
      val afterMemoryUsage: Map[String, MemoryUsage] = gcInfo.getMemoryUsageAfterGc
      for (key <- gcState.keys(info)) {
        val before: MemoryUsage = beforeMemoryUsage.get(key)
        val after: MemoryUsage = afterMemoryUsage.get(key)
        if (after != null && after.getUsed != before.getUsed) {
          bytes += before.getUsed - after.getUsed
        }
      }

      updateState(duration, bytes)

      if (duration > 0) {
        GCInspector.logger.info("GC info: GcAction {}, GcCause {}, GcName {}", info.getGcAction, info.getGcCause, info.getGcName)
        GCInspector.logger.info("Blocked for {} s, Total {}", duration / 1000d, gcInfo.getDuration / 1000d)
        if (duration < gcInfo.getDuration) {
          GCInspector.logger.info("Smaller for {} ms", gcInfo.getDuration - duration)
        }
      }
    }
  }

  @tailrec
  private def updateState(extraElapsed: Double, extraBytes: Double): Unit = {
    val prev = state.get
    if (!state.compareAndSet(prev, new State(extraElapsed, extraBytes, prev))) {
      updateState(extraElapsed, extraBytes)
    }
  }
}


object GCInspector {

  val MBEAN_NAME: String = "eu.inn.gc:type=GCInspector"
  val logger: Logger = LoggerFactory.getLogger(classOf[GCInspector])

  @throws[Exception]
  def register() {
    val inspector: GCInspector = new GCInspector
    val server: MBeanServer = ManagementFactory.getPlatformMBeanServer
    val gcName: ObjectName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*")
    import scala.collection.JavaConversions._
    for (name <- server.queryNames(gcName, null)) {
      server.addNotificationListener(name, inspector, null, null)
    }
  }
}