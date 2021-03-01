package com.cotyinc.londa.laserchat

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.ByteString
import java.net.InetSocketAddress

import akka.io.{IO, Tcp}


import scala.collection.mutable.ListBuffer

class Client(laser: Laser, listener: ActorRef) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  // sign death pact: this actor terminates when connection breaks
  // context watch listener

  val destination = new InetSocketAddress(laser.ipaddress, laser.port)

  IO(Tcp) ! Connect(destination, pullMode = true)  // pullMode

  //override def preStart: Unit = sender ! ResumeReading

  def receive = {
    case CommandFailed(_: Connect) =>
      log.error("connect failed")
      listener ! "connect failed"
      context stop self

    case c @ Connected(remote, local) =>
      log.debug("in Client.receive.c")
      //log.debug(c.toString)
      //log.debug(listener.toString())
      //log.debug(sender.toString())
      val connection = sender()
      connection ! Register(self)
      listener ! "connected"

      var message_lines = new ListBuffer[ByteString]()

      context become {

        case data: ByteString =>
          log.debug("writing {}", data.utf8String)
          connection ! ResumeReading
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          log.error("write failed")
          listener ! "write failed"
        case Received(data) =>
          log.debug("received from laser: {}",data.utf8String)
          if (data.endsWith(ByteString("OK\r\n")) ||
              data.containsSlice(ByteString("ERROR")) ||
              data.containsSlice(ByteString("RESULT"))
          ) {
            data.utf8String.split("\r\n").foreach(s => message_lines += ByteString(s))
            listener ! message_lines.toList
            message_lines.clear()
          } else {
            log.debug("wait for next receive...")
            message_lines += data
            connection ! ResumeReading
          }
        case "close" =>
          log.info("to close")
          connection ! Close
          //context stop self
        case _: ConnectionClosed =>
          log.info("connection closed")
          //context stop self
      }

    case Received(data) =>
      log.info("in Client.receive.Received")
      log.debug(data.toString)


  }
}
