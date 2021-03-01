package com.cotyinc.londa.laserchat

import java.util.Date

case class CPMSMessage(laser: Option[Laser],
                       command: String,
                       label: Option[Label])

case class ResponseMessage(var laser: String,
                           var status: Int,
                           var lastCommand: String,
                           var statusmsg: String,
                           var target_label: Option[String],
                           var target_layout: Option[String],
                           var target_text: Option[Vector[TextPair]],
                           var actual_label: Option[String],
                           var actual_layout: Option[String],
                           var actual_text: Option[Vector[TextPair]],
                           var layout: Option[String] = None
                          )


case class Laser(id: Option[Long] = None,
                 ipaddress: String,
                 port: Int = 20000,
                 name: String,
                 status: Int = 0,
                 line: String,
                 transfers: Long,
                 creation_date: Option[Date],
                 last_update: Option[Date],
                 last_transfer: Option[Date],
                 laser_type: Int = 0)

case class Label(id: Option[Long] = None,
                 article_id: String,
                 description: Option[String],
                 layout: Layout,
                 status: Boolean,
                 lines: List[String],
                 creation_date: Option[Date],
                 release_date: Option[Date],
                 last_update: Option[Date],
                 last_printed: Option[Date])

case class Layout(id: Option[Long] = None,
                  name: String,
                  xml: Option[String] = None,
                  description: Option[String] = None,
                  status: Boolean,
                  creation_date: Option[Date],
                  release_date: Option[Date],
                  last_update: Option[Date])

case class LayoutId(id: Option[Long] = None)

case class Text(id: Option[Long],
                name: String,
                layout: Option[Long])

case class TextValue(label: Long,
                    text: Text,
                    font: Option[Long],
                    parameterset: Option[Long],
                    value: Option[String])

case class TextPair(key: String, value: Option[String])

case class ErrorMessage(error_text: String)

case class LabelInfo(label: Long,
                     layout: String,
                     status: String)