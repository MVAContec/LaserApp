# Laser chat interface

## Message classes

```
case class CPMSMessage(laser: String,
                         port: Int,
                         command: String,
                         label: Option[String],
                         layout: Option[String] = None)  

case class ResponseMessage(laser: String,
                           status: Int,
                           lastCommand: String,
                           statusmsg: String,
                           target_label: Option[String],
                           target_layout: Option[String],
                           target_text: Option[Seq[Text]],
                           actual_label: Option[String],
                           actual_layout: Option[String],
                           actual_text: Option[Seq[Text]]
                          )

case class Text(name: String,
                value: Option[String])

case class LabelInfo(label: Long,
                      layout: String,
                      status: String)
```
                     
- Option[] : means that this value can be present or not in JSON request or response
- Seq[]: is a kind of Collection that results in output like '{"actual_text" : [{"name":"Nuacennummer","value":"66/03"}, {"name":"Enthält 1","value":"LR ESC IN NT DR"}]}'

## Requests

How to interact with the interface? POST a json message (see case class CPMSMessage) like:


- transfer a label to the laser (Übertragung) [POST]

```
curl -H "Content-Type: application/json" -d  '{"laser":"172.28.131.168", "port":20000, "command":"load","label":92130674}' http://derolxapp01.wella.team:8080/laserchat
```

- get status message of the laser [POST]

```
curl -H "Content-Type: application/json" -d  '{"laser":"172.28.131.168", "port":20000, "command":"getstatus","label":"92130674"}' http://derolxapp01.wella.team:8080/laserchat
```

- get actual project and text fields from laser and compare it with target values from label from database [POST]

```
curl -H "Content-Type: application/json" -d  '{"laser":"172.28.131.168", "port":20000, "command":"compare","label":92130674}' http://derolxapp01.wella.team:8080/laserchat
```

Send a GET request to retrieve information about a label:

- get label info (exists?, approved or not approved?, layout) [GET]

```
curl http://derolxapp01.wella.team:8080/labelinfo/95844118
```

## Responses 

are JSON as described in case class ResponseMessage for POST requests, e.g.:
    
- in response of getstatus

```json
{"laser":"172.28.131.168","status":0,"lastCommand":"getstatus","statusmsg":"RESULT GETSTATUS 4 550 \"SYSTEM ABGESCHALTET\""}
```

- error

```json
{"laser":"172.28.131.168","status":-1,"lastCommand":"getstatus","statusmsg":"ERROR 4"}
```

- load and compare

```json
{"laser":"172.28.131.168","status":2,"lastCommand":"load","statusmsg":"in sync","target_label":92130674,"target_layout":"Single_klein","target_text": [{"name":"Nuacennummer", "value":"66/03"},{"name":"Enthält 1", "value":"LR ESC IN NT DR"},{"name":"Enthält 2", "value":"RUB OSC INT ORO NA"},{"name":"Enthält 3", "value":""}],"actual_label":92130674,"actual_layout":"Single_klein","actual_text": [{"name":"Nuacennummer", "value":"66/03"},{"name":"Enthält 1", "value":"LR ESC IN NT DR"},{"name":"Enthält 2", "value":"RUB OSC INT ORO NA"},{"name":"Enthält 3", "value":""}]}
```

"status": 2..."is in sync", 1..."is not in sync", 0..."OK", -1..."ERROR" 

The response in case of a labelinfo GET request is

```json
{"label": 95844118, "layout": "KS 1 TR 07", "status": "approved"}
```
    
If label does not exist the response is a 404 http error.

## Monitoring

There is a simple alive-Resource `/alive` with simple GET:

```
curl http://derolxapp01.wella.team:8080/alive  
```

or

```
GET /alive HTTP/1.1
```

which will respond with HTTP response code 204 (NOCONTENT):

```
HTTP/1.1 204 No Content
```

## Misc

Maybe that the address derolxapp01 and port 8080 will change in production environment.

Maybe that the protocol changes to https.

The url parameters may change a little. 

Maybe that there will be a kind of access control, like basic authentication, client cert, access token.

Maybe that the JSON request or response will change somewhat.
