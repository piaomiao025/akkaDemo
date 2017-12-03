package iot

import akka.actor.ActorSystem

import scala.io.StdIn

/**
  * Created by Administrator on 2017/12/2.
  */
object IotApp {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("iot-system")

    try{
      val supervisor = system.actorOf(IotSupervisor.props(), "iot-supervisor")

      StdIn.readLine()
    }finally {
      system.terminate()
    }
  }
}
