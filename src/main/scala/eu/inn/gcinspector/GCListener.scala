package eu.inn.gcinspector

import java.util.EventListener

trait GCListener extends EventListener {

  def handleNotification(gcNotification: GCNotification)
}