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

import com.sun.management.{GarbageCollectionNotificationInfo, GcInfo}
import java.lang.management.{GarbageCollectorMXBean, ManagementFactory, MemoryUsage}
import javax.management.openmbean.CompositeData
import javax.management.{MBeanServer, Notification, NotificationListener, ObjectName}
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

class GCInspector(beanServer: MBeanServer) extends NotificationListener with GCInspectorMXBean {

  private val gcStates: Map[String, GCState] = {
    val gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*")
    beanServer.queryNames(gcName, null).map { name ⇒
      val gc = ManagementFactory.newPlatformMXBeanProxy(beanServer, name.getCanonicalName, classOf[GarbageCollectorMXBean])
      gc.getName → new GCState(gc)
    }.toMap
  }

  def handleNotification(notification: Notification, handback: Any): Unit = {
    convertToGcNotification(notification).foreach { gcNotification ⇒
      val gcName = gcNotification.getGcName
      val gcInfo: GcInfo = gcNotification.getGcInfo

      val gcState = gcStates(gcName)

      val duration = calculateDuration(gcInfo, gcState)

      val bytes = calculateBytes(gcInfo, gcState)

      if (duration > 0) {
        GCInspector.logger.info("GC gcNotification: GcAction {}, GcCause {}, GcName {}", gcNotification.getGcAction, gcNotification.getGcCause, gcNotification.getGcName)
        GCInspector.logger.info("Blocked for {} s, Total {}", duration / 1000d, gcInfo.getDuration / 1000d)
        if (duration < gcInfo.getDuration) {
          GCInspector.logger.info("Smaller for {} ms", gcInfo.getDuration - duration)
        }
      }
    }
  }

  private def convertToGcNotification(notification: Notification): Option[GarbageCollectionNotificationInfo] = notification.getType match {
    case GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION ⇒
      val cd = notification.getUserData.asInstanceOf[CompositeData]
      Some(GarbageCollectionNotificationInfo.from(cd))
    case _ ⇒ None
  }

  /*
   * The duration supplied in the notification gcNotification includes more than just
   * application stopped time for concurrent GCs. Try and do a better job coming up with a good stopped time
   * value by asking for and tracking cumulative time spent blocked in GC.
   */
  private def calculateDuration(gcInfo: GcInfo, gcState: GCState): Long = {
    if (gcState.assumeGCIsPartiallyConcurrent) {
      val previousTotal = gcState.lastGcTotalDuration
      val total = gcState.gcBean.getCollectionTime
      gcState.lastGcTotalDuration = total
      val possibleDuration: Long = total - previousTotal // may be zero for a really fast collection
      Math.min(gcInfo.getDuration, possibleDuration)
    } else {
      gcInfo.getDuration
    }
  }

  private def calculateBytes(gcInfo: GcInfo, gcState: GCState): Long = {
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


object GCInspector {

  private val MbeanName = "eu.inn.gc:type=GCInspector"
  val logger = LoggerFactory.getLogger(classOf[GCInspector])

  @throws[Exception]
  def register() {
    val server = ManagementFactory.getPlatformMBeanServer
    val inspector = new GCInspector(server)
    server.registerMBean(inspector, new ObjectName(GCInspector.MbeanName))
    val gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*")
    server.queryNames(gcName, null).foreach { name ⇒
      server.addNotificationListener(name, inspector, null, null)
    }
  }
}