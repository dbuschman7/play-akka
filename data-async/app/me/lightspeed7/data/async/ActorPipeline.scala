package me.lightspeed7.data.async

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.PoisonPill
import scala.concurrent.Await
import akka.util.Timeout
import akka.actor.OneForOneStrategy
import akka.routing.Broadcast
import akka.routing.RoundRobinPool

//
//
// Public State Messages
//
// //////////////////////////////////////////////////////

case class BatchFile(id: Int)
case class Statement()
case class Batch(id: Int)

//
// Akka Message case classes
//
// //////////////////////////////////////////////////////
object Protocol {
  case class ProcessBatchFile(batchFile: BatchFile)
  case class ProcessBatchDone(batchFile: BatchFile, count: Int)
  case class ConvertV14Statement(batchFile: BatchFile)
  case class StatementToSave(statement: Statement, batchFile: BatchFile)
  case class BatchClose(batchFile: BatchFile, statementCount: Int)
  case class ConverterShutDown(batchFile: BatchFile, count: Int)
  case class ConverterShutDownConfirm(batchFile: BatchFile, count: Int)
  case class SaverShutDown(batchFile: BatchFile, count: Int)
  case class SaverShutDownConfirm(batchFile: BatchFile, count: Int)
  case class SendAckEmail(batch: Batch)
  case class Done()
}

//
// Main Processor
// 
// //////////////////////////////////////////////////////
class V14Processor(name: String) extends Actor with ActorLogging {

  import Protocol._;

  def receive = {
    case msg: ProcessBatchFile => {
       val processor: ActorRef = context.actorOf(Props(classOf[V14Orchestrator], "v14Orchestrator"))
       processor ! msg
    }
  }

}
//
// Orchestrator
//
// /////////////////////////////////////////////////////

class V14Orchestrator(name: String) extends Actor with ActorLogging {

  import Protocol._;

  val start = System.currentTimeMillis();

  var converterCount: Int = 3;
  var saverCount: Int = 10;

  val sendAckEmail: ActorRef = context.actorOf(Props(classOf[SendBatchAcknowledgment], "sendAckEmail"))

  val markBatchComplete: ActorRef = context.actorOf(Props(classOf[MarkBatchComplete], "markBatchComplete", sendAckEmail))

  val saveStatement: ActorRef = context.actorOf(Props(classOf[SaveStatementActor], "saveStatement") //
    .withRouter(new RoundRobinPool(saverCount)))

  val convertStatement: ActorRef = context.actorOf(Props(classOf[ConvertStatementActor], "convertStatement", saveStatement) //
    .withRouter(new RoundRobinPool(converterCount)))

  val processBatchFile: ActorRef = context.actorOf(Props(classOf[ProcessBatchFileActor], "processBatchFile", convertStatement))

  def receive = {
    // process the file 
    case msg: ProcessBatchFile => {
      processBatchFile ! msg
    }

    // shutdown the statement converters
    case ProcessBatchDone(batchFile: BatchFile, count: Int) => {
      println("ProcessBatchDone received")
      convertStatement ! Broadcast(ConverterShutDown(batchFile, count))
    }

    // record converter shutdowns, then shutdown savers
    case ConverterShutDownConfirm(batchFile: BatchFile, count: Int) => {
      converterCount = converterCount - 1;
      if (converterCount <= 0) {
        saveStatement ! Broadcast(SaverShutDown(batchFile, count))
      }
    }

    // record saver shutdowns, then invoke batch complete handling
    case SaverShutDownConfirm(batchFile: BatchFile, count: Int) => {
      saverCount = saverCount - 1;
      if (saverCount <= 0) {
        markBatchComplete ! BatchClose(batchFile, count)
      }
    }

    // shut ourself down
    case Done => {
      val duration = (System.currentTimeMillis() - start + 0.5) / 1000;
      println(s"Done ${duration} seconds - sending Poison Pill")
      self ! PoisonPill
    }
  }

  override def preStart = {
    println("V14processor - starting up")
  }

  override def postStop = {
    val duration = (System.currentTimeMillis() - start + 0.5) / 1000;
    println(s"V14processor - ${duration} seconds - shutting down")
  }
}

class ProcessBatchFileActor(name: String, next: ActorRef) extends Actor with ActorLogging {

  import Protocol._;

  var count = 0;

  def receive = {
    case ProcessBatchFile(batchFile) => {
      import scala.collection.JavaConverters._

      println("Processing Batch File ...")
      val seeds = Processor.loadFile(batchFile)
      seeds.map(rowNum => {
        next ! Processor.createStatementMessage(batchFile, rowNum)
        count += 1;
      })
      println(s"Sending Process Batch Done to ${context.parent.path.name}")
      sender() ! ProcessBatchDone(batchFile, count)
    }

  }

  override def postStop = {
    println("ConvertStatementActor - shutting down")
  }
}

class ConvertStatementActor(name: String, next: ActorRef) extends Actor with ActorLogging {

  import Protocol._;

  def receive = {
    case msg: ConvertV14Statement => {
      next ! Processor.convertToStatement(msg)
    }
    case ConverterShutDown(batchFile: BatchFile, count: Int) => {
      sender() ! ConverterShutDownConfirm(batchFile, count)
    }
  }

  override def postStop = {
    println("ConvertStatementActor - shutting down")
  }
}

class SaveStatementActor(name: String) extends Actor with ActorLogging {

  import Protocol._;

  def receive = {
    case StatementToSave(statement, batchFile) => {
      Processor.saveStatement(statement, batchFile)
    }
    case SaverShutDown(batchFile: BatchFile, count: Int) => {
      sender() ! SaverShutDownConfirm(batchFile, count)
    }
  }

  override def postStop = {
    println("SaveStatementActor - shutting down")
  }
}

class MarkBatchComplete(name: String, next: ActorRef) extends Actor with ActorLogging {

  import Protocol._;

  def receive = {
    case BatchClose(batchFile, statementCount) => {
      next ! Processor.markBatchComplete(batchFile, statementCount)
    }
  }

  override def postStop = {
    println("MarkBatchComplete - shutting down")
  }
}

class SendBatchAcknowledgment(name: String) extends Actor with ActorLogging {

  import Protocol._;

  def receive = {
    case SendAckEmail(batch) => {
      Processor.sendAckEmail(batch)
      context.parent ! Done
    }
  }
  override def postStop = {
    println("SendBatchAcknowledgment - shutting down")
  }
}

//
