package iot

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by Administrator on 2017/12/2.
  */
object Device {
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  final case class ReadTemperature(requestId: Long)
  final case class RespondTemperature(requestId: Long, value: Option[Double])

  final case class RecordTemperature(requestId: Long, value: Double)
  final case class TemperatureRecorded(requestId: Long)
}
class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._

  var lastTemperature: Option[Double] = None

  override def preStart(): Unit = log.info("device actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit = log.info("device actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = {
    case DeviceManager.RequestTrackDevice(`groupId`, `deviceId`) =>
      sender() ! DeviceManager.DeviceRegistered

    case DeviceManager.RequestTrackDevice(groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevice request for {}-{}. This actor is responsible for {}-{}",
        groupId, deviceId, this.groupId, this.deviceId
      )

    case ReadTemperature(id) =>
      sender() ! RespondTemperature(id, lastTemperature)
    case RecordTemperature(id, value) =>
      lastTemperature = Some(value)
      sender() ! TemperatureRecorded(id)
  }
}
