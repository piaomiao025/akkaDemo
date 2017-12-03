package iot

/**
  * Created by Administrator on 2017/12/3.
  */
object DeviceManager {
  final case class RequestTrackDevice(groupId: String, deviceId: String)

  case object DeviceRegistered

}
