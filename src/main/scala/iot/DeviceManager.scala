package iot

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

/**
  * Created by Administrator on 2017/12/3.
  */
object DeviceManager {
  def props(): Props = Props(new DeviceManager)

  final case class RequestTrackDevice(groupId: String, deviceId: String)

  case object DeviceRegistered

  case class QueryDeviceGroups(requestId: Int)
  case class RespondDeviceGroupList(requestId: Int, ids: Set[String])
}

class DeviceManager extends Actor with ActorLogging {
  var groupIdToActor = Map.empty[String, ActorRef]
  var actorToGroupId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("DeviceManager started")
  override def postStop(): Unit = log.info("DeviceManager stopped")

  import DeviceManager._
  override def receive: Receive = {
    case trackMsg @ RequestTrackDevice(groupId, _) =>
      groupIdToActor.get(groupId) match {
        case Some(ref) =>
          ref forward trackMsg
        case None =>
          log.info("creating device group actor for {}", groupId)
          val group = context.actorOf(DeviceGroup.props(groupId))
          groupIdToActor += groupId -> group
          actorToGroupId += group -> groupId
          group forward trackMsg
      }
    case Terminated(groupActor) =>
      val groupId = actorToGroupId(groupActor)
      actorToGroupId -= groupActor
      groupIdToActor -= groupId
      log.info("Device Group actor for {} has been terminated", groupId)
    case QueryDeviceGroups(requestId) =>
      sender() ! RespondDeviceGroupList(requestId, groupIdToActor.keySet)
  }
}