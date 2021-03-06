package com.cotyinc.londa.laserchat

import akka.actor.{ActorRef, ActorSystem, Props}
//import akka.event.jul.Logger
import org.slf4j.LoggerFactory
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.RouteConcatenation.RouteWithConcatenation
import akka.io.Tcp.Register
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.collection.mutable


class MyJsonService(system: ActorSystem) extends Directives with JsonSupport {

  val logger = LoggerFactory.getLogger("com.cotyinc.londa.laserchat.MyJsonService")

  // this is an implicit connect timeout for Client connections
  implicit val timeout : Timeout = Timeout(10 seconds)

  //implicit val labelinfoWrites : Writes[LabelInfo] = Json.writes[LabelInfo]


  def laser_allowed(laser: Laser): Boolean = {
    (laser.status == 1)
  }

  val route =
    pathSingleSlash {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Please send a POST message with JSON content like {\"laser\":\"172.28.131.168\", \"port\":20000, \"command\":\"load\", \"label\":\"40380012151\"}"))
    } ~
      path("alive") {
        get {
          complete(StatusCodes.NoContent)
        }
      } ~
        pathPrefix("labelinfo") {
          pathSingleSlash {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Please send a label id after /" ))
          } ~
            path(Segment) { label_id =>
              logger.info("got label_id " + label_id)
              getLabel(label_id) match {
                case None =>
                  logger.warn("label not found")
                  complete(StatusCodes.NotFound)

                case Some(label) =>
                  val text: String = label.status match {
                    case false => "not approved"
                    case true => "approved"
                  }
                  complete(
                    HttpEntity(ContentTypes.`application/json`,
                      "{\"label\": " + label_id + ", \"layout\": \"" + label.layout.name + "\", \"status\": \"" + text + "\"}" )
                  )
              }
            }
        } ~
          pathPrefix("laserchat") {
            pathSingleSlash {
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Please send a POST message with JSON content like {\"laser\":\"172.28.131.168\", \"port\":20000, \"command\":\"load\", \"label\":\"40380012151\"}"))
            } ~
              post {
                decodeRequest {
                  entity(as[CPMSMessage]) { message =>
                    complete {
                      logger.info("received " + message)

                      val response: ActorRef = system.actorOf(Props(new ResponseAggregator(message)))

                      message.laser match {

                        case None => Await.result(response ? ErrorMessage("laser could\'nt be parsed from JSON"), 1.seconds).asInstanceOf[ResponseMessage]

                        case Some(laser) =>
                          logger.info("got laser : " + laser)
                          if (laser_allowed(laser)) {

                            message.command match {
                              case "getstatus" =>
                                logger.info("in getstatus")
                                val todo_queue: mutable.Queue[Any] = mutable.Queue("getstatus", "close")
                                val listener: ActorRef = system.actorOf(Props(new Listener(laser, null, todo_queue, response)))
                                listener ! Register(system.actorOf(Props(new Client(laser, listener))))
								
							  case "stop" =>
                                logger.info("in stop")
                                val todo_queue: mutable.Queue[Any] = mutable.Queue("stop", "close")
                                val listener: ActorRef = system.actorOf(Props(new Listener(laser, null, todo_queue, response)))
                                listener ! Register(system.actorOf(Props(new Client(laser, listener))))

                              case "getxml" =>
                                logger.info("in getxml")
                                val todo_queue: mutable.Queue[Any] = mutable.Queue("getstatus", "getxml", "close")
                                val listener: ActorRef = system.actorOf(Props(new Listener(laser, null, todo_queue, response)))
                                listener ! Register(system.actorOf(Props(new Client(laser, listener))))

                              case other =>
                                message.label match {
                                  case None => Await.result(response ? ErrorMessage("no label given"), 1.seconds).asInstanceOf[ResponseMessage]

                                  case Some(label) =>
                                    message.command match {

                                      case "compare" =>
                                        logger.info("in getstatus")
                                        val todo_queue: mutable.Queue[Any] = mutable.Queue("getstatus", "getcurrentproject", "gettexte", "close")
                                        val listener: ActorRef = system.actorOf(Props(new Listener(laser, label, todo_queue, response)))
                                        listener ! Register(system.actorOf(Props(new Client(laser, listener))))

                                      case "load" =>
                                        logger.info("in load layout")
                                        val todo_queue: mutable.Queue[Any] = mutable.Queue("getstatus", "setxml", "settexte", "getcurrentproject", "gettexte", "close")
                                        val listener: ActorRef = system.actorOf(Props(new Listener(laser, label, todo_queue, response)))
                                        listener ! Register(system.actorOf(Props(new Client(laser, listener))))

                                      case "simpleload" =>
                                        logger.info("in simpleload layout")
                                        val todo_queue: mutable.Queue[Any] = mutable.Queue("getstatus", "setxml", "close")
                                        val listener: ActorRef = system.actorOf(Props(new Listener(laser, label, todo_queue, response)))
                                        listener ! Register(system.actorOf(Props(new Client(laser, listener))))

                                      case other =>
                                        logger.warn("unknown command " + other)

                                    }
                                }

                            }
                            Await.result(response ? "ready", 60.seconds).asInstanceOf[ResponseMessage]

                          } else {
                            logger.error("Access to this laser is prohibited")
                            Await.result(response ? ErrorMessage("access to this laser is prohibited"), 1.seconds).asInstanceOf[ResponseMessage]
                          }
                      }
                    }
                  }
                }
              }
          }
}
