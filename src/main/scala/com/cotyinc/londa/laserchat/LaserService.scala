package com.cotyinc.londa.laserchat

import java.io.File
import java.net.URLEncoder

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.ActorMaterializer
import play.api.libs.json.{JsResultException, Json, Reads}
import spray.json._

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

  implicit val textFormat     : RootJsonFormat[Text]            = jsonFormat3(Text)
  implicit val textwertFormat : RootJsonFormat[TextValue]       = jsonFormat5(TextValue)
  implicit val textpairFormat : RootJsonFormat[TextPair]        = jsonFormat2(TextPair)
  implicit val responseFormat : RootJsonFormat[ResponseMessage] = jsonFormat11(ResponseMessage)

  implicit val laserReads     : Reads[Laser]      = Json.reads[Laser]
  implicit val layoutReads    : Reads[Layout]     = Json.reads[Layout]
  implicit val labelReads     : Reads[Label]      = Json.reads[Label]

  def getLaser(ip: String): Option[Laser] = {
    try {
      Option(
        Json.parse(
          scala.io.Source.fromURL("http://derolxapp01.wella.team:9000/json/laser/ip/" + ip).mkString
        ).as[Laser]
      )
    }
  }

  def getLabel(label: String): Option[Label] = {
    try {
      Option(
        Json.parse(
          scala.io.Source.fromURL("http://derolxapp01.wella.team:9000/json/label/id/" + URLEncoder.encode(label, "UTF-8").replace("+", "%20") ).mkString
        ).as[Label]
      )
    }
  }

  def getLayout(layout_id: BigDecimal): Option[Layout] = {
    try {
      Option(
        Json.parse(
          scala.io.Source.fromURL("http://derolxapp01.wella.team:9000/json/layout/" + layout_id ).mkString
        ).as[Layout]
      )
    }
  }

  implicit object CPMSJsonFormat extends RootJsonFormat[CPMSMessage] {

    override def write(c: CPMSMessage) = JsObject(
      "laser" -> JsString(c.laser.get.ipaddress),
      "port" -> JsNumber(c.laser.get.port),
      "command" -> JsString(c.command),
      "label" -> JsString(c.label.get.article_id))

    override def read(value: JsValue) : CPMSMessage = value.asJsObject.getFields("laser", "port", "command", "label", "layout") match {
      // regular, preferred method, comes from CPMS
      case Seq(JsString(laser), JsNumber(port), JsString(command), JsNumber(label)) =>
        new CPMSMessage(getLaser(laser), command, getLabel(label.toString()))
      // minimal parameters for getstatus, getxml
      case Seq(JsString(laser), JsNumber(port), JsString(command)) =>
        new CPMSMessage(getLaser(laser), command, None)
      // comes from dominolaser/management/label
      case Seq(JsString(laser), JsNumber(port), JsString(command), JsString(label)) =>
        new CPMSMessage(getLaser(laser), command, getLabel(label.toString()))
      // comes from dominolaser/management/layout
      case Seq(JsString(laser), JsNumber(port), JsString(command), JsNull, JsNumber(layout)) =>
        new CPMSMessage(getLaser(laser), command, Some(new Label(None,"",None, getLayout(layout).get, true, List(""), None, None, None, None)))
      // other cases not handled
      case _ => throw DeserializationException("CPMSMessage could not be parsed: " + value)
    }
  }

}


object LaserService extends App {

  //val config = ConfigFactory.parseFile(new File("src/main/resources/application.conf")) commented 27/03
  //implicit val system : ActorSystem = ActorSystem("LaserChat", config) commented 27/03
  implicit val system : ActorSystem = ActorSystem("LaserChat")
  implicit val materializer : ActorMaterializer = ActorMaterializer()
  implicit val executionContext : ExecutionContextExecutor = system.dispatcher

//  val manager = IO(Tcp)

//  val server: ActorRef = system.actorOf(Props[Server], name = "server")

  val service = new MyJsonService(system)

  val bindingFuture = Http().bindAndHandle(service.route, "0.0.0.0", 8080)

  println(s"Server online at http://0.0.0.0:8080/")
  //  println(s"Server online at http://0.0.0.0:8080/\nPress RETURN to stop...")
  //  StdIn.readLine() // let it run until user presses return
  //  bindingFuture
  //    .flatMap(_.unbind()) // trigger unbinding from the port
   //   .onComplete(_ => system.terminate()) // and shutdown when done

}
