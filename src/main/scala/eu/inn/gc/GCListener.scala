package eu.inn.gc

import java.util.EventListener

trait GCListener extends EventListener {

  def handleNotification(gcNotification: GCNotification)
}