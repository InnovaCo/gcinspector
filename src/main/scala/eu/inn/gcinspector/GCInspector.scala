/**
  * This code idea taken from the org.apache.cassandra.service.GCInspector class
  * in the Apache Cassandra project.
  *
  * You could see the original source code in
  * https://git1-us-west.apache.org/repos/asf?p=cassandra.git;a=blob;f=src/java/org/apache/cassandra/service/GCInspector.java
  */
package eu.inn.gcinspector

import com.sun.management.{GarbageCollectionNotificationInfo, GcInfo}
import com.typesafe.scalalogging.Logger
import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}
import javax.management.openmbean.CompositeData
import javax.management.{MBeanServer, Notification, NotificationListener, ObjectName}
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

class GCInspector(beanServer: MBeanServer, listener: GCListener) extends NotificationListener with GCInspectorMXBean {

  private val gcStates: Map[String, GCState] = {
    val gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*")
    beanServer.queryNames(gcName, null).map { name ⇒
      val gcBean = ManagementFactory.newPlatformMXBeanProxy(beanServer, name.getCanonicalName, classOf[GarbageCollectorMXBean])
      gcBean.getName → new GCState(gcBean)
    }.toMap
  }

  def handleNotification(notification: Notification, handback: Any): Unit = {
    convertToGcNotification(notification).foreach { gcNotification ⇒
      val gcName = gcNotification.getGcName
      val gcInfo: GcInfo = gcNotification.getGcInfo

      val stwPauseDuration = calculateStwPauseDuration(gcName, gcInfo)

      listener.handleNotification(new GCNotification(
        gcName = gcName,
        gcAction = gcNotification.getGcAction,
        gcCause = gcNotification.getGcCause,
        stwPauseDuration = stwPauseDuration,
        gcInfo = gcInfo
      ))
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
  private def calculateStwPauseDuration(gcName: String, gcInfo: GcInfo): Long = {
    val gcState = gcStates(gcName)
    if (gcState.assumeGCIsPartiallyConcurrent) {
      val previousTotal = gcState.lastGcTotalDuration
      val currentTotal = gcState.gcBean.getCollectionTime
      gcState.lastGcTotalDuration = currentTotal
      val possibleDuration: Long = currentTotal - previousTotal // may be zero for a really fast collection
      Math.min(gcInfo.getDuration, possibleDuration)
    } else {
      gcInfo.getDuration
    }
  }
}


object GCInspector {

  private val MbeanName = "eu.inn.gc:type=GCInspector"
  private val logger = Logger(LoggerFactory.getLogger(classOf[GCInspector]))

  @throws[Exception]
  def register(listener: GCListener) {
    val server = ManagementFactory.getPlatformMBeanServer
    val inspector = new GCInspector(server, listener)
    server.registerMBean(inspector, new ObjectName(GCInspector.MbeanName))
    val gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*")
    server.queryNames(gcName, null).foreach { name ⇒
      logger.info("Add a notification listener for the {} garbage collector", name.getKeyProperty("name"))
      server.addNotificationListener(name, inspector, null, null)
    }
  }
}