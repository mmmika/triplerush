/*
 *  @author Philip Stutz
 *  @author Mihaela Verman
 *  
 *  Copyright 2013 University of Zurich
 *      
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *         http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */

package com.signalcollect.triplerush

import scala.util.Random
import com.signalcollect.Edge
import com.signalcollect.GraphEditor
import scala.util.Sorting
import com.signalcollect.examples.CompactIntSet

object SignalSet extends Enumeration with Serializable {
  val BoundSubject = Value
  val BoundPredicate = Value
  val BoundObject = Value
}

/**
 * This vertex represents part of the TripleRush index.
 * The edge representation can currently only be modified during graph loading.
 * After graph loading, the `optimizeEdgeRepresentation` has to be called.
 * Query processing can only start once the edge representation has been optimized.
 */
class IndexVertex(id: TriplePattern) extends PatternVertex(id) {

  def optimizeEdgeRepresentation {
    childDeltasOptimized = CompactIntSet.create(childDeltas.toArray)
    childDeltas = null
  }

  override def edgeCount = edgeCounter

  var edgeCounter = 0

  var childDeltas = List[Int]()

  var childDeltasOptimized: Array[Byte] = null //TODO: Figure out if this is more elegant using ArrayBuffer

  override def removeAllEdges(graphEditor: GraphEditor[Any, Any]): Int = {
    childDeltas = List[Int]() // TODO: Make sure this still works as intended once we add index optimizations.
    edgeCount
  }

  override def addEdge(e: Edge[_], graphEditor: GraphEditor[Any, Any]): Boolean = {
    require(childDeltas != null)
    edgeCounter += 1
    val placeholderEdge = e.asInstanceOf[PlaceholderEdge]
    childDeltas = placeholderEdge.childDelta :: childDeltas
    true
  }

  def processSamplingQuery(query: PatternQuery, graphEditor: GraphEditor[Any, Any]) {
    throw new UnsupportedOperationException("Sampling queries are currently unsupported.")
    //    val targetIdCount = targetIds.length
    //    val bins = new Array[Long](targetIdCount)
    //    for (i <- 1l to query.tickets) {
    //      val randomIndex = Random.nextInt(targetIdCount)
    //      bins(randomIndex) += 1
    //    }
    //    val complete: Boolean = bins forall (_ > 0)
    //    var binIndex = 0
    //    for (targetId <- targetIds) {
    //      val ticketsForEdge = bins(binIndex)
    //      if (ticketsForEdge > 0) {
    //        val ticketEquippedQuery = query.withTickets(ticketsForEdge, complete)
    //        graphEditor.sendSignal(ticketEquippedQuery, targetId, None)
    //      }
    //      binIndex += 1
    //    }
  }

  protected val childPatternCreator = id.childPatternRecipe

  def processFullQuery(query: PatternQuery, graphEditor: GraphEditor[Any, Any]) {
    require(childDeltasOptimized != null)
    val targetIdCount = edgeCount
    val avg = query.tickets / targetIdCount
    val complete = avg > 0
    var extras = query.tickets % targetIdCount
    val averageTicketQuery = query.withTickets(avg, complete)
    val aboveAverageTicketQuery = query.withTickets(avg + 1, complete)
    CompactIntSet.foreach(childDeltasOptimized, childDelta => {
      val targetId = childPatternCreator(childDelta)
      if (extras > 0) {
        graphEditor.sendSignal(aboveAverageTicketQuery, targetId, None)
        extras -= 1
      } else if (complete) {
        graphEditor.sendSignal(averageTicketQuery, targetId, None)
      }
    })
  }

  override def process(query: PatternQuery, graphEditor: GraphEditor[Any, Any]) {
    if (query.isSamplingQuery) {
      processSamplingQuery(query, graphEditor)
    } else {
      processFullQuery(query, graphEditor)
    }
  }
}