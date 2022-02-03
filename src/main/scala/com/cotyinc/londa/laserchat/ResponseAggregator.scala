package com.cotyinc.londa.laserchat

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString

import scala.collection.mutable.ListBuffer

import scalaj.http._

case class LaserResult( message : ByteString )

class ResponseAggregator( request: CPMSMessage) extends Actor with ActorLogging {

  var ist_texte = new ListBuffer[TextPair]()
  var soll_texte = new ListBuffer[TextPair]()

  val response = ResponseMessage(request.laser.get.ipaddress, 0, request.command, "", None, None, None, None, None, None, None)

  def protocol(article: String, laser : String): Unit = {
    val result = Http("http://derolxapp01.wella.team:9000/json/protocol").postForm(Seq("article" -> article, "laser" -> laser))
      .header("Content-Type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.readTimeout(5000)).asString
  }

  def extract_content(text_raw: String) : Option[String] = {
    "\"([^\"]+)\"$".r.findFirstIn(text_raw) match {
      case Some(value) => Some(value.replace("\"",""))
      case _ => Some("")
    }
  }

  //RESULT GETCURRENTPROJECT "memory:Maxi_4N+3I.dprj"
  // "([A-Za-z0-9_+ äöüß]+)".r.findAllIn(extract_content("RESULT GETCURRENTPROJECT \"memory:Maxi_4N+3I.dprj\"").get).mkString(",")
  //RESULT GETCURRENTPROJECT "store:MsgStore1/Demo"
  // "([A-Za-z0-9_+ äöüß]+)".r.findAllIn(extract_content("RESULT GETCURRENTPROJECT \"store:MsgStore1/Demo\"").get).mkString(",")
  //RESULT GETCURRENTPROJECT "C:\dyn2\Masterlayouts\CT Rel08 4N_3I.dprj"
  // "([A-Za-z0-9_+ äöüß]+)".r.findAllIn(extract_content("RESULT GETCURRENTPROJECT \"C:\\dyn2\\Masterlayouts\\CT Rel08 4N_3I.dprj\"").get).mkString(",")
  def extract_project(text_raw: String) : Option[String] = {
    extract_content(text_raw) match {
      case Some(value) => Some("([A-Za-z0-9_\\-+ äöüß]+)".r.findAllIn(value).filterNot(s =>
        s.equals("memory") || s.equals("store") || s.equals("dprj") || s.equals("MsgStore1")
          || s.equals("C") || s.equals("dyn2") || s.equals("Masterlayouts")
      ).next)
      case _ => None
    }
  }

  def get_actual_project: Option[String] = {
    response.actual_layout
  }

  def identicalLayout: Boolean = {
    log.info("in identicalLayout: actual_layout = '" + response.actual_layout + "', target_layout = '" + response.target_layout + "'")
    response.actual_layout == response.target_layout
  }

  def identicalTexts: Boolean = {
    log.info("in identicalTexts: actual_texts = '" + response.actual_text + "', target_texts = '" + response.target_text + "'")
    response.actual_text.toArray.deep == response.target_text.toArray.deep
  }

  def isQuickstep(ip: String): Boolean = ip.startsWith("172.28.136") || ip.startsWith("172.28.138") || ip.startsWith("172.28.140") || ip.startsWith("127.0.0.1")

  def compare: Unit = {
    response.status = if (identicalLayout && identicalTexts)  2 else 1
    if (response.status == 2 & response.lastCommand == "load" & !isQuickstep(response.laser)) {
      log.info("in compare: protocol transfer of label " + response.target_label.get.toString + " to laser " + response.laser)
      protocol(response.target_label.get.toString, response.laser)
    }
  }

  def receive : Actor.Receive = {

    case error: ErrorMessage =>
      log.warning("in ResponseAggregator received error message : " + error.error_text)
      response.status = -1
      response.statusmsg = error.error_text
      sender ! response


    case "ready" =>
      log.info("in ResponseAggregator.ready")
      val xsender = sender()

      context become {
        case result: List[Any] =>
          log.debug("in ResponseAggregator: result = " + result.toString())
          if (result.nonEmpty) {
            // result.head.utf8String.takeWhile( _ != ' ' )
            log.debug("\"" + result.head.asInstanceOf[ByteString].utf8String.split(" ")(0) + "\"")
            // trim should remove trailing CRLF, which sometimes occur
            result.head.asInstanceOf[ByteString].utf8String.split(" ")(0).trim() match {
              case "getstatus" => response.statusmsg = result(1).asInstanceOf[ByteString].utf8String
              case "getcurrentproject" => response.actual_layout = extract_project(result(1).asInstanceOf[ByteString].utf8String)
              case "gettext" => ist_texte.append(TextPair(extract_content(result.head.asInstanceOf[ByteString].utf8String).getOrElse("unknown"),
                extract_content(result(1).asInstanceOf[ByteString].utf8String) ))
                // this should fill target_text within Listener.settext indirectly by response from sending "settext ..."
              case "settext" => // soll_texte.append(new TextPair(result.head.utf8String, Some(result(1).utf8String) ))
              case "getxml" => response.layout = Some(result(1).asInstanceOf[ByteString].utf8String)
              case "setxml" => log.debug("received useless setxml")
              case "loadproject" => log.debug("received useless loadproject")
			  case "mark" => response.statusmsg = result(1).asInstanceOf[ByteString].utf8String
            }
          }

        // this is called by Listener.gettext (in compare) and should fill target texts of a label
        case texte: Vector[Any] =>
          log.debug("in ResponseAggregator set target texte")
          texte.foreach{ case(tw) => log.debug(tw.asInstanceOf[TextValue].text.name) }
          soll_texte.appendAll( texte.map{ case(tw) => TextPair(tw.asInstanceOf[TextValue].text.name, tw.asInstanceOf[TextValue].value) } )

        case label: Label =>
          log.debug("in ResponseAggregator set target label and layout")
          response.target_label = Some(label.article_id)
          response.target_layout = Some(label.layout.name)

        case "finish" =>
          log.info("in ResponseAggregator.finish")
          response.target_text = Some(soll_texte.toVector)
          response.actual_text = Some(ist_texte.toVector)
          compare
          xsender ! response
          soll_texte.clear()
          ist_texte.clear()

        case "get_layout_name" =>
          log.info("in ResponseAggregator.get_layout_name for " + response.actual_layout)
          sender ! response.actual_layout

      }

  }

}

