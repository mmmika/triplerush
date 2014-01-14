package com.signalcollect.triplerush

import scala.concurrent.Future

trait QueryEngine {
  def addEncodedTriple(s: Int, p: Int, o: Int)
  def prepareExecution
  def executeQuery(q: QuerySpecification): Traversable[Array[Int]]
  def awaitIdle
  def shutdown
}
