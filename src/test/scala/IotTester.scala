import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import iot.{Device, DeviceGroup, DeviceGroupQuery, DeviceManager}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

/**
  * Created by Administrator on 2017/12/2.
  */
class IotTester(_system: ActorSystem)
  extends TestKit(_system) with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  def this() = this(ActorSystem("iotTester"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "iot supervisor" should "reply with empty reading if no temperature is known" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(Device.ReadTemperature(requestId = 42), probe.ref)
    val response = probe.expectMsgType[Device.RespondTemperature]
    response.requestId should === (42)
    response.value should === (None)
  }

  "iot2 supervisor2" should "reply with latest temperature reading" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(Device.RecordTemperature(requestId = 1, 24.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 1))

    deviceActor.tell(Device.ReadTemperature(requestId = 2), probe.ref)
    val response1 = probe.expectMsgType[Device.RespondTemperature]
    response1.requestId should === (2)
    response1.value should === (Some(24.0))

    deviceActor.tell(Device.RecordTemperature(requestId = 3, 55.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 3))

    deviceActor.tell(Device.ReadTemperature(requestId = 4), probe.ref)
    val response2 = probe.expectMsgType[Device.RespondTemperature]
    response2.requestId should === (4)
    response2.value should === (Some(55.0))
  }


  "iot3 supervisor" should "reply to registration requests" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(DeviceManager.RequestTrackDevice("group", "device"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    probe.lastSender should === (deviceActor)
  }

  "iot4 supervisor" should "ignore wrong registration requests" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.ref)
    probe.expectNoMsg(500.milliseconds)

    deviceActor.tell(DeviceManager.RequestTrackDevice("group", "Wrongdevice"), probe.ref)
    probe.expectNoMsg(500.milliseconds)
  }

  "iot5 supervisor" should "be albe to register a device actor" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor1 = probe.lastSender

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor2 = probe.lastSender
    deviceActor1 should !== (deviceActor2)

    // check that the device actors are working
    deviceActor1.tell(Device.RecordTemperature(requestId = 0, 1.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 0))
    deviceActor2.tell(Device.RecordTemperature(requestId = 1, 2.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 1))

  }

  "iot6" should "ignore requests for wrong groupId" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device1"), probe.ref)
    probe.expectNoMsg(500 milliseconds)
  }

  "iot7" should "reply device list " in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    val toShutdown = probe.lastSender
    probe.watch(toShutdown)

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 1), probe.ref)
    probe.expectMsg(DeviceGroup.ReplyDeviceList(1, Set("device1", "device2")))

    toShutdown.tell(PoisonPill, probe.ref)

    probe.expectTerminated(toShutdown)
    probe.awaitAssert{
      groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 2), probe.ref)
      probe.expectMsg(DeviceGroup.ReplyDeviceList(2, Set("device2")))
    }

  }

  "device group test" should "device group manager " in {
    val probe = TestProbe()
    val deviceManager = system.actorOf(DeviceManager.props())

    import DeviceManager._
    deviceManager.tell(RequestTrackDevice("group1", "device1"), probe.ref)
    probe.expectMsg(DeviceRegistered)

    deviceManager.tell(RequestTrackDevice("group1", "device2"), probe.ref)
    probe.expectMsg(DeviceRegistered)

    deviceManager.tell(RequestTrackDevice("group2", "device3"), probe.ref)
    probe.expectMsg(DeviceRegistered)

    deviceManager.tell(RequestTrackDevice("group2", "device4"), probe.ref)
    probe.expectMsg(DeviceRegistered)


    deviceManager.tell(QueryDeviceGroups(requestId = 1), probe.ref)
    probe.expectMsg(RespondDeviceGroupList(1, Set("group1", "group2")))
  }

  "device group query " should "return temperature value for working devices " in {
    val requester = TestProbe()

    val device1 = TestProbe()
    val device2 = TestProbe()

    val queryActor = system.actorOf(DeviceGroupQuery.props(
      actorToDeviceId = Map(device1.ref -> "device1", device2.ref -> "device2"),
      requestId = 1,
      requester = requester.ref,
      timeout = 3.seconds
    ))

    device1.expectMsg(Device.ReadTemperature(requestId = 0))
    device2.expectMsg(Device.ReadTemperature(requestId = 0))

    queryActor.tell(Device.RespondTemperature(requestId = 0, Some(1.0)), device1.ref)
    queryActor.tell(Device.RespondTemperature(requestId = 0, Some(2.0)), device2.ref)

    requester.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 1,
      temperatures = Map(
        "device1" -> DeviceGroup.Temperature(1.0),
        "device2" -> DeviceGroup.Temperature(2.0)
      )
    ))
  }

  "final test" should "be able to collect temperatures from all active devices" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor1 = probe.lastSender

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor2 = probe.lastSender

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device3"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor3 = probe.lastSender

    // Check that the device actors are working
    deviceActor1.tell(Device.RecordTemperature(requestId = 0, 1.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 0))
    deviceActor2.tell(Device.RecordTemperature(requestId = 1, 2.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 1))

    groupActor.tell(DeviceGroup.RequestAllTemperatures(requestId = 0), probe.ref)

    probe.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 0,
      temperatures = Map("device1" -> DeviceGroup.Temperature(1.0),
                         "device2" -> DeviceGroup.Temperature(2.0),
                         "device3" -> DeviceGroup.TemperatureNotAvailable)
    ))
  }
}
