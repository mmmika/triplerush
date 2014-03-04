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

package com.signalcollect.triplerush.vertices

import com.signalcollect.triplerush.TriplePattern
import com.signalcollect.triplerush.QueryParticle._
import com.signalcollect.triplerush.CardinalityRequest
import com.signalcollect.GraphEditor
import com.signalcollect.triplerush.CardinalityAndEdgeCountReply

final class PIndex(id: TriplePattern) extends CardinalityCountingIndex(id)
  with Forwarding {

  assert(id.s == 0 && id.p != 0 && id.o == 0)
  def nextRoutingAddress(childDelta: Int) = TriplePattern(childDelta, id.p, 0)

  override def handleCardinalityRequest(c: CardinalityRequest, graphEditor: GraphEditor[Any, Any]) {
    graphEditor.sendSignal(CardinalityAndEdgeCountReply(
      c.forPattern, cardinality, edgeCount), c.requestor, None)
  }

}
