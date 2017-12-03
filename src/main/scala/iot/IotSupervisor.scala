package iot

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by Administrator on 2017/12/2.
  */
object IotSupervisor {
  def props(): Props = Props(new IotSupervisor)

}

class IotSupervisor extends Actor with ActorLogging{
  override def preStart(): Unit = log.info("Iot Application started")
  override def postStop(): Unit = log.info("Iot Application stopped")

  override def receive = Actor.emptyBehavior
}
