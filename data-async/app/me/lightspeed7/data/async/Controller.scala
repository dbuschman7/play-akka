package me.lightspeed7.data.async

import play.api.mvc.Action
import play.api.http.MimeTypes
import akka.actor.ActorRef
import play.api.mvc.Controller
import play.libs.Akka
import akka.actor.Props
import play.api.libs.json.Json
import play.mvc.Results
import akka.routing.RoundRobinPool

object AysncController extends Controller {

  import me.lightspeed7.data.async.Protocol._;

  implicit val batchFileFormat = Json.format[BatchFile]
  implicit val batchFormat = Json.format[Batch]

  val processors = Akka.system.actorOf(Props(classOf[V14Processor], "v14Processor").withRouter(new RoundRobinPool(3)))

  def async = Action { implicit request =>

    val batchFile = create()

    processors ! ProcessBatchFile(batchFile)

    val accept: String = request.headers.get("Accept").getOrElse(MimeTypes.JSON) match {
      case "*/*" => MimeTypes.JSON
      case s: String => s
    }

    // send the response
    println("Response returning")
    Ok(Json.toJson(batchFile).toString())
  }

  def sync = Action { implicit request =>

    val start = System.currentTimeMillis();

    val batchFile = create()

    val seeds: List[Int] = Processor.loadFile(batchFile);
    seeds.map(seed => {
      val toConvertMsg = Processor.createStatementMessage(batchFile, seed)

      val statementToSave = Processor.convertToStatement(toConvertMsg)

      Processor.saveStatement(statementToSave.statement, statementToSave.batchFile)
    })

    val sendAckEmail = Processor.markBatchComplete(batchFile, seeds.size)

    Processor.sendAckEmail(sendAckEmail.batch);

    val duration = (System.currentTimeMillis() - start) / 1000.0;
    println(s"Sync - ${duration} seconds")

    // send the response
    println("Response returning")
    Ok(Json.toJson(sendAckEmail.batch).toString())
  }

  def create(): BatchFile = {
    val batchFile = new BatchFile(123)
    batchFile
  }

}