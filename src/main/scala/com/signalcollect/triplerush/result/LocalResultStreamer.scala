/*
 * Copyright (C) 2015 Cotiviti Labs (nexgen.admin@cotiviti.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.signalcollect.triplerush.result

import scala.collection.immutable.Queue
import com.signalcollect.triplerush.TriplePattern
import com.signalcollect.triplerush.index.Index
import com.signalcollect.triplerush.query.{ ParticleDebug, QueryExecutionHandler, QueryParticle }
import com.signalcollect.triplerush.util.Streamer
import akka.actor.Props
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Request
import com.signalcollect.triplerush.query.QueryExecutionHandler.RegisterForQuery
import com.signalcollect.triplerush.query.QueryExecutionHandler.RequestResultsForQuery
import akka.contrib.pattern.ReceivePipeline

object LocalResultStreamer {

  val emptyQueue = Queue.empty[Array[Int]]

  def props(queryId: Int,
            query: Seq[TriplePattern],
            tickets: Long,
            numberOfSelectVariables: Int): Props = Props(
    new LocalResultStreamer(queryId, query, tickets, numberOfSelectVariables))

}

// TODO: Define timeout and terminate when it is reached.
// TODO: Max queue size? What to do when full?
final class LocalResultStreamer(
  queryId: Int,
  query: Seq[TriplePattern],
  tickets: Long,
  numberOfSelectVariables: Int)
    extends Streamer[Array[Int]] with ActorPublisher[Array[Int]] with ReceivePipeline {

  def bufferSize = QueryExecutionHandler.maxBufferPerQuery
  var completed = false

  override def preStart(): Unit = {
    assert(numberOfSelectVariables > 0)
    if (query.length == 0) {
      // No patterns, no results: complete immediately.
      self ! Streamer.Completed
    } else {
      val particle = QueryParticle(
        patterns = query,
        queryId = queryId,
        numberOfSelectVariables = numberOfSelectVariables,
        tickets = tickets)
      QueryExecutionHandler.shard(context.system) ! RegisterForQuery(queryId)
      QueryExecutionHandler.shard(context.system) ! RequestResultsForQuery(queryId, queue.freeCapacity)
      println(s"Sending ${ParticleDebug(particle)} on its merry way.")
      Index.shard(context.system) ! particle
    }
  }

  def receive = {
    case Streamer.DeliverFromQueue =>
      println(s"local result streamer got DeliverFromQueue (size=${queue.size}, totalDemand=$totalDemand)")
      if (totalDemand < queue.size) {
        // Safe conversion to Int, because smaller than queue size.
        queue.batchProcessAtMost(totalDemand.toInt, onNext)
      } else {
        queue.batchProcessAtMost(queue.size, onNext)
      }
      if (completed && queue.isEmpty) {
        println(s"$self is completing the outgoing stream")
        onCompleteThenStop()
      }
    case Streamer.Completed =>
      println("got completed message from upstream")
      completed = true
  }

}
