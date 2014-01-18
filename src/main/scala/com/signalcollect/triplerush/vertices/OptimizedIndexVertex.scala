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

import com.signalcollect.util.Ints._
import com.signalcollect.triplerush.TriplePattern
import com.signalcollect.util.IntSet
import com.signalcollect.util.SplayIntSet
import com.signalcollect.GraphEditor

abstract class OptimizedIndexVertex(
  id: TriplePattern) extends IndexVertex(id) {

  override def afterInitialization(graphEditor: GraphEditor[Any, Any]) {
    super.afterInitialization(graphEditor)
    optimizedChildDeltas = new MemoryEfficientSplayIntSet
  }

  @transient var optimizedChildDeltas: SplayIntSet = _

  override def edgeCount = {
    if (optimizedChildDeltas != null) optimizedChildDeltas.size else 0
  }
  def cardinality = optimizedChildDeltas.size

  @inline def foreachChildDelta(f: Int => Unit) = optimizedChildDeltas.foreach(f)

  def addChildDelta(delta: Int): Boolean = {
    val deltasBeforeInsert = optimizedChildDeltas
    val wasInserted = optimizedChildDeltas.insert(delta)
    wasInserted
  }

}
