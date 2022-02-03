package com.cotyinc.londa.laserchat

import java.net.URLEncoder

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp.{ConnectionClosed, PeerClosed, Register}
import akka.util.ByteString

import scala.xml.transform._
import scala.xml.{Elem, Node, NodeSeq}
import play.api.libs.json.{Json, Reads}

import scala.collection.mutable


class Listener(laser: Laser, label: Label = null, queue: mutable.Queue[Any], resp: ActorRef ) extends Actor with ActorLogging {

  implicit val textReads : Reads[Text] = Json.reads[Text]
  implicit val textwertReads : Reads[TextValue] = Json.reads[TextValue]
  implicit val layoutReads : Reads[LayoutId] = Json.reads[LayoutId]

  def getTexts(label: String): String = {
    val url = "http://derolxapp01.wella.team:9000/json/texts/" + label
    scala.io.Source.fromURL(url).mkString
  }

  def getTextsFromLayout(layout_id: Long): String = {
    val url = "http://derolxapp01.wella.team:9000/json/texts/layout/" + layout_id
    scala.io.Source.fromURL(url).mkString
  }

  def getLayout(name: String): String = {
    val url = "http://derolxapp01.wella.team:9000/json/layout/name/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    scala.io.Source.fromURL(url).mkString
  }

  val removeIt : RewriteRule = new RewriteRule {
    override def transform(n: Node): NodeSeq = n match {
      case e: Elem if e.label == "Parameter-Set" => NodeSeq.Empty
      case `n` => n
    }
  }

  def receive : Actor.Receive = {
    case Register(handler, keepopen, resume) =>
      log.info("in Listener.Register")
      log.debug(handler.toString)
      log.debug(queue.toString())

      val client = handler

      context become {
        case "connected" =>
          log.info("Listener: Client is connected")
          self ! queue.dequeue()

        case data: List[Any] =>
          log.info("in Listener.data")
          data.foreach(s => log.debug(s.asInstanceOf[ByteString].utf8String))
          resp ! data
          if (queue.nonEmpty) {
            self ! queue.dequeue()
          } else {
            log.info("queue is empty")
          }

        case "getstatus" =>
          log.info("in Listener.getstatus")
          client ! ByteString("getstatus\r\n")
		  
		case "stop" =>
          log.info("in Listener.stop")
          client ! ByteString("mark stop\r\n")

        case "getcurrentproject" =>
          log.info("in Listener.getcurrentproject")
          client ! ByteString("getcurrentproject\r\n")

        case "setxml" =>
          log.info("in Listener: set xml ")
          laser.laser_type match {
            case 2 => {
              val nodes = xml.XML.loadString(label.layout.xml.get)
              val removedParameterSet = new RuleTransformer(removeIt).transform(nodes)
              client ! ByteString("setxml <?xml version=\"1.0\" encoding=\"UTF-8\"?>" + removedParameterSet + "\r\n")
            }
            case 1 => {
              client ! ByteString("loadproject \"C:\\dyn2\\Masterlayouts\\" + label.layout.name + ".dprj\"\r\n")
            }
          }

        case "settexte" =>
          log.info("in Listener: set textwerte")
          val texte = Json.parse(getTexts(label.article_id)).as[Vector[TextValue]]
          val rest = queue.clone()
          queue.clear()
          texte.foreach { queue.enqueue(_) }
          queue ++= rest.toList
          log.debug("new queue: " + queue.toString())
          self ! queue.dequeue()

        case "gettexte" =>
          log.info("in Listener: get texte")
          resp ! "get_layout_name"

        case layout_name : Option[String] =>
          //val layout = Json.parse(getLayout(layout_name.get)).as[Layout]
          val layout = Json.parse(getLayout(layout_name.get)).as[LayoutId]
          // get texts [Text] by layout from DB to get values from laser
          val layout_texts = Json.parse(getTextsFromLayout(layout.id.get)).as[Vector[Text]]
          // set label and layout in response_message

          val label_texts = Json.parse(getTexts(label.article_id)).as[Vector[TextValue]]
          // set target texte in response_message
          resp ! label_texts

          resp ! label
          val rest = queue.clone()
          queue.clear()
          layout_texts.foreach( text =>
            queue.enqueue(text)
          )
          queue ++= rest.toList
          log.debug("new queue: " + queue.toString())
          self ! queue.dequeue()

        case text: Text =>
          client ! ByteString("gettext \"" + text.name + "\"\r\n")

        case textvalue: TextValue =>
          client ! ByteString("settext \"" + textvalue.text.name + "\" \"" + textvalue.value.getOrElse("") + "\"\r\n")

        case "getxml" =>
          log.info("in Listener.getxml")
          client ! ByteString("getxml\r\n")

        case "close" =>
          log.debug("Listener received close message")
          resp ! "finish"
          client ! "close"

        case PeerClosed =>
          log.info("peer closed")
          context stop self

        case _: ConnectionClosed =>
          log.info("Server connection closed")
          client ! "close"
          context stop self


      }

  }
}
