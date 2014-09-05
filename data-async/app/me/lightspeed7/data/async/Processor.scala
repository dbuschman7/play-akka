package me.lightspeed7.data.async

import me.lightspeed7.data.async.Protocol.ConvertV14Statement
import scala.collection.immutable.List
import me.lightspeed7.data.async.Protocol.ConvertV14Statement
import me.lightspeed7.data.async.Protocol.StatementToSave
import me.lightspeed7.data.async.Protocol.SendAckEmail
import me.lightspeed7.data.async.Protocol.Done

object Processor {

  def loadFile(batchFile: BatchFile): List[Int] = {
    Thread.sleep(1000);
    List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
  }

  def createStatementMessage(batchFile: BatchFile, rowNum: Int): ConvertV14Statement = {
    ConvertV14Statement(batchFile)
  }

  def convertToStatement(msg: ConvertV14Statement) = {
    Thread.sleep(500)
    println("Converting to statement ...")
    val stmt = new Statement()
    // DO MORE HERE
    StatementToSave(stmt, msg.batchFile)
  }

  def saveStatement(statement: Statement, batchFile: BatchFile) = {
    Thread.sleep(1000)
    println("Saving statement ...")
  }

  def markBatchComplete(batchFile: BatchFile, statementCount: Int): SendAckEmail = {
    Thread.sleep(2000)
    println(s"Mark Batch Complete ${batchFile.id} - count = ${statementCount}")
    val batch: Batch = new Batch(batchFile.id)
    SendAckEmail(batch)
  }

  def sendAckEmail(batch: Batch): Done = {
    println("Sending Batch Email")
    Thread.sleep(200)
    Done()
  }

}